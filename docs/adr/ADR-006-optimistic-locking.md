# ADR-006: Optimistic locking for concurrent task updates

## Status
Accepted

## Context

CollabFlow's brief explicitly requires handling this scenario correctly: two users load the
same task, both act on what they saw, and both submit an update. Without any concurrency
control, whichever write reaches the database second silently overwrites the first - a "lost
update." Nobody gets an error; one user's change simply vanishes. This is a correctness bug,
not an edge case: it's a certainty in any multi-user system with no protection against it,
just a matter of how often it's hit in practice.

## Decision

Use **optimistic locking** via JPA's `@Version` annotation on `Task.version` (a `BIGINT`
column, defaulting to 0). Every `UPDATE` Hibernate generates for a versioned entity
automatically includes `AND version = <the value this transaction read>` in its `WHERE`
clause, and bumps the stored value by one. If a concurrent transaction already updated (and
so already bumped) that row between this transaction's read and its write, the `WHERE` clause
matches zero rows, and Hibernate raises `OptimisticLockException` - translated by
`GlobalExceptionHandler` into `409 CONFLICT` with a `CONCURRENT_MODIFICATION` error code and a
message telling the client to refresh and retry.

This was not just implemented, but proven under a real race during Phase 7 testing: two
`curl` requests fired concurrently (`&` + `wait` in bash, not sequential) against the same
task, both starting from `version: 1`. One succeeded (title/priority change, landing on
`version: 2`); the other, a status change, received `409 CONCURRENT_MODIFICATION` instead of
silently overwriting the first change. See `docs/troubleshooting.md`'s Phase 5/6 entries for
the pattern of bugs this kind of end-to-end testing (versus just reading the code) has caught
throughout this project.

**Why only `Task` has a `@Version` column, not every entity**: optimistic locking has a real
cost - every write must carry the extra `WHERE` predicate, and a losing concurrent writer gets
an error it must handle (retry, or surface to its user). That cost is worth paying where
concurrent writes to the *same row* by *different users* are a realistic, product-relevant
scenario - exactly the "two people editing the same task" case the brief calls out. A user's
own profile, a team's name, a project's description are edited by one person at a time in
normal use; adding version-checking there would be cost without a corresponding benefit. This
is a deliberate, scoped decision, documented on `Task`'s own Javadoc, not an oversight that
happens to have skipped other entities.

## Alternatives considered

### Pessimistic locking (`SELECT ... FOR UPDATE`)
Would prevent the conflict by having the first reader hold a database-level lock on the row
until its transaction ends, blocking (not failing) a second reader/writer until the first
finishes. Rejected as the default here because it trades a rare, cheap-to-handle 409 for
request threads blocking on each other - a worse trade for a web API where a "transaction"
corresponds to one HTTP request, and a slow or hung request would now stall every other user
trying to touch the same task, not just fail cleanly. Pessimistic locking remains the right
tool for a narrower, different problem - e.g., serializing two `SELECT ... FOR UPDATE`-style
balance-decrement operations in a financial ledger, where "let one wait for the other" is
exactly the desired behavior instead of an error either side must handle. Nothing in this
schema currently has that shape.

### No concurrency control, "last write wins"
The default if nothing is done. Rejected outright - it's the lost-update bug itself, and the
brief explicitly requires this project to demonstrate that it doesn't have this bug.

### Application-level "check the timestamp, then update" (manual optimistic check)
Read `updated_at`, compare it in application code before writing, `UPDATE ... WHERE updated_at
= ?`. This is functionally similar to what `@Version` does, but reinvents it with a coarser
column (timestamp precision can theoretically collide; a dedicated integer version cannot) and
without Hibernate's built-in `OptimisticLockException` handling. `@Version` was chosen as the
standard, well-understood JPA mechanism for exactly this problem.

## Consequences

**Positive**
- Lost updates are structurally impossible for tasks, not just "unlikely" - verified under an
  actual concurrent-request race, not just unit-tested with mocks.
- The failure mode (409, "refresh and try again") is one a client can handle mechanically -
  re-fetch, re-apply, retry - without any data loss or silent corruption.

**Negative / trade-offs accepted**
- A genuinely busy task (many people editing constantly) will produce more `409`s under this
  scheme than under pessimistic locking's "wait your turn" behavior. At this project's scale,
  that trade is clearly worth it; a hypothetical task board with extremely high per-task
  contention might eventually revisit this.
- The client (frontend, Phase 12+) must actually handle `409 CONCURRENT_MODIFICATION` -
  re-fetching and either re-applying the user's change or prompting them - rather than treating
  it as a generic error. This is called out explicitly so it isn't silently dropped later.

## Interviewer questions this ADR should let you answer
- "What happens if two people edit the same task at the same time?"
- "Why optimistic locking and not pessimistic locking (`SELECT FOR UPDATE`)?"
- "Why don't your other entities have a version column?"
- "How did you actually verify this works, versus just writing the code?"
