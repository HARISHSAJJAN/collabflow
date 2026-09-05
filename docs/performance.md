# Performance review (Phase 19)

Every number on this page was actually measured on this project's own development machine
(a single Windows laptop running Postgres/Redis/Kafka via the Testcontainers-free
`docker-compose.yml` dev stack, plus the Spring Boot app running natively, not containerized)
- nothing here is estimated, industry-average, or copied from someone else's benchmark. That
also means these numbers say nothing about production capacity on real infrastructure; they're
useful for one thing only - comparing this app's own endpoints against each other and against
itself before/after a change, which is exactly how the finding below was made.

## Real finding: an N+1 query on every task listing endpoint

**What was there**: `TaskService.listTasks`, `searchTasks`, and `listMyAssignedTasks` each
mapped their result page with `page.map(t -> toResponse(t, labelsOf(t.getId())))` -
`labelsOf` is one `SELECT` per task. A page of 20 tasks meant 1 query for the page of tasks
plus 20 more, one per row, for their labels: 21 queries to render one page.

**How it was found**: not by reading the code and guessing - by running a real load test
(`k6`, scenario below) against the `list_tasks`/`search_tasks` endpoints, then checking the
actual SQL Hibernate issued (`logging.level.org.hibernate.SQL: DEBUG`, the `dev` profile
already has this on) for a single request.

**The fix**: `TaskLabelRepository.findByTaskIdIn(Collection<UUID>)`, one query for every label
belonging to every task on the page, grouped into a `Map<UUID, List<String>>` in memory and
looked up per row instead of queried per row. Verified before/after by literally counting the
`task_labels` queries logged for one identical request:

```
Before: 1 query for the page of tasks, then 20 individual
        `select ... from task_labels where task_id=?` queries (one per task)

After:  1 query for the page of tasks, then exactly 1
        `select tl1_0.id,tl1_0.label,tl1_0.task_id from task_labels tl1_0
         where tl1_0.task_id in (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`
```

...and confirmed the batched version still returns the *correct* data, not just fewer queries:
labeled 3 of the 20 tasks on a page, requested the page, and confirmed exactly those 3 (and no
others) came back with their label attached.

This is the kind of bug that's easy to miss precisely because it doesn't fail anything - the
endpoint returns correct data and, at this project's tiny data volumes (a handful of tasks per
project), the extra round trips to a local Postgres are fast enough to be invisible in casual
testing. It gets worse linearly with page size and is a completely different story against a
network-hop-away production database instead of `localhost` - exactly why "it works and feels
fast on my machine" isn't the same claim as "this doesn't have an N+1," and why this was worth
actually measuring instead of only reading the code.

## Load test: `list_tasks` / `search_tasks` / `create_task`

Tool: [k6](https://k6.io) 2.2.0, run locally against the app running directly on the host
(`mvn spring-boot:run`), Postgres/Redis/Kafka in Docker via `docker-compose.yml`. Three
back-to-back scenarios, each simulating realistic (not maximum-throughput) usage - a
`sleep(0.1-0.2)` between each virtual user's iterations, deliberately not hammering as fast as
possible, since "requests this app can serve per second with zero think time" isn't a
meaningful number for a UI-driven app like this one:

| Scenario | Load | avg | p90 | p95 | max |
|---|---|---|---|---|---|
| `GET /api/v1/tasks` (list, paginated) | 10 VUs × 20s | 38.1ms | 44.7ms | 49.2ms | 227ms |
| `GET /api/v1/tasks/search` (full-text) | 10 VUs × 20s | 42.9ms | 54.4ms | 58.4ms | 194ms |
| `POST /api/v1/tasks` (create) | 5 VUs × 20s | 12.4ms | 14.2ms | 15.3ms | 65ms |

3,314 total requests, **0 failures** (`http_req_failed: 0.00%`), ~55 req/s aggregate across all
three scenarios combined (again, throttled by the deliberate `sleep()`, not the app's ceiling).
Full k6 output, including every built-in HTTP/iteration metric, was inspected before writing
this table down - only the numbers that support a specific claim above are reproduced here.

**What this test does not claim**: it is not a capacity test (no attempt to find the app's
actual breaking point/max throughput), not a soak test (20 seconds per scenario, not hours),
and not run against anything resembling production hardware or a production-sized dataset (30
seed tasks, one project). It answers one real question honestly - "are these three endpoints
fast and correct under light concurrent load, on this machine, today" - and no other.

## Other reviewed, already-adequate areas

- **Connection pool sizing**: `application.yml`'s Hikari `maximum-pool-size: 10` /
  `minimum-idle: 2` - see the comment there and `docs/database.md` for the sizing rationale.
  Not changed by this review; the load test above never came close to exhausting it (Postgres
  connections aren't the bottleneck at this concurrency).
- **Indexes**: reviewed against `docs/database.md`'s migration/schema tables - every foreign
  key and every column this project actually filters/sorts by in a hot query already has one
  (including the GIN index backing full-text search, added in Phase 13). No missing index was
  found during this review.
- **Caching**: `TeamService.findRole` and `UserAccountService.findSummaryById` are the two
  Redis-cached lookups (see `docs/redis.md`); both sit on the hot path of nearly every
  authenticated request (authorization checks), which is exactly the reasoning that was already
  used to choose what to cache in Phase 9 - this review didn't find a case that should be
  cached and isn't, or one that's cached and shouldn't be.
