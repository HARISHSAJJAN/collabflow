# ADR-007: REST over HTTP, not GraphQL

## Status
Accepted

## Context

The brief asks for a REST API and lists REST API design explicitly among the concepts this
project exists to demonstrate. That alone would settle it, but it's worth being able to defend
*why* REST is also the right choice on its own merits here, not just the assigned one - the
same reasoning that led every other architectural decision in this project (see ADR-001's
"modular monolith, not microservices" for the same spirit: pick the tool that matches this
project's actual shape, not the one that would look most impressive on a slide).

CollabFlow's domain is a set of well-defined resources with real hierarchical structure - teams
contain projects, projects contain tasks, tasks have comments and labels - and the clients
consuming this API (a browser SPA today, potentially a mobile client later) mostly need
standard CRUD-shaped operations on those resources, plus a few actions that don't fit CRUD
cleanly (`POST /tasks/{id}/labels`, `POST /auth/refresh`). That shape is exactly what REST over
HTTP is built for: resources addressed by URL, HTTP methods as verbs, status codes as the
outcome, a body as the payload.

## Decision

Every endpoint in this project is a REST resource under `/api/v1/...`, documented via
OpenAPI/Swagger (`springdoc-openapi`, see `docs/api.md`), returning JSON, using HTTP status
codes as the primary signal of outcome (`201` for creation, `409` for a version conflict,
`403` vs `404` deliberately chosen per-case for authorization - see `TeamService`'s Javadoc on
not leaking a private team's existence to a non-member). Actions that don't map to a CRUD verb
cleanly are modeled as sub-resource `POST`s (`/tasks/{id}/labels`, `/tasks/{id}/assignee`)
rather than forcing them into `PUT`/`PATCH` on the task itself, keeping each endpoint's
semantics obvious from its URL and method alone.

## Alternatives considered

### GraphQL
GraphQL's real strength is letting a client request exactly the fields it needs across multiple
related resources in one round trip, which matters most when there are many different client
shapes (web, mobile, third-party integrations) each wanting a different slice of the same data,
or when a screen's data requirements are deeply nested and would otherwise cost several
sequential REST calls. Neither pressure exists here yet: this project has one client shape in
mind (the dashboard-style SPA in `frontend/`), and the actual nesting depth a screen needs (a
task board: project → tasks → assignee/labels) is one or two joins, already served by a single
well-designed REST endpoint (`GET /api/v1/tasks?projectId=...`) rather than requiring a
GraphQL-style client-driven query. GraphQL also brings real operational cost this project would
be paying for no benefit yet: a single `/graphql` endpoint means HTTP-level tooling (caching,
rate limiting per-endpoint - see `RateLimiter`'s use on `/auth/login`, `/auth/register`
specifically, not globally) has to be reimplemented at the GraphQL layer instead of coming free
from the transport; resolver-level N+1 queries are a well-known GraphQL footgun (ironically the
exact class of bug found and fixed in Phase 19's performance review - a batched DataLoader
would be needed to avoid it at the framework level, extra machinery this REST version simply
doesn't need); and query complexity/depth limiting becomes its own security concern (an
attacker-crafted deeply-nested query as a denial-of-service vector) that a fixed set of REST
endpoints doesn't have to think about at all.

**What would change this**: a second, meaningfully different client (a mobile app wanting a
much thinner payload than the web dashboard, or a public third-party integration surface) whose
data-shape needs actually diverge from the web client's - that's the concrete signal GraphQL's
trade-off would start paying for itself, not "GraphQL is more modern."

### gRPC
Excellent for internal service-to-service calls in a microservices architecture (strongly-typed
contracts via protobuf, HTTP/2 multiplexing, low serialization overhead) - none of which this
project's own architecture needs, since ADR-001 already chose a modular monolith specifically
to avoid having internal service-to-service calls at all. gRPC is also a poor fit for the
primary client here: browser JavaScript can't speak gRPC directly (it needs gRPC-Web plus a
proxy translating to real gRPC), adding infrastructure for no benefit when plain JSON-over-HTTP
already works natively in every browser with zero extra tooling.

### SOAP
Included for completeness, not because it was seriously considered: XML envelopes, WS-*
extensions, and a heavier client/server contract than this project's needs justify. No modern
greenfield API in this problem space would reach for it.

## Consequences

**Positive**
- Every endpoint is independently cacheable, rate-limitable, and testable via plain HTTP
  tooling (`curl`, `TestRestTemplate` in the Phase 14 suite, Swagger UI for manual exploration)
  with no GraphQL-specific client library or schema-stitching layer required.
- The API's shape mirrors the domain's own resource hierarchy, so `docs/api.md` and the OpenAPI
  spec generated from the controllers stay a genuinely accurate map of "what this system does,"
  not a single opaque `/graphql` endpoint hiding the real operations behind one schema file.

**Negative / trade-offs accepted**
- A frontend screen needing data from several different resources (e.g., a task board showing
  task + assignee name + project name) makes more than one request, or the backend grows a
  purpose-built composite endpoint for that screen - the classic REST "under-fetching" trade-off
  GraphQL exists to solve. Accepted here because today's client shape doesn't yet make this
  costly enough to justify GraphQL's own overhead (see "what would change this" above).
- Versioning is coarse (`/api/v1/`, a new `/api/v2/` if a breaking change is ever needed) rather
  than GraphQL's schema-level deprecation/field-versioning story. Acceptable at this project's
  current single-client-version scale.

## Interviewer questions this ADR should let you answer
- "Why REST and not GraphQL, given GraphQL is what a lot of new projects reach for?"
- "What would actually change your mind and make GraphQL worth it here?"
- "How do you version this API, and what's the trade-off of that approach?"
- "Where does gRPC fit, and why doesn't this project use it?"
