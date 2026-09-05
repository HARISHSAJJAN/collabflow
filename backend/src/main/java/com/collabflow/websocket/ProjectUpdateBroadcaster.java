package com.collabflow.websocket;

import com.collabflow.common.TransactionUtils;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * The generic broadcaster any domain module can use to push a live update to everyone
 * currently viewing a project - see {@code internal.TaskUpdateBroadcastListener} for the
 * concrete case this project implements (task/comment changes), reacting to the same
 * in-process Spring events {@code audit} already consumes (see docs/architecture.md's "Two
 * kinds of cross-module events" - this is now a third, independent reaction to the same
 * publish call, added without touching the producers at all).
 *
 * <p>Broadcasts are deferred until the originating transaction commits
 * ({@link TransactionUtils#runAfterCommit}), for the same reason Kafka publishing and cache
 * eviction are: broadcasting a change before it's durably committed risks telling a connected
 * client about something that then rolls back.</p>
 */
@Component
public class ProjectUpdateBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(ProjectUpdateBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;

    public ProjectUpdateBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcastToProject(UUID projectId, Object payload) {
        TransactionUtils.runAfterCommit(() -> {
            String destination = "/topic/projects/" + projectId;
            messagingTemplate.convertAndSend(destination, payload);
            log.debug("Broadcast {} to {}", payload.getClass().getSimpleName(), destination);
        });
    }
}
