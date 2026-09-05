package com.collabflow.project;

import com.collabflow.common.web.PageResponse;
import com.collabflow.config.CurrentUserId;
import com.collabflow.project.dto.AddProjectMemberRequest;
import com.collabflow.project.dto.CreateProjectRequest;
import com.collabflow.project.dto.UpdateProjectRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@CurrentUserId UUID userId, @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse project = projectService.createProject(userId, request.teamId(), request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @GetMapping
    public ResponseEntity<PageResponse<ProjectResponse>> listTeamProjects(
            @CurrentUserId UUID userId,
            @RequestParam UUID teamId,
            @RequestParam(required = false) ProjectStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(projectService.listTeamProjects(teamId, userId, status, pageable));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> getProject(@CurrentUserId UUID userId, @PathVariable UUID projectId) {
        return ResponseEntity.ok(projectService.getProject(projectId, userId));
    }

    @PatchMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> updateProject(
            @CurrentUserId UUID userId, @PathVariable UUID projectId, @Valid @RequestBody UpdateProjectRequest request) {
        return ResponseEntity.ok(projectService.updateProject(projectId, userId, request.name(), request.description()));
    }

    @PostMapping("/{projectId}/archive")
    public ResponseEntity<ProjectResponse> archiveProject(@CurrentUserId UUID userId, @PathVariable UUID projectId) {
        return ResponseEntity.ok(projectService.archiveProject(projectId, userId));
    }

    @PostMapping("/{projectId}/unarchive")
    public ResponseEntity<ProjectResponse> unarchiveProject(@CurrentUserId UUID userId, @PathVariable UUID projectId) {
        return ResponseEntity.ok(projectService.unarchiveProject(projectId, userId));
    }

    @GetMapping("/{projectId}/members")
    public ResponseEntity<List<ProjectMemberResponse>> listMembers(@CurrentUserId UUID userId, @PathVariable UUID projectId) {
        return ResponseEntity.ok(projectService.listMembers(projectId, userId));
    }

    @PostMapping("/{projectId}/members")
    public ResponseEntity<ProjectMemberResponse> addMember(
            @CurrentUserId UUID userId, @PathVariable UUID projectId, @Valid @RequestBody AddProjectMemberRequest request) {
        ProjectMemberResponse member = projectService.addMember(projectId, userId, request.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @DeleteMapping("/{projectId}/members/{targetUserId}")
    public ResponseEntity<Void> removeMember(@CurrentUserId UUID userId, @PathVariable UUID projectId, @PathVariable UUID targetUserId) {
        projectService.removeMember(projectId, userId, targetUserId);
        return ResponseEntity.noContent().build();
    }
}
