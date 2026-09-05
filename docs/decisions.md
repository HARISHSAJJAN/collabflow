# Architecture Decision Records

This file is an index. Each ADR lives in its own file under `docs/adr/` and follows the
standard Context / Decision / Alternatives / Consequences / Trade-offs format.

| ADR | Title | Status |
|---|---|---|
| [ADR-001](adr/ADR-001-modular-monolith.md) | Modular monolith instead of microservices | Accepted |
| [ADR-002](adr/ADR-002-postgresql.md) | Why PostgreSQL | Accepted |
| [ADR-003](adr/ADR-003-redis.md) | Why Redis | Accepted |
| [ADR-004](adr/ADR-004-kafka.md) | Why Kafka | Accepted |
| [ADR-005](adr/ADR-005-websockets.md) | Why WebSockets (STOMP over raw WebSocket) | Accepted |
| [ADR-006](adr/ADR-006-optimistic-locking.md) | Why optimistic locking for task updates | Accepted |
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
- **Task labels are free-text per task, not a shared per-project label catalog** (Phase 7, the
  V5 migration's comment): `task_labels` is just `(task_id, label)`, unique per task. A full
  catalog (its own table, color-coding, reuse/rename-everywhere-at-once across a project) is
  real additional scope the brief's "add labels" doesn't demand. If label reuse/color-coding
  became an actual product requirement, that would be the point to add a `labels` table and
  migrate `task_labels` to reference it.
- **Assigning a task is ADMIN+-only; a MEMBER may only self-assign at creation time, then
  edit a task already assigned to them** (Phase 7, `TaskService`'s class Javadoc): read
  literally, the brief's "MEMBER: create/update assigned tasks" describes a MEMBER acting on
  work already assigned to them, not reassigning work to anyone (including themselves) at
  will after the fact. Deciding who works on what was treated as a management action.
- **Comment deletion allows a narrow ADMIN+ moderation override; editing never does** (Phase
  8, `CommentService`'s class Javadoc): the brief phrases comment edit/delete as things "users
  can" do to their own comments, without an explicit moderation carve-out. Deletion was
  extended to team ADMIN/OWNER anyway - removing someone else's inappropriate comment is a
  normal, low-risk moderation action - while editing someone else's comment content stays
  author-only with no exception, since rewriting what someone else said is a different,
  higher-risk kind of action than removing it.
- **The notification feature was mostly built in Phase 10, ahead of the brief's own "Phase
  11: Notifications"** (see docs/kafka.md's opening note and the V8 migration's comment):
  Kafka's consumer side needs a real, meaningful consumer to actually demonstrate consumption
  (offsets, consumer groups, idempotent processing) rather than a throwaway demo listener, and
  "create a notification when a relevant event arrives" is exactly that consumer. Phase 11
  then added the two pieces that genuinely needed their own phase: `DUE_DATE_APPROACHING`
  (a daily scheduled sweep - `DueDateReminderJob` - since nothing "happens" to trigger it) and
  `TASK_MENTION` (parsing comment bodies). Phase 12 adds real-time WebSocket delivery on top
  of what's already being created here.
- **@mentions match an exact email address, not a username/handle** (Phase 11,
  `CommentEventsListener`'s Javadoc): this project has no username/handle system at all (users
  are identified by email and display name only), so building mention-matching around one
  would mean building that system first - real additional scope the brief's "task mention"
  line item doesn't itself demand. Matching on exact email is simple, unambiguous, and
  requires no autocomplete UI to be usable by a determined user. A mentioned email that isn't
  an actual project member (a typo, an outsider) notifies no one, silently - not an error,
  and specifically not a way to probe whether an email has an account.
- **No cross-module JPA associations, ever** (first established Phase 5, `Team.java`'s
  Javadoc; see also `docs/database.md`'s "Cross-module foreign keys" section): every
  reference from one module's entity to a row owned by another module is a plain UUID column
  with a real SQL foreign key, never a JPA `@ManyToOne`/`@OneToMany` crossing a module
  boundary. This is what lets Spring Modulith's boundary check mean something at the
  persistence layer, not just at the service layer.
