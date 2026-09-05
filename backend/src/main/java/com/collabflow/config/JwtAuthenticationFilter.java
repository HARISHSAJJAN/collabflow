package com.collabflow.config;

import com.collabflow.auth.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs once per request, before any controller: if the {@code Authorization: Bearer <token>}
 * header carries a valid access token, the request's authenticated principal is set to the
 * token's subject (the user id, as a string) with no authorities. This app does not use
 * Spring Security {@code GrantedAuthority}/role checks - team and project roles
 * (OWNER/ADMIN/MEMBER) are business data, checked in service-layer code against the actual
 * team/project membership row, not something that can be baked into a generic "is this user
 * an ADMIN" check at the HTTP layer. This filter's only job is: is this request authenticated
 * at all, and if so, as whom.
 *
 * <p>If the header is missing, malformed, or the token is invalid/expired, this filter simply
 * does not set an authentication and lets the request continue unauthenticated - it is
 * {@link SecurityConfig}'s {@code authorizeHttpRequests} rules, not this filter, that decide
 * whether a given endpoint requires authentication and therefore whether an unauthenticated
 * request gets rejected (401) further down the chain.</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        extractToken(request)
                .flatMap(jwtService::validateAndExtractUserId)
                .ifPresent(this::setAuthentication);
        filterChain.doFilter(request, response);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return Optional.of(header.substring(BEARER_PREFIX.length()));
        }
        return Optional.empty();
    }

    private void setAuthentication(UUID userId) {
        var authentication = new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
