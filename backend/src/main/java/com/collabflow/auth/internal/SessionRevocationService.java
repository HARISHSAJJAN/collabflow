package com.collabflow.auth.internal;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exists as its own Spring bean - not just a method on {@link RefreshTokenService} - for one
 * specific reason: {@code REQUIRES_NEW} propagation only takes effect when a method is called
 * *through the Spring proxy*, i.e. from a different bean. A same-class ("self") call bypasses
 * the proxy entirely and would silently run in whatever transaction is already active.
 *
 * <p>This matters here because of a real bug caught while testing refresh-token reuse
 * detection (see docs/troubleshooting.md): the "revoke every session for this user" action
 * was originally a plain call inside {@link RefreshTokenService#rotate}, in the same
 * transaction as the caller ({@code AuthService.refresh}), which then throws
 * {@code InvalidRefreshTokenException} to report the failure to the client. Spring's default
 * rule is to roll back the transaction on any unchecked exception - which silently undid the
 * revocation that was supposed to be the security response to a detected token-theft attempt.
 * Routing the revocation through this REQUIRES_NEW-annotated method makes it commit
 * immediately, in its own transaction, regardless of what the caller does afterward.</p>
 */
@Component
class SessionRevocationService {

    private final RefreshTokenRepository repository;

    SessionRevocationService(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void revokeAllActiveSessionsNow(UUID userId) {
        repository.revokeAllActiveForUser(userId, Instant.now());
    }
}
