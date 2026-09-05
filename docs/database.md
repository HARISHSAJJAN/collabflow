# Database

PostgreSQL 16. Rationale for choosing PostgreSQL at all: [ADR-002](adr/ADR-002-postgresql.md).
Migrations are managed with Flyway, versioned SQL files in
`backend/src/main/resources/db/migration/`, run automatically on application startup.

## Why Flyway (not Hibernate `ddl-auto: update`, not Liquibase)

- `ddl-auto: update`/`create` (letting Hibernate infer and apply schema changes from entity
  classes) is explicitly avoided. It's fine for a five-minute demo, but it can silently
  generate destructive or unintended changes, gives no history of *how* the schema got to its
  current shape, and can't be reviewed in a pull request the way a `.sql` file can.
  `application.yml` sets `hibernate.ddl-auto: validate` everywhere — Hibernate is only allowed
  to check that entity mappings match the schema Flyway already created, never to change it.
- Liquibase (XML/YAML changesets) is a reasonable alternative but adds a layer of abstraction
  over SQL that isn't needed here — this project's migrations are plain, reviewable SQL, and
  being able to read exactly what will run against the database is worth more than
  Liquibase's database-portability feature (this project is intentionally PostgreSQL-only).
- **Migration versioning**: each file is named `V<n>__description.sql`. Flyway tracks which
  versions have been applied in a `flyway_schema_history` table it manages itself, and refuses
  to start the application if a previously-applied migration file's checksum has changed
  underneath it (protecting against "someone edited an already-shipped migration"). This is
  what makes schema evolution safe across multiple environments (a developer's machine, CI, a
  future staging/production environment) — everyone runs the same ordered, checksummed set of
  changes.
- **Rollback**: Flyway's free/community edition (used here) does not support automated
  `undo` migrations. Rolling back a bad migration in this project means writing and applying a
  new *forward* migration that reverses the change (e.g. `V7__drop_bad_column.sql`), the same
  way most teams handle it in practice — not because "true" rollback wouldn't be nice, but
  because in a real multi-writer production database, blindly running a generated "undo" of a
  schema change that data has already been written under is often more dangerous than writing
  a deliberate forward fix.
- **Schema evolution**: each phase of this project that touches the schema adds its own
  migration file(s) rather than one giant upfront schema — see the table below. This is a
  deliberately honest choice: it's what schema evolution actually looks like on a real project
  (add a table when the feature needing it is built), rather than a single "final" schema
  written all at once and dressed up as if it evolved.

## Migrations applied so far

| Version | File | Adds | Phase |
|---|---|---|---|
| V1 | `V1__create_users_table.sql` | `users` | 2 |
| V2 | `V2__create_refresh_tokens_table.sql` | `refresh_tokens` | 2 |
| V3 | `V3__create_teams_and_team_members.sql` | `teams`, `team_members` | 5 |
| V4 | `V4__create_projects_and_project_members.sql` | `projects`, `project_members` | 6 |
| V5 | `V5__create_tasks_and_task_labels.sql` | `tasks`, `task_labels` | 7 |
| V6 | `V6__create_comments.sql` | `comments` | 8 |
| V7 | `V7__create_audit_logs.sql` | `audit_logs` | 8 |
| V8 | `V8__create_notifications_and_processed_events.sql` | `notifications`, `processed_events` | 10 |
| V9 | `V9__add_task_search.sql` | `tasks.search_vector` (generated column) + GIN index | 13 |

## `users`

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | PK, `gen_random_uuid()`. See ADR-002 for why UUID over a serial int. |
| `email` | `VARCHAR(255)` | Enforced unique via `ux_users_email_lower` on `lower(email)`, not on the raw column — see the migration file comment for why. |
| `password_hash` | `VARCHAR(255)` | BCrypt hash only. The raw password is never persisted or logged; see `docs/security.md` (added Phase 3/15). |
| `full_name` | `VARCHAR(255)` | |
| `avatar_url` | `VARCHAR(1024)`, nullable | |
| `is_active` | `BOOLEAN` | Soft-disable a user without deleting their row (and everything that references it). |
| `last_login_at` | `TIMESTAMPTZ`, nullable | |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | Present on every table in this schema, defaulted to `now()`. |

**Why no separate `roles` table**: the brief lists `roles` as a "likely entity," but this
schema doesn't create one. Team/project roles (`OWNER`/`ADMIN`/`MEMBER`) are a small, fixed,
rarely-changing set — modeled as a Postgres enum on `team_members.role` (added in Phase 5)
rather than a lookup table joined on every permission check. A separate `roles` table would
only pay for itself if roles needed to be dynamic (tenant-defined custom roles), which this
system does not require. This is deliberate under-normalization, and it's the kind of
schema decision worth being able to defend in an interview: normalize where it prevents
inconsistency, not automatically everywhere a value repeats.

