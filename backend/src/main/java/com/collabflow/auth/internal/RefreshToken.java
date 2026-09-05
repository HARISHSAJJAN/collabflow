package com.collabflow.auth.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Backs the {@code refresh_tokens} table (V2 migration). Does not extend
 * {@code common.BaseEntity}: that base class's {@code createdAt}/{@code updatedAt} pair
 * doesn't fit here - this table's natural timestamps are {@code issuedAt}/{@code expiresAt}/
 * {@code revokedAt}, which have their own specific meaning, and there is no "updated_at"
 * column (a refresh token row is either inserted or revoked, never otherwise mutated).
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address")
    private String ipAddress;

    protected RefreshToken() {
        // JPA
    }

    RefreshToken(UUID userId, String tokenHash, Instant issuedAt, Instant expiresAt, String userAgent, String ipAddress) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    String getTokenHash() {
        return tokenHash;
    }

    Instant getIssuedAt() {
        return issuedAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getRevokedAt() {
        return revokedAt;
    }

    void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    UUID getReplacedByTokenId() {
        return replacedByTokenId;
    }

    void setReplacedByTokenId(UUID replacedByTokenId) {
        this.replacedByTokenId = replacedByTokenId;
    }

    String getUserAgent() {
        return userAgent;
    }

    String getIpAddress() {
        return ipAddress;
    }

    boolean isActive() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
