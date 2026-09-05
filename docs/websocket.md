# WebSockets

Rationale for using WebSockets/STOMP at all: [ADR-005](adr/ADR-005-websockets.md). This
document is the practical reference: how to connect, what's available, and what was actually
verified versus what's a documented gap.

## Connecting

```
ws://localhost:8080/ws   (wss:// in any deployment behind TLS)
```

STOMP over the raw WebSocket - no SockJS. After the socket opens, send a STOMP `CONNECT`
frame with the access token:

```
CONNECT
accept-version:1.2
Authorization:Bearer <accessToken>

^@
```

(`^@` is the STOMP frame terminator, a NUL byte.) A valid, non-expired access token gets back:

```
CONNECTED
version:1.2
user-name:<your user id>
```

A missing or invalid token gets an `ERROR` frame and the connection is closed (verified with a
hand-rolled STOMP test client during Phase 12 - see ADR-005). See
`websocket.JwtStompAuthInterceptor`'s Javadoc for exactly why authentication happens here, at
the STOMP frame level, and not at the HTTP handshake.

## Subscribing

| Destination | What you get |
|---|---|
| `/topic/projects/{projectId}` | Every task/comment update for that project, for as long as you're subscribed - the brief's own example ("User B viewing the project sees User A's change"). Requires no additional authorization check beyond being authenticated; the payload itself carries only ids, so subscribing to a project you don't have access to leaks IDs at most, not data - a documented, low-severity gap (see "Known gaps" below). |
| `/user/queue/notifications` | Your own newly created notifications, in real time, as a live addition on top of `GET /api/v1/notifications`. Requires no subscribe-time parameter - Spring's user-destination routing delivers only to the session that authenticated as you. |

### `/topic/projects/{projectId}` message shape

```json
{"eventType": "TASK_STATUS_CHANGED", "data": {"taskId": "...", "projectId": "...", "oldStatus": "TODO", "newStatus": "IN_PROGRESS", "changedBy": "..."}}
```

`eventType` is one of `TASK_CREATED`, `TASK_ASSIGNED`, `TASK_STATUS_CHANGED`,
`TASK_PRIORITY_CHANGED`, `COMMENT_ADDED` - the same set of task/comment domain events
published elsewhere (Kafka, audit), reused here rather than inventing a parallel event
vocabulary. `data` is the raw event record, JSON-serialized.

### `/user/queue/notifications` message shape

Identical to a row from `GET /api/v1/notifications` - `{"id", "type", "payload", "read",
"createdAt"}` - so a client can use the same rendering code for a pushed notification and one
loaded over REST.

## Verified (Phase 12)

Not just implemented - checked with a real client speaking the real protocol (`ws` +
hand-written STOMP framing, since curl doesn't speak STOMP): connecting with a valid token,
being rejected with no token, subscribing to a real project's topic, changing that task's
status over the ordinary REST API, and receiving the resulting `MESSAGE` frame on both the
project topic and the personal notification queue - with a correctly populated `createdAt` on
the pushed notification (a class of timing bug this project has hit and fixed three times
before elsewhere; checked for and avoided here proactively).

## Known gaps

- **No per-subscription authorization check on `/topic/projects/{projectId}`**: any
  authenticated user can subscribe to any project's topic id, whether or not they're actually
  a member of that project. The data leaked by doing so is limited to what's already in the
  broadcast payload (ids, status enum values - no titles, descriptions, or comment bodies), so
  the severity is low, but it is a real gap relative to this API's usual "you must have project
  access" rule everywhere else. Closing it would mean validating project membership in a
  `SUBSCRIBE`-frame interceptor, similar to how `JwtStompAuthInterceptor` handles `CONNECT`.
- **In-memory broker, single instance only** - see ADR-005's "negative consequences."
- **No message ordering guarantee across topics** - each topic/queue delivers in the order
  messages were sent to it, but nothing coordinates ordering between, say, a project-topic
  broadcast and a notification-queue push for the same underlying action (they're two separate
  `runAfterCommit`-deferred sends).
