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

Details (connection lifecycle, auth, channels, disconnect/reconnect, security) are documented
in full once Phase 12 (WebSockets) is implemented.

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
