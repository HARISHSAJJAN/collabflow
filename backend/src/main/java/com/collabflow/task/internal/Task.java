package com.collabflow.task.internal;

import com.collabflow.common.BaseEntity;
import com.collabflow.task.TaskPriority;
import com.collabflow.task.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Backs {@code tasks}. {@code projectId}, {@code assigneeId}, and {@code reporterId} are
 * plain UUID columns - the usual cross-module-reference convention (see {@code team.internal.
 * Team}'s Javadoc).
 *
 * <p>{@code version} is deliberately declared here, not on {@link BaseEntity}: optimistic
 * locking is a real cost (every update must re-check the version, and a losing concurrent
 * writer gets an error it must handle) that is only worth paying where concurrent writes to
 * the same row by different users are a realistic scenario the product actually needs to
 * protect against - two people editing the same task at once. Most other entities in this
 * schema (a user's own profile, a team's name) don't have that same multi-writer risk profile
 * in normal use, so they don't pay this cost. See docs/database.md's concurrency section and
 * ADR-006 for the full reasoning, and {@code TaskConcurrencyTest} (Phase 14) for this actually
 * being exercised under a real race, not just declared.</p>
 */
@Entity
@Table(name = "tasks")
public class Task extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Task() {
        // JPA
    }

    public Task(UUID projectId, String title, String description, TaskPriority priority, LocalDate dueDate, UUID reporterId) {
        this.projectId = projectId;
        this.title = title;
        this.description = description;
        this.priority = priority != null ? priority : TaskPriority.MEDIUM;
        this.dueDate = dueDate;
        this.reporterId = reporterId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
        this.assigneeId = assigneeId;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public Long getVersion() {
        return version;
    }
}
