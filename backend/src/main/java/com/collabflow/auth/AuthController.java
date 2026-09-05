package com.collabflow.auth;

import com.collabflow.auth.dto.AuthResponse;
import com.collabflow.auth.dto.LoginRequest;
import com.collabflow.auth.dto.RefreshRequest;
import com.collabflow.auth.dto.RegisterRequest;
import com.collabflow.auth.dto.SessionResponse;
import com.collabflow.cache.RateLimiter;
import com.collabflow.common.exception.RateLimitExceededException;
import com.collabflow.config.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration, login, token refresh, logout, and session management. Full lifecycle
 * documented in docs/api.md and docs/security.md.
 *
 * <p>Login is rate-limited by <b>two independent keys</b> (see {@link RateLimiter} for the
 * algorithm and its documented fail-open behavior on a Redis outage): a tight, short-window
 * limit per client IP (stops a single source from hammering the endpoint - the most common
 * real-world abuse pattern), and a wider, longer-window limit per submitted email (Phase 15 -
 * stops a distributed brute force against one specific account from many different IPs, which
 * the IP-only limiter cannot see). Registration is rate-limited by IP only - repeatedly
 * registering the same email is already rejected by the uniqueness constraint, so a per-email
 * limiter there would add no protection the database doesn't already provide.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RateLimiter rateLimiter;
    private final int loginCapacity;
    private final Duration loginWindow;
    private final int loginByEmailCapacity;
    private final Duration loginByEmailWindow;
    private final int registerCapacity;
    private final Duration registerWindow;

    public AuthController(
            AuthService authService,
            RateLimiter rateLimiter,
            @Value("${collabflow.rate-limit.login.capacity}") int loginCapacity,
            @Value("${collabflow.rate-limit.login.window-seconds}") long loginWindowSeconds,
            @Value("${collabflow.rate-limit.login-by-email.capacity}") int loginByEmailCapacity,
            @Value("${collabflow.rate-limit.login-by-email.window-seconds}") long loginByEmailWindowSeconds,
            @Value("${collabflow.rate-limit.register.capacity}") int registerCapacity,
            @Value("${collabflow.rate-limit.register.window-seconds}") long registerWindowSeconds) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.loginCapacity = loginCapacity;
        this.loginWindow = Duration.ofSeconds(loginWindowSeconds);
        this.loginByEmailCapacity = loginByEmailCapacity;
        this.loginByEmailWindow = Duration.ofSeconds(loginByEmailWindowSeconds);
        this.registerCapacity = registerCapacity;
        this.registerWindow = Duration.ofSeconds(registerWindowSeconds);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, UUID>> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        requireWithinRateLimit("register", clientIp(httpRequest), registerCapacity, registerWindow);
        UUID userId = authService.register(request.email(), request.password(), request.fullName());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("userId", userId));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        requireWithinRateLimit("login-ip", clientIp(httpRequest), loginCapacity, loginWindow);
        requireWithinRateLimit("login-email", request.email().trim().toLowerCase(), loginByEmailCapacity, loginByEmailWindow);
        AuthResponse response = authService.login(
                request.email(), request.password(), httpRequest.getHeader("User-Agent"), clientIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        AuthResponse response = authService.refresh(
                request.refreshToken(), httpRequest.getHeader("User-Agent"), clientIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@CurrentUserId UUID userId) {
        authService.logoutAllSessions(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> listSessions(@CurrentUserId UUID userId) {
        return ResponseEntity.ok(authService.listSessions(userId));
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Void> revokeAllSessions(@CurrentUserId UUID userId) {
        authService.logoutAllSessions(userId);
        return ResponseEntity.noContent().build();
    }

    private void requireWithinRateLimit(String action, String identity, int capacity, Duration window) {
        String key = "ratelimit:" + action + ":" + identity;
        if (!rateLimiter.tryAcquire(key, capacity, window)) {
            throw new RateLimitExceededException("Too many " + action + " attempts; try again later");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
