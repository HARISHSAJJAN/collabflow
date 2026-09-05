package com.collabflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.collabflow.AbstractIntegrationTest;
import com.collabflow.auth.dto.AuthResponse;
import com.collabflow.auth.dto.LoginRequest;
import com.collabflow.auth.dto.RefreshRequest;
import com.collabflow.auth.dto.RegisterRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The registration/login/refresh/logout lifecycle end-to-end - real HTTP requests through the
 * full stack (filters, controllers, services, a real Postgres database), the automated
 * equivalent of the curl-based verification done by hand after Phase 3 (see docs/
 * troubleshooting.md and ADR-008 for the bugs that same manual process caught).
 */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerThenLoginSucceeds() {
        String email = uniqueEmail("alice");
        ResponseEntity<Map> register = restTemplate.postForEntity(
                baseUrl + "/api/v1/auth/register", new RegisterRequest(email, "SuperSecret123", "Alice"), Map.class);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<AuthResponse> login = restTemplate.postForEntity(
                baseUrl + "/api/v1/auth/login", new LoginRequest(email, "SuperSecret123"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().accessToken()).isNotBlank();
        assertThat(login.getBody().refreshToken()).isNotBlank();
    }

    @Test
    void registeringTheSameEmailTwiceConflicts() {
        String email = uniqueEmail("bob");
        var request = new RegisterRequest(email, "SuperSecret123", "Bob");
        restTemplate.postForEntity(baseUrl + "/api/v1/auth/register", request, Map.class);

        ResponseEntity<Map> second = restTemplate.postForEntity(baseUrl + "/api/v1/auth/register", request, Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void loginWithWrongPasswordFails() {
        String email = uniqueEmail("carol");
        restTemplate.postForEntity(baseUrl + "/api/v1/auth/register", new RegisterRequest(email, "SuperSecret123", "Carol"), Map.class);

        ResponseEntity<Map> login = restTemplate.postForEntity(
                baseUrl + "/api/v1/auth/login", new LoginRequest(email, "WrongPassword"), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshRotatesTheTokenAndInvalidatesTheOldOne() {
        String email = uniqueEmail("dave");
        restTemplate.postForEntity(baseUrl + "/api/v1/auth/register", new RegisterRequest(email, "SuperSecret123", "Dave"), Map.class);
        AuthResponse login = restTemplate.postForObject(
                baseUrl + "/api/v1/auth/login", new LoginRequest(email, "SuperSecret123"), AuthResponse.class);

        AuthResponse refreshed = restTemplate.postForObject(
                baseUrl + "/api/v1/auth/refresh", new RefreshRequest(login.refreshToken()), AuthResponse.class);
        assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());

        // The old (now-rotated) refresh token must no longer work.
        ResponseEntity<Map> reuseOldToken = restTemplate.postForEntity(
                baseUrl + "/api/v1/auth/refresh", new RefreshRequest(login.refreshToken()), Map.class);
        assertThat(reuseOldToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reusingAnAlreadyRotatedRefreshTokenRevokesTheWholeSessionFamily() {
        // The regression guard for the Phase 3 bug (docs/troubleshooting.md / ADR-008):
        // presenting an already-used refresh token must revoke every session for the account,
        // including the token issued by the rotation that made the old one stale.
        String email = uniqueEmail("erin");
        restTemplate.postForEntity(baseUrl + "/api/v1/auth/register", new RegisterRequest(email, "SuperSecret123", "Erin"), Map.class);
        AuthResponse login = restTemplate.postForObject(
                baseUrl + "/api/v1/auth/login", new LoginRequest(email, "SuperSecret123"), AuthResponse.class);
        AuthResponse rotated = restTemplate.postForObject(
                baseUrl + "/api/v1/auth/refresh", new RefreshRequest(login.refreshToken()), AuthResponse.class);

        // Replay the original (stolen-token scenario).
        restTemplate.postForEntity(baseUrl + "/api/v1/auth/refresh", new RefreshRequest(login.refreshToken()), Map.class);

        // The legitimately-rotated token must now ALSO be dead.
        ResponseEntity<Map> afterReuseDetected = restTemplate.postForEntity(
                baseUrl + "/api/v1/auth/refresh", new RefreshRequest(rotated.refreshToken()), Map.class);
        assertThat(afterReuseDetected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loggingOutRevokesTheRefreshTokenButNotTheStillLiveAccessToken() {
        String email = uniqueEmail("frank");
        restTemplate.postForEntity(baseUrl + "/api/v1/auth/register", new RegisterRequest(email, "SuperSecret123", "Frank"), Map.class);
        AuthResponse login = restTemplate.postForObject(
                baseUrl + "/api/v1/auth/login", new LoginRequest(email, "SuperSecret123"), AuthResponse.class);

        ResponseEntity<Void> logout = restTemplate.postForEntity(
                baseUrl + "/api/v1/auth/logout", new RefreshRequest(login.refreshToken()), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> refreshAfterLogout = restTemplate.postForEntity(
                baseUrl + "/api/v1/auth/refresh", new RefreshRequest(login.refreshToken()), Map.class);
        assertThat(refreshAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
