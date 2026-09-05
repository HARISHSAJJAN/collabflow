# CollabFlow

A distributed project & team collaboration platform (Jira/Trello/Slack-inspired) built as a
**modular monolith** — a single Spring Boot backend with strongly separated domain modules,
a PostgreSQL database, Redis caching, Kafka-based eventing, and WebSocket real-time updates.

This is a personal portfolio/learning project. It is **not** deployed to production, has no
real users, and no performance numbers below are claimed unless they were actually measured
locally — see `docs/troubleshooting.md` and `docs/decisions.md` for what is and isn't
implemented yet.

## Status

Under active, phased development. See `docs/architecture.md` → "Development phase log" for
exactly which phases are complete. Currently: **Phase 11 (notifications: due-date reminders + @mentions) done.**

## Why this project exists

To demonstrate — and let its author honestly defend in an interview — real understanding of:
backend architecture, REST API design, relational database design, concurrency (optimistic
locking), caching strategy, event-driven architecture with Kafka, real-time systems with
WebSockets, authentication/authorization, testing, containerization, and CI/CD. See
`docs/interview-preparation.md` (added as later phases complete) for the questions this
project is built to be able to answer.

## Architecture at a glance

```
collabflow/
├── backend/     Spring Boot modular monolith (Java 21, Maven)
├── frontend/    React + TypeScript + Vite
├── docs/        architecture, database, API, security, ADRs, interview prep
├── docker-compose.yml   Postgres + Redis + Kafka (+ backend/frontend once containerized)
└── .env.example
```

Full module breakdown and rationale: [`docs/architecture.md`](docs/architecture.md) and
[ADR-001](docs/adr/ADR-001-modular-monolith.md).

## Technology stack

| Concern | Choice | Why (ADR) |
|---|---|---|
| Language / runtime | Java 21 (LTS) | modern LTS, virtual threads, records |
| Framework | Spring Boot 3.5 | de facto standard, huge ecosystem, matches the skills this project targets |
| Database | PostgreSQL 16 | relational integrity for a highly relational domain (teams→projects→tasks) |
| Cache | Redis 7 | see ADR-003 (added Phase 9) |
| Messaging | Apache Kafka (KRaft) | see ADR-004 (added Phase 10) |
| Real-time | WebSocket + STOMP | see ADR-005 (added Phase 12) |
| Migrations | Flyway | versioned, reviewable schema evolution |
| Module boundaries | Spring Modulith | enforced (not just conventional) modular monolith |
| Frontend | React + TypeScript + Vite | modern, fast dev loop, not the focus of this project |
| Containerization | Docker / Docker Compose | reproducible local environment |
| CI | GitHub Actions | build → test → package → docker build |

Full list with reasoning: [`docs/decisions.md`](docs/decisions.md).

## Prerequisites

- Java 21 (JDK)
- Maven 3.9+ (or use the included wrapper once added)
- Docker + Docker Compose
- Node.js 20+ (frontend only)

## Running locally

1. Copy `.env.example` to `.env` and fill in real values (at minimum generate a `JWT_SECRET`
   with `openssl rand -base64 64` — the app refuses to start without one, on purpose).
2. Start infrastructure:
   ```bash
   docker compose up -d postgres redis kafka
   ```
3. Run the backend (until Phase 16, run it directly rather than via Docker):
   ```bash
   cd backend
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```
   The app reads DB/Redis/Kafka connection details from environment variables (see
   `.env.example`); export them into your shell or use a tool like `direnv` / your IDE's
   run-configuration env support.
4. API docs (Swagger UI) once the auth/API phases land: `http://localhost:8080/swagger-ui.html`
5. Health check: `http://localhost:8080/actuator/health`

Frontend setup instructions are added once the frontend phase begins.

## Testing

```bash
cd backend
mvn test
```

Test strategy (unit, integration with Testcontainers, security, concurrency) is documented in
`docs/architecture.md` and expanded in Phase 14.

## Environment variables

See [`.env.example`](.env.example) for the full list with defaults and descriptions. Secrets
(`JWT_SECRET`, `DB_PASSWORD`, `REDIS_PASSWORD`) are never committed and have no baked-in
default in `application.yml` — the app fails fast at startup if `JWT_SECRET` is missing.

## Documentation

- [`docs/architecture.md`](docs/architecture.md) — module boundaries, real-time flow, event flow
- [`docs/database.md`](docs/database.md) — schema, indexes, concurrency (added Phase 2+)
- [`docs/redis.md`](docs/redis.md) — what's cached and why, rate limiting (added Phase 9)
- [`docs/kafka.md`](docs/kafka.md) — topics, delivery guarantees, idempotency (added Phase 10)
- [`docs/api.md`](docs/api.md) — REST API reference (added Phase 3+)
- [`docs/security.md`](docs/security.md) — security review (added Phase 15)
- [`docs/deployment.md`](docs/deployment.md) — Docker/CI-CD (added Phase 16-17)
- [`docs/troubleshooting.md`](docs/troubleshooting.md) — common local-dev issues (added as they're found)
- [`docs/decisions.md`](docs/decisions.md) — ADR index
- [`docs/interview-preparation.md`](docs/interview-preparation.md) — question bank (added Phase 20)

## License

Personal portfolio project. No license granted for reuse yet.
