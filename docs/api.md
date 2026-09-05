# API Reference

Base path: `/api/v1`. Interactive docs (Swagger UI) at `/swagger-ui/index.html` once the app
is running; machine-readable spec at `/v3/api-docs`. This file documents intent and gives
copy-pasteable examples; the OpenAPI spec is the source of truth for exact request/response
schemas.

Every error response uses the same shape, documented once here rather than repeated per
endpoint:

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "status": 409,
  "error": "CONFLICT",
  "message": "An account with this email already exists",
  "path": "/api/v1/auth/register",
  "fieldErrors": null
}
```

`fieldErrors` is populated only for `400 VALIDATION_ERROR` responses, as a list of
`{"field": "...", "message": "..."}`.

## Auth (`/api/v1/auth`) — Phase 3

All auth endpoints are public (no `Authorization` header needed) **except** `/sessions` and
`/logout-all`, which require a valid access token.

### `POST /register`
```json
// request
{"email": "alice@example.com", "password": "SuperSecret123", "fullName": "Alice Anderson"}
// 201 response
{"userId": "b5c188cd-75c2-4aad-bc59-7af09672fe31"}
```
`409 CONFLICT` if the email is already registered. `400 VALIDATION_ERROR` for a malformed
email, a password under 8 characters, or a blank name.

### `POST /login`
```json
// request
{"email": "alice@example.com", "password": "SuperSecret123"}
// 200 response
{
  "userId": "b5c188cd-75c2-4aad-bc59-7af09672fe31",
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "LZ9CLULkQpd5Dx0FLQ1U1vHN8nPyEgwbYsOHXmQDh6U",
  "expiresInSeconds": 900,
  "tokenType": "Bearer"
}
```
`401 AUTHENTICATION_FAILED` for a wrong password, an unknown email, or a deactivated account -
deliberately the same message for all three (see ADR-008 / `AuthService.login`'s Javadoc) so
the response doesn't tell an attacker which emails are registered.

Use the access token on subsequent requests: `Authorization: Bearer <accessToken>`.

### `POST /refresh`
```json
// request
{"refreshToken": "LZ9CLULkQpd5Dx0FLQ1U1vHN8nPyEgwbYsOHXmQDh6U"}
// 200 response: same shape as /login, with a NEW access token AND a new refresh token
```
The presented refresh token is revoked as part of this call (rotation) - the response's
`refreshToken` is the one to use next time, not the one you sent. `401 AUTHENTICATION_FAILED`
if the token is unknown, expired, or has already been used once before (see ADR-008's
"rotation reuse detection").

### `POST /logout`
```json
// request
{"refreshToken": "..."}
// 204 No Content
```
Revokes just this one session. Idempotent - calling it again (or with an already-revoked
token) still returns 204.

### `POST /logout-all` (requires auth)
Revokes every active session for the authenticated user. `204 No Content`.

### `GET /sessions` (requires auth)
```json
[
  {
    "id": "c9fc76ce-120c-4cc5-a2b3-1f2296ebb32c",
    "userAgent": "Mozilla/5.0 ...",
    "ipAddress": "203.0.113.7",
    "issuedAt": "2026-09-05T13:20:42Z",
    "expiresAt": "2026-10-05T13:20:42Z"
  }
]
```
Lists this user's currently-active (not revoked, not expired) sessions - the "manage
sessions" feature.

### `DELETE /sessions` (requires auth)
Equivalent to `/logout-all`; provided as the more RESTful spelling of the same action.

## Users (`/api/v1/users`) — Phase 4

All endpoints require authentication and act on the caller's own account only - there is
deliberately no way to view or edit another user's profile through this API.

### `GET /me`
```json
{
  "id": "f66be7a5-ebd7-4cd9-a52c-e3dd4a87f208",
  "email": "carol@example.com",
  "fullName": "Carol Danvers",
  "avatarUrl": null,
  "lastLoginAt": "2026-09-05T13:31:42Z",
  "createdAt": "2026-09-05T13:31:41Z"
}
```

### `PATCH /me`
```json
// request
{"fullName": "Carol D.", "avatarUrl": "https://example.com/carol.png"}
// 200 response: same shape as GET /me, updated
```

### `POST /me/password`
```json
// request
{"currentPassword": "SuperSecret123", "newPassword": "NewSecret456"}
// 204 No Content
```
`401 AUTHENTICATION_FAILED` if `currentPassword` doesn't match. On success, **every active
session for this account is revoked** (see `AuthService.onPasswordChanged` and ADR-008) - the
access/refresh tokens used to make this very call keep working only until the access token's
normal 15-minute expiry, but the refresh token (and every other session's) is immediately
revoked, so the user (or an attacker who had a stolen session) must log in again everywhere.

## Coming in later phases

- `/api/v1/teams` (Phase 5)
- `/api/v1/projects` (Phase 6)
- `/api/v1/tasks` (Phase 7)
- `/api/v1/comments` (Phase 8)
- `/api/v1/notifications` (Phase 11)
