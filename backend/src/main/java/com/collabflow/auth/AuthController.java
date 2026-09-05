package com.collabflow.auth;

import com.collabflow.auth.dto.AuthResponse;
import com.collabflow.auth.dto.LoginRequest;
import com.collabflow.auth.dto.RefreshRequest;
import com.collabflow.auth.dto.RegisterRequest;
import com.collabflow.auth.dto.SessionResponse;
import com.collabflow.config.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * documented in docs/api.md and docs/security.md. Login/register/refresh are rate-limited
 * once the Redis-backed limiter lands in Phase 9 - see docs/security.md for the interim gap.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, UUID>> register(@Valid @RequestBody RegisterRequest request) {
        UUID userId = authService.register(request.email(), request.password(), request.fullName());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("userId", userId));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
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

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
