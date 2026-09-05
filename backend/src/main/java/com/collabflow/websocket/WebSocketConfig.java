package com.collabflow.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket, native only - deliberately no SockJS fallback. SockJS exists for
 * browsers/networks that can't do a real WebSocket upgrade (old IE, some restrictive
 * corporate proxies); every browser this project targets supports native WebSocket, so
 * SockJS would be extra protocol-negotiation complexity with no one left for it to help. This
 * is a scope decision, not an oversight - see docs/websocket.md.
 *
 * <p><b>Two destination prefixes, two purposes</b>:</p>
 * <ul>
 *   <li>{@code /topic/projects/{projectId}} - broadcast: everyone currently viewing a project
 *       gets the same task/comment update. See {@code ProjectUpdateBroadcaster}.</li>
 *   <li>{@code /user/queue/notifications} - per-user: Spring's user-destination machinery
 *       resolves this to a queue unique to the connected principal, so a notification is
 *       delivered only to the one person it's for. Requires the STOMP session to actually
 *       have an authenticated principal, which is exactly what
 *       {@link JwtStompAuthInterceptor} sets during {@code CONNECT}.</li>
 * </ul>
 *
 * <p>The message broker is Spring's <b>simple in-memory broker</b>
 * ({@code enableSimpleBroker}), not a relay to an external broker like RabbitMQ. It runs
 * inside this application instance and holds no state beyond the current process's connected
 * sessions - correct and sufficient for a single-instance deployment, but it means a message
 * broadcast by one instance would not reach a client connected to a different instance once
 * this application is horizontally scaled. See docs/websocket.md's scaling note.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtStompAuthInterceptor authInterceptor;
    private final String allowedOrigins;

    public WebSocketConfig(JwtStompAuthInterceptor authInterceptor, @Value("${collabflow.cors.allowed-origins}") String allowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // permitAll in SecurityConfig for this path: the HTTP upgrade handshake itself carries
        // no Authorization header a browser's native WebSocket client can set (see
        // JwtStompAuthInterceptor's Javadoc for where authentication actually happens instead).
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOrigins.split(","));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }

    // No setApplicationDestinationPrefixes("/app") and no @MessageMapping controller: this app
    // has no client-to-server STOMP messages, only server-to-client pushes.
}
