# Architecture Decision Records

This file is an index. Each ADR lives in its own file under `docs/adr/` and follows the
standard Context / Decision / Alternatives / Consequences / Trade-offs format.

| ADR | Title | Status |
|---|---|---|
| [ADR-001](adr/ADR-001-modular-monolith.md) | Modular monolith instead of microservices | Accepted |
| [ADR-002](adr/ADR-002-postgresql.md) | Why PostgreSQL | Accepted |
| ADR-003 | Why Redis | Planned — Phase 9 |
| ADR-004 | Why Kafka | Planned — Phase 10 |
| ADR-005 | Why WebSockets (STOMP over raw WebSocket) | Planned — Phase 12 |
| ADR-006 | Why optimistic locking for task updates | Planned — Phase 7 |
| ADR-007 | Why REST (not GraphQL) | Planned — Phase 6 |
| ADR-008 | Why JWT access + refresh tokens (not server sessions) | Planned — Phase 3 |

ADRs are added as the corresponding phase of `docs/architecture.md`'s development log is
completed — each one reflects a decision actually made and implemented, not a decision
planned in the abstract.
