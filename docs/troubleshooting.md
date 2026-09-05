# Troubleshooting

Real issues hit during development of this project, and how they were actually diagnosed and
fixed — not a generic checklist.

## Backend fails to start: `FATAL: password authentication failed for user "collabflow"`

**When**: running the backend locally against `docker compose up -d postgres`, with the
correct `DB_PASSWORD` exported and confirmed correct.

**Root cause found**: the host machine already had a **native PostgreSQL service** (a Windows
service, `postgres.exe`) bound to `0.0.0.0:5432`. Docker Desktop's port-forwarding for the
`postgres` container was *also* configured to publish `5432`, and `netstat` showed two
different processes both listed as `LISTENING` on that port. Connections from the host to
`localhost:5432` were non-deterministically reaching the native service instead of the Docker
container, so the credentials the app sent (correct for the container) were rejected by a
different Postgres instance entirely.

Confirmed by:
```bash
netstat -ano | grep ":5432"          # showed two PIDs listening
tasklist //FI "PID eq <pid>"         # one was postgres.exe (a Windows service), one was Docker
docker exec -e PGPASSWORD=... collabflow-postgres psql -U collabflow -d collabflow -c "select 1;"
# ^ this succeeded, proving the container's own credentials were correct all along
```

**Fix**: map the container to a non-default host port instead of fighting the existing
service. `docker-compose.yml` already parameterizes the host port via `${DB_PORT:-5432}`, so
the fix was just setting `DB_PORT=5433` in `.env` (and defaulting `.env.example` to `5433`)
and recreating the container. `DB_HOST`/`DB_PORT` inside the app's own config are read from
the same `.env`, so no code change was needed.

**Takeaway**: if a "correct password" is still rejected, verify with `netstat`/`docker port`
that you're actually talking to the container you think you are, especially on a machine that
might already run Postgres/Redis/Kafka natively or via another project's Docker Compose setup
on the default port.

## `mvn compile` fails with `NoClassDefFoundError: org/springframework/boot/json/JsonWriter$Extractor`

**When**: first `mvn compile` after adding `spring-modulith-starter-core`.

**Root cause**: `spring-modulith-apt` (an annotation processor bundled transitively by
`spring-modulith-starter-core`, used to generate module-documentation JSON at compile time)
calls into Spring Boot's internal `org.springframework.boot.json.JsonWriter` class. That
class is not public API, and its shape changed in a later Spring Boot 3.5.x patch release
than the one `spring-modulith-apt:2.1.1` was built/tested against, so the two are binary
incompatible even though both jars declare compatibility with "Spring Boot 3.5."

**Fix**: exclude the `spring-modulith-apt` transitive dependency in `backend/pom.xml`. This
only disables the *optional* documentation-generating annotation processor — the actual
module-boundary check (`ApplicationModules.of(...).verify()`, a JUnit test added in Phase 14)
runs via reflection at test time and does not need `spring-modulith-apt` at all.

**Takeaway**: when a very recent patch version of a big framework (here, a `3.5.16` picked up
automatically by "always resolve latest") breaks an ecosystem library's internal-API usage,
excluding the offending optional artifact is often less risky than pinning the whole framework
back to an older patch version.

## Refresh-token reuse detection didn't actually revoke other sessions

**When**: manually testing the "stolen refresh token" scenario during Phase 3 - present an
already-rotated (already-used) refresh token, expect every session for that user to be
revoked as a precaution. The 401 response was correct, but a *second* still-valid rotated
token kept working afterward, when it should have been revoked too.

**Root cause**: `RefreshTokenService.rotate()` performed the "revoke every active session for
this user" bulk update, then returned a result that `AuthService.refresh()` turned into a
thrown `InvalidRefreshTokenException` - both inside the *same* `@Transactional` method
boundary (`AuthService.refresh`, with `rotate` joining that transaction via default
`REQUIRED` propagation). Spring's default behavior is to roll back the transaction on any
unchecked exception, so throwing to report "this token is invalid" silently rolled back the
revocation that was supposed to be the security response.

Confirmed by inspecting `refresh_tokens` directly after the failed test: the token that should
have been revoked instead had no `revoked_at` and was successfully rotated by a later request
- proof its revocation never actually committed.

