# Observability (Phase 18)

## Health and readiness

Spring Boot Actuator, wired since Phase 1: `GET /actuator/health` (public), backed by the
default JVM/disk indicators plus Redis (`management.health.redis.enabled`) and Kubernetes-style
liveness/readiness probe groups (`management.health.livenessstate` /
`.readinessstate`, `management.endpoint.health.probes.enabled: true`) - so this app is already
correctly wired for a Kubernetes deployment's `livenessProbe`/`readinessProbe` even though it
doesn't run on Kubernetes today. `show-details: when-authorized` means the public health check
only ever reveals `UP`/`DOWN`, never per-component detail, to an unauthenticated caller.

## Metrics

`GET /actuator/prometheus` (behind authentication - see `SecurityConfig`) exposes everything
Micrometer's Spring Boot auto-configuration provides for free - JVM memory/GC, HTTP request
latency histograms per endpoint (`http.server.requests`), HikariCP pool utilization, Kafka
consumer lag, Tomcat thread pool usage - plus a small set of **business metrics** added this
phase, each chosen because it answers a real operational question a purely technical metric
can't:

| Metric | Tags | Answers |
|---|---|---|
| `collabflow.auth.login` | `result=success\|failure` | Is login failure rate spiking (credential stuffing, or a client-side bug)? |
| `collabflow.auth.register` | — | Signup volume over time. |
| `collabflow.ratelimit.exceeded` | `action=login-ip\|login-email\|register` | Which rate limiter is actually being hit, and how often - was Phase 15's per-email limiter worth adding? |
| `collabflow.tasks.created` | `priority` | Task creation volume, by priority - is a project drowning in `URGENT` work? |
| `collabflow.tasks.status_changed` | `to` | Throughput: how many tasks reach `DONE` per day, not just how many exist. |
| `collabflow.notifications.sent` | `type` | Notification volume by type - confirms the Kafka consumer pipeline is actually delivering, not just that messages were published. |

These are plain `MeterRegistry.counter(name, tag...).increment()` calls at the point each event
is known to have genuinely happened (after the database write commits, not before) - not
`@Counted`/`@Timed` annotations. Same reasoning as `RedisCacheService`'s explicit cache-aside
methods over `@Cacheable`: the metric name, its tags, and exactly when it fires are visible in
the method itself, not derived from where an annotation happens to sit.

**What a real deployment would add on top of this** (not built here - no Prometheus/Grafana
server is actually running for this project, so there's nothing to screenshot honestly): a
Prometheus server scraping `/actuator/prometheus` on an interval, and Grafana dashboards/alerts
over these exact metric names - e.g. `rate(collabflow_auth_login_total{result="failure"}[5m])`
alerting if it exceeds some multiple of the `result="success"` rate.

## Request correlation

`config.CorrelationIdFilter` assigns every request an id - reusing an incoming `X-Request-Id`
header if a caller or upstream reverse proxy already set one, otherwise generating a fresh one
- puts it in SLF4J's MDC for the request's duration, and echoes it back as a response header.
`logging.pattern.console` (`application.yml`) includes `%X{requestId}`, so every log line
written while handling a request - across every module that logs during it - can be grep'd
together by that one id. Registered at `Ordered.HIGHEST_PRECEDENCE` as a plain servlet filter,
outside Spring Security's own filter chain, specifically so a request Spring Security itself
rejects (a 401 on a missing/invalid token) still gets an id and is still traceable in the logs -
support diagnosis ("what did the server see for request X") shouldn't depend on the request
having made it past authentication first.

## Distributed tracing - deliberately not implemented

Full distributed tracing (Micrometer Tracing + an exporter like Zipkin or an OTel collector)
is a real gap, left out on purpose rather than by oversight: it needs its own piece of running
infrastructure (a trace collector/UI) that this project doesn't otherwise have a use for, and
with a single Spring Boot process as the only thing handling a request end-to-end (no
downstream microservices to hop between), a trace would mostly reconstruct what the correlation
id above already gives for free - one id, present in every log line for that request. Tracing
earns its operational cost specifically when a request crosses multiple independently-deployed
services and you need to see the whole hop-by-hop timeline in one place; that's a real
justification for microservices-style tracing infrastructure, and also a good example of why
this project chose a modular monolith (see ADR-001) - one of the costs microservices pay that a
monolith simply doesn't have to.
