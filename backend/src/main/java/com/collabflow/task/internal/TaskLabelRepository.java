package com.collabflow.task.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskLabelRepository extends JpaRepository<TaskLabel, UUID> {

    List<TaskLabel> findByTaskId(UUID taskId);

    void deleteByTaskIdAndLabel(UUID taskId, String label);

    boolean existsByTaskIdAndLabel(UUID taskId, String label);
}
