package com.collabflow.audit.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Backs {@code audit_logs}. {@code oldValueJson}/{@code newValueJson} are pre-serialized JSON
 * strings (produced by {@code AuditService} via Jackson before construction), mapped to a
 * native {@code jsonb} column with Hibernate 6's built-in {@code @JdbcTypeCode(SqlTypes.JSON)}
 * - no third-party "hibernate-types" library needed for this, unlike in older Hibernate 5
 * codebases.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "team_id")
    private UUID teamId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private String oldValueJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private String newValueJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
        // JPA
    }

    public AuditLog(
            UUID actorId, String action, String resourceType, UUID resourceId,
            UUID projectId, UUID teamId, String oldValueJson, String newValueJson) {
        this.actorId = actorId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.projectId = projectId;
        this.teamId = teamId;
        this.oldValueJson = oldValueJson;
        this.newValueJson = newValueJson;
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public String getOldValueJson() {
        return oldValueJson;
    }

    public String getNewValueJson() {
        return newValueJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
