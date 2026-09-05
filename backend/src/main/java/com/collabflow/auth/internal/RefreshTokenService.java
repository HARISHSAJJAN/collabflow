package com.collabflow.auth.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, validates, and rotates refresh tokens. This class is {@code public} because
 * {@code com.collabflow.auth.AuthService} (a sibling package in the same module) calls it -
 * but it lives under {@code auth.internal}, so Spring Modulith's boundary check still fails
 * the build if any *other* module ever depends on it. No other module has any business asking
 * for a raw refresh token; {@code AuthService} is meant to be the only caller anywhere in the
 * codebase.
 *
 * <p>Refresh tokens are opaque random values, not JWTs (contrast with
 * {@code com.collabflow.auth.JwtService}). A refresh token is only ever presented to one
 * endpoint ({@code POST /api/v1/auth/refresh}), which already has to hit the database to
 * check revocation status - so a self-contained, signature-verifiable JWT would add parsing
 * overhead for zero benefit here. Making it a random value looked up by hash also means
 * revocation is a simple, immediate database update, with no "wait for the JWT to expire"
 * lag a stateless token would have.</p>
 *
 * <p><b>Rotation</b>: every successful refresh revokes the presented token and issues a new
 * one, linked via {@code replaced_by_token_id}. If a token that has already been rotated is
 * presented again (its {@code revoked_at} is set but it's not the newest in its chain), that
 * is treated as a signal the token was stolen and replayed - see {@link #rotate}.</p>
 */
@Component
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final SessionRevocationService sessionRevocationService;
    private final Duration refreshTokenTtl;

    RefreshTokenService(
            RefreshTokenRepository repository,
            SessionRevocationService sessionRevocationService,
            @Value("${collabflow.jwt.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        this.repository = repository;
        this.sessionRevocationService = sessionRevocationService;
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
    }

    public record IssuedToken(String rawValue, UUID tokenId, Instant expiresAt) {}

    @Transactional
    public IssuedToken issue(UUID userId, String userAgent, String ipAddress) {
        String rawValue = generateRawToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(refreshTokenTtl);
        RefreshToken token = new RefreshToken(userId, hash(rawValue), now, expiresAt, userAgent, ipAddress);
        RefreshToken saved = repository.save(token);
        return new IssuedToken(rawValue, saved.getId(), expiresAt);
    }

    public enum RotationResult {
        ROTATED,
        NOT_FOUND,
        EXPIRED,
        REUSE_DETECTED
    }

    public record RotationOutcome(RotationResult result, UUID userId, IssuedToken issuedToken) {}

    /**
     * Validates a presented refresh token and, if valid, revokes it and issues a replacement.
     * If the token was already revoked (a sign it was already used once, or stolen and
     * replayed by an attacker after the legitimate rotation), every active session for that
     * user is revoked as a precaution and {@link RotationResult#REUSE_DETECTED} is returned.
     */
    @Transactional
    public RotationOutcome rotate(String rawValue, String userAgent, String ipAddress) {
        Optional<RefreshToken> found = repository.findByTokenHash(hash(rawValue));
        if (found.isEmpty()) {
            return new RotationOutcome(RotationResult.NOT_FOUND, null, null);
        }
        RefreshToken token = found.get();

        if (token.getRevokedAt() != null) {
            // Must commit even though this method's transaction is about to be rolled back by
            // the caller throwing InvalidRefreshTokenException - see SessionRevocationService's
            // Javadoc for why this can't be a plain call to `repository` here.
            sessionRevocationService.revokeAllActiveSessionsNow(token.getUserId());
            return new RotationOutcome(RotationResult.REUSE_DETECTED, token.getUserId(), null);
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            return new RotationOutcome(RotationResult.EXPIRED, token.getUserId(), null);
        }

        IssuedToken next = issue(token.getUserId(), userAgent, ipAddress);
        token.setRevokedAt(Instant.now());
        token.setReplacedByTokenId(next.tokenId());
        repository.save(token);
        return new RotationOutcome(RotationResult.ROTATED, token.getUserId(), next);
    }

    @Transactional
    public void revoke(String rawValue) {
        repository.findByTokenHash(hash(rawValue)).ifPresent(t -> {
            t.setRevokedAt(Instant.now());
            repository.save(t);
        });
    }

    @Transactional
    public int revokeAllForUser(UUID userId) {
        return repository.revokeAllActiveForUser(userId, Instant.now());
    }

    public record SessionView(UUID id, String userAgent, String ipAddress, Instant issuedAt, Instant expiresAt) {}

    public List<SessionView> listActiveSessions(UUID userId) {
        return repository.findByUserIdAndRevokedAtIsNullOrderByIssuedAtDesc(userId).stream()
                .map(t -> new SessionView(t.getId(), t.getUserAgent(), t.getIpAddress(), t.getIssuedAt(), t.getExpiresAt()))
                .toList();
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
