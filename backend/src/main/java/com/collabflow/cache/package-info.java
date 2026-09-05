/**
 * Redis infrastructure: {@code RedisTemplate}/{@code CacheManager} configuration, cache name
 * constants, TTL policy, and the Lua-script-backed rate limiter used by sensitive endpoints.
 *
 * <p>Marked OPEN so any module can apply caching or rate limiting without this becoming a
 * domain module in its own right - it holds no business entities.</p>
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.collabflow.cache;
