package com.collabflow.task;

import com.collabflow.common.exception.ConflictException;
import com.collabflow.common.exception.ForbiddenOperationException;
import com.collabflow.common.exception.ResourceNotFoundException;
import com.collabflow.common.web.PageResponse;
import com.collabflow.kafka.DomainEventPublisher;
import com.collabflow.kafka.KafkaTopics;
import com.collabflow.project.ProjectService;
import com.collabflow.task.internal.Task;
import com.collabflow.task.internal.TaskLabel;
import com.collabflow.task.internal.TaskLabelRepository;
import com.collabflow.task.internal.TaskRepository;
import com.collabflow.team.TeamRole;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * This module's public API: task CRUD, status/priority/assignment changes, and labels.
 *
 * <p><b>Authorization policy</b> (matching the brief's "MEMBER: create/update assigned tasks
 * ... ADMIN: ... manage tasks" verbatim): any user with access to the project (an explicit
 * project member, or ADMIN+ on the team - see {@code ProjectService}) may create a task and
 * view any task in the project. Editing a task's title/description/priority/due date/status/
 * labels requires either team role ADMIN+ (any task) or being the task's current assignee
 * (only that task) - a MEMBER who is not assigned to a task cannot touch it even though they
 * can see it. <b>Assigning/unassigning a task is ADMIN+ only</b> - a MEMBER cannot hand work
 * to themselves or anyone else by editing the assignee field; deciding who works on what is a
 * management action in this system, not a self-service one. Deleting a task is ADMIN+ only.</p>
 *
 * <p><b>Concurrency</b>: every mutating method here loads the {@code Task} fresh within its
 * own transaction and saves it back; the entity's {@code @Version} field is what actually
 * prevents a lost update if two requests race - see {@code Task}'s Javadoc, ADR-006, and
 * {@code TaskConcurrencyTest} (Phase 14).</p>
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskLabelRepository taskLabelRepository;
    private final ProjectService projectService;
    private final ApplicationEventPublisher eventPublisher;
    private final DomainEventPublisher domainEventPublisher;
    private final MeterRegistry meterRegistry;

    public TaskService(
            TaskRepository taskRepository,
            TaskLabelRepository taskLabelRepository,
            ProjectService projectService,
            ApplicationEventPublisher eventPublisher,
            DomainEventPublisher domainEventPublisher,
            MeterRegistry meterRegistry) {
        this.taskRepository = taskRepository;
        this.taskLabelRepository = taskLabelRepository;
        this.projectService = projectService;
        this.eventPublisher = eventPublisher;
        this.domainEventPublisher = domainEventPublisher;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Publishes to both channels described in docs/architecture.md's "Two kinds of
     * cross-module events": the in-process Spring event (fast, same-JVM, consumed today by
     * {@code audit}) and the Kafka topic (for out-of-process consumers - today,
     * {@code notification}, added in Phase 10/11).
     */
    private void publishBoth(Object event, String topic, UUID aggregateKey) {
        eventPublisher.publishEvent(event);
        domainEventPublisher.publish(topic, aggregateKey.toString(), event);
    }

    @Transactional
    public TaskResponse createTask(
            UUID requestingUserId, UUID projectId, String title, String description,
            TaskPriority priority, LocalDate dueDate, UUID assigneeId) {
        TeamRole role = projectService.requireProjectAccess(projectId, requestingUserId);

        if (assigneeId != null) {
            requireCanAssign(projectId, requestingUserId, assigneeId, role);
        }

        Task task = new Task(projectId, title.trim(), blankToNull(description), priority, dueDate, requestingUserId);
        task.setAssigneeId(assigneeId);
        Task saved = taskRepository.saveAndFlush(task);
        meterRegistry.counter("collabflow.tasks.created", "priority", saved.getPriority().name()).increment();
        publishBoth(new TaskCreatedEvent(saved.getId(), projectId, saved.getTitle(), requestingUserId), KafkaTopics.TASK_EVENTS, saved.getId());
        if (assigneeId != null) {
            publishBoth(new TaskAssignedEvent(saved.getId(), projectId, assigneeId, requestingUserId), KafkaTopics.TASK_EVENTS, saved.getId());
        }
        return toResponse(saved, List.of());
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID taskId, UUID requestingUserId) {
        Task task = requireTask(taskId);
        projectService.requireProjectAccess(task.getProjectId(), requestingUserId);
        return toResponse(task, labelsOf(taskId));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> listTasks(
            UUID projectId, UUID requestingUserId, TaskStatus status, TaskPriority priority, UUID assigneeId, Pageable pageable) {
        projectService.requireProjectAccess(projectId, requestingUserId);
        Page<Task> page = taskRepository.search(projectId, status, priority, assigneeId, pageable);
        return toResponsePage(page);
    }

    /**
     * The full search endpoint (Phase 13): status/priority/assignee filters plus keyword
     * (full-text), due-date range, and label - see {@code TaskRepository.advancedSearch}'s
     * Javadoc for why this is a separate method (a native query, with a fixed sort order)
     * rather than folded into {@link #listTasks}.
     */
    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> searchTasks(
            UUID projectId, UUID requestingUserId, TaskStatus status, TaskPriority priority, UUID assigneeId,
            LocalDate dueDateFrom, LocalDate dueDateTo, String label, String keyword, Pageable pageable) {
        projectService.requireProjectAccess(projectId, requestingUserId);
        Page<Task> page = taskRepository.advancedSearch(
                projectId,
                status == null ? null : status.name(),
                priority == null ? null : priority.name(),
                assigneeId, dueDateFrom, dueDateTo, label, keyword, pageable);
        return toResponsePage(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> listMyAssignedTasks(UUID requestingUserId, Pageable pageable) {
        Page<Task> page = taskRepository.findByAssigneeId(requestingUserId, pageable);
        return toResponsePage(page);
    }

    @Transactional
    public TaskResponse updateTask(
            UUID taskId, UUID requestingUserId, String title, String description, TaskPriority priority, LocalDate dueDate) {
        Task task = requireTask(taskId);
        requireCanModify(task, requestingUserId);
        TaskPriority oldPriority = task.getPriority();
        task.setTitle(title.trim());
        task.setDescription(blankToNull(description));
        task.setPriority(priority != null ? priority : task.getPriority());
        task.setDueDate(dueDate);
        Task saved = taskRepository.saveAndFlush(task);
        if (priority != null && priority != oldPriority) {
            publishBoth(new TaskPriorityChangedEvent(taskId, task.getProjectId(), oldPriority, priority, requestingUserId), KafkaTopics.TASK_EVENTS, taskId);
        }
        return toResponse(saved, labelsOf(taskId));
    }

    @Transactional
    public TaskResponse changeStatus(UUID taskId, UUID requestingUserId, TaskStatus newStatus) {
        Task task = requireTask(taskId);
        requireCanModify(task, requestingUserId);
        TaskStatus oldStatus = task.getStatus();
        task.setStatus(newStatus);
        Task saved = taskRepository.saveAndFlush(task);
        if (newStatus != oldStatus) {
            meterRegistry.counter("collabflow.tasks.status_changed", "to", newStatus.name()).increment();
            publishBoth(new TaskStatusChangedEvent(taskId, task.getProjectId(), oldStatus, newStatus, requestingUserId), KafkaTopics.TASK_EVENTS, taskId);
        }
        return toResponse(saved, labelsOf(taskId));
    }

    @Transactional
    public TaskResponse assignTask(UUID taskId, UUID requestingUserId, UUID newAssigneeId) {
        Task task = requireTask(taskId);
        TeamRole role = projectService.requireProjectAccess(task.getProjectId(), requestingUserId);
        if (!role.satisfies(TeamRole.ADMIN)) {
            throw new ForbiddenOperationException("Only a team ADMIN or OWNER may assign tasks");
        }
        if (newAssigneeId != null) {
            requireCanAssign(task.getProjectId(), requestingUserId, newAssigneeId, role);
        }
        task.setAssigneeId(newAssigneeId);
        Task saved = taskRepository.saveAndFlush(task);
        publishBoth(new TaskAssignedEvent(taskId, task.getProjectId(), newAssigneeId, requestingUserId), KafkaTopics.TASK_EVENTS, taskId);
        return toResponse(saved, labelsOf(taskId));
    }

    @Transactional
    public void deleteTask(UUID taskId, UUID requestingUserId) {
        Task task = requireTask(taskId);
        TeamRole role = projectService.requireProjectAccess(task.getProjectId(), requestingUserId);
        if (!role.satisfies(TeamRole.ADMIN)) {
            throw new ForbiddenOperationException("Only a team ADMIN or OWNER may delete tasks");
        }
        taskRepository.delete(task);
    }

    @Transactional
    public TaskResponse addLabel(UUID taskId, UUID requestingUserId, String label) {
        Task task = requireTask(taskId);
        requireCanModify(task, requestingUserId);
        String normalized = label.trim();
        if (!taskLabelRepository.existsByTaskIdAndLabel(taskId, normalized)) {
            taskLabelRepository.save(new TaskLabel(taskId, normalized));
        }
        return toResponse(task, labelsOf(taskId));
    }

    @Transactional
    public TaskResponse removeLabel(UUID taskId, UUID requestingUserId, String label) {
        Task task = requireTask(taskId);
        requireCanModify(task, requestingUserId);
        taskLabelRepository.deleteByTaskIdAndLabel(taskId, label.trim());
        return toResponse(task, labelsOf(taskId));
    }

    // --- cross-module API, used by the comment module (Phase 8) and notification module (Phase 11) ---

    @Transactional(readOnly = true)
    public TaskSummary requireTaskSummary(UUID taskId) {
        Task task = requireTask(taskId);
        return new TaskSummary(task.getId(), task.getProjectId(), task.getAssigneeId(), task.getReporterId());
    }

    /** Used by {@code notification.internal.DueDateReminderJob}'s daily sweep - there is no "due date approaching" event to listen for, since nothing happens to trigger one. */
    @Transactional(readOnly = true)
    public List<DueSoonTask> findTasksDueOn(LocalDate date) {
        return taskRepository.findDueOnAndAssignedAndNotDone(date).stream()
                .map(t -> new DueSoonTask(t.getId(), t.getProjectId(), t.getAssigneeId(), t.getTitle(), t.getDueDate()))
                .toList();
    }

    // --- internal helpers ---

    private Task requireTask(UUID taskId) {
        return taskRepository.findById(taskId).orElseThrow(() -> ResourceNotFoundException.of("Task", taskId));
    }

    /** @throws ForbiddenOperationException unless the caller is ADMIN+ on the team, or is this task's current assignee. */
    private void requireCanModify(Task task, UUID requestingUserId) {
        TeamRole role = projectService.requireProjectAccess(task.getProjectId(), requestingUserId);
        boolean isAssignee = requestingUserId.equals(task.getAssigneeId());
        if (!role.satisfies(TeamRole.ADMIN) && !isAssignee) {
            throw new ForbiddenOperationException("Only the assigned user or a team ADMIN/OWNER may modify this task");
        }
    }

    private void requireCanAssign(UUID projectId, UUID requestingUserId, UUID assigneeId, TeamRole requestingRole) {
        boolean selfAssign = assigneeId.equals(requestingUserId);
        if (!selfAssign && !requestingRole.satisfies(TeamRole.ADMIN)) {
            throw new ForbiddenOperationException("Only a team ADMIN or OWNER may assign a task to someone else");
        }
        try {
            projectService.requireProjectAccess(projectId, assigneeId);
        } catch (ResourceNotFoundException e) {
            throw new ConflictException("The assignee must have access to this project");
        }
    }

    private List<String> labelsOf(UUID taskId) {
        return taskLabelRepository.findByTaskId(taskId).stream().map(TaskLabel::getLabel).toList();
    }

    /**
     * The page-listing equivalent of {@link #labelsOf} - found by testing under real load
     * (Phase 19's performance review, see docs/performance.md): calling {@code labelsOf} once
     * per row while mapping a page is a classic N+1 query, one extra round trip per task on the
     * page instead of one for the whole page. This fetches every label for every task id on the
     * page in a single {@code IN (...)} query and groups them in memory instead.
     */
    private PageResponse<TaskResponse> toResponsePage(Page<Task> page) {
        List<UUID> taskIds = page.getContent().stream().map(Task::getId).toList();
        Map<UUID, List<String>> labelsByTaskId = taskLabelRepository.findByTaskIdIn(taskIds).stream()
                .collect(Collectors.groupingBy(TaskLabel::getTaskId, Collectors.mapping(TaskLabel::getLabel, Collectors.toList())));
        return PageResponse.from(page.map(t -> toResponse(t, labelsByTaskId.getOrDefault(t.getId(), List.of()))));
    }

    private static TaskResponse toResponse(Task task, List<String> labels) {
        return new TaskResponse(
                task.getId(), task.getProjectId(), task.getTitle(), task.getDescription(), task.getStatus(), task.getPriority(),
                task.getAssigneeId(), task.getReporterId(), task.getDueDate(), labels, task.getVersion(),
                task.getCreatedAt(), task.getUpdatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
