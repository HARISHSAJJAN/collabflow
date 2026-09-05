# ADR-002: PostgreSQL as the primary datastore

## Status
Accepted

## Context

CollabFlow's core domain is deeply relational: a `user` belongs to `teams` through
`team_members`, a `team` has `projects`, a `project` has `tasks`, a `task` has `comments` and
`labels`, and almost every interesting query ("show me my assigned tasks across all my
projects, sorted by due date, filtered by status") joins several of these tables at once.
The system also needs real transactional guarantees: e.g. "create a project and add its
creator as a member" must either both happen or neither happen.

## Decision

Use **PostgreSQL 16** as the single system-of-record database for every module.

Key reasons:
- **Relational integrity fits the domain.** Foreign keys, unique constraints, and check
  constraints let the database itself refuse to store impossible states (a task pointing at a
  project that doesn't exist, two team-membership rows for the same user/team pair), instead
  of relying on application code to always remember to check.
- **ACID transactions** make multi-table writes ("create task" + "write audit log entry" in
  the same request) trivial to get correct, with no distributed-transaction machinery needed
  because there's one database.
- **Real concurrency control primitives**: PostgreSQL's MVCC plus row versioning (`SELECT ...
  FOR UPDATE`, and application-level optimistic locking via a `version` column checked in the
  `UPDATE ... WHERE version = ?` clause) directly support the concurrent-task-update scenario
  this project is required to handle correctly (see ADR-006, added in Phase 7).
- **Mature indexing and query planning**, including partial indexes, expression indexes, and
  full-text search (`tsvector`/`GIN`) — enough for the task/project search and filtering
  requirement without needing a separate search engine (see the "Search and filtering"
  section of `docs/architecture.md`, added Phase 13, for the concrete decision not to add
  Elasticsearch yet).
- **`JSONB` columns** where a field is genuinely semi-structured (audit log old/new values,
  notification payloads) without abandoning a relational schema for the parts of the domain
  that are genuinely relational.

## Alternatives considered

### MongoDB / a document database
Rejected: the domain is not document-shaped. A task's relationships to project, assignee,
labels, and comments are exactly the kind of many-to-many and one-to-many structure relational
databases are designed for. Modeling it as documents would mean either deep embedding (data
duplication, painful updates when, say, a user renames themselves and every embedded copy of
their name needs updating) or reference-heavy documents that reinvent joins in application
code, without gaining anything MongoDB is actually good at (schema flexibility isn't needed —
the schema here is well understood up front; horizontal write scaling isn't needed at this
project's scale).

### MySQL / MariaDB
A reasonable alternative — also relational, also mature. PostgreSQL was chosen over it for:
richer native types used in this schema (`JSONB` with indexing, native `UUID`, enum types),
stronger default transaction isolation behavior, and because PostgreSQL is the more common
choice in the modern Java/Spring ecosystem this project is demonstrating fluency in.

### DynamoDB / a key-value store
Rejected outright: no ability to do the ad hoc relational queries (join tasks with projects
with teams with users, filter and sort across those joins) this application needs, without
maintaining multiple denormalized access-pattern-specific tables by hand — significant
complexity with no corresponding benefit at this scale.

## Consequences

**Positive**
- Referential integrity is enforced by the database, not just by application discipline.
- Standard SQL, standard JDBC, standard Spring Data JPA — no bespoke data-access layer.
- One connection pool, one backup/restore story, one place to reason about consistency.

**Negative / trade-offs accepted**
- Vertical scaling limits apply until read replicas / sharding are introduced — deliberately
  deferred; see `docs/architecture.md`'s system-design-at-scale section (added Phase 19) for
  when that becomes necessary.
- All modules currently share one Postgres instance and one connection pool sized for a single
  small deployment (see `docs/database.md` for the pool-sizing rationale) — a slow query in one
  module can, at today's scale, add latency for every module. This is an accepted trade-off of
  the modular-monolith decision in ADR-001, not something PostgreSQL itself causes.

## Interviewer questions this ADR should let you answer
- "Why not a NoSQL database, isn't that more scalable?"
- "How do you guarantee two tables stay consistent with each other?"
- "What would make you reach for read replicas or sharding?"
