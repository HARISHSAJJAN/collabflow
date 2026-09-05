package com.collabflow.config;

import com.collabflow.auth.JwtService;
import com.collabflow.common.web.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The entire authentication/authorization posture of the API lives here. See
 * docs/security.md for the full review; the short version:
 *
 * <ul>
 *   <li><b>Stateless sessions</b> ({@code SessionCreationPolicy.STATELESS}): no server-side
 *       session, no session cookie. Every request authenticates itself via the JWT in the
 *       {@code Authorization} header. This is what makes the backend horizontally scalable
 *       without sticky sessions or a shared session store.</li>
 *   <li><b>CSRF is disabled</b>: CSRF protection defends session-cookie-based auth, where a
 *       browser automatically attaches cookies to cross-site requests. This API never uses
 *       cookies for authentication - the token lives in a header the browser will not attach
 *       on its own - so the attack CSRF protection exists for does not apply. It would need
 *       to be reconsidered if authentication ever moved to an httpOnly cookie.</li>
 *   <li><b>CORS</b> is explicit and configurable ({@code collabflow.cors.allowed-origins}),
 *       not wide open.</li>
 *   <li><b>Public endpoints</b> are an explicit allow-list (auth endpoints, health, API docs);
 *       everything else requires authentication by default, so a new controller added later
 *       is secure-by-default rather than accidentally public.</li>
 *   <li><b>{@code /ws/**} is public here too</b>, but is not actually open access - see
 *       {@code websocket.JwtStompAuthInterceptor}'s Javadoc: the WebSocket handshake itself
 *       can't carry an {@code Authorization} header (a browser API limitation), so
 *       authentication for that endpoint happens one protocol layer up, at the first STOMP
 *       frame, instead of here.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final String allowedOrigins;

    public SecurityConfig(
            JwtService jwtService,
            ObjectMapper objectMapper,
            @Value("${collabflow.cors.allowed-origins}") String allowedOrigins) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Cost factor 12 (default 10 in most examples; 12 is a deliberate, still-fast-enough
        // strengthening) - see docs/security.md for the trade-off between login latency and
        // resistance to offline brute-force if the password_hash column is ever leaked.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(this::handleUnauthenticated)
                        .accessDeniedHandler((req, res, ex) -> handleForbidden(req, res)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/ws/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void handleUnauthenticated(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            org.springframework.security.core.AuthenticationException ex) throws java.io.IOException {
        writeError(response, request.getRequestURI(), 401, "AUTHENTICATION_REQUIRED", "Authentication is required to access this resource");
    }

    private void handleForbidden(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        writeError(response, request.getRequestURI(), 403, "ACCESS_DENIED", "You do not have permission to access this resource");
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse response, String path, int status, String error, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiError.of(status, error, message, path));
    }
}
