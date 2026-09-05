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

## Known gaps (not yet implemented - tracked, not hidden)

| Gap | Planned phase | Why it's not done yet |
|---|---|---|
| Per-email (not just per-IP) login rate limiting | Not yet planned | A distributed brute force against one account from many IPs would not be caught by the current IP-only limiter. See `AuthController`'s Javadoc. |
| Refresh-token/expired-session cleanup job | Not yet planned | The `ix_refresh_tokens_expires_at` index exists for this; the scheduled job itself isn't written yet - old revoked/expired rows just accumulate. |
| No per-subscription project-membership check on WebSocket `/topic/projects/{id}` | Not yet planned | Any authenticated user can subscribe to any project's topic id; the leaked data is limited to ids and enum values already in the broadcast payload (no titles/descriptions/comment bodies), so severity is low, but it's a real inconsistency with this API's usual "you must have project access" rule. See `docs/websocket.md`. |
| Secure response headers (HSTS, CSP, X-Content-Type-Options, etc.) | Phase 15 | Deferred to the dedicated security-hardening phase. |
| Dependency vulnerability scanning | Phase 15 / 17 (CI) | Not yet wired into the build. |
| CSRF reconsideration if cookie-based auth is ever added | N/A unless the auth transport changes | See "Stateless sessions" above - not a gap under the current design, but the assumption to revisit if that design changes. |

## Interviewer questions this document should let you answer
- "Walk me through what happens, security-wise, when a user logs in."
- "How do you know the password is never logged anywhere?"
- "What's still missing from this API's security posture, and why?"
