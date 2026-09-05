package com.collabflow.cache;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * A Redis-backed <b>fixed-window counter</b> rate limiter, used on sensitive, cheap-to-abuse
 * endpoints (login, registration - see {@code AuthController}). Full write-up, including why
 * fixed-window over sliding-window/token-bucket, in docs/security.md's rate limiting section.
 *
 * <p><b>Algorithm</b>: each call atomically increments a counter keyed by
 * {@code (identity, action, current window)} and sets its expiry to the window length on the
 * first increment (both steps happen in one Lua script - see {@code CacheConfig} - so there's
 * no race between "check the count" and "increment it" across concurrent requests). If the
 * post-increment count exceeds the configured capacity, the request is rejected with
 * {@code 429 Too Many Requests}.</p>
 *
 * <p><b>Failure behavior - deliberately different from {@link RedisCacheService}'s</b>: on a
 * Redis error, this <em>allows</em> the request through (fails open) rather than rejecting it.
 * This is a real trade-off, not an obviously-correct default: failing open means a Redis
 * outage removes brute-force throttling from login/registration for as long as the outage
 * lasts; failing closed would instead turn a Redis outage into a total login/registration
 * outage for every legitimate user. This project accepts the former - password hashing cost
 * (BCrypt, factor 12) still makes brute-forcing expensive even without rate limiting, whereas
 * there is no equivalent fallback protection against "nobody can log in." A system with a
 * stricter security posture might choose to fail closed instead; this is exactly the kind of
 * trade-off worth being able to discuss and justify in an interview, not treat as settled.</p>
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> fixedWindowScript;

    public RateLimiter(RedisTemplate<String, String> redisTemplate, RedisScript<Long> fixedWindowRateLimiterScript) {
        this.redisTemplate = redisTemplate;
        this.fixedWindowScript = fixedWindowRateLimiterScript;
    }

    /**
     * @param key unique per identity+action, e.g. {@code "ratelimit:login:203.0.113.7"}
     * @return true if the request is allowed; false if the caller has exceeded {@code capacity} requests within {@code window}
     */
    public boolean tryAcquire(String key, int capacity, Duration window) {
        try {
            Long count = redisTemplate.execute(fixedWindowScript, List.of(key), String.valueOf(window.toSeconds()));
            boolean allowed = count == null || count <= capacity;
            if (!allowed) {
                log.info("Rate limit exceeded for {} ({} requests in the current window, limit {})", key, count, capacity);
            }
            return allowed;
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for rate limiting on {} - failing open (allowing the request)", key, e);
            return true;
        }
    }
}
