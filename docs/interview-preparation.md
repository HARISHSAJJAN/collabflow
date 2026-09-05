# Interview preparation

A consolidated question bank, pulled together from the "Interviewer questions this ADR/doc
should let you answer" section every ADR and `docs/security.md` already end with. This file
doesn't re-answer them - the point is to practice answering out loud, then check the
referenced doc for the real answer (with the actual bug, the actual trade-off, the actual
number) if anything felt shaky. Answering from memory without a concrete example, a real
number, or a real bug found is exactly the "faked experience" this project's own philosophy
(see `docs/troubleshooting.md`'s opening line) refuses to do - these questions are only useful
answered the same way.

## The one-minute pitch

"CollabFlow is a Jira/Trello-style team collaboration API - teams, projects, tasks, comments,
real-time updates - built as a **modular monolith**: one Spring Boot deployable with genuinely
enforced module boundaries (Spring Modulith fails the build if one module reaches into
another's internals), not a folder-organized ball of mud and not microservices either. The
point wasn't to build the simplest possible CRUD app - it was to deliberately hit and solve the
real problems a production system runs into: a lost-update race under concurrent edits, at-
least-once Kafka delivery needing idempotent consumers, a cache-invalidation race, transaction-
propagation bugs with `@TransactionalEventListener`, an N+1 query only a real load test
surfaced. Every one of those is documented with the actual bug, root cause, and fix - not just
the working code that resulted."

## Architecture & module boundaries (ADR-001)
- "Why didn't you just build microservices, isn't that more impressive?"
- "How do you know your modules are actually decoupled and not just in different folders?"
- "If notification traffic grew 100x, what would you actually do, concretely?"
- "What's the downside of putting everything in one JVM?"

## Database (ADR-002)
- "Why not a NoSQL database, isn't that more scalable?"
- "How do you guarantee two tables stay consistent with each other?"
- "What would make you reach for read replicas or sharding?"

## Caching & rate limiting (ADR-003)
- "Why Redis and not an in-memory cache?"
- "What happens to your app if Redis goes down - which parts, exactly?"
- "Walk me through a cache-invalidation bug you actually found."
- "Why does your rate limiter fail open instead of fail closed?"

## Event-driven architecture & Kafka (ADR-004)
- "What's your Kafka delivery guarantee, actually?"
- "How do you handle a duplicate/redelivered message?"
- "Walk me through a bug you found in your idempotent-consumer logic."
- "Why one topic per domain instead of one per event type?"

## Real-time updates (ADR-005)
- "How does WebSocket authentication actually work, given browsers can't set headers on the handshake?"
- "What happens when a client disconnects, or reconnects?"
- "Why STOMP and not raw WebSockets or SSE?"
- "What breaks about your real-time setup once you have two backend instances?"

## Concurrency (ADR-006)
- "What happens if two people edit the same task at the same time?"
- "Why optimistic locking and not pessimistic locking (`SELECT FOR UPDATE`)?"
- "Why don't your other entities have a version column?"
- "How did you actually verify this works, versus just writing the code?" (Real answer involves
  both a manual `curl & curl & wait` race in Phase 7 *and* an automated 10-thread regression
  test in Phase 14 - and a real bug in the automated version of that test, where a losing
  racer's 409 was being misread as a deserialization failure. See `docs/testing.md`.)

## API design (ADR-007)
- "Why REST and not GraphQL, given GraphQL is what a lot of new projects reach for?"
- "What would actually change your mind and make GraphQL worth it here?"
- "How do you version this API, and what's the trade-off of that approach?"
- "Where does gRPC fit, and why doesn't this project use it?"

## Authentication (ADR-008)
- "Why not just use one kind of token everywhere?"
- "How do you actually invalidate a JWT before it expires?"
- "Walk me through what happens if a refresh token is stolen."
- "What's a `@Transactional` propagation bug you've actually hit?"

## Security (`docs/security.md`)
- "Walk me through what happens, security-wise, when a user logs in."
- "How do you know the password is never logged anywhere?"
- "What's still missing from this API's security posture, and why?" (Answer honestly from the
  "Known gaps" table - a refresh-token cleanup job and a WebSocket topic-subscription
  authorization gap are both real, acknowledged, unfixed gaps, not hidden ones.)
- "Why does your CSP allow `'unsafe-inline'` for styles but not scripts?"
- "How did you actually verify your security headers work, not just that the config compiles?"

## Testing (`docs/testing.md`)
- "What's your test strategy, and what did you deliberately *not* test?"
- "Why does your test suite fork one JVM per test class instead of sharing one?" (A real
  environment bug found by testing, not a default choice - see `docs/troubleshooting.md`.)
- "Walk me through the weirdest bug you hit while building your own test suite." (Four
  candidates: a Spring Modulith/Boot version mismatch, a Testcontainers Kafka image
  incompatibility, containers dying mid-run on this specific Windows/WSL2 setup, and a
  concurrency test that looked broken but was actually misreading its own HTTP responses.)

## Docker & CI/CD (`docs/deployment.md`)
- "Walk me through your Docker image - why three stages, not one?"
- "What's actually in each layer, and why does that ordering matter?"
- "What did your CI pipeline actually catch, versus just passing on the first try?" (A real
  one: GHCR rejected an uppercase image tag on the very first push - caught by watching the
  run, not assumed to work.)
- "How do you know your published image is actually usable, not just that `docker build`
  succeeded?" (Pulled it with zero authentication from a machine logged out of the registry.)

## Observability (`docs/observability.md`)
- "What would page you at 3am if this were in production, and how would you know?"
- "Why didn't you add distributed tracing?"
- "Walk me through one of your custom metrics and the exact operational question it answers."

## Performance (`docs/performance.md`)
- "What's the most interesting performance bug you found, and how did you find it?" (The N+1
  query on task listings - found by running a real load test and reading the actual SQL log,
  not by guessing.)
- "What does your load test prove, and what does it explicitly not prove?" (Answer honestly:
  correctness and latency under light concurrent load on a laptop - not production capacity,
  not a soak test, not a real dataset size.)

## General / behavioral
- "What was the hardest bug in this whole project, end to end?" (Good answer material: the
  multi-layered Testcontainers/environment investigation in Phase 14 - ruled out sleep, Docker
  Desktop's Resource Saver, and Ryuk before finding the real, and completely different, cause.)
- "What would you do differently if you rebuilt this from scratch?"
- "What's the biggest thing you'd need to change before this could actually go to production?"
  (Real production TLS termination, a frontend that exists, the acknowledged security gaps,
  actual load-tested capacity numbers - not a trick question, an honest gap-listing exercise.)
