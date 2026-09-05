# ADR-003: Redis for caching and rate limiting

## Status
Accepted

## Context

Two unrelated needs both point at the same tool: (1) some reads happen far more often than
the data they read changes, and re-querying PostgreSQL for them on every request is wasted
work at scale; (2) a handful of sensitive endpoints (login, registration) need throttling
that survives across requests and, once this application runs as more than one instance,
across processes too.

## Decision

Use **Redis** for both, via a small explicit cache-aside helper (`RedisCacheService`) and a
Lua-script-backed fixed-window rate limiter (`RateLimiter`) - see `docs/redis.md` for exactly
what is cached, the TTLs, and the invalidation strategy for each.

Redis was chosen over the alternatives for both use cases together:

- **Shared across instances**: an in-JVM cache (e.g. Caffeine) is invisible to any other
  instance of this application. The moment this app runs as more than one process (the
  first, smallest step in `docs/architecture.md`'s scaling story), an in-process cache and an
  in-process rate-limit counter both silently stop working correctly - two instances would
  each allow their own 5 login attempts, doubling the effective limit, and each would cache
  independently with no way to invalidate the other's copy on a write. Redis is one shared
  store every instance talks to, so correctness doesn't depend on how many instances exist.
- **Atomic operations across concurrent requests**: the rate limiter's increment-and-check
  needs to be atomic under concurrent requests from the same source - Redis's single-threaded
  command execution (and, here, a Lua script run server-side - see `CacheConfig`) gives that
  for free, without a database transaction or an application-level lock.
- **TTL as a first-class feature**: every cache entry and every rate-limit counter in this
  project has a natural expiry. Redis expires keys natively; building the equivalent on top
  of PostgreSQL would mean either a background cleanup job or checking an `expires_at` column
  on every read - solvable, but Redis already does exactly this.

## Alternatives considered

### In-process cache (Caffeine, Guava Cache)
Faster (no network hop) and simpler to operate (nothing extra to run), and would have been
the right choice if this were guaranteed to stay a single instance forever. Rejected because
it doesn't survive the very first horizontal-scaling step this project's own scaling
narrative describes, and because two instances with independent local caches lose all
consistency guarantees the moment one of them invalidates an entry the other still holds.

### Rate limiting entirely inside PostgreSQL (a counter table)
Would work, but adds write load (and, without care, lock contention) to the database for
something that's fundamentally ephemeral, high-frequency, and self-expiring data - exactly
what a cache/counter store like Redis exists for, not what a relational system of record is
for.

### A database-agnostic in-memory rate-limit library run per-instance
Rejected for the same "doesn't survive more than one instance" reason as an in-process cache.

## Consequences

**Positive**
- Caching the hottest authorization read (`TeamService.findRole`) and the hottest display
  read (`UserAccountService.findSummaryById`) removes a database round trip from the large
  majority of authorized requests once warm.
- Rate limiting is correct across however many backend instances exist, with no
  code change needed when this app is eventually scaled horizontally.

**Negative / trade-offs accepted**
- A new runtime dependency: if Redis is fully down, see each component's documented failure
  behavior - `RedisCacheService` fails open (falls back to PostgreSQL, slower but correct);
  `RateLimiter` also fails open (allows requests through, a deliberate and debatable trade-off
  - see its Javadoc). Neither failure mode makes the application incorrect, only slower or
  temporarily unthrottled.
- Cache invalidation adds real complexity: evicting *before* a transaction commits was found,
  during testing, to open a race where a concurrent reader could re-cache a soon-to-be-stale
  value (see `docs/troubleshooting.md`). Every eviction in this codebase now happens
  after-commit for exactly this reason - a subtlety that would not exist without a cache at
  all, accepted because the cache's benefit is worth this one carefully-handled pattern.

## Interviewer questions this ADR should let you answer
- "Why Redis and not an in-memory cache?"
- "What happens to your app if Redis goes down - which parts, exactly?"
- "Walk me through a cache-invalidation bug you actually found."
- "Why does your rate limiter fail open instead of fail closed?"
