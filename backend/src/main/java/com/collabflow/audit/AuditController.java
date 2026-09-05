package com.collabflow.audit;

import com.collabflow.common.exception.BadRequestException;
import com.collabflow.common.web.PageResponse;
import com.collabflow.config.CurrentUserId;
import com.collabflow.project.ProjectService;
import com.collabflow.team.TeamService;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "View project/team activity" - a read-only view over what {@link AuditService} has
 * recorded. The authorization check (does this caller have access to the project/team being
 * asked about) lives here rather than in {@code AuditService} itself: {@code AuditService} is
 * a pure sink that only ever listens for events and answers queries, and never calls into
 * another module (see its Javadoc) - the access check necessarily depends on
 * {@code ProjectService}/{@code TeamService}, so it belongs at this presentation-layer
 * boundary rather than compromising that invariant.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {

    private final AuditService auditService;
    private final ProjectService projectService;
    private final TeamService teamService;

    public AuditController(AuditService auditService, ProjectService projectService, TeamService teamService) {
        this.auditService = auditService;
        this.projectService = projectService;
        this.teamService = teamService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AuditLogResponse>> listActivity(
            @CurrentUserId UUID userId,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID teamId,
            @PageableDefault(size = 30) Pageable pageable) {
        if (projectId != null) {
            projectService.requireProjectAccess(projectId, userId);
            return ResponseEntity.ok(auditService.listForProject(projectId, pageable));
        }
        if (teamId != null) {
            teamService.requireMembership(teamId, userId);
            return ResponseEntity.ok(auditService.listForTeam(teamId, pageable));
        }
        throw new BadRequestException("Either projectId or teamId is required");
    }
}
