package com.collabflow.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.collabflow.AbstractIntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@link RateLimiter} against a real Redis (not a mock), exercised directly rather than
 * through {@code AuthController} - the controller's own rate-limit capacities are deliberately
 * set very high in {@link AbstractIntegrationTest} (see its Javadoc) so that no other
 * integration test accidentally trips them, which makes the HTTP layer the wrong place to
 * prove the limiter's own counting behavior. Calling {@code tryAcquire} directly with small
 * capacities sidesteps that without touching the shared test configuration.
 */
class RateLimiterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RateLimiter rateLimiter;

    @Test
    void allowsUpToCapacityThenRejects() {
        String key = "ratelimit:test:" + UUID.randomUUID();

        assertThat(rateLimiter.tryAcquire(key, 3, Duration.ofMinutes(1))).isTrue();
        assertThat(rateLimiter.tryAcquire(key, 3, Duration.ofMinutes(1))).isTrue();
        assertThat(rateLimiter.tryAcquire(key, 3, Duration.ofMinutes(1))).isTrue();
        assertThat(rateLimiter.tryAcquire(key, 3, Duration.ofMinutes(1))).isFalse();
        assertThat(rateLimiter.tryAcquire(key, 3, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void differentKeysAreCountedIndependently() {
        // The property Phase 15's per-email login limiter relies on: an IP-keyed counter and
        // an email-keyed counter for the same login attempt must not share state, or one
        // attacker's IP getting throttled would have no relationship to their target's email
        // also getting throttled (and vice versa - a shared account under attack from many
        // IPs must trip the email key even though no single IP key ever fills up).
        String ipKey = "ratelimit:login-ip:" + UUID.randomUUID();
        String emailKey = "ratelimit:login-email:" + UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            assertThat(rateLimiter.tryAcquire(ipKey, 3, Duration.ofMinutes(1))).isTrue();
        }
        assertThat(rateLimiter.tryAcquire(ipKey, 3, Duration.ofMinutes(1))).isFalse();

        // The email key has seen zero requests so far - it must still have its full capacity.
        assertThat(rateLimiter.tryAcquire(emailKey, 3, Duration.ofMinutes(1))).isTrue();
    }
}
