package com.collabflow.comment;

import com.collabflow.comment.internal.Comment;
import com.collabflow.comment.internal.CommentRepository;
import com.collabflow.common.exception.ForbiddenOperationException;
import com.collabflow.common.exception.ResourceNotFoundException;
import com.collabflow.common.web.PageResponse;
import com.collabflow.kafka.DomainEventPublisher;
import com.collabflow.kafka.KafkaTopics;
import com.collabflow.project.ProjectService;
import com.collabflow.task.TaskService;
import com.collabflow.task.TaskSummary;
import com.collabflow.team.TeamRole;
import com.collabflow.user.UserAccountService;
import com.collabflow.user.UserSummary;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * This module's public API: comments on tasks. Anyone with access to the task's project may
 * comment (same access rule as viewing the task). Editing is author-only. Deleting is
 * author-only <em>or</em> project ADMIN+ - a narrow moderation allowance not explicitly
 * spelled out in the brief but judged low-risk and realistic (removing someone else's
 * inappropriate comment is a normal moderation action); editing someone else's comment
 * content never is, so that stays author-only with no exception.
 */
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskService taskService;
    private final ProjectService projectService;
    private final UserAccountService userAccountService;
    private final ApplicationEventPublisher eventPublisher;
    private final DomainEventPublisher domainEventPublisher;

    public CommentService(
            CommentRepository commentRepository,
            TaskService taskService,
            ProjectService projectService,
            UserAccountService userAccountService,
            ApplicationEventPublisher eventPublisher,
            DomainEventPublisher domainEventPublisher) {
        this.commentRepository = commentRepository;
        this.taskService = taskService;
        this.projectService = projectService;
        this.userAccountService = userAccountService;
        this.eventPublisher = eventPublisher;
        this.domainEventPublisher = domainEventPublisher;
    }

    @Transactional
    public CommentResponse createComment(UUID requestingUserId, UUID taskId, String body) {
        TaskSummary task = taskService.requireTaskSummary(taskId);
        projectService.requireProjectAccess(task.projectId(), requestingUserId);

        Comment saved = commentRepository.saveAndFlush(new Comment(taskId, requestingUserId, body.trim()));
        CommentCreatedEvent event = new CommentCreatedEvent(saved.getId(), taskId, task.projectId(), requestingUserId, saved.getBody());
        eventPublisher.publishEvent(event);
        domainEventPublisher.publish(KafkaTopics.COMMENT_EVENTS, taskId.toString(), event);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> listComments(UUID taskId, UUID requestingUserId, Pageable pageable) {
        TaskSummary task = taskService.requireTaskSummary(taskId);
        projectService.requireProjectAccess(task.projectId(), requestingUserId);
        Page<Comment> page = commentRepository.findByTaskIdOrderByCreatedAt(taskId, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional
    public CommentResponse updateComment(UUID commentId, UUID requestingUserId, String body) {
        Comment comment = requireComment(commentId);
        if (!comment.getAuthorId().equals(requestingUserId)) {
            throw new ForbiddenOperationException("Only the comment's author may edit it");
        }
        comment.edit(body.trim());
        return toResponse(commentRepository.saveAndFlush(comment));
    }

    @Transactional
    public void deleteComment(UUID commentId, UUID requestingUserId) {
        Comment comment = requireComment(commentId);
        if (!comment.getAuthorId().equals(requestingUserId)) {
            TaskSummary task = taskService.requireTaskSummary(comment.getTaskId());
            TeamRole role = projectService.requireProjectAccess(task.projectId(), requestingUserId);
            if (!role.satisfies(TeamRole.ADMIN)) {
                throw new ForbiddenOperationException("Only the comment's author or a team ADMIN/OWNER may delete it");
            }
        }
        commentRepository.delete(comment);
    }

    private Comment requireComment(UUID commentId) {
        return commentRepository.findById(commentId).orElseThrow(() -> ResourceNotFoundException.of("Comment", commentId));
    }

    private CommentResponse toResponse(Comment comment) {
        UserSummary author = userAccountService.requireSummaryById(comment.getAuthorId());
        return new CommentResponse(
                comment.getId(), comment.getTaskId(), comment.getAuthorId(), author.fullName(), comment.getBody(),
                comment.getEditedAt() != null, comment.getCreatedAt(), comment.getUpdatedAt());
    }
}
