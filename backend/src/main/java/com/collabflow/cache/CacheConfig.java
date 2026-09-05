package com.collabflow.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class CacheConfig {

    /**
     * A plain {@code String}-to-{@code String} template, not a generic-object one with a
     * Jackson serializer: {@link RedisCacheService} serializes/deserializes values to JSON
     * itself via the application's own {@code ObjectMapper} (already configured with the
     * app's Jackson modules) before they ever reach Redis. This avoids Spring Data Redis's
     * generic value serializer embedding Java class-name metadata into every cached value,
     * which would make cached JSON both larger and needlessly coupled to exact class names.
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(StringRedisSerializer.UTF_8);
        template.setValueSerializer(StringRedisSerializer.UTF_8);
        return template;
    }

    /**
     * The fixed-window rate limiter's counter increment-and-check has to be atomic (see
     * {@link RateLimiter}) - a Lua script run server-side by Redis is how that's achieved
     * without a round trip per sub-step or a distributed lock.
     */
    @Bean
    public RedisScript<Long> fixedWindowRateLimiterScript() {
        String script = """
                local current = redis.call('INCR', KEYS[1])
                if current == 1 then
                    redis.call('EXPIRE', KEYS[1], ARGV[1])
                end
                return current
                """;
        return new DefaultRedisScript<>(script, Long.class);
    }
}
