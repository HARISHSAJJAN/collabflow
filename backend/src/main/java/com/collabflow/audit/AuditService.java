package com.collabflow.audit;

import com.collabflow.audit.internal.AuditLog;
import com.collabflow.audit.internal.AuditLogRepository;
import com.collabflow.comment.CommentCreatedEvent;
import com.collabflow.common.web.PageResponse;
import com.collabflow.project.ProjectCreatedEvent;
import com.collabflow.task.TaskAssignedEvent;
import com.collabflow.task.TaskCreatedEvent;
import com.collabflow.task.TaskPriorityChangedEvent;
import com.collabflow.task.TaskStatusChangedEvent;
import com.collabflow.team.MemberAddedEvent;
import com.collabflow.team.MemberRemovedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * This module's public API is deliberately tiny: read-only queries for "what happened."
 * Every row is written by listening to a domain event published by another module (task,
 * project, team, comment) - {@code audit} never calls into any other module, only listens,
 * which is why it's safe for it to know about every other domain module's events without
 * creating a dependency cycle (nothing depends on audit calling it back).
 *
 * <p>These are plain in-process Spring events, not Kafka, for now - see
 * docs/architecture.md's "Two kinds of cross-module events." From Phase 10 onward, some of
 * these same event types are additionally published to Kafka for other consumers
 * (notifications); audit keeps listening to the in-process event regardless, since nothing
 * about audit logging needs cross-process delivery.</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> listForProject(UUID projectId, Pageable pageable) {
        return PageResponse.from(auditLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable).map(AuditService::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> listForTeam(UUID teamId, Pageable pageable) {
        return PageResponse.from(auditLogRepository.findByTeamIdOrderByCreatedAtDesc(teamId, pageable).map(AuditService::toResponse));
    }

    @EventListener
    @Transactional
    public void onTaskCreated(TaskCreatedEvent event) {
        record("TASK_CREATED", event.createdBy(), "TASK", event.taskId(), event.projectId(), null, null, Map.of("title", event.title()));
    }

    @EventListener
    @Transactional
    public void onTaskAssigned(TaskAssignedEvent event) {
        record("TASK_ASSIGNED", event.assignedBy(), "TASK", event.taskId(), event.projectId(), null, null, Map.of("assigneeId", String.valueOf(event.assigneeId())));
    }

    @EventListener
    @Transactional
    public void onTaskStatusChanged(TaskStatusChangedEvent event) {
        record("TASK_STATUS_CHANGED", event.changedBy(), "TASK", event.taskId(), event.projectId(), null,
                Map.of("status", event.oldStatus()), Map.of("status", event.newStatus()));
    }

    @EventListener
    @Transactional
    public void onTaskPriorityChanged(TaskPriorityChangedEvent event) {
        record("TASK_PRIORITY_CHANGED", event.changedBy(), "TASK", event.taskId(), event.projectId(), null,
                Map.of("priority", event.oldPriority()), Map.of("priority", event.newPriority()));
    }

    @EventListener
    @Transactional
    public void onProjectCreated(ProjectCreatedEvent event) {
        record("PROJECT_CREATED", event.createdBy(), "PROJECT", event.projectId(), event.projectId(), event.teamId(), null, Map.of("name", event.name()));
    }

    @EventListener
    @Transactional
    public void onMemberAdded(MemberAddedEvent event) {
        record("MEMBER_ADDED", event.addedBy(), "TEAM", event.teamId(), null, event.teamId(), null, Map.of("userId", event.userId()));
    }

    @EventListener
    @Transactional
    public void onMemberRemoved(MemberRemovedEvent event) {
        record("MEMBER_REMOVED", event.removedBy(), "TEAM", event.teamId(), null, event.teamId(), Map.of("userId", event.userId()), null);
    }

    @EventListener
    @Transactional
    public void onCommentCreated(CommentCreatedEvent event) {
        record("COMMENT_ADDED", event.authorId(), "COMMENT", event.commentId(), event.projectId(), null, null, Map.of("taskId", event.taskId()));
    }

    private void record(
            String action, UUID actorId, String resourceType, UUID resourceId,
            UUID projectId, UUID teamId, Object oldValue, Object newValue) {
        AuditLog entry = new AuditLog(actorId, action, resourceType, resourceId, projectId, teamId, toJson(oldValue), toJson(newValue));
        auditLogRepository.save(entry);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit log value of type {}", value.getClass(), e);
            return null;
        }
    }

    private static AuditLogResponse toResponse(AuditLog entry) {
        return new AuditLogResponse(
                entry.getId(), entry.getActorId(), entry.getAction(), entry.getResourceType(), entry.getResourceId(),
                entry.getOldValueJson(), entry.getNewValueJson(), entry.getCreatedAt());
    }
}
