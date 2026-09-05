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
`/logout-all`, which require a valid access token. `/login` and `/register` are rate-limited
per client IP (Phase 9) - `429 RATE_LIMIT_EXCEEDED` past the configured capacity (default 5
logins/minute, 3 registrations/hour; see `.env.example`). See `docs/redis.md`.

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

## Comments (`/api/v1/comments`) — Phase 8

All endpoints require project access (same rule as viewing the task the comment is on).

### `POST /` — add a comment
```json
{"taskId": "...", "body": "Looking into this now"}
```

### `GET /?taskId={id}&page=&size=` — list a task's comments, oldest first (paginated)

### `PATCH /{commentId}` — edit a comment (author only)
```json
{"body": "..."}
```
`403` if you're not the author - no exceptions, including for ADMIN/OWNER.

### `DELETE /{commentId}` — delete a comment (author, **or** a team ADMIN/OWNER)
A narrow moderation allowance not explicitly required by the brief but judged reasonable -
see `CommentService`'s Javadoc for why deletion has this exception and editing never does.

## Audit / activity (`/api/v1/audit-logs`) — Phase 8

### `GET /?projectId={id}&page=&size=` — project activity (requires project access)
### `GET /?teamId={id}&page=&size=` — team activity, e.g. membership changes (requires team membership)

Exactly one of `projectId`/`teamId` is required; `400 BAD_REQUEST` if neither is given.
```json
{
  "content": [
    {"id": "...", "actorId": "...", "action": "TASK_STATUS_CHANGED", "resourceType": "TASK", "resourceId": "...",
     "oldValue": "{\"status\":\"TODO\"}", "newValue": "{\"status\":\"IN_PROGRESS\"}", "createdAt": "..."}
  ],
  "page": 0, "size": 30, "totalElements": 1, "totalPages": 1, "last": true
}
```
Populated automatically from domain events - see `AuditService`'s Javadoc. Currently recorded
actions: `PROJECT_CREATED`, `TASK_CREATED`, `TASK_ASSIGNED`, `TASK_STATUS_CHANGED`,
`TASK_PRIORITY_CHANGED`, `COMMENT_ADDED`, `MEMBER_ADDED`, `MEMBER_REMOVED`.

## Notifications (`/api/v1/notifications`) — Phase 10

Built alongside Kafka rather than waiting for Phase 11 - see docs/decisions.md. All endpoints
require authentication and act only on the caller's own notifications.

### `GET /?page=&size=` — my notifications, newest first (paginated)
```json
{"content": [{"id": "...", "type": "TASK_ASSIGNED", "payload": "{\"taskId\":\"...\",...}", "read": false, "createdAt": "..."}], "page": 0, "size": 20, ...}
```
`type` is one of `TASK_ASSIGNED`, `TASK_STATUS_CHANGED`, `COMMENT_ADDED`, `TEAM_MEMBER_ADDED`,
`TASK_MENTION` (Phase 11 - see below), or `DUE_DATE_APPROACHING` (Phase 11 - a daily scheduled
sweep, not an event; see `docs/kafka.md`). `payload` is a JSON string whose shape depends on
`type`.

`TASK_MENTION` (Phase 11) is produced by writing `@someone@example.com` (an exact email
address, not a username/handle - this project has no such system) in a comment body. Only
notifies if that email belongs to an actual project member; a typo'd or outsider email
silently notifies no one, rather than leaking whether that address has an account at all.

### `GET /unread-count`
```json
{"unreadCount": 3}
```

### `POST /{notificationId}/read` — mark one notification read
### `POST /read-all` — mark every notification read

## WebSocket (`/ws`) — Phase 12

STOMP over native WebSocket, authenticated at the STOMP `CONNECT` frame (not the HTTP
handshake). Full protocol reference, message shapes, and verification notes:
[`docs/websocket.md`](websocket.md) and [ADR-005](adr/ADR-005-websockets.md).

- Subscribe to `/topic/projects/{projectId}` for live task/comment updates.
- Subscribe to `/user/queue/notifications` for real-time notification delivery, on top of
  (not instead of) `GET /api/v1/notifications`.
