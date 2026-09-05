package com.collabflow.task.internal;

import com.collabflow.task.TaskPriority;
import com.collabflow.task.TaskStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    /** Backs "due date approaching" (Phase 11) - assigned, not finished, due on the given date. */
    @Query("""
            select t from Task t
            where t.dueDate = :dueDate
              and t.assigneeId is not null
              and t.status <> com.collabflow.task.TaskStatus.DONE
            """)
    List<Task> findDueOnAndAssignedAndNotDone(@Param("dueDate") LocalDate dueDate);

    @Query("""
            select t from Task t
            where t.projectId = :projectId
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
              and (:assigneeId is null or t.assigneeId = :assigneeId)
            """)
    Page<Task> search(
            @Param("projectId") UUID projectId,
            @Param("status") TaskStatus status,
            @Param("priority") TaskPriority priority,
            @Param("assigneeId") UUID assigneeId,
            Pageable pageable);

    Page<Task> findByAssigneeId(UUID assigneeId, Pageable pageable);

    /**
     * The full-featured search (Phase 13): everything {@link #search} covers, plus keyword
     * (full-text, via the generated {@code search_vector} column - see the V9 migration),
     * due-date range, and label. A <b>native</b> query, not JPQL, because JPQL has no way to
     * express the {@code @@} full-text-match operator or an {@code EXISTS} subquery against
     * {@code task_labels} as cleanly. {@code status}/{@code priority} are bound as plain
     * {@code String} (the caller passes {@code enum.name()}), sidestepping any ambiguity in
     * how a native query binds a Java enum parameter against a plain {@code varchar} column.
     *
     * <p><b>No dynamic {@code Sort} support</b> - a real, documented limitation, not an
     * oversight: Spring Data JPA's {@code Pageable}-driven sorting maps Java property names to
     * columns using the entity's JPA metadata, which native queries don't participate in (a
     * client requesting {@code ?sort=dueDate} would silently fail to match the actual
     * {@code due_date} column). This method instead applies a fixed, sensible order -
     * un-finished tasks with the nearest due date first, nulls last - since Sort passed in via
     * {@code pageable} here would not reliably work.</p>
     *
     * <p>Every optional parameter is explicitly {@code cast(... as ...)} in its {@code IS NULL}
     * check - found necessary by testing, not stylistic: PostgreSQL resolves a native query's
     * parameter types once, when the statement is prepared, from the query's structure alone
     * (before any value is bound). A parameter whose <em>only</em> appearance is
     * {@code :param IS NULL} gives the planner no typed context to infer from, and
     * {@code dueDateFrom} - a real {@code LocalDate} value, not even null, in the request that
     * surfaced this - failed with {@code ERROR: could not determine data type of parameter $8}.
     * The explicit cast gives every such parameter an unambiguous type regardless of whether
     * it's actually null at execution time.</p>
     */
    @Query(value = """
            select t.* from tasks t
            where t.project_id = :projectId
              and (cast(:status as varchar) is null or t.status = :status)
              and (cast(:priority as varchar) is null or t.priority = :priority)
              and (cast(:assigneeId as uuid) is null or t.assignee_id = :assigneeId)
              and (cast(:dueDateFrom as date) is null or t.due_date >= :dueDateFrom)
              and (cast(:dueDateTo as date) is null or t.due_date <= :dueDateTo)
              and (cast(:label as varchar) is null or exists (select 1 from task_labels tl where tl.task_id = t.id and tl.label = :label))
              and (cast(:keyword as varchar) is null or t.search_vector @@ plainto_tsquery('english', :keyword))
            order by (t.due_date is null), t.due_date, t.created_at desc
            """,
            countQuery = """
            select count(*) from tasks t
            where t.project_id = :projectId
              and (cast(:status as varchar) is null or t.status = :status)
              and (cast(:priority as varchar) is null or t.priority = :priority)
              and (cast(:assigneeId as uuid) is null or t.assignee_id = :assigneeId)
              and (cast(:dueDateFrom as date) is null or t.due_date >= :dueDateFrom)
              and (cast(:dueDateTo as date) is null or t.due_date <= :dueDateTo)
              and (cast(:label as varchar) is null or exists (select 1 from task_labels tl where tl.task_id = t.id and tl.label = :label))
              and (cast(:keyword as varchar) is null or t.search_vector @@ plainto_tsquery('english', :keyword))
            """,
            nativeQuery = true)
    Page<Task> advancedSearch(
            @Param("projectId") UUID projectId,
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("assigneeId") UUID assigneeId,
            @Param("dueDateFrom") LocalDate dueDateFrom,
            @Param("dueDateTo") LocalDate dueDateTo,
            @Param("label") String label,
            @Param("keyword") String keyword,
            Pageable pageable);
}
