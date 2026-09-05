package com.collabflow.team;

import com.collabflow.common.web.PageResponse;
import com.collabflow.config.CurrentUserId;
import com.collabflow.team.dto.AddMemberRequest;
import com.collabflow.team.dto.ChangeRoleRequest;
import com.collabflow.team.dto.CreateTeamRequest;
import com.collabflow.team.dto.UpdateTeamRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/teams")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(@CurrentUserId UUID userId, @Valid @RequestBody CreateTeamRequest request) {
        TeamResponse team = teamService.createTeam(userId, request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(team);
    }

    @GetMapping
    public ResponseEntity<PageResponse<TeamResponse>> listMyTeams(
            @CurrentUserId UUID userId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(teamService.listMyTeams(userId, pageable));
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<TeamResponse> getTeam(@CurrentUserId UUID userId, @PathVariable UUID teamId) {
        return ResponseEntity.ok(teamService.getTeam(teamId, userId));
    }

    @PatchMapping("/{teamId}")
    public ResponseEntity<TeamResponse> updateTeam(
            @CurrentUserId UUID userId, @PathVariable UUID teamId, @Valid @RequestBody UpdateTeamRequest request) {
        return ResponseEntity.ok(teamService.updateTeam(teamId, userId, request.name(), request.description()));
    }

    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> deleteTeam(@CurrentUserId UUID userId, @PathVariable UUID teamId) {
        teamService.deleteTeam(teamId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{teamId}/members")
    public ResponseEntity<List<TeamMemberResponse>> listMembers(@CurrentUserId UUID userId, @PathVariable UUID teamId) {
        return ResponseEntity.ok(teamService.listMembers(teamId, userId));
    }

    @PostMapping("/{teamId}/members")
    public ResponseEntity<TeamMemberResponse> addMember(
            @CurrentUserId UUID userId, @PathVariable UUID teamId, @Valid @RequestBody AddMemberRequest request) {
        TeamMemberResponse member = teamService.addMemberByEmail(teamId, userId, request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @PatchMapping("/{teamId}/members/{targetUserId}")
    public ResponseEntity<TeamMemberResponse> changeRole(
            @CurrentUserId UUID userId,
            @PathVariable UUID teamId,
            @PathVariable UUID targetUserId,
            @Valid @RequestBody ChangeRoleRequest request) {
        return ResponseEntity.ok(teamService.changeRole(teamId, userId, targetUserId, request.role()));
    }

    @DeleteMapping("/{teamId}/members/{targetUserId}")
    public ResponseEntity<Void> removeMember(@CurrentUserId UUID userId, @PathVariable UUID teamId, @PathVariable UUID targetUserId) {
        teamService.removeMember(teamId, userId, targetUserId);
        return ResponseEntity.noContent().build();
    }
}
