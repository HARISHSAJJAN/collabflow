package com.collabflow.task.internal;

import com.collabflow.task.TaskPriority;
import com.collabflow.task.TaskStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {

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
}
