# Deployment

## Backend Docker image (Phase 16)

`backend/Dockerfile` is a three-stage build - see the file's own comments for the reasoning
behind each stage; the summary:

1. **`build`** (`maven:3.9.9-eclipse-temurin-21`): resolves dependencies from `pom.xml` alone
   before any source is copied in, so that layer's Docker cache is only invalidated when
   `pom.xml` itself changes - not on every source edit, which is the common case. Then compiles
   and packages the application (`mvn -DskipTests package` - tests run in CI/locally via
   `mvn test`, not as part of the image build, which would otherwise force a full Testcontainers
   run, including Docker-in-Docker, on every image build).
2. **`extract`** (`eclipse-temurin:21-jre-alpine`): runs Spring Boot's own
   `java -Djarmode=tools -jar app.jar extract --layers --launcher`, which splits the repackaged
   fat jar into four layers ordered by how often they actually change:
   `dependencies` (third-party jars - changes rarely), `spring-boot-loader` (Spring Boot's own
   launcher classes - changes almost never), `snapshot-dependencies` (empty for this project,
   since it has none - present for projects that do), and `application` (this project's own
   compiled classes - changes on every commit).
3. **`runtime`** (`eclipse-temurin:21-jre-alpine`): copies only those four layers, in that
   order, as separate `COPY` instructions - not the fat jar itself. This is the actual payoff of
   the layering: because Docker caches each `COPY` as its own image layer, a routine code
   change (which only touches the `application` layer) lets every earlier layer be reused from
   cache on rebuild and, more importantly for a real deployment, means only that one small
   layer needs to be re-pushed/re-pulled from a registry - not the whole dependency set again.
   Runs as a dedicated non-root user (`collabflow`), and sizes its heap with
   `-XX:MaxRAMPercentage=75.0` rather than a hardcoded `-Xmx`, so it scales correctly with
   whatever memory limit the container is actually run under instead of a number baked into the
   image that would silently become wrong if that limit ever changes.

**Verified, not just written**: built the image, ran it as the `backend` service in
`docker-compose.yml` against the real Postgres/Redis/Kafka containers, waited for its own
Docker healthcheck to report `healthy`, and exercised a real register → login round trip against
it over HTTP (got back a real JWT and refresh token, with the Phase 15 security headers present
on the response) - not just a successful `docker build`.

Base images are `-alpine` variants specifically to keep the final image small (no shell
utilities, package managers, or C library bloat beyond what Alpine's musl libc needs) - measured
at ~488MB for this project's dependency set (Kafka's client library alone pulls in a
non-trivial transitive tree).

### Running it

```bash
docker compose up -d --build
```

builds and starts Postgres, Redis, Kafka, and the backend together, waiting for each
infrastructure service's own healthcheck (`condition: service_healthy` in
`docker-compose.yml`) before starting the backend - the same dependency ordering problem this
project's Kafka setup already had to solve for KRaft (see `docs/architecture.md`), solved here
the standard Compose way instead.

### Frontend Docker image

`frontend/Dockerfile` is a two-stage build: a `node:22-alpine` stage runs `npm ci` and
`vite build` (dependencies installed from `package-lock.json` before the rest of the source is
copied in, same layer-caching reasoning as the backend image), and the runtime stage is plain
`nginx:1.27-alpine` serving the resulting static files - no Node.js at all in the image that
actually runs, since a Vite production build has no server-side runtime to speak of.
`nginx.conf` adds one thing beyond nginx's defaults: a `try_files ... /index.html` fallback, so
a hard refresh on a client-routed path like `/teams/<id>` doesn't 404 against a server that has
no idea what that path is - React Router only resolves it once `index.html`'s own JS has
loaded.

`VITE_API_BASE_URL` is a **build-time** argument (`ARG`/`ENV` in the Dockerfile), not a runtime
environment variable - Vite inlines `import.meta.env` values into the bundle during `vite
build`, so changing it after the image is built has no effect. A real multi-environment
deployment would rebuild the image per environment for exactly this reason, the same trade-off
any compile-time-configured static site makes.

This is a later addition than the rest of Phase 16: `docker-compose.yml` had a `frontend`
service block since Phase 1, in anticipation of the frontend work, but was deliberately removed
during Phase 16 itself because the React app didn't exist yet at that point - a `build:
{ context: ./frontend }` pointing at a directory with no `Dockerfile` would have made
`docker compose up` fail outright. The service definition returned once the frontend was
actually built (see `docs/frontend.md`).

## CI/CD (Phase 17, extended once the frontend existed)

`.github/workflows/ci.yml`, four jobs, run on every push and pull request against `main`:

1. **`test`**: sets up JDK 21 (Temurin, `actions/setup-java`'s built-in Maven cache) and runs
   `./mvnw test` - the real Testcontainers-backed suite from `docs/testing.md`, not a mocked
   subset. GitHub's `ubuntu-latest` runners have Docker available by default, so this needs no
   extra setup, the same way it needs none in local development. Surefire's report directory is
   uploaded as a build artifact on every run (`if: always()`), so a failure's test reports are
   inspectable from the Actions UI without re-running anything locally.
2. **`backend-docker-build`** (`needs: test` - never builds/publishes an image from code that
   didn't even pass its own tests): builds `backend/Dockerfile` with Buildx, using the GitHub
   Actions cache backend (`cache-from`/`cache-to: type=gha`) so unchanged layers don't get
   rebuilt on every run. On an actual push to `main` (never for a pull request, including one
   from a fork that wouldn't have permission to push packages here anyway), logs into GHCR with
   the automatically-provided `GITHUB_TOKEN` (no extra secret to create or rotate) and pushes
   the image tagged both `:latest` and `:<commit-sha>`.
3. **`frontend`**: `npm ci` then `npm run build` (type-check via `tsc -b`, then `vite build`) -
   the same command a real deploy would run. Deliberately does **not** run
   `frontend/e2e/smoke.spec.ts` here: that suite needs the full backend stack
   (Postgres/Redis/Kafka + the Spring Boot app) up alongside the frontend dev server, which
   would roughly double this workflow's total runtime for one additional layer of coverage over
   what the backend's own test suite and this build step already provide - see
   `docs/testing.md`'s scope philosophy. It stays a local/pre-release check for now, the same
   way the backend suite's WebSocket coverage stays manual (docs/testing.md again).
4. **`frontend-docker-build`** (`needs: frontend`): the same build-then-push pattern as
   `backend-docker-build`, for `frontend/Dockerfile`.

A `concurrency` group keyed on the branch/PR ref cancels a still-running CI job the moment a
newer commit supersedes it - no value in finishing a build for a commit nobody will look at.

**Verified, not just written**: pushed the workflow, watched it run with `gh run watch`, hit a
real failure (GHCR rejects a tag containing uppercase characters, and this repo's owner name
has them - `github.repository_owner` can't be used directly in an image tag), fixed it with a
small shell step that lowercases the owner name before it's used, pushed again, watched it go
green, and then confirmed the pushed image is genuinely public by pulling it with Docker on a
machine logged out of GHCR entirely (`docker pull ghcr.io/harishsajjan/collabflow-backend
:latest` succeeded with zero authentication).

**A pre-existing Dependabot failure, understood and left alone**: `.github/dependabot.yml`'s
`github-actions` ecosystem entry (added in Phase 15, before any workflow existed) failed its
first scheduled run with `dependency_file_not_found` - there was nothing to scan yet, since
`.github/workflows/` was empty at the time. This is expected, not a misconfiguration: Dependabot
re-scans on its own schedule, and the very next run after this phase's workflow file landed has
something to find.
