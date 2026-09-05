# Security Review

Living document, updated as each phase lands. Every claim here says which phase implemented
it - nothing is described as done before it actually is.

## Implemented

### Password storage (Phase 3)
BCrypt (`BCryptPasswordEncoder`, cost factor 12) via `config.SecurityConfig#passwordEncoder`.
Plaintext passwords are never persisted, logged, or included in any response - `User.
passwordHash` never leaves the `user` module except as `UserCredentials.passwordHash`,
consumed only by `AuthService.login`'s `passwordEncoder.matches(...)` call, and is never
returned in any DTO exposed to a controller.

### JWT access tokens + opaque refresh tokens (Phase 3)
See [ADR-008](adr/ADR-008-jwt-and-refresh-tokens.md) for the design and a real transaction-
propagation bug it surfaced and fixed. Signing key (`collabflow.jwt.secret`) has no default in
`application.yml` - the app refuses to start without one, so it can't silently run with a
guessable key. Access tokens are short-lived (15 min default); refresh tokens are opaque,
stored only as a SHA-256 hash, and rotate on every use.

### Account enumeration resistance (Phase 3)
Login returns the identical `401` message ("Invalid email or password") whether the email
doesn't exist, the account is deactivated, or the password is wrong.

### Stateless sessions, CSRF disabled deliberately (Phase 3)
See the Javadoc on `config.SecurityConfig` for the reasoning: CSRF protection defends
cookie-based auth, and this API never authenticates via cookies, so the specific attack CSRF
protection exists for doesn't apply here. This would need to be revisited if authentication
ever moved to an httpOnly cookie.

### CORS (Phase 3)
Explicit allow-list (`collabflow.cors.allowed-origins`), not a wildcard. Configured, not
hand-waved: see `SecurityConfig#corsConfigurationSource`.

### Centralized error handling, no stack traces to clients (Phase 3)
`common.web.GlobalExceptionHandler` - every error response is the same `ApiError` JSON shape;
unexpected exceptions are logged with full detail server-side (`log.error(..., ex)`) and
returned to the client as a generic `500 INTERNAL_ERROR` with no exception detail.

### Input validation (Phase 3, ongoing)
Bean Validation (`jakarta.validation`) on every request DTO - email format, password length
bounds (8-128 chars), required fields. Enforced by `@Valid` in controllers;
`MethodArgumentNotValidException` is mapped to a `400` with per-field messages.

### Password change revokes every session (Phase 4)
`POST /api/v1/users/me/password` requires the current password, and on success publishes an
in-process event that revokes every refresh token for the account (see
`AuthService#onPasswordChanged`) - a stale or stolen session cannot outlive a password change.
Surfaced a real `@TransactionalEventListener` propagation bug while testing this (see
`docs/troubleshooting.md`): the first implementation silently failed to revoke anything.

### Rate limiting on login and registration (Phase 9)
A Redis-backed fixed-window counter (`RateLimiter`), keyed by client IP, rejects excess
`POST /api/v1/auth/login` and `/register` calls with `429 RATE_LIMIT_EXCEEDED`. Fails open on
a Redis outage (allows requests through) - a deliberate, debatable trade-off documented on
`RateLimiter`'s Javadoc and in `docs/redis.md`. Does not yet limit by submitted email/identity
(only by IP) - see the gaps table below.

### Team/project role-based authorization (Phases 5-7)
OWNER/ADMIN/MEMBER enforced in `TeamService`/`ProjectService`/`TaskService` - see those
classes' Javadoc and `docs/decisions.md` for the exact policy per action. Never enforced only
on the frontend; every check happens server-side before the corresponding write.

### Secure-by-default authorization (Phase 3)
`SecurityConfig`'s endpoint rules are an explicit allow-list for *public* endpoints;
`.anyRequest().authenticated()` is the fallback. A new controller added later requires
authentication unless someone deliberately adds it to the public list - it cannot become
accidentally public by omission.