**Fix**: moved the revocation into its own Spring bean, `SessionRevocationService`, with
`@Transactional(propagation = Propagation.REQUIRES_NEW)`. `REQUIRES_NEW` only takes effect
when called *through the Spring proxy* (i.e. from a different bean) - a same-class call would
have bypassed the proxy and stayed in the original, doomed-to-roll-back transaction. See
ADR-008 for the full writeup.

**Takeaway**: when a side effect must survive regardless of what the calling code does next
(especially "report failure by throwing"), check whether it's sharing a transaction with code
that might roll back - and remember that `@Transactional` propagation changes only take effect
across a proxy boundary, not on a same-class method call.

## `@TransactionalEventListener` action silently didn't happen (`TransactionRequiredException` in the logs, but the client got a normal success response)

**When**: Phase 4, wiring "revoke all sessions when the password changes" as a
`@TransactionalEventListener(phase = AFTER_COMMIT)` in `AuthService`, listening for a
`PasswordChangedEvent` published by the `user` module. The password-change HTTP call
correctly returned `204`, but a refresh token issued before the change still worked
afterward - the revocation silently never took effect.

**Root cause**: two stacked mistakes, in order:

1. First attempt: `onPasswordChanged` had no `@Transactional` of its own and called
   `logoutAllSessions(...)` (which *is* `@Transactional`) via a same-class ("self")
   invocation. Self-invocation bypasses the Spring proxy, so no transaction was opened at
   all, and the bulk-revoke `@Modifying` query failed with
   `jakarta.persistence.TransactionRequiredException`. Because this happens inside an
   `AFTER_COMMIT` listener - which runs *after* the original HTTP request's transaction has
   already committed and its response effectively decided - the exception only showed up as
   an `ERROR o.s.t.s.TransactionSynchronizationUtils - TransactionSynchronization.
   afterCompletion threw exception` in the server log, never as an HTTP error to the client.
   This is worth remembering on its own: a failure in an `AFTER_COMMIT` listener cannot
   surface as an HTTP error, because the response for the request that triggered it may
   already be on the wire.
2. Second attempt: added plain `@Transactional` to `onPasswordChanged` to fix that - Spring
   refused to even start, failing fast at context-refresh time with `@TransactionalEvent
   Listener method must not be annotated with @Transactional unless when declared as
   REQUIRES_NEW or NOT_SUPPORTED`. This makes sense in hindsight: by the time an
   `AFTER_COMMIT` listener runs, the transaction it was listening on is already gone, so
   `REQUIRED` propagation ("join the current transaction, or start one if none exists") has
   nothing sensible to join.

**Fix**: `@Transactional(propagation = Propagation.REQUIRES_NEW)` on `onPasswordChanged`.

**Takeaway**: a `@TransactionalEventListener` method needs its own explicit transaction
management if it does write work, and that transaction must be `REQUIRES_NEW` (or
`NOT_SUPPORTED`) - never plain `REQUIRED` - because there is no ambient transaction left by
the time `AFTER_COMMIT`/`AFTER_ROLLBACK`/`AFTER_COMPLETION` fires. And because failures here
don't surface to the original HTTP client, this kind of listener is worth a specific
end-to-end test (or at minimum a manual check of the server log), not just a happy-path check
of the endpoint that published the event.

## API response had `null` (or stale) `createdAt`/`updatedAt`/`joinedAt`, even though the database row was correct

**When**: Phase 5, `POST /api/v1/teams` - the response's `createdAt`/`updatedAt` came back
`null` immediately after creating a team, and a freshly-added team member's `joinedAt` was
also `null`.

