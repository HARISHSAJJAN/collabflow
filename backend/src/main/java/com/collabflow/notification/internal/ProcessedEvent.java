package com.collabflow.notification.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

/**
 * The idempotency ledger backing {@code processed_events} - see that migration's comment and
 * {@code NotificationService.recordEventAndNotify}'s Javadoc for how this row existing already
 * is what makes a redelivered (duplicate) Kafka message a safe no-op instead of a duplicate
 * notification. The actual dedup check ({@code ProcessedEventRepository.tryInsert}) uses a
 * native atomic {@code INSERT ... ON CONFLICT DO NOTHING} rather than this entity's
 * {@code save()} - see that repository method's Javadoc for why an exception-free check was
 * necessary, not just simpler.
 *
 * <p>This class still implements {@link Persistable} defensively, for a real, separate bug
 * found earlier while arriving at that design (see docs/troubleshooting.md): this entity's
 * {@code @Id} is assigned by application code, not {@code @GeneratedValue}. Spring Data JPA's
 * default {@code isNew()} check - "is the id field null?" - is always false here, since the id
 * is set in the constructor before {@code save()} would ever be called. Left unfixed, that
 * makes {@code SimpleJpaRepository.save()} call {@code EntityManager.merge()} instead of
 * {@code persist()} for every call, including the very first one - and {@code merge()} on an
 * id that already exists performs an <em>update</em>, not an insert, so a duplicate id would
 * never violate the primary key or throw at all if anything in this codebase ever calls
 * {@code save()} on this entity directly in the future. Overriding {@link #isNew()} to be
 * {@code true} for every instance constructed by application code closes that trap ahead of
 * time, even though the current dedup path no longer depends on it.</p>
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent implements Persistable<UUID> {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @Transient
    private boolean isNew;

    protected ProcessedEvent() {
        this.isNew = false; // JPA hydrating an existing row
    }

    public ProcessedEvent(UUID eventId) {
        this.eventId = eventId;
        this.isNew = true; // application code creating a genuinely new marker
    }

    @Override
    public UUID getId() {
        return eventId;
    }

    public UUID getEventId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }
}
