package com.collabflow.task;

import com.collabflow.common.web.PageResponse;
import com.collabflow.config.CurrentUserId;
import com.collabflow.task.dto.AddLabelRequest;
import com.collabflow.task.dto.AssignTaskRequest;
import com.collabflow.task.dto.ChangeStatusRequest;
import com.collabflow.task.dto.CreateTaskRequest;
import com.collabflow.task.dto.UpdateTaskRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@CurrentUserId UUID userId, @Valid @RequestBody CreateTaskRequest request) {
        TaskResponse task = taskService.createTask(
                userId, request.projectId(), request.title(), request.description(), request.priority(), request.dueDate(), request.assigneeId());
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    @GetMapping
    public ResponseEntity<PageResponse<TaskResponse>> listTasks(
            @CurrentUserId UUID userId,
            @RequestParam UUID projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) UUID assigneeId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(taskService.listTasks(projectId, userId, status, priority, assigneeId, pageable));
    }

    /**
     * The full search endpoint (Phase 13) - status/priority/assignee (same as {@code GET /}),
     * plus keyword (full-text), a due-date range, and a label. Kept as its own endpoint rather
     * than folded into {@code GET /} because its sort order is fixed, not client-controlled -
     * see {@code TaskRepository.advancedSearch}'s Javadoc for why.
     */
    @GetMapping("/search")
    public ResponseEntity<PageResponse<TaskResponse>> searchTasks(
            @CurrentUserId UUID userId,
            @RequestParam UUID projectId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) LocalDate dueDateFrom,
            @RequestParam(required = false) LocalDate dueDateTo,
            @RequestParam(required = false) String label,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(taskService.searchTasks(
                projectId, userId, status, priority, assigneeId, dueDateFrom, dueDateTo, label, keyword, pageable));
    }

    /** "My assigned tasks" - the dashboard's central query, across every project. */
    @GetMapping("/me")
    public ResponseEntity<PageResponse<TaskResponse>> listMyAssignedTasks(
            @CurrentUserId UUID userId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(taskService.listMyAssignedTasks(userId, pageable));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponse> getTask(@CurrentUserId UUID userId, @PathVariable UUID taskId) {
        return ResponseEntity.ok(taskService.getTask(taskId, userId));
    }

    @PatchMapping("/{taskId}")
    public ResponseEntity<TaskResponse> updateTask(
            @CurrentUserId UUID userId, @PathVariable UUID taskId, @Valid @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(taskService.updateTask(taskId, userId, request.title(), request.description(), request.priority(), request.dueDate()));
    }

    @PatchMapping("/{taskId}/status")
    public ResponseEntity<TaskResponse> changeStatus(
            @CurrentUserId UUID userId, @PathVariable UUID taskId, @Valid @RequestBody ChangeStatusRequest request) {
        return ResponseEntity.ok(taskService.changeStatus(taskId, userId, request.status()));
    }

    @PatchMapping("/{taskId}/assignee")
    public ResponseEntity<TaskResponse> assignTask(
            @CurrentUserId UUID userId, @PathVariable UUID taskId, @RequestBody AssignTaskRequest request) {
        return ResponseEntity.ok(taskService.assignTask(taskId, userId, request.assigneeId()));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(@CurrentUserId UUID userId, @PathVariable UUID taskId) {
        taskService.deleteTask(taskId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{taskId}/labels")
    public ResponseEntity<TaskResponse> addLabel(
            @CurrentUserId UUID userId, @PathVariable UUID taskId, @Valid @RequestBody AddLabelRequest request) {
        return ResponseEntity.ok(taskService.addLabel(taskId, userId, request.label()));
    }

    @DeleteMapping("/{taskId}/labels/{label}")
    public ResponseEntity<TaskResponse> removeLabel(@CurrentUserId UUID userId, @PathVariable UUID taskId, @PathVariable String label) {
        return ResponseEntity.ok(taskService.removeLabel(taskId, userId, label));
    }
}