## `refresh_tokens`

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID` | PK |
| `user_id` | `UUID` | FK → `users.id`, `ON DELETE CASCADE` (deleting a user removes their sessions) |
| `token_hash` | `VARCHAR(255)` | SHA-256 hash of the actual refresh token — see the migration file comment. Unique. |
| `issued_at`, `expires_at` | `TIMESTAMPTZ` | |
| `revoked_at` | `TIMESTAMPTZ`, nullable | Set on logout or on rotation (see ADR-008, added Phase 3) |
| `replaced_by_token_id` | `UUID`, nullable, FK → `refresh_tokens.id` | Builds the rotation chain, so a reused/stolen old token can be detected and the whole chain revoked |
| `user_agent`, `ip_address` | nullable | Shown to the user under "manage sessions" so they can recognize/revoke a specific device |

### Indexing rationale

- `ux_refresh_tokens_token_hash` (unique): every token refresh does an exact-match lookup by
  hash — this is the hottest query against this table and must also enforce uniqueness.
- `ix_refresh_tokens_user_id_active` (partial, `WHERE revoked_at IS NULL`): "list my active
  sessions" and "revoke all sessions" both filter by `user_id`, and only care about
  not-yet-revoked rows. A partial index keeps it small even as revoked/expired history
  accumulates, instead of indexing rows that are never queried by this predicate again.
- `ix_refresh_tokens_expires_at`: backs a scheduled cleanup job (Phase 3) that deletes rows
  well past expiry, so this table doesn't grow unbounded.

## `teams` / `team_members`

| Table | Notable columns | Notes |
|---|---|---|
| `teams` | `created_by UUID REFERENCES users(id)` | See "Cross-module foreign keys" below - this is a real SQL foreign key, but `Team.java` does **not** have a JPA `@ManyToOne` to `User`. |
| `team_members` | `role VARCHAR(20) CHECK (role IN (...))`, unique on `(team_id, user_id)` | One row per (team, user) - "assign a role" is an `UPDATE`, not a new row. See the V3 migration's comment for why `CHECK` was chosen over a native Postgres `ENUM` type. |

Indexes: `ux_team_members_team_user` (unique, and doubles as the index for "list this team's
roster" since `team_id` is its leading column), `ix_team_members_user_id` (for "list my
teams" - a `user_id`-only lookup the unique index's column order can't serve efficiently),
`ix_teams_created_by`.

### Cross-module foreign keys: a database-level FK without a Java-level object reference

`teams.created_by` has a real `REFERENCES users(id)` constraint - PostgreSQL still enforces
that a team can't be created for a nonexistent user, and referential integrity doesn't erode
just because `users` and `teams` are owned by different Java modules. What does **not** exist
is a JPA `@ManyToOne` from `Team` to `user.internal.User`: that would require the `team`
module to import a class from `user.internal`, which is exactly the kind of dependency Spring
Modulith's boundary check is there to catch and fail the build over (see ADR-001). Every
cross-module reference in this schema follows the same pattern: a plain UUID column, a real
SQL foreign key for integrity, and - when the referencing module needs display data about the
referenced row - a call to the owning module's public service (e.g.
`UserAccountService.findSummaryById(...)`) rather than an object graph traversal. This is a
deliberate, consistent convention across the whole codebase, not a one-off.

## `projects` / `project_members`

| Table | Notable columns | Notes |
|---|---|---|
| `projects` | `team_id UUID REFERENCES teams(id)`, `status VARCHAR(20) CHECK (...)` | A project always belongs to exactly one team. `status` is `ACTIVE`/`ARCHIVED` (same CHECK-not-native-enum reasoning as `team_members.role`). |
| `project_members` | unique on `(project_id, user_id)` | **No `role` column.** A project member's permissions are their *team* role, looked up at authorization time (`ProjectService`) - see the V4 migration's comment. Adding a project-level role that duplicates the team role would be two sources of truth for the same fact. |

Indexes: `ix_projects_team_id_status` (a composite index serving both "all of a team's
projects" and "a team's ACTIVE projects" - `status` alone would rarely be queried without also
filtering by team), `ux_project_members_project_user`, `ix_project_members_user_id`.

## `tasks` / `task_labels`

| Table | Notable columns | Notes |
|---|---|---|
| `tasks` | `version BIGINT NOT NULL DEFAULT 0` | JPA optimistic locking (`@Version`) - see [ADR-006](adr/ADR-006-optimistic-locking.md). This is the only table in the schema with a version column; see that ADR and `Task.java`'s Javadoc for why it's scoped to just this one, product-motivated case. |
| `task_labels` | unique on `(task_id, label)` | Free-text per task, not a shared catalog - see `docs/decisions.md`. |

Indexes: `ix_tasks_project_id_status` (composite - the task board is always project-scoped,
then usually filtered by status/column), `ix_tasks_assignee_id` (partial, `WHERE assignee_id
IS NOT NULL` - backs "my assigned tasks," the dashboard's central cross-project query),
`ix_tasks_due_date` (partial, for "upcoming deadlines" and future date-range filtering in
Phase 13).

### Concurrency: proving the lost-update scenario is actually prevented

Two real `curl` requests fired concurrently (not sequentially) against the same task, both
starting from the same version, is the exact test run during Phase 7 (see
`docs/troubleshooting.md`'s Phase 5/6 entries for the pattern of bugs this kind of true
end-to-end testing - as opposed to reading the code and assuming - has caught in this
project). One request won and the row's version advanced by exactly one; the other received
`409 CONCURRENT_MODIFICATION` instead of silently overwriting the winner's change. A proper
concurrent-access `TaskConcurrencyTest` (two real threads, not two sequential requests) is
added in Phase 14 to keep this guarantee under automated regression coverage.

## `comments`

`(task_id, author_id, body, edited_at, created_at, updated_at)`. `edited_at` is set explicitly
by application code (`Comment.edit()`) the moment a genuine post-creation edit happens - not
derived by comparing `created_at`/`updated_at`, which are both populated by Hibernate at the
same flush and aren't guaranteed to differ meaningfully for a just-created row. Index:
`ix_comments_task_id_created_at` (a task's comment thread, in order - the only query this
table needs to serve).

## `audit_logs`

`(actor_id, action, resource_type, resource_id, project_id, team_id, old_value JSONB,
new_value JSONB, created_at)`. `actor_id` is nullable - unlike almost every other "who did
this" column in this schema - because a future system-initiated action (a scheduled job) would
have no human actor, and the trail should still be able to record what happened.
`old_value`/`new_value` are `JSONB` (mapped via Hibernate 6's built-in `@JdbcTypeCode(SqlTypes.
JSON)`, no third-party library needed) because a generic audit trail spans many different
resource types with genuinely different "what changed" shapes - see the V7 migration's
comment and [ADR-002](adr/ADR-002-postgresql.md) for why this is a deliberate, narrow use of
JSONB rather than a wholesale move away from relational modeling elsewhere in this schema.

Populated purely by listening to events from other modules (task, project, team, comment) -
see `AuditService`'s Javadoc and docs/architecture.md's "Two kinds of cross-module events."
Indexes: `ix_audit_logs_project_id_created_at` and `ix_audit_logs_team_id_created_at` (both
partial, `WHERE ... IS NOT NULL` - not every row has both scopes) back "view project/team
activity"; `ix_audit_logs_actor_id` backs "what has this user done."

## `notifications` / `processed_events`

| Table | Notable columns | Notes |
|---|---|---|
| `notifications` | `type VARCHAR(50)` (no `CHECK` constraint, unlike other status-like columns in this schema), `payload JSONB`, `is_read BOOLEAN` | `type` has no `CHECK` constraint deliberately: new notification types are expected to be added over time (this project already added two - `TASK_MENTION`, `DUE_DATE_APPROACHING` - in Phase 11 alone) and a `CHECK` list would need a migration for each one, for a column whose valid values are really an application-level concern (`NotificationType`), not a database integrity concern the way `task.status` is. |
| `processed_events` | `event_id UUID PRIMARY KEY` | The Kafka-consumer idempotency ledger - see [ADR-004](adr/ADR-004-kafka.md) and `docs/kafka.md` for the two real bugs found getting this table's *use* right (a JPA `save()`-vs-`persist()` upsert trap, then a transaction-rollback-poisoning trap) before landing on an atomic `INSERT ... ON CONFLICT DO NOTHING`. |

Indexes: `ix_notifications_recipient_created_at` (my notifications, newest first - the only
ordering this table needs) and a partial `ix_notifications_recipient_unread` (`WHERE is_read =
FALSE` - keeps the unread-count query cheap regardless of how much read history accumulates).

## `tasks.search_vector` (Phase 13)

A generated, `STORED` `tsvector` column (`to_tsvector('english', title || ' ' ||
description)`) with a GIN index, backing keyword search - see
[`docs/search.md`](search.md) for the full "why PostgreSQL text search, not Elasticsearch"
reasoning and a real bind-parameter-type-inference bug this phase's native query surfaced
(see `docs/troubleshooting.md`).

## Connection pooling

HikariCP (Spring Boot's default), sized small on purpose: `maximum-pool-size: 10`,
`minimum-idle: 2` (see `application.yml`). PostgreSQL connections are relatively expensive
(each holds a backend process on the server), and this is a single-instance app talking to a
single small Postgres instance — a large pool would just create more contention on the
database side for no throughput benefit at this scale. `docs/architecture.md`'s scaling
section (added Phase 19) covers how pool sizing changes with multiple app instances and/or
read replicas.

## Pagination

Every "list" endpoint that can return more than a handful of rows (tasks, projects, team
members, notifications, audit logs) is paginated with Spring Data's `Pageable` — offset-based
for now, using an indexed sort column. Nothing in this codebase does `findAll()` on a
potentially large table and returns it whole. Keyset/cursor pagination is discussed as a future
improvement once a table's page-N-of-many offset cost would actually matter (see
`docs/architecture.md`'s scaling section).