### Per-email login rate limiting, alongside the existing per-IP limiter (Phase 15)
`AuthController`'s `/login` now checks two independent `RateLimiter` keys: the original tight,
short-window one keyed by client IP, and a new wider, longer-window one keyed by the submitted
email (normalized the same way `UserAccountService` normalizes it - trimmed, lower-cased - so
case variation can't be used to dodge it). This closes the gap the previous version of this
document tracked below: a distributed brute force against one specific account, spread across
many different source IPs, now trips the email-keyed limiter even though no single IP ever
gets close to its own limit. Registration is deliberately **not** given a matching per-email
limiter - repeatedly registering the same email is already rejected by the uniqueness
constraint, so a second limiter there would add no protection the database doesn't already
provide. Proven directly against real Redis in `RateLimiterIntegrationTest` (capacity
enforcement, and that independent keys don't share state - the property the two-key design
above actually depends on).

### Secure response headers (Phase 15)
`SecurityConfig`'s `headers(...)` block, on top of Spring Security's own defaults
(`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, cache-control on sensitive
responses):
- **Content-Security-Policy**: `default-src 'self'` with same-origin script/style/image/connect
  sources, `frame-ancestors 'none'` (this API is never meant to be framed), `base-uri 'none'`,
  `form-action 'self'`. `style-src` allows `'unsafe-inline'` only because Swagger UI's bundled
  CSS needs it; verified by hand that Swagger UI's own JS is entirely same-origin `<script src>`
  tags with no inline scripts, so `script-src 'self'` (no `'unsafe-inline'`) doesn't break it.
- **Referrer-Policy**: `strict-origin-when-cross-origin` - never leaks the full request path to
  a third-party origin, still allows same-origin referrer information.
- **Permissions-Policy**: locks down browser features this API's responses never need
  (`geolocation=()`, `camera=()`, `microphone=()`, `payment=()`, `usb=()`).
- **Strict-Transport-Security**: `max-age=31536000; includeSubDomains`. Only takes effect over
  an actual HTTPS connection (confirmed by curling the running app over plain HTTP locally and
  observing the header is correctly absent) - TLS termination is a deployment-time concern (a
  reverse proxy/load balancer in front of this app), not something the app does itself, so this
  header is inert until that's in place.

All of the above verified by curling a real running instance and inspecting the raw response
headers, not just reading the configuration and assuming it's wired correctly.

### Dependency vulnerability review (Phase 15)
Chose GitHub Dependabot (`.github/dependabot.yml`, weekly, Maven + GitHub Actions ecosystems)
over running OWASP `dependency-check` locally: `dependency-check` needs its own NVD data feed
synced before every scan (slow, and rate-limited without an API key), where Dependabot runs
continuously on GitHub's own infrastructure and opens a PR the moment a tracked dependency has
a known advisory - a better fit for a project without its own scanning infrastructure to
maintain. Alongside adding it, manually reviewed this project's most security-relevant direct
and transitive dependencies against current advisories as a one-time baseline:
- **Spring Boot 3.5.16 / Spring Security 6.5.11**: checked against three 2026 Spring CVEs
  found during this review (actuator health-group auth bypass CVE-2026-22731, fixed in 3.5.12;
  a Spring Security header-omission bug CVE-2026-22732, fixed in 6.5.9; and a Mail
  auto-configuration TLS issue CVE-2026-40992, fixed in 3.5.15, which doesn't apply here anyway
  since this project has no `spring-boot-starter-mail` dependency) - already patched by the
  versions resolved.
- **PostgreSQL JDBC driver**: Spring Boot 3.5.16 manages `42.7.11`, which carries two real 2026
  CVEs (CVE-2026-42198, unbounded PBKDF2 iteration count from the server during SCRAM auth can
  exhaust client CPU; CVE-2026-54291, `channelBinding=require` not enforced), fixed in
  `42.7.12`. **Fixed**: pinned `postgresql.version` to `42.7.12` in `backend/pom.xml`,
  overriding Spring Boot's managed version - the only dependency version actually changed by
  this review.
- **`io.jsonwebtoken:jjwt` 0.12.6**: no known CVE found for this library at this version as of
  this review.

## Known gaps (not yet implemented - tracked, not hidden)

| Gap | Planned phase | Why it's not done yet |
|---|---|---|
| Refresh-token/expired-session cleanup job | Not yet planned | The `ix_refresh_tokens_expires_at` index exists for this; the scheduled job itself isn't written yet - old revoked/expired rows just accumulate. |
| No per-subscription project-membership check on WebSocket `/topic/projects/{id}` | Not yet planned | Any authenticated user can subscribe to any project's topic id; the leaked data is limited to ids and enum values already in the broadcast payload (no titles/descriptions/comment bodies), so severity is low, but it's a real inconsistency with this API's usual "you must have project access" rule. See `docs/websocket.md`. |
| CSRF reconsideration if cookie-based auth is ever added | N/A unless the auth transport changes | See "Stateless sessions" above - not a gap under the current design, but the assumption to revisit if that design changes. |

## Interviewer questions this document should let you answer
- "Walk me through what happens, security-wise, when a user logs in."
- "How do you know the password is never logged anywhere?"
- "What's still missing from this API's security posture, and why?"
