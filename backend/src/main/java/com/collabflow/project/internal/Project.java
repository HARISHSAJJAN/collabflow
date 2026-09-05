package com.collabflow.project.internal;

import com.collabflow.common.BaseEntity;
import com.collabflow.project.ProjectStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Backs {@code projects}. {@code teamId} and {@code createdBy} are plain UUID columns, not
 * JPA associations - same cross-module-reference convention as {@code team.internal.Team}
 * (see its Javadoc and docs/database.md).
 */
@Entity
@Table(name = "projects")
public class Project extends BaseEntity {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected Project() {
        // JPA
    }

    public Project(UUID teamId, String name, String description, UUID createdBy) {
        this.teamId = teamId;
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }
}
