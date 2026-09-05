package com.collabflow.notification.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /**
     * An atomic "insert if absent" - {@code ON CONFLICT DO NOTHING} means a duplicate
     * {@code eventId} affects zero rows and throws no exception at all, rather than a
     * constraint violation. See {@code NotificationService.recordEventAndNotify}'s Javadoc
     * for why avoiding an exception-based check here matters, not just style: catching a
     * constraint-violation exception from a {@code save()} call, even one call away, was
     * found (via testing - see docs/troubleshooting.md) to still mark the surrounding
     * transaction rollback-only before the catch block ever ran, because
     * {@code JpaRepository}'s own transactional advice intercepts the exception first.
     *
     * @return 1 if this was a new event, 0 if {@code eventId} was already recorded
     */
    @Modifying
    @Query(value = "insert into processed_events (event_id, processed_at) values (:eventId, now()) on conflict (event_id) do nothing", nativeQuery = true)
    int tryInsert(@Param("eventId") UUID eventId);
}
