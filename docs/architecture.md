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
