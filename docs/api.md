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

## Teams (`/api/v1/teams`) — Phase 5

All endpoints require authentication. Authorization policy (full reasoning in `TeamService`'s
Javadoc): viewing a team or its roster requires being a member (any role) - a non-member gets
`404 RESOURCE_NOT_FOUND`, identical to a nonexistent team id, so team existence is never
revealed to non-members. Editing the team's name/description requires OWNER or ADMIN.
Everything membership-related - inviting, removing, changing roles, deleting the team - is
OWNER-only. **A team must always have at least one OWNER**: removing or demoting the last
OWNER returns `409 CONFLICT`.

### `POST /` — create a team
```json
// request
{"name": "Platform Team", "description": "Core platform"}
// 201 response - the creator becomes OWNER automatically
{"id": "...", "name": "Platform Team", "description": "Core platform", "createdBy": "...", "myRole": "OWNER", "createdAt": "...", "updatedAt": "..."}
```

### `GET /` — list my teams (paginated)
Standard Spring Data page params: `?page=0&size=20`.
```json
{"content": [ {"...team..."} ], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1, "last": true}
```

### `GET /{teamId}` — team details
`404` if the team doesn't exist or you're not a member.

### `PATCH /{teamId}` — update name/description (OWNER or ADMIN)

### `DELETE /{teamId}` — delete the team (OWNER only)

### `GET /{teamId}/members` — list the roster (any member)
```json
[{"userId": "...", "email": "...", "fullName": "...", "avatarUrl": null, "role": "MEMBER", "joinedAt": "..."}]
```

### `POST /{teamId}/members` — add a member by email (OWNER only)
```json
{"email": "frank@example.com"}
```
`404` if no account exists with that email; `409` if they're already a member. New members
always start as `MEMBER` - promote separately via the role endpoint. **Note on scope**: this
adds an *existing* registered user immediately; it is not a pending invitation the invitee
must accept, and it cannot invite an email with no account yet. A full invitation-with-
acceptance flow (including inviting not-yet-registered emails) is a deliberately deferred
future improvement, not a hidden gap - see `docs/decisions.md`.

### `PATCH /{teamId}/members/{userId}` — change a member's role (OWNER only)
```json
{"role": "ADMIN"}
```

### `DELETE /{teamId}/members/{userId}` — remove a member (OWNER only)

## Projects (`/api/v1/projects`) — Phase 6

All endpoints require authentication. Authorization (full reasoning in `ProjectService`'s
Javadoc): creating a project, editing it, archiving/unarchiving, and managing membership all
require at least ADMIN on the project's **team**. Visibility is asymmetric on purpose: a team
OWNER/ADMIN sees every project belonging to their team; a plain team MEMBER only sees projects
they've been explicitly added to as a project member. A project you can't see returns `404`,
identical to a nonexistent id.

### `POST /` — create a project
```json
// request
{"teamId": "...", "name": "Website Redesign", "description": "Q3 relaunch"}
// 201 response - creator is automatically added as a project member
{"id": "...", "teamId": "...", "name": "Website Redesign", "description": "Q3 relaunch", "status": "ACTIVE", "createdBy": "...", "createdAt": "...", "updatedAt": "..."}
```

### `GET /?teamId={teamId}&status={ACTIVE|ARCHIVED}` — list projects (paginated)
`status` is optional (omit for both). See the visibility note above - the same call returns
different results depending on the caller's team role.

### `GET /{projectId}` — project details

### `PATCH /{projectId}` — update name/description (ADMIN+)
`409 CONFLICT` if the project is archived - unarchive it first.

### `POST /{projectId}/archive` / `POST /{projectId}/unarchive` (ADMIN+)

### `GET /{projectId}/members` — list project members (anyone with project access)
```json
[{"userId": "...", "email": "...", "fullName": "...", "avatarUrl": null, "teamRole": "MEMBER", "addedAt": "..."}]
```

### `POST /{projectId}/members` — add a project member (ADMIN+)
```json
{"userId": "..."}
```
The target user must already be a member of the project's **team** - project membership is
always a subset of team membership, never an independent pool of users. `404` if they aren't
a team member; `409` if they're already a project member.

### `DELETE /{projectId}/members/{userId}` — remove a project member (ADMIN+)

## Tasks (`/api/v1/tasks`) — Phase 7

All endpoints require authentication and project access (full reasoning in `TaskService`'s
Javadoc). Editing a task (title/description/priority/due date/status/labels) requires either
team role ADMIN+ (any task in the project) or being the task's current assignee (only that
task). **Assigning/unassigning is ADMIN+ only** - a MEMBER may self-assign only at creation
time, never reassign afterward. Deleting is ADMIN+ only.

### `POST /` — create a task
```json
{"projectId": "...", "title": "Fix login bug", "priority": "HIGH", "dueDate": "2026-09-20", "assigneeId": "..."}
```
`assigneeId` is optional; if set to anyone other than yourself, requires ADMIN+. `403` if a
MEMBER tries to assign someone else at creation.

### `GET /?projectId={id}&status=&priority=&assigneeId=&page=&size=` — list/filter tasks (paginated)
Full keyword search and date-range filtering land in Phase 13; this phase covers exact-match
filtering by status/priority/assignee.

### `GET /me` — my assigned tasks across every project (paginated)
The dashboard's central query.

### `GET /{taskId}` — task details
Response includes `version` - see the concurrency note below.

### `PATCH /{taskId}` — update title/description/priority/due date
`409 CONCURRENT_MODIFICATION` if the task was modified by someone else since your last read -
re-fetch and retry, don't just resubmit blindly. See [ADR-006](adr/ADR-006-optimistic-locking.md).

### `PATCH /{taskId}/status`
```json
{"status": "IN_PROGRESS"}
```

### `PATCH /{taskId}/assignee` (ADMIN+)
```json
{"assigneeId": "..."}
```
`assigneeId` may be `null` to unassign. `409 CONFLICT` if the target isn't a project member.

### `DELETE /{taskId}` (ADMIN+)

### `POST /{taskId}/labels` / `DELETE /{taskId}/labels/{label}`
```json
{"label": "backend"}
```

## Coming in later phases
- `/api/v1/comments` (Phase 8)
- `/api/v1/notifications` (Phase 11)
