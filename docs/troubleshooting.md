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