**Root cause**: `createdAt`/`updatedAt` (on `BaseEntity`, via `@CreationTimestamp`/
`@UpdateTimestamp`) and `TeamMember.joinedAt` are populated by Hibernate at **flush** time,
not at the moment `repository.save(entity)` is called. `save()` on a new entity with a
client-generated id (`GenerationType.UUID` - see `BaseEntity`'s Javadoc) doesn't need to hit
the database immediately to get an id back the way `GenerationType.IDENTITY` would, so
Hibernate has no reason to flush early; with default `FlushMode.AUTO` and no intervening
query, nothing forces a flush until the transaction commits. Reading
`team.getCreatedAt()` on the same in-memory instance immediately after `save()`, inside the
same transaction, therefore reads a field Hibernate hasn't populated yet.

The row in the database is always correct once the transaction actually commits - this bug
only affected the JSON returned for *that specific request*, which builds its response from
the in-memory entity before commit.

**Fix**: `repository.saveAndFlush(entity)` instead of `save(entity)` at every call site that
immediately reads a Hibernate-generated field back into a response DTO within the same
transaction (`TeamService.createTeam`, `updateTeam`, `addMemberByEmail`).

**Takeaway**: any time a response DTO is built from an entity's `@CreationTimestamp`/
`@UpdateTimestamp`/similar Hibernate-generated field right after writing that same entity in
the same transaction, check whether a flush actually happened first - `save()` alone does not
guarantee it. This is easy to miss because it only shows up in the response, not in the
database, so a "does the data look right in the DB?" check alone won't catch it; it takes an
end-to-end request/response test, which is exactly how this was caught.

## Cache-invalidation race: evicting before commit let a concurrent read re-cache a stale value

**When**: Phase 9, implementing cache invalidation for `TeamService.findRole` (evicted on
`changeRole`/`removeMember`) and `UserAccountService.findSummaryById` (evicted on
`updateProfile`). Caught by reasoning through the transaction timing while writing the
eviction calls - not from an observed test failure, unlike the Phase 3-5 bugs above. It's
included here for the same reason: the pattern (a side effect racing against its own
transaction's commit) is real and worth recognizing on sight, whether it's caught by
inspection or by a failing test.

**Root cause**: the eviction call originally ran inside the same `@Transactional` method as
the database write that made the cached value stale, *before* that transaction committed.
Between the eviction and the commit, a concurrent request reading the same key would miss the
(now-empty) cache, query PostgreSQL - which, under normal read-committed isolation, still
shows the *pre-write* row, since this transaction hasn't committed yet - and re-cache that
soon-to-be-stale value. Once the original transaction then committed, the cache would be left
holding data that was already wrong, and would keep serving it until the TTL expired.

**Fix**: `RedisCacheService.evictAfterCommit`, which uses
`TransactionSynchronizationManager.registerSynchronization(...)` to defer the actual eviction
until the transaction's `afterCommit` callback fires. By the time eviction happens, the new
value is durably committed, so any reader that misses the cache after that point reads the
correct, current row.

**Takeaway**: this is the same family of bug as the `@TransactionalEventListener`/propagation
issues found in Phases 3-4 (see those entries above) - a side effect's *timing relative to
commit* matters, not just whether it eventually happens. Cache invalidation specifically
should almost always be deferred to after-commit; evicting immediately is a trap that looks
correct in a single-request test (there's no concurrent reader to race against) and only
breaks under real concurrent load, which is exactly the kind of bug that's cheap to prevent by
recognizing the pattern up front and expensive to find later by testing alone.

## Kafka idempotent-consumer dedup silently didn't work - twice, for two different reasons

**When**: Phase 10, building the `notification` module's Kafka consumers. Tested by directly
forcing message redelivery: reset the `notification-service` consumer group's offsets to
earliest with `kafka-consumer-groups.sh --reset-offsets --to-earliest`, restart the app, and
check whether already-processed messages produced duplicate notifications. (On Windows Git
Bash, running the broker's CLI scripts via `docker exec` needs `MSYS_NO_PATHCONV=1` prefixed,
or Git Bash mangles `/opt/kafka/bin/...` into a bogus Windows path first.)

**Bug 1**: the dedup marker table, `processed_events(event_id PRIMARY KEY)`, was written via
`processedEventRepository.saveAndFlush(new ProcessedEvent(eventId))`, expecting a duplicate
`eventId` to violate the primary key and throw. On replay, `processed_events`' row count
correctly stayed constant (no growth) - looking right - but the `notifications` table's
counts *doubled*. Diagnostic logging pinned it down: the dedup check reported `firstTime=true`
on both the original pass and the replay, for the identical `eventId`. Root cause:
`ProcessedEvent`'s `@Id` is assigned by application code (`new ProcessedEvent(eventId)`), not
`@GeneratedValue`. Spring Data JPA's default `isNew()` heuristic ("is the id field null?") is
therefore always `false`, so `save()` silently called `EntityManager.merge()` (an upsert)
instead of `persist()` (an insert) - on every call, including the first. `merge()` on an
already-existing id just updates that row; it never violates the primary key, so the
"duplicate" check never actually fires. Fixed by implementing `Persistable<UUID>` on
`ProcessedEvent`, with an explicit `isNew()` that's `true` for the application-code
constructor and `false` for the JPA no-arg one (see that class's Javadoc) - now `save()`
genuinely inserts.

**Bug 2** (found immediately after fixing Bug 1, on the next replay): with a real insert now
happening, a genuine duplicate correctly raised `DataIntegrityViolationException` - caught
inside `EventDeduplicationService.tryMarkProcessed`, its own `@Transactional(propagation =
REQUIRES_NEW)` bean/method, the exact pattern that had already fixed two earlier propagation
bugs in this project (Phases 3 and 4). This time the message logs showed
`org.springframework.transaction.UnexpectedRollbackException: Transaction silently rolled
back because it has been marked as rollback-only` on every "duplicate," instead of a clean
`false`. Root cause: `JpaRepository`'s own transactional advice around `saveAndFlush` sees the
constraint-violation exception *before* it ever reaches the application's `catch` block, and
marks the (REQUIRES_NEW) transaction rollback-only right then - a mark that catching the
exception one line later in application code cannot undo. When the method then returns
normally, Spring's transaction interceptor tries to commit a transaction already marked
rollback-only, and that's what throws `UnexpectedRollbackException` - which propagated up
into the Kafka listener's own catch block and triggered a retry/dead-letter cycle for what
was actually a routine, correctly-detected duplicate.

**Final fix**: stop relying on an exception at all. `ProcessedEventRepository.tryInsert` uses
a native, atomic `INSERT ... ON CONFLICT DO NOTHING` and returns the affected-row count (0 =
duplicate, 1 = new). No exception, so no transaction is ever marked rollback-only, and the
marker insert and the notification insert now safely share one ordinary transaction -
simpler than either of the two previous attempts, and correct: a genuine failure saving the
notification now rolls back the marker too, instead of permanently marking a
never-actually-notified event as done.

**Takeaway**: two lessons, not one. (1) An entity with a manually-assigned id needs
`Persistable` or Spring Data will quietly turn every `save()` into an upsert - a duplicate key
that should be impossible will simply never be detected. (2) Catching a persistence exception
one call away, even in a dedicated `REQUIRES_NEW` transaction, is not always enough - if the
failing call itself carries its own transactional advice (as `JpaRepository` methods do), that
advice can mark the transaction rollback-only before your `catch` block runs. When a "this
might legitimately fail, and that's fine" check can be expressed as a plain atomic
SQL operation instead (`ON CONFLICT DO NOTHING`, `UPDATE ... WHERE ...` returning a row count),
prefer that over catch-and-continue around an ORM `save()` - it sidesteps this whole class of
problem rather than working around it. Both bugs were found the same way: not by reading the
code and reasoning about it, but by mechanically forcing the exact scenario (message
redelivery) and checking the database afterward.

## Backend fails to start: `Required property 'collabflow.jwt.secret' not found` (or connection refused to Postgres/Redis/Kafka)

**When**: running `mvn spring-boot:run` without infrastructure up, or without a `.env`/exported
environment variables.

**Fix**:
1. `docker compose up -d postgres redis kafka` first.
2. Ensure `JWT_SECRET` is exported in the shell running Maven — the app deliberately has no
   default for it (see `application.yml`) so it fails fast instead of running with a guessable
   secret. Generate one with `openssl rand -base64 64`.
3. On Windows/Git Bash, environment variables exported with `export` in one `Bash` tool call do
   **not** persist to a separate call — source `.env` (`set -a; source .env; set +a`) in the
   *same* shell invocation that starts Maven.
