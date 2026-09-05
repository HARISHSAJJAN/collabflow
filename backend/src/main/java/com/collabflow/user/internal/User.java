package com.collabflow.user.internal;

import com.collabflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The JPA entity backing the {@code users} table (see V1 migration and docs/database.md).
 *
 * <p>This class is {@code public} because {@link UserRepository} and this module's own
 * {@code UserAccountService} (a sibling package, {@code com.collabflow.user}) need to use it -
 * plain Java package-private would block that same-module access too, since it's a different
 * package. The actual cross-module hiding is enforced by Spring Modulith, not by Java
 * visibility: {@code ModularityTests} (added in Phase 14) fails the build if any type outside
 * this module ever references anything under {@code user.internal}. Other modules that need
 * user data go through {@link com.collabflow.user.UserAccountService}, which returns plain
 * DTOs ({@link com.collabflow.user.UserCredentials}, {@link com.collabflow.user.UserSummary}) -
 * this is what lets the user module change its persistence model later without any other
 * module noticing.</p>
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {
        // JPA
    }

    public User(String email, String passwordHash, String fullName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
}
