# ADR-001: Modular monolith instead of microservices

## Status
Accepted

## Context

CollabFlow needs to support users, teams, projects, tasks, comments, notifications, and an
audit trail, with real-time updates and asynchronous event processing. A team building a
system like this from scratch faces an early, consequential choice: start as a set of
independently deployable microservices, or start as a single deployable unit with strong
internal module boundaries (a "modular monolith")?

Relevant constraints for this project specifically:

- It is built and operated by a single developer, not a team with dedicated platform/SRE
  capacity.
- There is no existing production traffic or team-ownership pressure forcing service
  boundaries today.
- The domains (auth, user, team, project, task, comment, notification, audit) are highly
  interrelated: a task belongs to a project, which belongs to a team, which has members who
  are users. Most "transactions" in the business sense (e.g. "create a task and notify the
  assignee") span more than one of these domains.
- The project must still demonstrate an understanding of distributed-systems concepts
  (event-driven architecture, eventual consistency, idempotency) because those are explicit
  learning goals.

## Decision

Build CollabFlow as a **modular monolith**: one Spring Boot application, one deployment
artifact, one database — but with the codebase organized into explicit modules
(`auth`, `user`, `team`, `project`, `task`, `comment`, `notification`, `audit`, plus the
infrastructure-only modules `kafka`, `websocket`, `cache`, `config`, `common`), each living
in its own top-level Java package under `com.collabflow`.

Module boundaries are not just a naming convention. They are enforced with
[Spring Modulith](https://spring.io/projects/spring-modulith):

- Only classes at the root of a module's package are considered its public API; anything in
  a subpackage (e.g. `com.collabflow.task.internal`) is invisible to other modules at compile
  time in spirit, and is verified with a `ModularityTests` test (see `docs/architecture.md`)
  that fails the build if one module reaches into another module's internals.
- Cross-module communication that represents "this happened, other modules may care" goes
  through Kafka events (in-process today, since there's one JVM, but using the same producer/
  consumer code that would be used across process boundaries) rather than direct method calls
  between unrelated modules. For example, `task` never calls `notification` directly to create
  a notification; it publishes a `TaskAssignedEvent` and `notification` listens for it.
- Direct, synchronous cross-module calls are used only where the caller genuinely needs an
  answer before it can proceed within the same request (e.g. `task` asking `project` whether
  the current user is a member before allowing a task to be created). Those calls go through
  a small `@NamedInterface`-exposed service, not the module's repository or entities.

This gives the codebase the same *seams* a microservices migration would need, without paying
the operational cost of microservices before there is a reason to.

## Alternatives considered

### Microservices from day one
Split `auth`, `task`, `notification`, etc. into separate deployable services communicating
over the network from the start.

Rejected for this project because:
- It requires solving distributed transactions, service discovery, inter-service auth, and
  multiple CI/CD pipelines before a single feature works end-to-end — cost paid up front for
  a benefit (independent scaling/deployment of each domain) that doesn't yet exist for a
  single-developer, non-production system.
- It would make the *interesting* engineering (concurrency, caching, eventing, schema design)
  harder to see clearly, buried under infrastructure plumbing.
- A rushed microservices split, done for resume-keyword reasons rather than a real scaling
  need, is a well-known anti-pattern and is explicitly what this project's brief warns
  against.

### Unstructured monolith (no enforced module boundaries)
A single Spring Boot app with packages organized by technical layer (`controller/`,
`service/`, `repository/`) instead of by domain, and no automated boundary enforcement.

Rejected because it tends to degrade into a "big ball of mud" where every service can call
every repository directly, and by the time you'd want to extract a service, nobody can say
with confidence what its actual dependencies are. The whole point of choosing "monolith now,
maybe microservices later" as a strategy is to keep the *option* to extract services open;
that option only stays open if boundaries are real.

### Modulith with events only in-process (no Kafka)
Use Spring's in-process `ApplicationEventPublisher` for cross-module events instead of Kafka,
and add Kafka only if/when a real second consumer needs to exist out-of-process.

This was seriously considered, since it's arguably more honest for a single-JVM app.
Kafka was still chosen deliberately (see ADR-004) as an explicit learning goal of this project
is to demonstrate produce/consume, partitioning, consumer groups, idempotent processing, and
failure handling — concepts that don't exist with a plain in-process event bus. The trade-off
is added operational complexity (a Kafka broker must be running) in exchange for a more
realistic distributed-systems story that can be defended in an interview. This is called out
explicitly rather than hidden: Kafka is not *required* by CollabFlow's current scale, it is
used because the project's purpose is partly educational.

## When microservices would become justified

Extracting a module into its own service becomes worth the operational cost when at least one
of these becomes true for that specific module:

1. **Independent scaling need**: one module's load profile diverges sharply from the rest
   (e.g. `notification` fan-out at 100k+ users needs 20 instances while `auth` needs 2).
2. **Independent deployment cadence**: a team owns that module and needs to ship without
   coordinating releases with every other team.
3. **Technology mismatch**: a module would clearly benefit from a different runtime (e.g. a
   search/indexing service in a language with better text-processing libraries).
4. **Blast radius**: a module's failure or resource exhaustion (e.g. a runaway report job)
   should not be able to take down unrelated request paths in the same JVM.

`notification` is the most likely first extraction candidate in this codebase: it is already
event-driven (consumes Kafka events, no other module calls into it synchronously), owns its
own table, and has a fan-out profile that scales differently from the rest of the system as
user count grows. See `docs/architecture.md` for what that extraction would concretely involve
using this module's existing boundary.

## Consequences

**Positive**
- One codebase, one build, one deployment — fast local iteration, no distributed-systems
  debugging tax during feature development.
- One database means straightforward ACID transactions across, e.g., "create project + add
  creator as OWNER member" — no saga/2PC needed for the common case.
- Module boundaries are enforced by an automated test (`ModularityTests`), not just
  discipline, so they don't silently rot.
- The architecture can be *discussed* as a deliberate, revisitable choice in an interview,
  rather than presented as the only way the system could be built.

**Negative / trade-offs accepted**
- All modules currently share one connection pool, one JVM, one deployment unit: a memory
  leak or a slow query in one module can degrade the whole application. This is an accepted
  risk at current scale (see `docs/system-design` sections in `docs/architecture.md` for how
  this changes at 100k+ users).
- Because Kafka runs in-process today, "the network" that Kafka would abstract over is not
  actually being exercised the same way it would be across real services — event ordering and
  latency characteristics in this local setup are more favorable than a genuinely distributed
  deployment would see. This is documented, not hidden, in `docs/architecture.md`'s failure
  scenarios section.
- Spring Modulith's compile-time-ish enforcement is a discipline aid, not a hard guarantee the
  way a network boundary is — a determined change could still reach across a module boundary
  if a reviewer isn't paying attention. The `ModularityTests` build-breaking test is what makes
  this catchable rather than purely aspirational.

## Interviewer questions this ADR should let you answer

- "Why didn't you just build microservices, isn't that more impressive?"
- "How do you know your modules are actually decoupled and not just in different folders?"
- "If notification traffic grew 100x, what would you actually do, concretely?"
- "What's the downside of putting everything in one JVM?"
