package com.collabflow.websocket;

import com.collabflow.auth.JwtService;
import java.util.List;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Where WebSocket authentication actually happens in this app - <b>not</b> at the HTTP
 * handshake. A browser's native {@code WebSocket} API cannot set arbitrary headers (no
 * {@code Authorization} header) on the upgrade request, so the handshake endpoint
 * ({@code /ws}) is left public in {@code SecurityConfig} and authentication is deferred to the
 * first STOMP frame instead - the {@code CONNECT} frame, whose headers are just part of the
 * STOMP text protocol carried over the now-open WebSocket connection, with no browser-API
 * restriction on what they can contain.
 *
 * <p><b>Connection lifecycle</b>:</p>
 * <ol>
 *   <li>Client opens a WebSocket to {@code /ws} (HTTP upgrade - unauthenticated, permitted).</li>
 *   <li>Client sends a STOMP {@code CONNECT} frame with an {@code Authorization: Bearer <jwt>}
 *       header. This interceptor validates it here, via the same {@link JwtService} the HTTP
 *       filter chain uses, and sets the resulting principal on the STOMP session.</li>
 *   <li>Missing or invalid token at {@code CONNECT}: this method throws, which rejects the
 *       STOMP session (the client receives an {@code ERROR} frame and the connection is
 *       closed) - a WebSocket session can exist without ever becoming an authenticated STOMP
 *       session.</li>
 *   <li>Every subsequent frame on this session (SUBSCRIBE, DISCONNECT, and any future
 *       client-sent frames) carries the principal set here for the lifetime of the
 *       connection - no per-frame re-validation, matching how the JWT itself already has a
 *       short (15 minute default) expiry; a long-lived WebSocket session simply keeps whatever
 *       principal it authenticated with at {@code CONNECT} until the client disconnects or
 *       reconnects.</li>
 * </ol>
 *
 * <p><b>Disconnect / reconnection</b>: this class doesn't need to do anything special for
 * disconnects - Spring's STOMP session and the simple broker's subscription registry are
 * cleaned up automatically when the underlying WebSocket closes. A client that reconnects
 * (network drop, tab reactivated) is a brand-new STOMP session as far as this interceptor is
 * concerned - it must send a fresh {@code CONNECT} with a still-valid access token. If the
 * access token has since expired, reconnection fails the same way an expired token fails any
 * HTTP request - the frontend's normal token-refresh flow (Phase 3/4) is what a real client
 * would use before attempting to reconnect, not something this interceptor handles itself.</p>
 */
@Component
public class JwtStompAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    public JwtStompAuthInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            UUID userId = authenticate(accessor);
            accessor.setUser(new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of()));
        }
        return message;
    }

    private UUID authenticate(StompHeaderAccessor accessor) {
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        String bearer = (authHeaders == null || authHeaders.isEmpty()) ? null : authHeaders.get(0);
        if (bearer == null || !bearer.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing Authorization header on STOMP CONNECT");
        }
        return jwtService.validateAndExtractUserId(bearer.substring("Bearer ".length()))
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired access token"));
    }
}
