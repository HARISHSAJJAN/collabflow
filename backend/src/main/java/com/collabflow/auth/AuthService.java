package com.collabflow.auth;

import com.collabflow.auth.dto.AuthResponse;
import com.collabflow.auth.dto.SessionResponse;
import com.collabflow.auth.internal.RefreshTokenService;
import com.collabflow.user.UserAccountService;
import com.collabflow.user.UserCredentials;
import com.collabflow.user.UserSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public AuthService(
            UserAccountService userAccountService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public UUID register(String email, String rawPassword, String fullName) {
        String passwordHash = passwordEncoder.encode(rawPassword);
        return userAccountService.register(email, passwordHash, fullName);
    }

    /**
     * Deliberately returns the same generic failure ("invalid email or password") whether the
     * email doesn't exist, the account is deactivated, or the password is wrong - not
     * distinguishing which one to an attacker is a basic account-enumeration defense.
     */
    @Transactional
    public AuthResponse login(String email, String rawPassword, String userAgent, String ipAddress) {
        UserCredentials credentials = userAccountService.findCredentialsByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!credentials.active() || !passwordEncoder.matches(rawPassword, credentials.passwordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        userAccountService.touchLastLogin(credentials.userId());
        String accessToken = jwtService.generateAccessToken(credentials.userId(), credentials.email());
        RefreshTokenService.IssuedToken refreshToken = refreshTokenService.issue(credentials.userId(), userAgent, ipAddress);
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
}
