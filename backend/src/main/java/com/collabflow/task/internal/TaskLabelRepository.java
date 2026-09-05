package com.collabflow.task.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskLabelRepository extends JpaRepository<TaskLabel, UUID> {

    List<TaskLabel> findByTaskId(UUID taskId);

    /** Batch form of {@link #findByTaskId}, used when labeling a whole page of tasks at once -
     * see {@code TaskService}'s Javadoc on why the single-task form would be an N+1 query
     * there. */
    List<TaskLabel> findByTaskIdIn(Collection<UUID> taskIds);

    void deleteByTaskIdAndLabel(UUID taskId, String label);

    boolean existsByTaskIdAndLabel(UUID taskId, String label);
}
