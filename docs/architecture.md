# Architecture

## Style: modular monolith

CollabFlow is one Spring Boot application (`backend/`) organized into domain modules, each
living in its own Java package under `com.collabflow`. See
[ADR-001](adr/ADR-001-modular-monolith.md) for the full reasoning.

```
com.collabflow
├── auth            domain module — registration, login, JWT issuance/refresh
├── user            domain module — user profiles
├── team            domain module — teams, team membership, team roles
├── project         domain module — projects, project membership
├── task            domain module — tasks, labels, status/priority, optimistic locking
├── comment         domain module — task comments
├── notification    domain module — in-app notifications (read/unread)
├── audit           domain module — append-only audit trail
├── kafka           infrastructure (OPEN) — producer/consumer config, event envelope
├── websocket       infrastructure (OPEN) — STOMP config, broadcast helper
├── cache           infrastructure (OPEN) — Redis config, rate limiter
├── config          infrastructure (OPEN) — security filter chain, CORS, OpenAPI
└── common          infrastructure (OPEN) — base entity, shared DTOs, exceptions
```

Domain modules are **encapsulated**: only classes at a module's package root are visible to
other modules. A `ModularityTests` test (added in Phase 14) runs
`ApplicationModules.of(CollabFlowApplication.class).verify()` from Spring Modulith, which
fails the build if any module reaches into another module's internal subpackage. The
infrastructure modules (`kafka`, `websocket`, `cache`, `config`, `common`) are marked `OPEN`
because every domain module legitimately needs them.

### How modules are allowed to talk to each other

1. **Same-request, synchronous, "I need an answer now"**: a direct call to another module's
   exposed service interface (e.g. `task` asks `project` "is this user a member?" before
   creating a task). This stays a normal Java method call — it's a single JVM, there's no
   reason to fake network calls for this.
2. **"This happened, zero or more other modules may care"**: a Kafka event. `task` publishes
   `TaskAssignedEvent`; it does not know or care that `notification`, `audit`, and the
   WebSocket broadcaster are listening. New listeners can be added without changing `task`.

Rule of thumb used throughout: if module A needs a *return value* to keep doing its own job,
it's a direct call to a narrow interface. If module A is just *announcing* something happened,
it's an event.

### What extracting a module into a real microservice would require

Using `notification` as the concrete example (see ADR-001 for why it's the most likely first
candidate):

1. Move `com.collabflow.notification` into its own Maven/Spring Boot project with its own
   `pom.xml`, keeping its existing package-internal structure unchanged.
2. Give it its own database (or at minimum its own schema) — it already owns the
   `notifications` table exclusively, so this is a data migration, not a redesign.
3. Point its Kafka consumer at the same topics it already consumes — no change needed there,
   since it was never called synchronously by other modules.
