/**
 * WebSocket / STOMP infrastructure: the {@code /ws} endpoint configuration, the JWT
 * handshake interceptor, and a generic broadcaster used by domain modules to push
 * real-time updates to subscribed clients.
 *
 * <p>Marked OPEN because any domain module may need to broadcast an update (a task change,
 * a new notification) to connected clients. As with {@code kafka}, this module owns only the
 * transport - the payloads being broadcast are domain DTOs owned by their respective
 * modules.</p>
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.collabflow.websocket;
