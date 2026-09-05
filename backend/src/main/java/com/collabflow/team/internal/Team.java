package com.collabflow.team.internal;

import com.collabflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Backs the {@code teams} table. {@code createdBy} is a plain {@code UUID}, not a JPA
 * {@code @ManyToOne} to the user module's {@code User} entity - this codebase never lets a
 * JPA association cross a module boundary, because that would require importing another
 * module's internal entity class, which is exactly what Spring Modulith's boundary check
 * exists to prevent. The database-level foreign key to {@code users(id)} still exists (see
 * the V3 migration) and still enforces referential integrity; only the *Java object graph*
 * stops at the module boundary. When display data about the creator is needed, callers ask
 * {@code UserAccountService.findSummaryById(team.getCreatedBy())} instead of navigating an
 * object reference.
 */
@Entity
@Table(name = "teams")
public class Team extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected Team() {
        // JPA
    }

    public Team(String name, String description, UUID createdBy) {
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
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

    public UUID getCreatedBy() {
        return createdBy;
    }
}
