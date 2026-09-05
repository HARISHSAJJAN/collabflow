package com.collabflow.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A small, explicit cache-aside helper - deliberately not Spring's {@code @Cacheable}
 * annotation abstraction, so that cache keys, TTLs, hit/miss behavior, and Redis-failure
 * handling are all visible in one place rather than hidden behind annotation processing.
 * See docs/redis.md for the full write-up of what's cached, why, and the trade-offs.
 *
 * <p><b>Redis failure behavior - "fail open," not "fail closed"</b>: every method here catches
 * {@link DataAccessException} (the common superclass for Redis connectivity/timeout problems
 * in Spring Data Redis) and treats it as a cache miss / no-op, logging a warning rather than
 * propagating the failure. The cache is an optimization, not a source of truth - the caller
 * always falls back to loading from PostgreSQL on a miss (see {@code getOrLoad}), so a Redis
 * outage degrades this application to "slightly slower," never "broken." This is a
 * deliberate choice documented here because the opposite ("fail closed" - treat a cache
 * failure as a request failure) would be wrong for a cache specifically, even though it can
 * be the right choice for other Redis uses (e.g. distributed locking, where "I don't know if
 * I hold the lock" must not be treated as "I hold it").</p>
 */
@Component
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Cache-aside: return the cached value if present (a hit); otherwise call {@code loader},
     * cache its result under {@code key} for {@code ttl}, and return it (a miss). If Redis
     * itself is unavailable, skips straight to {@code loader} without caching the result -
     * see this class's Javadoc on fail-open behavior.
     */
    public <T> T getOrLoad(String key, Duration ttl, TypeReference<T> type, java.util.function.Supplier<T> loader) {
        Optional<T> cached = get(key, type);
        if (cached.isPresent()) {
            log.debug("Cache hit: {}", key);
            return cached.get();
        }
        log.debug("Cache miss: {}", key);
        T loaded = loader.get();
        put(key, loaded, ttl);
        return loaded;
    }

    public <T> Optional<T> get(String key, TypeReference<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, type));
        } catch (DataAccessException e) {
            log.warn("Redis unavailable reading cache key {} - falling back as a cache miss", key, e);
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize cached value for key {} - treating as a cache miss", key, e);
            return Optional.empty();
        }
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable writing cache key {} - proceeding without caching", key, e);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize value for cache key {} - not caching", key, e);
        }
    }

    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable evicting cache key {} - a stale value may be served until its TTL expires", key, e);
        }
    }

    /**
     * Evicts {@code key} only after the current transaction commits, instead of immediately.
     *
     * <p>This matters whenever the eviction happens inside the same transaction as the write
     * that made the old value stale (the common case - see e.g. {@code TeamService.changeRole}):
     * evicting immediately, before commit, opens a window where a concurrent read can miss the
     * now-empty cache entry, read the *still-uncommitted-change, pre-update* row from the
     * database (its own transaction sees the old committed state, not this transaction's
     * in-flight write), and re-cache that soon-to-be-stale value - which would then survive in
     * the cache until its TTL expires, well after this transaction's change actually commits.
     * Deferring the eviction to run only once the new value is durably committed closes that
     * window entirely: any reader that misses the cache after that point will see the
     * database's new, correct value.</p>
     *
     * <p>Falls back to evicting immediately if no transaction is active (this call is a no-op
     * safety net for callers outside a transaction, which none of this codebase's callers
     * currently are, but a future one could be).</p>
     */
    public void evictAfterCommit(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evict(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evict(key);
            }
        });
    }
}
