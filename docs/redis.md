# Redis

Rationale for using Redis at all: [ADR-003](adr/ADR-003-redis.md). This document covers what
is actually cached, why, and the exact invalidation strategy for each - "don't cache
everything, and be able to explain every cache" is the standard this project holds itself to.

## What is cached (and, explicitly, what is not)

| Data | Key pattern | TTL | Why cached | Invalidated on |
|---|---|---|---|---|
| A user's team role | `team:role:{teamId}:{userId}` | 5 min | The single hottest read in the app - looked up on essentially every authorized request touching a project, task, or comment (`TeamService.requireMembership`/`requireAtLeast`, called from `ProjectService.requireProjectAccess`). | `changeRole`, `removeMember` |
| A user's display summary | `user:summary:{userId}` | 10 min | Resolved repeatedly to render names - once per member when listing a roster, once per comment author, etc. Rarely changes. | `updateProfile` |

**Deliberately not cached**: `UserAccountService.findCredentialsByEmail` (used for login).
Caching a password hash lookup - even briefly - would mean a deactivated account or a
just-changed password could still successfully authenticate against a stale cached value
until the entry expired. Anything that feeds directly into an authentication decision is
read fresh from PostgreSQL, full stop; the cost of that database read is trivial compared to
the cost of getting this wrong. See `UserAccountService`'s Javadoc.

**Not cached because they don't need it**: everything else - task/project/team CRUD reads all
go straight to PostgreSQL. They aren't hit anywhere near as often per-request as the two rows
above (each project/task operation calls `findRole` and `findSummaryById` *multiple times*
per request across many different rows, but calls e.g. `ProjectRepository.findById` once for
the one project the request concerns), so the cost/benefit doesn't clear the bar the same way.
If profiling ever showed otherwise, the same `RedisCacheService.getOrLoad` pattern used here
would extend to them without any new infrastructure.

## Cache-aside, implemented explicitly

`RedisCacheService` implements the classic cache-aside pattern by hand (get → miss → load →
put) rather than via Spring's `@Cacheable` annotation, specifically so that cache keys, TTLs,
and Redis-failure handling are all visible in the calling code instead of hidden behind
annotation processing - see its Javadoc for the reasoning.

- **Cache key**: always `{domain}:{concept}:{id...}` (e.g. `team:role:{teamId}:{userId}`) -
  namespaced so keys are self-describing when inspected directly (`redis-cli KEYS`).
- **TTL**: chosen per cache, short enough that any invalidation gap self-heals quickly (5-10
  minutes here), long enough to actually reduce load on the hot paths above.
- **Cache hit**: return the cached value, no database query.
- **Cache miss**: query PostgreSQL, cache the result, return it.
- **Invalidation**: explicit `evict`/`evictAfterCommit` calls at the specific write sites that
  make a cached value stale (see the table above) - not a blanket "clear everything" on any
  write.
- **Stale data**: bounded by the TTL in the worst case (a missed invalidation path would
  self-correct within 5-10 minutes) - and see the after-commit note below for a stale-data bug
  that was found and fixed during Phase 9, not merely theorized about.
- **Redis failure**: `RedisCacheService` fails open - every method catches
  `DataAccessException` and falls back to treating the failure as a cache miss (read) or a
  no-op (write/evict), logging a warning. The cache is an optimization on top of PostgreSQL,
  never the source of truth, so a Redis outage degrades this application to "slower," never
  "broken." See its Javadoc.
- **Cache stampede considerations**: with the current TTLs and read volume, a "thundering
  herd" of simultaneous cache misses for the same key on expiry is unlikely enough not to
  warrant extra machinery (e.g. request coalescing, jittered TTLs) yet - each miss just costs
  one ordinary PostgreSQL read, the same query this cache is saving on every other request.
  This would be revisited if a specific hot key's expiry started producing a visible load
  spike in practice.

### A real invalidation bug this project caught: evict-before-commit

Every eviction in this codebase runs via `evictAfterCommit`, not a plain immediate `evict`,
for a concrete reason found while implementing this phase, not a hypothetical one: evicting a
key *inside* the same transaction as the write that makes it stale opens a window where a
concurrent request can miss the now-empty key, read the *old* (still-committed, pre-update)
row from PostgreSQL - because this transaction's change isn't visible to others until it
commits - and re-cache that soon-to-be-stale value. That value would then sit in the cache
until its TTL expired, even though the write that should have invalidated it already
happened. `evictAfterCommit` (via `TransactionSynchronizationManager.registerSynchronization`,
firing only in `afterCommit`) closes this window: nothing can re-cache the old value after the
point where "old" stops being true. See `RedisCacheService#evictAfterCommit`'s Javadoc and
`docs/troubleshooting.md`.

## Rate limiting

See `RateLimiter`'s Javadoc for the full write-up (algorithm, Redis implementation via a Lua
script, and its deliberately fail-open failure behavior) and `docs/security.md` for which
endpoints use it. Short version: a Redis-backed fixed-window counter,
`INCR`+conditional-`EXPIRE` executed atomically in one Lua script, applied to
`/api/v1/auth/login` and `/api/v1/auth/register`, keyed by client IP.

## Verified

Directly inspected via `redis-cli` during Phase 9 testing, not just asserted from the code:
a cache key appearing with the correct TTL on first access; the correct value evicted after a
role change and the *next* read re-populating it with the new, correct value (not the stale
one); and the rate limiter's own counter key correctly hitting its capacity (`429
RATE_LIMIT_EXCEEDED`) and then resetting once its window elapsed.
