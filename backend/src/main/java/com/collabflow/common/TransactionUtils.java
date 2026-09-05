package com.collabflow.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Extracted after the same "defer this side effect until the transaction actually commits"
 * pattern showed up a third time (Redis cache eviction, Kafka publishing, and now WebSocket
 * broadcasting) - see {@code cache.RedisCacheService#evictAfterCommit} and
 * {@code kafka.DomainEventPublisher#publish} for the two bugs this pattern exists to avoid
 * (a concurrent reader re-caching a soon-to-be-stale value; a "phantom" event for a database
 * change that then rolled back). Rather than copy the same
 * {@code TransactionSynchronizationManager} boilerplate a fourth time in
 * {@code websocket.WebSocketBroadcaster}, it's a shared utility now.
 */
public final class TransactionUtils {

    private TransactionUtils() {
    }

    /**
     * Runs {@code action} once the current transaction commits, or immediately if there is no
     * active transaction (so this is also safe to call from code with no transaction at all).
     */
    public static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
