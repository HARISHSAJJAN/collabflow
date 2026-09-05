package com.collabflow.audit.internal;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    Page<AuditLog> findByTeamIdOrderByCreatedAtDesc(UUID teamId, Pageable pageable);
}
