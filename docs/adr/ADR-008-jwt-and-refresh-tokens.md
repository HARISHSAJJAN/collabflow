# ADR-008: JWT access tokens + opaque, hashed, rotating refresh tokens

## Status
Accepted

## Context

The API needs stateless-enough authentication (no server-side session store required for
every request, so the backend stays horizontally scalable without sticky sessions), while
still being able to immediately revoke a compromised or logged-out session - something a pure
stateless design can't do.

## Decision

Use **two different token types with two different designs**, not one:

- **Access token**: a signed JWT (HS256), 15-minute default TTL (`collabflow.jwt.access-
  token-ttl-minutes`). Verified by signature on every request (`JwtService`,
  `config.JwtAuthenticationFilter`) - no database lookup needed to authenticate a request.
- **Refresh token**: an opaque random 256-bit value (`RefreshTokenService`), NOT a JWT, sent
  to `POST /api/v1/auth/refresh` to obtain a new access token. Only its SHA-256 hash is stored
  server-side (see the V2 migration's comment); it is looked up by that hash on every use, so
  it's inherently revocable at any time, unlike a self-contained JWT which stays "valid" by
  signature alone until it expires. Default TTL 30 days (`collabflow.jwt.refresh-token-ttl-
  days`).

**Rotation**: every successful refresh revokes the presented token and issues a new one,
chained via `replaced_by_token_id`. If an already-revoked (already-used) refresh token is
presented again, every active session for that user is immediately revoked
(`SessionRevocationService`, see the note below on why that's its own bean) - the working
theory being that a revoked token being presented again means it was copied/stolen and the
thief and the legitimate user are now racing each other, so the safest response is to end
every session and force a fresh login everywhere.

## Why not one token type for everything

**All-JWT (no server-side refresh token)**: would mean either accepting that a compromised or
logged-out token stays technically valid until it expires (unacceptable for "logout" to be a
real feature), or maintaining a server-side denylist of revoked JWTs anyway - at which point
you've built a database-backed revocation system on top of JWTs and gained none of the
"stateless" benefit for the token that actually needs revocation.

**All-opaque (no JWT, even for the access token)**: every single API request would need a
database round-trip just to authenticate, on top of whatever the endpoint itself needs to do.
For a request-heavy API this is a real cost the 15-minute-lived, self-verifying access token
avoids entirely.

**Server-side sessions (a traditional session cookie)**: rejected primarily because of CORS
and multi-client reality (a future mobile client, or the frontend and backend on different
origins) being simpler with a bearer token than with cookies-and-CSRF-tokens, and because a
session store becomes a piece of shared state every backend instance must reach - solvable,
but it's exactly the kind of shared mutable state a stateless-access-token design avoids for
the (much higher volume) per-request path, confining "needs a DB hit" to just the refresh
path, which happens roughly one time per 15 minutes per user instead of every request.

## A bug this design surfaced (and fixed) during Phase 3

While testing rotation reuse-detection, revoking "every active session" on a detected replay
turned out to silently do nothing: the code that performed the revocation and the code that
threw `InvalidRefreshTokenException` to report the failure ran in the *same* `@Transactional`
method, and Spring's default rollback-on-unchecked-exception rolled back the revocation right
along with reporting the error. The fix was routing the revocation through a separate bean
(`SessionRevocationService`) with `@Transactional(propagation = REQUIRES_NEW)`, so it commits
immediately and independently of whatever the caller does next. Full writeup:
`docs/troubleshooting.md`. This is a genuinely easy mistake to make with declarative
transactions and worth being able to explain in an interview: propagation isn't just an
academic setting, it decides whether a security-critical side effect actually survives the
method that triggered it.

## Consequences

**Positive**
- Logout, and detected token theft, both take effect immediately - no "valid until it
  expires" gap for the credential that actually needs one.
- Per-request auth cost is one signature check, not a database round trip.
- "Manage sessions" (list/revoke devices) falls out of the refresh-token table almost for
  free, since it already has to exist for revocation to work at all.

**Negative / trade-offs accepted**
- Two token formats to reason about and explain, instead of one.
- The refresh endpoint is a database write on every use (rotation), which is more expensive
  than validating a JWT - accepted because it happens far less often than access-token
  validation.
- HS256 (symmetric signing) means the same secret both signs and verifies tokens; since this
  is a single-backend-instance modular monolith today, there's no second service that would
  need to verify tokens without also being trusted to issue them, so an asymmetric scheme
  (RS256) would add complexity without a corresponding benefit yet. If a genuinely separate
  service needed to verify (not issue) tokens, that would be the point to revisit this.

## Interviewer questions this ADR should let you answer
- "Why not just use one kind of token everywhere?"
- "How do you actually invalidate a JWT before it expires?"
- "Walk me through what happens if a refresh token is stolen."
- "What's a `@Transactional` propagation bug you've actually hit?"
