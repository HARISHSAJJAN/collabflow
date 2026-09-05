package com.collabflow.websocket.internal;

import com.collabflow.comment.CommentCreatedEvent;
import com.collabflow.task.TaskAssignedEvent;
import com.collabflow.task.TaskCreatedEvent;
import com.collabflow.task.TaskPriorityChangedEvent;
import com.collabflow.task.TaskStatusChangedEvent;
import com.collabflow.websocket.ProjectUpdateBroadcaster;
import com.collabflow.websocket.ProjectUpdateMessage;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The concrete case {@code docs/architecture.md}'s "real-time update flow" diagram describes:
 * User A changes a task, User B (subscribed to {@code /topic/projects/{id}}) sees it without
 * refreshing. Listens to the same in-process Spring events {@code audit} already consumes -
 * this is a third, independent reaction added without touching any producer
 * (task/comment service).
 */
@Component
class TaskUpdateBroadcastListener {

    private final ProjectUpdateBroadcaster broadcaster;

    TaskUpdateBroadcastListener(ProjectUpdateBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @EventListener
    public void onTaskCreated(TaskCreatedEvent event) {
        broadcaster.broadcastToProject(event.projectId(), new ProjectUpdateMessage("TASK_CREATED", event));
    }

    @EventListener
    public void onTaskAssigned(TaskAssignedEvent event) {
        broadcaster.broadcastToProject(event.projectId(), new ProjectUpdateMessage("TASK_ASSIGNED", event));
    }

    @EventListener
    public void onTaskStatusChanged(TaskStatusChangedEvent event) {
        broadcaster.broadcastToProject(event.projectId(), new ProjectUpdateMessage("TASK_STATUS_CHANGED", event));
    }

    @EventListener
    public void onTaskPriorityChanged(TaskPriorityChangedEvent event) {
        broadcaster.broadcastToProject(event.projectId(), new ProjectUpdateMessage("TASK_PRIORITY_CHANGED", event));
    }

    @EventListener
    public void onCommentCreated(CommentCreatedEvent event) {
        broadcaster.broadcastToProject(event.projectId(), new ProjectUpdateMessage("COMMENT_ADDED", event));
    }
}
