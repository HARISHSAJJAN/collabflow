# ADR-005: WebSockets (STOMP) for real-time updates

## Status
Accepted

## Context

The brief's own example is the concrete requirement: when User A changes a task, User B -
already viewing that project - should see the update without refreshing. Polling (the client
re-fetching on a timer) could technically satisfy this, but at the cost of either noticeable
latency (a long poll interval) or wasted load (a short one, mostly returning "nothing
changed"). A push-based channel is the right tool once "update the client the moment something
happens" is the actual requirement.

## Decision

Use **WebSockets with the STOMP subprotocol** (Spring's `spring-boot-starter-websocket`,
native, no SockJS fallback - see `WebSocketConfig`'s Javadoc), authenticated at the STOMP
`CONNECT` frame rather than the HTTP handshake (see `JwtStompAuthInterceptor`'s Javadoc for
why - a browser's native WebSocket client cannot set an `Authorization` header on the upgrade
request).

**Two kinds of destinations**, both server-push only (this app has no client-to-server STOMP
messages):
- `/topic/projects/{projectId}` - broadcast. Everyone subscribed (anyone currently viewing
  that project) gets the same message. Backs the brief's own example.
- `/user/queue/notifications` - per-user, via Spring's user-destination routing keyed by the
  principal `JwtStompAuthInterceptor` set at `CONNECT`. Backs real-time notification delivery
  on top of the existing REST polling path (Phase 10/11) - purely additive, not a replacement.

Both reuse the **same in-process Spring events** `audit` already consumes
(`TaskCreatedEvent`, `TaskStatusChangedEvent`, etc. - see docs/architecture.md's "Two kinds of
cross-module events"). `websocket.internal.TaskUpdateBroadcastListener` is a *third*
independent reaction to the same publish call, added without touching task/comment/project/
team services at all - exactly the extensibility ADR-001 predicted for this event-driven
design.

Broadcasts and pushes are deferred until the originating transaction commits
(`common.TransactionUtils.runAfterCommit` - the same shared utility now backing Redis cache
eviction, Kafka publishing, and this), for the same reason as both of those: telling a
connected client about a change before it's durably committed risks telling them about
something that then rolls back.

## Connection lifecycle, security, and failure handling (as implemented, verified with a real test client)

1. **Handshake**: client opens a WebSocket to `/ws` (public in `SecurityConfig` - see its
   comment for why this is not actually open access).
2. **Authentication**: client sends a STOMP `CONNECT` frame with `Authorization: Bearer <jwt>`.
   `JwtStompAuthInterceptor` validates it via the same `JwtService` the HTTP filter chain uses.
   **Verified with a hand-rolled STOMP test client** (`ws` + raw STOMP framing, not a browser):
   a valid token gets a `CONNECTED` frame back with `user-name` set to the correct user id; an
   absent `Authorization` header gets an `ERROR` frame and the connection closes immediately
   (WebSocket close code 1002) - a STOMP session can exist without ever becoming an
   authenticated one.
3. **Subscribe**: an authenticated client subscribes to the topics/queues it cares about.
   **Verified**: subscribing to a real project's topic and then changing that task's status
   over the ordinary REST API delivered a `MESSAGE` frame with the expected
   `TASK_STATUS_CHANGED` payload; the same action simultaneously delivered a `MESSAGE` on
   `/user/queue/notifications` with the newly created notification (correct, non-null
   `createdAt` - the same `saveAndFlush` timing lesson from earlier phases was checked for and
   avoided here proactively, not found by testing this time).
4. **Disconnect**: no special handling needed - Spring's STOMP session and the broker's
   subscription registry are cleaned up automatically when the WebSocket closes.
5. **Reconnection**: a reconnecting client is a brand-new STOMP session as far as this app is
   concerned - it must send a fresh `CONNECT` with a still-valid access token. An expired
   access token fails reconnection exactly like it fails any HTTP request; the frontend's
   existing refresh-token flow (Phase 3/4) is what a real client uses before reconnecting, not
   something this WebSocket layer handles itself.

## Alternatives considered

### Polling
Rejected for the reasons in "Context" above - either latency or wasted load, and it doesn't
demonstrate the real-time architecture this project is meant to show understanding of.

### Server-Sent Events (SSE)
A reasonable, simpler alternative for the notification-push case specifically (one-directional,
server-to-client). Rejected in favor of STOMP for consistency: this project needs
topic-scoped broadcast (multiple users, one project) as well as per-user push, and STOMP's
subscription model handles both with one mechanism rather than two.

### An external message broker relay (e.g. RabbitMQ via STOMP relay)
Spring's simple in-memory broker (`enableSimpleBroker`) was chosen instead - see
`WebSocketConfig`'s Javadoc for the explicit trade-off: correct for one instance, and would
need revisiting (a relay to a real broker) the moment this application runs as more than one
instance, since the in-memory broker holds no state beyond its own process's connected
sessions.

## Consequences

**Positive**
- Real-time updates work with zero changes to any producer (task/project/team/comment
  services) - the listener pattern already established for `audit` extended naturally.
- Verified end-to-end with an actual client speaking the real protocol, not asserted from
  reading the code.

**Negative / trade-offs accepted**
- The in-memory broker doesn't scale past one instance without further work (see
  `docs/architecture.md`'s scaling section).
- No delivery guarantee for either channel - a client with no open WebSocket session simply
  doesn't get the push and falls back to whatever it does on next REST poll/load. This is the
  correct expectation for a live UI enhancement, not a source of truth (contrast with Kafka's
  at-least-once, which the underlying data is not dependent on this layer for).

## Interviewer questions this ADR should let you answer
- "How does WebSocket authentication actually work, given browsers can't set headers on the handshake?"
- "What happens when a client disconnects, or reconnects?"
- "Why STOMP and not raw WebSockets or SSE?"
- "What breaks about your real-time setup once you have two backend instances?"