4. Replace the one synchronous call *into* notification, if any exists by that point (e.g. "get
   unread count for badge"), with an HTTP call from the API gateway / BFF layer instead of an
   in-process call.
5. Add it to CI/CD as its own pipeline and to `docker-compose.yml` as its own service.

Because the module boundary was real from day one, this is a mechanical extraction, not a
redesign — which is the entire point of choosing "modular monolith" over "unstructured
monolith."

## Real-time update flow (WebSockets)

```
User A                         User B
  │  PATCH /api/v1/tasks/42       │  (subscribed to /topic/projects/{id})
  ▼                               │
REST controller (task module)     │
  ▼                               │
Service: validate, apply,         │
  save (optimistic lock check)    │
  ▼                               │
DB transaction commits            │
  ▼                               │
Publish TaskStatusChangedEvent    │
  ▼                               │
WebSocket broadcast listener  ────┼──► STOMP message on /topic/projects/{id}
                                   ▼
                              User B's browser updates without a refresh
```

This is exactly what's implemented as of Phase 12, verified with a real STOMP test client, not
just diagrammed speculatively - full details (connection lifecycle, auth, channels, disconnect/
reconnect, security, and known gaps) in [`docs/websocket.md`](websocket.md) and
[ADR-005](adr/ADR-005-websockets.md).

## Event-driven flow (Kafka)

```
Task Created (task module, same DB transaction as the task insert)
        │
        ▼
   Kafka topic: task.created
        │
   ┌────┼────────────┬───────────────┐
   ▼    ▼             ▼               ▼
notification       audit          (future) analytics
 consumer         consumer          consumer
```

Producer/consumer configuration, delivery guarantees, idempotency strategy, ordering, and
dead-letter handling are documented in full once Phase 10 (Kafka events) is implemented —
see `docs/architecture.md`'s "Event delivery guarantees" section, added at that point.

## Development phase log

Each entry is added when that phase is actually complete, compiling, and tested — not before.

- **Phase 1 — Architecture & repository setup (done)**: repo structure, Maven project skeleton,
  package-per-module layout with Spring Modulith module markers, `application.yml` with
  externalized config, `docker-compose.yml` for Postgres/Redis/Kafka (KRaft, single broker),
  `.env.example`, ADR-001.
- **Phase 2 — Database & migrations (done)**: Flyway wired in (`ddl-auto: validate`, never
  `update`), `users` and `refresh_tokens` tables (V1/V2), verified applied against the real
  Postgres container with the correct indexes/constraints. Details and indexing rationale in
  `docs/database.md`; ADR-002 (why PostgreSQL).
- **Phase 3 — Authentication (done)**: `user` module (User entity + repository kept internal,
  `UserAccountService` as the only way in - see the module boundary discussion above),
  `auth` module (JWT access tokens, opaque hashed rotating refresh tokens, reuse detection,
  session listing/revocation), Spring Security wired for stateless JWT auth with a custom
  `@CurrentUserId` argument resolver and JSON error responses for 401/403. Caught and fixed a
  real `@Transactional` propagation bug during testing (revocation on token-reuse was silently
  rolled back) - see `docs/troubleshooting.md` and ADR-008. Verified end-to-end against the
  real running app with curl: register, duplicate-email conflict, validation errors, login,
  wrong password, protected-endpoint 401/200, refresh rotation, reuse detection revoking all
  sessions, logout, and Swagger UI/OpenAPI exposure.
- **Phase 4 — User profile management (done)**: `GET/PATCH /api/v1/users/me`, `POST /api/v1/
  users/me/password`. Introduced the codebase's first cross-module in-process event
  (`user.PasswordChangedEvent`, consumed by `auth.AuthService`) rather than a direct method
  call, specifically to avoid a module dependency cycle (`auth` already depends on `user`).
  See "Two kinds of cross-module events" below. Caught and fixed a second real transaction
  bug in the same session - a `@TransactionalEventListener` needs `REQUIRES_NEW`, not plain
  `@Transactional`, to actually do write work; full writeup in `docs/troubleshooting.md`.
  Verified end-to-end: profile view/update, wrong-current-password rejection, and - the part
  that actually matters - a refresh token issued before a password change failing correctly
  afterward.

### Two kinds of cross-module events

This codebase now has two genuinely different mechanisms for "module A tells module B
something happened," and it's worth being able to say which is which and why:

- **Kafka domain events** (from Phase 10 onward): `TaskCreatedEvent`, `TaskAssignedEvent`,
  etc. - things a genuinely separate bounded context, or a future extracted service, would
  plausibly want to know about. Asynchronous, at-least-once, serialized, replayable.
- **Plain in-process Spring events** (`ApplicationEventPublisher`, first used in Phase 4 for
  `PasswordChangedEvent`): a side effect purely internal to this one JVM, between two modules
  that already both live here and always will as far as this decision is concerned. No
  serialization, no broker, no "what if the consumer is down" - and, used with
  `@TransactionalEventListener(phase = AFTER_COMMIT)`, a guarantee the listener only fires if
  the publishing transaction actually committed.

The test for which to use: would a hypothetical future separate `notification-service` or
`audit-service` need this event over the network? If yes, it's Kafka-shaped. If it's purely
"module A just did something module B, still in the same JVM, needs to react to," a Spring
event is simpler and avoids paying for guarantees (ordering across a partition, replay,
cross-process delivery) that don't apply.

- **Phase 5 — Teams (done)**: `team` module - teams, team membership, OWNER/ADMIN/MEMBER
  roles, with the invariant that a team always has at least one OWNER enforced in
  `TeamService` (attempting to remove or demote the last OWNER returns `409`). This phase is
  where the "no cross-module JPA associations" convention was established (`Team.java`'s
  Javadoc, and `docs/database.md`'s "Cross-module foreign keys" section) - `teams.created_by`
  is a real SQL foreign key to `users.id`, but there is no `@ManyToOne` from `Team` to the
  user module's entity. Caught and fixed a third real bug: `@CreationTimestamp`/
  `@UpdateTimestamp` fields read immediately after `save()` (before the transaction's next
  flush) came back `null`/stale in the API response even though the database row was correct
  - fixed with `saveAndFlush()`; full writeup in `docs/troubleshooting.md`. Verified
  end-to-end: team creation, non-member 404s, adding a member, MEMBER-role authorization
  boundaries (403 on admin actions), the last-owner-protection invariant (409, then success
  after promoting a second owner), post-removal 404, and pagination.
- **Phase 6 — Projects (done)**: `project` module - projects scoped to a team, archiving, and
  membership drawn from the team roster (never an independent user pool - adding a project
  member requires them to already be a team member). No project-level `role` column: a
  project member's permissions are their team role, resolved through `TeamService` at
  authorization time. Visibility is asymmetric by design - team ADMIN+ sees every project in
  the team, a MEMBER only sees projects they've been explicitly added to - which is what
  reproduced the `saveAndFlush` timestamp bug's pattern in a new place (fixed the same way).
  Verified end-to-end: MEMBER blocked from creating projects (403), the asymmetric visibility
  rule (MEMBER sees 0 then 1 project after being added, ADMIN always sees both), a non-member
  getting 404 on direct access, and archive → blocked-edit (409) → unarchive → edit-succeeds
  with a correctly updated `updatedAt`.
- **Phase 7 — Tasks (done)**: `task` module - tasks, status/priority, assignment, free-text
  labels, and this project's mandatory concurrency requirement: JPA optimistic locking
  (`@Version`) on `Task.version`, the only versioned entity in the schema (see ADR-006 for
  why it's scoped to just this one). Authorization: edit requires ADMIN+ or being the current
  assignee; assignment changes and delete are ADMIN+ only. **Verified under an actual
  concurrent race**, not just implemented: two `curl` requests fired truly concurrently
  (`&` + `wait`, not sequential) against the same task, both starting from the same version -
  one succeeded and advanced the version by one, the other received `409
  CONCURRENT_MODIFICATION` instead of silently overwriting the winner's change. Also verified:
  self-assign-at-creation allowed, assigning someone else as a MEMBER rejected (403),
  ADMIN-only delete enforced, and the cross-module internal-package import check still clean
  with five modules now in place.
- **Phase 8 — Comments and activity history (done)**: `comment` module (author-only edit,
  author-or-ADMIN+ delete as a narrow moderation allowance) and `audit` module. `audit` is a
  pure sink: it never calls into another module, only listens to plain in-process Spring
  events published by task/project/team/comment (`TaskCreatedEvent`, `TaskAssignedEvent`,
  `TaskStatusChangedEvent`, `TaskPriorityChangedEvent`, `ProjectCreatedEvent`,
  `MemberAddedEvent`, `MemberRemovedEvent`, `CommentCreatedEvent`) - the same event types
  Phase 10 additionally publishes to Kafka for other consumers, so this phase's event classes
  are reused rather than replaced later. "View project/team activity" authorization lives in
  `AuditController` (calling `ProjectService`/`TeamService`), not `AuditService`, to keep that
  module's "never calls out" invariant intact. Verified end-to-end: the full event chain from
  creating a team/project/task/comment through to the activity feed showing every one of them
  with correct actor/old-value/new-value, author-only comment editing (403 for anyone else),
  ADMIN-moderated comment deletion, and the cross-module internal-package import check clean
  across all seven modules.
- **Phase 9 — Redis caching and rate limiting (done)**: `cache` module - an explicit
  cache-aside helper (`RedisCacheService`, deliberately not `@Cacheable`, so keys/TTLs/
  failure handling stay visible) and a Lua-script fixed-window `RateLimiter`. Caches exactly
  two things (see docs/redis.md for why these two and not more): `TeamService.findRole` (the
  single hottest read - every authorized request touching a project/task/comment hits it) and
  `UserAccountService.findSummaryById` (resolved repeatedly for display names). Deliberately
  never caches credential lookups (`findCredentialsByEmail`) - a stale cached hash could let a
  deactivated account or an old password keep authenticating. Rate limiting on
  login/register, closing the gap flagged in Phase 3's docs/security.md, fails open on a Redis
  outage (a documented, debatable trade-off) while the cache also fails open but for a
  different reason (it's an optimization, not a security control). Found - by reasoning
  through transaction timing, not from a failing test - and fixed a cache-invalidation race:
  evicting a key before its transaction commits lets a concurrent reader repopulate the cache
  with a soon-to-be-stale value; every eviction here now runs `afterCommit` via
  `TransactionSynchronizationManager`. Verified end-to-end with `redis-cli`: a cache key
  appearing with the correct TTL, eviction on a role change followed by re-population with
  the *new* correct role (not the stale one), and the rate limiter hitting its capacity
  (`429`) and correctly resetting after its window elapsed.
- **Phase 10 — Kafka events (done)**: `kafka` infrastructure module (`DomainEventPublisher`,
  publishing after transaction commit; `KafkaConsumerConfig`'s retry+dead-letter policy) and
  the `notification` module's consumer side, built here rather than waiting for the brief's
  own "Phase 11" because Kafka's consumer side needed a real consumer to actually demonstrate
  consumption - see docs/decisions.md and docs/kafka.md. Task/project/team/comment services now
  dual-publish: the existing Phase 8 in-process Spring event (for `audit`) plus a new Kafka
  publish (for `notification`). **Found two real, sequential idempotent-consumer bugs by
  mechanically forcing message redelivery** (resetting the consumer group's offsets and
  replaying already-processed messages) - not by reasoning about the code: (1) a manually-
  assigned `@Id` made Spring Data JPA's `save()` silently upsert instead of insert, so a
  "duplicate" key check never actually triggered; (2) after fixing that, catching the
  resulting constraint-violation exception one call away in its own `REQUIRES_NEW` transaction
  still threw `UnexpectedRollbackException`, because `JpaRepository`'s own transactional
  advice marked that transaction rollback-only before the catch block ran. Final fix: an
  atomic, exception-free `INSERT ... ON CONFLICT DO NOTHING`. Full writeup in ADR-004 and
  docs/troubleshooting.md. Verified end-to-end: the full produce → broker → consumer-group →
  idempotent-write → notification pipeline for task assignment, status change, comment
  fan-out (with per-recipient dedup keys), and team membership; self-actions correctly
  suppress their own notification; and - the part that actually matters - replaying the exact
  same message backlog twice produces exactly one notification per event, not two.
- **Phase 11 — Notifications, remainder (done)**: the two pieces not already covered by
  Phase 10's consumer work. `DUE_DATE_APPROACHING` via `DueDateReminderJob`, a daily
  `@Scheduled` sweep - deliberately not a Kafka event, since nothing "happens" to produce one
  (see docs/kafka.md). `TASK_MENTION` via `CommentEventsListener` parsing `@email` tokens out
  of the comment body (added as a new field on `CommentCreatedEvent`), matched against exact
  email addresses since this project has no username/handle system - a mentioned email that
  isn't an actual project member notifies no one, silently. Verified end-to-end: mentioning a
  real project member's email produces a `TASK_MENTION` notification for them; mentioning a
  non-member/outsider email in the same comment produces nothing for it; and the due-date
  query was verified against a real task due tomorrow (the 08:00 cron firing itself wasn't
  observed live in testing - stated plainly rather than implied).
- **Phase 12 — WebSockets (done)**: STOMP over native WebSocket (`/ws`), authenticated at the
  STOMP `CONNECT` frame rather than the HTTP handshake (a browser can't set an
  `Authorization` header on the WebSocket upgrade request - see `JwtStompAuthInterceptor`'s
  Javadoc). `/topic/projects/{id}` broadcasts task/comment updates; `/user/queue/notifications`
  delivers real-time notification pushes on top of the existing REST polling path. Both are
  a *third* independent reaction to the same in-process Spring events `audit` already
  consumes - added without touching any producer. Extracted `common.TransactionUtils` after
  the "defer until commit" pattern (Redis eviction, Kafka publish, now WebSocket
  broadcast/push) showed up a third time. **Verified with a hand-rolled STOMP test client**
  (`ws` + raw STOMP framing - curl doesn't speak STOMP), not just implemented: a valid token
  gets `CONNECTED` with the correct `user-name`; no token gets `ERROR` and the connection
  closes; subscribing to a real project and changing a task's status over REST delivered the
  expected `MESSAGE` on both the project topic and the personal notification queue, with a
  correctly populated (non-null) `createdAt` on the pushed notification - the same timing
  class of bug hit three times before, checked for and avoided here proactively rather than
  found after the fact. ADR-005; docs/websocket.md documents a real, low-severity gap (no
  per-subscription project-membership check) rather than hiding it.
- **Phase 13 — Search, filtering, sorting (done)**: `GET /api/v1/tasks/search` adds keyword
  (PostgreSQL full-text search via a generated, GIN-indexed `search_vector` column - not
  Elasticsearch, see `docs/search.md` for the explicit "why not, and what would justify it
  later" reasoning the brief asks for), due-date range, and label filtering on top of the
  existing status/priority/assignee filters and pagination from Phase 7. A native query, with
  a documented, deliberate limitation: no client-controlled sort (native queries don't
  participate in Spring Data's property-to-column `Sort` mapping the way the simpler JPQL-based
  `GET /api/v1/tasks` listing does). **Found a real bug via testing**: PostgreSQL couldn't
  determine a bind parameter's type for an optional filter whose only appearance was
  `:param IS NULL` (a prepare-time, structural ambiguity, independent of whether the value was
  actually null at runtime) - fixed with explicit `cast(:param as <type>)` on every optional
  parameter, not just the one that happened to fail first. Also closed a real documentation
  gap found while writing this entry: the `notifications`/`processed_events` tables (built in
  Phase 10) had never been added to `docs/database.md`'s migration table or given their own
  schema section - fixed alongside this phase's own V9 documentation. Verified end-to-end:
  keyword search correctly matching stemmed forms and excluding non-matches, label filtering,
  date-range filtering (post-fix), a combined multi-filter query, and the default sort order
  (nearest due date first, nulls last).

- **Phase 14 — Testing (done)**: an automated JUnit 5 + Testcontainers suite (real Postgres/
  Redis/Kafka, no mocks, per `docs/testing.md`'s coverage table) - 31 tests across modularity
  boundary enforcement, JWT/validation unit tests, the full auth lifecycle, team RBAC, the
  Phase 7 concurrency proof (now automated: ten real threads racing a PATCH, asserting the 409/
  200 split and final version), the Phase 10 Kafka-dedup idempotency guarantee, and cross-
  cutting security boundaries (missing/malformed/tampered tokens, task's assignee-or-ADMIN+
  rule). **Found four real bugs getting to a reliable green run**, three of them environment/
  tooling issues that looked identical at first (a Postgres/Redis/Kafka "connection refused")
  but had three unrelated causes, and a fourth that was an actual test-design bug masking real
  409s as an unrelated deserialization error - full root-cause writeup for all four in
  `docs/troubleshooting.md`. Net result: `spring-modulith` pinned to the Spring Boot 3.5-
  compatible `1.4.13` (not the newer-but-incompatible `2.1.1`), the Kafka Testcontainer switched
  to the class that natively supports the `apache/kafka` image, Surefire configured for one JVM
  fork per test class instead of one shared fork for the whole run, and the concurrency test's
  racer requests read as `String` instead of the success-only response DTO.

- **Phase 15 — Security hardening (done)**: closed the three gaps `docs/security.md` had
  explicitly tracked as deferred to this phase, plus one found along the way. (1) A second,
  email-keyed login rate limiter alongside the existing per-IP one, closing the "distributed
  brute force against one account from many IPs" gap - proven with a new
  `RateLimiterIntegrationTest` against real Redis. (2) Secure response headers
  (`Content-Security-Policy`, `Referrer-Policy`, `Permissions-Policy`,
  `Strict-Transport-Security`) added to `SecurityConfig`, verified by curling a running
  instance and inspecting the raw headers rather than trusting the configuration alone -
  including confirming Swagger UI still renders correctly under the new CSP (its bundle is
  entirely same-origin `<script src>` tags, no inline scripts, so no `'unsafe-inline'` was
  needed for `script-src`). (3) Dependency vulnerability scanning wired in via GitHub
  Dependabot (`.github/dependabot.yml`) rather than a local OWASP `dependency-check` run - see
  `docs/security.md` for the trade-off - plus a one-time manual review against current
  advisories that found a real, fixable issue: Spring Boot 3.5.16's managed PostgreSQL JDBC
  driver version (`42.7.11`) carries two 2026 CVEs fixed in `42.7.12`, so `postgresql.version`
  is now pinned in `backend/pom.xml`. Test suite grew from 31 to 33 tests (the new
  `RateLimiterIntegrationTest`'s two cases); full details of every change in
  `docs/security.md`'s Phase 15 entries.
