# Testing

## Strategy: representative coverage, not exhaustive per-endpoint

This suite proves the mechanisms this project exists to demonstrate actually work, and guards
against the specific bugs already found and fixed in earlier phases (cross-referenced from
`docs/troubleshooting.md`) recurring silently. It is **not** a test for every endpoint, every
validation rule, or every permutation of role x resource - that would be a much larger suite
for comparatively little additional confidence, since most endpoints share the same handful of
authorization/validation/transaction primitives already exercised elsewhere. Where a mechanism
is proven once (e.g., "a non-member gets 404, not 403" on teams), it isn't re-proven per module
unless that module's authorization policy is actually different (tasks' "assignee-or-ADMIN+"
rule *is* different from teams' pure role check, so it gets its own test).

Two categories of correctness are deliberately **not** covered by this automated suite, and are
instead covered by the manual, documented verification already on record for each phase:

- **WebSockets** (Phase 12): proven with a real STOMP client against a running instance (see
  `docs/websocket.md` and `docs/architecture.md`'s phase log) rather than an automated test,
  since a full STOMP-over-WebSocket integration test adds significant harness complexity for a
  mechanism this project only uses in one place.
- **Kafka's own delivery guarantees** (retry, dead-letter routing): proven by directly forcing
  redelivery against a running broker (`docs/troubleshooting.md`'s Kafka entry) during Phase 10.
  What *is* covered here is the idempotency guarantee on the consuming side
  (`NotificationIdempotencyIntegrationTest`), since that's the part a code change could most
  easily regress silently.

## What's covered, and why each class exists

| Class | Proves |
|---|---|
| `ModularityTests` | Spring Modulith's `ApplicationModules.verify()` - no module reaches into another's `.internal` package, and all 13 modules are actually discovered by the scan (not silently zero or one, which would make `verify()` pass for the wrong reason). This is the automated form of the `grep -rl "import com.collabflow.<mod>.internal."` check done by hand after every phase since Phase 1. |
| `JwtServiceTest` | Token generation/validation/expiry/tampering, in isolation from Spring context or a database - a pure unit test, since `JwtService` has no such dependencies. |
| `ValidationTest` | Bean Validation constraints on `RegisterRequest` fire correctly (blank email, short password, etc.) without needing a running server. |
| `AuthFlowIntegrationTest` | The full register -> login -> refresh -> logout lifecycle over real HTTP, plus the two security-critical paths: rotated-token reuse revokes the whole session family (ADR-008's regression guard for the Phase 3 bug), and logout revokes the refresh token without killing the still-live access token. |
| `TeamAuthorizationIntegrationTest` | `TeamService`'s authorization policy end-to-end: non-member gets 404 (not 403, so team existence isn't leaked), a MEMBER can't invite or delete, and the "a team always has at least one OWNER" invariant holds under both removal and self-demotion, including the succession path (promote a second owner, then the first can leave). |
| `TaskConcurrencyIntegrationTest` | The project's mandatory concurrency requirement (ADR-006): ten real OS threads, synchronized to fire simultaneously via a `CountDownLatch` gate, all PATCH the same task. Asserts at least one loses with 409, the rest win with 200, the counts sum to ten, and the task's final version equals exactly the number of writes that actually won - the automated, always-run equivalent of the manual `curl ... & curl ... & wait` proof done by hand after Phase 7. |
| `NotificationIdempotencyIntegrationTest` | The Phase 10 Kafka dedup guarantee, exercised directly at `NotificationService.recordEventAndNotify` (the layer the actual fix lives in - see `docs/troubleshooting.md`): the same event id never produces two notifications, even under a ten-times replay of the identical id, while different event ids for the same recipient each still get their own notification. |
| `SecurityBoundaryIntegrationTest` | Cross-cutting auth boundaries that don't belong to any one module: no `Authorization` header, a structurally invalid token, and a token with a tampered signature are all rejected with 401; and task's own authorization rule (only ADMIN+ or the current assignee may edit/assign) is checked explicitly, since it's stricter than teams' plain role check. |

## Running the suite

```bash
cd backend
mvn test
```

Every integration test extends `AbstractIntegrationTest`, which starts real Postgres, Redis,
and Kafka via Testcontainers (not the developer's own `docker-compose.yml` stack) and boots the
full Spring context on a random port. No manually-started infrastructure is required - Docker
itself is the only prerequisite.

**Surefire is configured for one JVM fork per test class** (`<forkCount>1</forkCount>
<reuseForks>false</reuseForks>` in `backend/pom.xml`), not Surefire's default of one shared JVM
for the whole run. This was a deliberate fix for a real environment limitation found while
building this suite (see `docs/troubleshooting.md`'s Phase 14 entry): on this Windows/WSL2/
Docker Desktop setup, a long-lived "singleton" Postgres/Redis/Kafka container set shared across
many sequential Spring test contexts in one JVM would silently get killed and replaced partway
through the run. Isolating each class to its own JVM keeps each class's containers alive only
for the few seconds that class needs them, comfortably inside whatever window this environment
can actually sustain - at the cost of a few extra seconds of container-startup overhead per
class, which is a good trade for a suite that otherwise fails unpredictably partway through.
