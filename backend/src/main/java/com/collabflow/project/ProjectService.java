package com.collabflow.project;

import com.collabflow.common.exception.ConflictException;
import com.collabflow.common.exception.ResourceNotFoundException;
import com.collabflow.common.web.PageResponse;
import com.collabflow.kafka.DomainEventPublisher;
import com.collabflow.kafka.KafkaTopics;
import com.collabflow.project.internal.Project;
import com.collabflow.project.internal.ProjectMember;
import com.collabflow.project.internal.ProjectMemberRepository;
import com.collabflow.project.internal.ProjectRepository;
import com.collabflow.team.TeamRole;
import com.collabflow.team.TeamService;
import com.collabflow.user.UserAccountService;
import com.collabflow.user.UserSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * This module's public API: project CRUD, archiving, and membership. A project always
 * belongs to exactly one team, and every authorization decision here ultimately asks the
 * {@code team} module "what role does this user hold on this project's team?" - projects have
 * no independent role system of their own (see the V4 migration's comment on why
 * {@code project_members} has no {@code role} column).
 *
 * <p><b>Authorization policy</b>: creating a project, editing it, archiving/unarchiving it,
 * and managing its membership all require at least {@link TeamRole#ADMIN} on the project's
 * team - matching the brief's authorization section verbatim ("ADMIN: manage project
 * members, manage tasks"). <b>Visibility</b> is asymmetric by design: an ADMIN or OWNER can
 * see and list every project belonging to their team, while a plain MEMBER only sees projects
 * they have been explicitly added to as a project member - this is what "MEMBER: ... view
 * project" in the brief means in practice: membership-gated visibility, not team-wide
 * visibility. A user with no relationship to the project at all gets the same
 * {@link ResourceNotFoundException} as a nonexistent project id, for the same reason
 * {@code TeamService} does this for teams.</p>
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TeamService teamService;
    private final UserAccountService userAccountService;
    private final ApplicationEventPublisher eventPublisher;
    private final DomainEventPublisher domainEventPublisher;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            TeamService teamService,
            UserAccountService userAccountService,
            ApplicationEventPublisher eventPublisher,
            DomainEventPublisher domainEventPublisher) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.teamService = teamService;
        this.userAccountService = userAccountService;
        this.eventPublisher = eventPublisher;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public ProjectResponse createProject(UUID requestingUserId, UUID teamId, String name, String description) {
        teamService.requireAtLeast(teamId, requestingUserId, TeamRole.ADMIN);
        Project project = projectRepository.saveAndFlush(new Project(teamId, name.trim(), blankToNull(description), requestingUserId));
        projectMemberRepository.saveAndFlush(new ProjectMember(project.getId(), requestingUserId));
        ProjectCreatedEvent event = new ProjectCreatedEvent(project.getId(), teamId, project.getName(), requestingUserId);
        eventPublisher.publishEvent(event);
        domainEventPublisher.publish(KafkaTopics.PROJECT_EVENTS, project.getId().toString(), event);
        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID projectId, UUID requestingUserId) {
        Project project = requireAccessibleProject(projectId, requestingUserId);
        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> listTeamProjects(UUID teamId, UUID requestingUserId, ProjectStatus statusFilter, Pageable pageable) {
        TeamRole role = teamService.requireMembership(teamId, requestingUserId);
        Page<Project> page;
        if (role.satisfies(TeamRole.ADMIN)) {
            page = statusFilter == null
                    ? projectRepository.findByTeamId(teamId, pageable)
                    : projectRepository.findByTeamIdAndStatus(teamId, statusFilter, pageable);
        } else {
            page = statusFilter == null
                    ? projectRepository.findAccessibleProjectsInTeam(teamId, requestingUserId, pageable)
                    : projectRepository.findAccessibleProjectsInTeam(teamId, requestingUserId, statusFilter, pageable);
        }
        return PageResponse.from(page.map(ProjectService::toResponse));
    }

    @Transactional
    public ProjectResponse updateProject(UUID projectId, UUID requestingUserId, String name, String description) {
        Project project = requireProjectForManagement(projectId, requestingUserId);
        requireNotArchived(project);
        project.setName(name.trim());
        project.setDescription(blankToNull(description));
        return toResponse(projectRepository.saveAndFlush(project));
    }

    @Transactional
    public ProjectResponse archiveProject(UUID projectId, UUID requestingUserId) {
        Project project = requireProjectForManagement(projectId, requestingUserId);
        project.setStatus(ProjectStatus.ARCHIVED);
        return toResponse(projectRepository.saveAndFlush(project));
    }

    @Transactional
    public ProjectResponse unarchiveProject(UUID projectId, UUID requestingUserId) {
        Project project = requireProjectForManagement(projectId, requestingUserId);
        project.setStatus(ProjectStatus.ACTIVE);
        return toResponse(projectRepository.saveAndFlush(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> listMembers(UUID projectId, UUID requestingUserId) {
        Project project = requireAccessibleProject(projectId, requestingUserId);
        return projectMemberRepository.findByProjectIdOrderByAddedAt(project.getId()).stream()
                .map(pm -> toMemberResponse(project.getTeamId(), pm))
                .toList();
    }

    @Transactional
    public ProjectMemberResponse addMember(UUID projectId, UUID requestingUserId, UUID targetUserId) {
        Project project = requireProjectForManagement(projectId, requestingUserId);
        // A project member must already be a member of the project's team - project
        // membership is a subset of team membership, never a separate user pool.
        teamService.requireMembership(project.getTeamId(), targetUserId);

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, targetUserId)) {
            throw new ConflictException("This user is already a member of the project");
        }
        ProjectMember saved = projectMemberRepository.saveAndFlush(new ProjectMember(projectId, targetUserId));
        return toMemberResponse(project.getTeamId(), saved);
    }

    @Transactional
    public void removeMember(UUID projectId, UUID requestingUserId, UUID targetUserId) {
        Project project = requireProjectForManagement(projectId, requestingUserId);
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(project.getId(), targetUserId)
                .orElseThrow(() -> ResourceNotFoundException.of("Project membership for user", targetUserId));
        projectMemberRepository.delete(member);
    }

    // --- Cross-module API, used by the task module (Phase 7) ---

    @Transactional(readOnly = true)
    public ProjectSummary requireProjectSummary(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + projectId + " was not found"));
        return new ProjectSummary(project.getId(), project.getTeamId(), project.getName(), project.getStatus());
    }

    @Transactional(readOnly = true)
    public boolean isProjectMember(UUID projectId, UUID userId) {
        return projectMemberRepository.existsByProjectIdAndUserId(projectId, userId);
    }

    /**
     * Returns the requesting user's effective team role if they may access this project
     * (either an explicit project member, or ADMIN+ on the team), for the caller to make
     * finer-grained decisions (e.g. "MEMBER may only modify tasks assigned to them").
     *
     * @throws ResourceNotFoundException if the user has no access to the project at all
     */
    @Transactional(readOnly = true)
    public TeamRole requireProjectAccess(UUID projectId, UUID userId) {
        Project project = requireAccessibleProject(projectId, userId);
        return teamService.requireMembership(project.getTeamId(), userId);
    }

    // --- internal helpers ---

    private Project requireAccessibleProject(UUID projectId, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + projectId + " was not found"));
        boolean hasAccess = isProjectMember(projectId, userId)
                || teamService.findRole(project.getTeamId(), userId).map(r -> r.satisfies(TeamRole.ADMIN)).orElse(false);
        if (!hasAccess) {
            throw new ResourceNotFoundException("Project " + projectId + " was not found");
        }
        return project;
    }

    private Project requireProjectForManagement(UUID projectId, UUID requestingUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + projectId + " was not found"));
        teamService.requireAtLeast(project.getTeamId(), requestingUserId, TeamRole.ADMIN);
        return project;
    }

    private static void requireNotArchived(Project project) {
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new ConflictException("Cannot modify an archived project; unarchive it first");
        }
    }

    private ProjectMemberResponse toMemberResponse(UUID teamId, ProjectMember member) {
        UserSummary user = userAccountService.requireSummaryById(member.getUserId());
        TeamRole teamRole = teamService.findRole(teamId, member.getUserId()).orElse(TeamRole.MEMBER);
        return new ProjectMemberResponse(user.id(), user.email(), user.fullName(), user.avatarUrl(), teamRole, member.getAddedAt());
    }

    private static ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(), project.getTeamId(), project.getName(), project.getDescription(),
                project.getStatus(), project.getCreatedBy(), project.getCreatedAt(), project.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
