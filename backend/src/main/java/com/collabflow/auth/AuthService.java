package com.collabflow.auth;

import com.collabflow.auth.dto.AuthResponse;
import com.collabflow.auth.dto.SessionResponse;
import com.collabflow.auth.internal.RefreshTokenService;
import com.collabflow.user.PasswordChangedEvent;
import com.collabflow.user.UserAccountService;
import com.collabflow.user.UserCredentials;
import com.collabflow.user.UserSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The auth module's public API and the only class in the codebase that is allowed to decide
 * "is this login attempt valid" or "should this refresh token be honored." See ADR-008 for
 * why access tokens (JwtService) and refresh tokens (RefreshTokenService) use different
 * mechanisms.
 */
@Service
public class AuthService {

    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final MeterRegistry meterRegistry;

    public AuthService(
            UserAccountService userAccountService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            MeterRegistry meterRegistry) {
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public UUID register(String email, String rawPassword, String fullName) {
        String passwordHash = passwordEncoder.encode(rawPassword);
        UUID userId = userAccountService.register(email, passwordHash, fullName);
        meterRegistry.counter("collabflow.auth.register").increment();
        return userId;
    }

    /**
     * Deliberately returns the same generic failure ("invalid email or password") whether the
     * email doesn't exist, the account is deactivated, or the password is wrong - not
     * distinguishing which one to an attacker is a basic account-enumeration defense.
     *
     * <p>The {@code collabflow.auth.login} counter's {@code result} tag (Phase 18) is the one
     * business metric on this endpoint worth watching in production: a sudden spike in
     * {@code result=failure} relative to {@code result=success}, sustained rather than a single
     * blip, is exactly the shape a credential-stuffing attempt or a client-side bug (e.g. a
     * frontend deploy that broke the login form) would produce - either way, something a human
     * should be paged about long before it shows up as a support ticket.</p>
     */
    @Transactional
    public AuthResponse login(String email, String rawPassword, String userAgent, String ipAddress) {
        UserCredentials credentials = userAccountService.findCredentialsByEmail(email).orElse(null);
        if (credentials == null || !credentials.active() || !passwordEncoder.matches(rawPassword, credentials.passwordHash())) {
            meterRegistry.counter("collabflow.auth.login", "result", "failure").increment();
            throw new BadCredentialsException("Invalid email or password");
        }

        userAccountService.touchLastLogin(credentials.userId());
        String accessToken = jwtService.generateAccessToken(credentials.userId(), credentials.email());
        RefreshTokenService.IssuedToken refreshToken = refreshTokenService.issue(credentials.userId(), userAgent, ipAddress);
        meterRegistry.counter("collabflow.auth.login", "result", "success").increment();
        return AuthResponse.bearer(credentials.userId(), accessToken, refreshToken.rawValue(), jwtService.getAccessTokenTtlSeconds());
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        RefreshTokenService.RotationOutcome outcome = refreshTokenService.rotate(rawRefreshToken, userAgent, ipAddress);
        return switch (outcome.result()) {
            case ROTATED -> {
                UserSummary user = userAccountService.requireSummaryById(outcome.userId());
                String accessToken = jwtService.generateAccessToken(user.id(), user.email());
                yield AuthResponse.bearer(
                        user.id(), accessToken, outcome.issuedToken().rawValue(), jwtService.getAccessTokenTtlSeconds());
            }
            case REUSE_DETECTED -> throw new InvalidRefreshTokenException(
                    "This refresh token was already used; all sessions for this account have been revoked as a precaution");
            case EXPIRED -> throw new InvalidRefreshTokenException("Refresh token has expired");
            case NOT_FOUND -> throw new InvalidRefreshTokenException("Invalid refresh token");
        };
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    @Transactional
    public void logoutAllSessions(UUID userId) {
        refreshTokenService.revokeAllForUser(userId);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(UUID userId) {
        return refreshTokenService.listActiveSessions(userId).stream()
                .map(s -> new SessionResponse(s.id(), s.userAgent(), s.ipAddress(), s.issuedAt(), s.expiresAt()))
                .toList();
    }

    /**
     * A password change should not leave pre-existing sessions valid - including a possible
     * attacker's, if the change was prompted by a suspected compromise. Listening for
     * {@link PasswordChangedEvent} (published by the user module, a plain in-process Spring
     * event - see that class's Javadoc for why this isn't a Kafka event) rather than the user
     * module calling back into auth directly avoids a module dependency cycle: auth already
     * depends on user (for {@code UserAccountService}), so user must never depend on auth.
     *
     * <p>{@code AFTER_COMMIT} on purpose: if the password-change transaction rolls back for any
     * reason, sessions must not be revoked for a change that never actually happened.</p>
     *
     * <p>Explicitly transactional here too, even though this delegates straight to the
     * already-{@code @Transactional} {@link #logoutAllSessions}: {@code
     * @TransactionalEventListener} only controls *when* this method runs relative to the
     * original transaction's commit - it does not open a transaction of its own. Without this
     * annotation, the call below is a same-class ("self") invocation that bypasses this
     * method's own proxy, so the bulk revoke query would run with no active transaction at all
     * and fail with {@code TransactionRequiredException} - a real bug caught by testing this
     * end-to-end (see docs/troubleshooting.md), not a hypothetical one. Propagation must be
     * {@code REQUIRES_NEW} specifically: Spring refuses to start an {@code @TransactionalEvent
     * Listener} method with plain {@code REQUIRED} propagation, since by the time
     * {@code AFTER_COMMIT} fires the original transaction is already gone - there is nothing
     * left to "require" joining.</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPasswordChanged(PasswordChangedEvent event) {
        logoutAllSessions(event.userId());
    }
}
