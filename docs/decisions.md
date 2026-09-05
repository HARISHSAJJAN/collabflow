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
| [ADR-008](adr/ADR-008-jwt-and-refresh-tokens.md) | Why JWT access + refresh tokens (not server sessions) | Accepted |

ADRs are added as the corresponding phase of `docs/architecture.md`'s development log is
completed — each one reflects a decision actually made and implemented, not a decision
planned in the abstract.

## Smaller design decisions (not full ADRs)

Not every decision warrants a full ADR. Smaller, scoped choices are documented inline in the
relevant code's Javadoc and cross-referenced here so they're easy to find:

- **Team invitations are immediate, not a pending accept/decline flow** (Phase 5,
  `TeamService.addMemberByEmail`): an OWNER adding a member by email adds them right away,
  rather than creating a pending invitation the invitee must accept. This was a deliberate
  scope decision, not an oversight - a full invitation flow (pending state, accept/decline
  endpoints, invitations to not-yet-registered emails, expiry) is real additional surface
  area that isn't justified yet by anything in the brief beyond "invite users," which this
  simpler version satisfies for an already-registered user. If the product requirement ever
  needed to invite people who don't have accounts yet, or needed the invitee's consent before
  being added, that would be the point to build the fuller flow.
- **Team membership management is OWNER-only; ADMIN can edit the team but not its roster**
  (Phase 5, `TeamService`'s class Javadoc): the brief's authorization section lists OWNER as
  managing "members" and ADMIN as managing "project members" specifically - read literally,
  that puts team-roster changes (invite/remove/role-assign) at the OWNER level only, with
  ADMIN's membership-management power scoped to projects (Phase 6). Editing the team's
  name/description was judged a lower-risk action and opened to ADMIN as well.
- **Project membership has no role of its own; project members draw their permissions from
  their team role** (Phase 6, `ProjectService`'s class Javadoc and the V4 migration's
  comment): a project-level role column would be a second source of truth that could drift
  from the team role it would inevitably need to stay consistent with. Project visibility is
  still asymmetric (ADMIN+ sees all of a team's projects, MEMBER only sees projects they've
  been added to) without needing a role of its own to express that.
- **No cross-module JPA associations, ever** (first established Phase 5, `Team.java`'s
  Javadoc; see also `docs/database.md`'s "Cross-module foreign keys" section): every
  reference from one module's entity to a row owned by another module is a plain UUID column
  with a real SQL foreign key, never a JPA `@ManyToOne`/`@OneToMany` crossing a module
  boundary. This is what lets Spring Modulith's boundary check mean something at the
  persistence layer, not just at the service layer.
