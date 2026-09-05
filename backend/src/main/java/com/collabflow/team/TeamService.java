package com.collabflow.team;

import com.collabflow.common.exception.ConflictException;
import com.collabflow.common.exception.ForbiddenOperationException;
import com.collabflow.common.exception.ResourceNotFoundException;
import com.collabflow.common.web.PageResponse;
import com.collabflow.team.internal.Team;
import com.collabflow.team.internal.TeamMember;
import com.collabflow.team.internal.TeamMemberRepository;
import com.collabflow.team.internal.TeamRepository;
import com.collabflow.user.UserAccountService;
import com.collabflow.user.UserSummary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * This module's public API: team CRUD, membership, and role authorization. Every method that
 * acts on an existing team first resolves the requesting user's membership - there is no
 * "admin override" or superuser bypass; even the platform has no path to a team's data other
 * than being a member of it.
 *
 * <p><b>Authorization policy implemented here</b> (see docs/decisions.md for the reasoning):
 * team membership management (invite, remove, change role) and deleting the team are OWNER-
 * only. Editing the team's name/description is OWNER or ADMIN. Viewing the team and its
 * roster requires being a member at all, any role. A team must always have at least one
 * OWNER - the last OWNER can neither be removed nor demoted.</p>
 *
 * <p><b>Visibility policy</b>: a non-member requesting a team by id gets the same
 * {@link ResourceNotFoundException} as a nonexistent id - team existence is not revealed to
 * non-members. This keeps one consistent rule ("can't see it, don't know it exists") instead
 * of maintaining two different denial semantics (404 vs 403) across every module that has
 * this same "must be a member" shape (team, and project in Phase 6).</p>
 */
