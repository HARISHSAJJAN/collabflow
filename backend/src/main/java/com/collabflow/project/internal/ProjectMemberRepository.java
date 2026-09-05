package com.collabflow.project.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    List<ProjectMember> findByProjectIdOrderByAddedAt(UUID projectId);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);
}