@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserAccountService userAccountService;
    private final ApplicationEventPublisher eventPublisher;

    public TeamService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            UserAccountService userAccountService,
            ApplicationEventPublisher eventPublisher) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.userAccountService = userAccountService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TeamResponse createTeam(UUID creatorId, String name, String description) {
        // saveAndFlush, not save: @CreationTimestamp/@UpdateTimestamp fields are populated by
        // Hibernate at flush time, not at save() call time. Reading team.getCreatedAt() right
        // after a plain save() - before this transaction's next flush, which without an
        // intervening query wouldn't happen until commit - would return null. This is a real
        // bug caught by testing (see docs/troubleshooting.md), not a hypothetical one: the
        // database row is always correct once the transaction commits regardless, but the
        // *response returned to this same request* would have silently had null timestamps.
        Team team = teamRepository.saveAndFlush(new Team(name.trim(), blankToNull(description), creatorId));
        teamMemberRepository.saveAndFlush(new TeamMember(team.getId(), creatorId, TeamRole.OWNER));
        return toResponse(team, TeamRole.OWNER);
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeam(UUID teamId, UUID requestingUserId) {
        Team team = requireTeam(teamId);
        TeamRole role = requireMembership(teamId, requestingUserId);
        return toResponse(team, role);
    }

    @Transactional(readOnly = true)
    public PageResponse<TeamResponse> listMyTeams(UUID userId, Pageable pageable) {
        Page<TeamMember> memberships = teamMemberRepository.findByUserId(userId, pageable);
        List<UUID> teamIds = memberships.getContent().stream().map(TeamMember::getTeamId).toList();
        Map<UUID, Team> teamsById = teamRepository.findAllById(teamIds).stream()
                .collect(Collectors.toMap(Team::getId, t -> t));
        Page<TeamResponse> page = memberships.map(m -> toResponse(teamsById.get(m.getTeamId()), m.getRole()));
        return PageResponse.from(page);
    }

    @Transactional
    public TeamResponse updateTeam(UUID teamId, UUID requestingUserId, String name, String description) {
        Team team = requireTeam(teamId);
        requireAtLeast(teamId, requestingUserId, TeamRole.ADMIN);
        team.setName(name.trim());
        team.setDescription(blankToNull(description));
        Team saved = teamRepository.saveAndFlush(team); // see createTeam's comment on why saveAndFlush
        TeamRole role = requireMembership(teamId, requestingUserId);
        return toResponse(saved, role);
    }

    @Transactional
    public void deleteTeam(UUID teamId, UUID requestingUserId) {
        requireTeam(teamId);
        requireAtLeast(teamId, requestingUserId, TeamRole.OWNER);
        teamRepository.deleteById(teamId);
    }

    @Transactional
    public TeamMemberResponse addMemberByEmail(UUID teamId, UUID requestingUserId, String email) {
        requireTeam(teamId);
        requireAtLeast(teamId, requestingUserId, TeamRole.OWNER);

        UserSummary newMember = userAccountService.findCredentialsByEmail(email)
                .flatMap(c -> userAccountService.findSummaryById(c.userId()))
                .orElseThrow(() -> new ResourceNotFoundException("No account exists with email " + email));

        if (teamMemberRepository.existsByTeamIdAndUserId(teamId, newMember.id())) {
            throw new ConflictException("This user is already a member of the team");
        }
        TeamMember saved = teamMemberRepository.saveAndFlush(new TeamMember(teamId, newMember.id(), TeamRole.MEMBER));
        eventPublisher.publishEvent(new MemberAddedEvent(teamId, newMember.id(), requestingUserId));
        return new TeamMemberResponse(newMember.id(), newMember.email(), newMember.fullName(), newMember.avatarUrl(), saved.getRole(), saved.getJoinedAt());
    }

    @Transactional
    public void removeMember(UUID teamId, UUID requestingUserId, UUID targetUserId) {
        requireTeam(teamId);
        requireAtLeast(teamId, requestingUserId, TeamRole.OWNER);
        TeamMember target = teamMemberRepository.findByTeamIdAndUserId(teamId, targetUserId)
                .orElseThrow(() -> ResourceNotFoundException.of("Team membership for user", targetUserId));

        if (target.getRole() == TeamRole.OWNER && teamMemberRepository.countByTeamIdAndRole(teamId, TeamRole.OWNER) <= 1) {
            throw new ConflictException("A team must always have at least one OWNER; transfer ownership before removing this member");
        }
        teamMemberRepository.delete(target);
        eventPublisher.publishEvent(new MemberRemovedEvent(teamId, targetUserId, requestingUserId));
    }

    @Transactional
    public TeamMemberResponse changeRole(UUID teamId, UUID requestingUserId, UUID targetUserId, TeamRole newRole) {
        requireTeam(teamId);
        requireAtLeast(teamId, requestingUserId, TeamRole.OWNER);
        TeamMember target = teamMemberRepository.findByTeamIdAndUserId(teamId, targetUserId)
                .orElseThrow(() -> ResourceNotFoundException.of("Team membership for user", targetUserId));

        if (target.getRole() == TeamRole.OWNER && newRole != TeamRole.OWNER
                && teamMemberRepository.countByTeamIdAndRole(teamId, TeamRole.OWNER) <= 1) {
            throw new ConflictException("A team must always have at least one OWNER; promote another member first");
        }
        target.setRole(newRole);
        TeamMember saved = teamMemberRepository.save(target);
        UserSummary user = userAccountService.requireSummaryById(targetUserId);
        return new TeamMemberResponse(user.id(), user.email(), user.fullName(), user.avatarUrl(), saved.getRole(), saved.getJoinedAt());
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listMembers(UUID teamId, UUID requestingUserId) {
        requireTeam(teamId);
        requireMembership(teamId, requestingUserId);
        return teamMemberRepository.findByTeamIdOrderByJoinedAt(teamId).stream()
                .map(m -> {
                    UserSummary user = userAccountService.requireSummaryById(m.getUserId());
                    return new TeamMemberResponse(user.id(), user.email(), user.fullName(), user.avatarUrl(), m.getRole(), m.getJoinedAt());
                })
                .toList();
    }

    // --- Cross-module API, used by the project module (Phase 6) for authorization checks ---

    @Transactional(readOnly = true)
    public Optional<TeamRole> findRole(UUID teamId, UUID userId) {
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId).map(TeamMember::getRole);
    }

    @Transactional(readOnly = true)
    public TeamSummary requireTeamSummary(UUID teamId) {
        Team team = requireTeam(teamId);
        return new TeamSummary(team.getId(), team.getName());
    }

    /** @throws ResourceNotFoundException if the user is not a member (team existence is not revealed) */
    public TeamRole requireMembership(UUID teamId, UUID userId) {
        return findRole(teamId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Team " + teamId + " was not found"));
    }

    /**
     * @throws ResourceNotFoundException if the user is not a member at all
     * @throws ForbiddenOperationException if the user is a member but below {@code minimum}
     */
    public void requireAtLeast(UUID teamId, UUID userId, TeamRole minimum) {
        TeamRole role = requireMembership(teamId, userId);
        if (!role.satisfies(minimum)) {
            throw new ForbiddenOperationException("This action requires at least the " + minimum + " role on this team");
        }
    }

    private Team requireTeam(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team " + teamId + " was not found"));
    }

    private static TeamResponse toResponse(Team team, TeamRole myRole) {
        return new TeamResponse(
                team.getId(), team.getName(), team.getDescription(), team.getCreatedBy(), myRole, team.getCreatedAt(), team.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
