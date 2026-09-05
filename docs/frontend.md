# Frontend

React 19 + TypeScript + Vite, built after all 20 backend phases were complete, against the
real API documented in `docs/api.md` - not a separate mock backend. The brief treats the
frontend as secondary to the backend/architecture work (see README's "Why this project
exists"), so this isn't an attempt at feature parity with every backend endpoint; it covers the
core loop end to end - auth, teams, projects, a real-time Kanban board, comments, notifications
- and does it with real visual and interaction polish rather than bare unstyled forms, since a
portfolio project's frontend is the one part a reviewer actually *looks* at rather than reads.

## Stack, and why each piece

| Choice | Why |
|---|---|
| **Vite** | Fast dev server/HMR, zero-config TypeScript + JSX. The default choice for a new React SPA with no framework-level routing/SSR need. |
| **Tailwind CSS v4** | Utility-first, and v4's `@theme`/`@custom-variant` CSS-native config means no separate `tailwind.config.js` - design tokens (the brand color scale, light/dark CSS variables) live in `index.css` itself. |
| **TanStack Query** | Every screen's data is server state (teams, projects, tasks, comments, notifications) - Query's cache, invalidation, and optimistic-update primitives fit that directly, instead of hand-rolling loading/error/refetch state in every component. |
| **Zustand** | The *only* genuine client-only state is auth (tokens) and theme preference - a small, non-normalized slice that doesn't need Redux-scale tooling. `persist` middleware handles localStorage sync directly. |
| **@dnd-kit** | The Kanban board's drag-and-drop. Chosen over `react-beautiful-dnd` (unmaintained) and a hand-rolled HTML5 drag API (inconsistent cross-browser semantics, no built-in accessibility story) - dnd-kit is actively maintained, accessible by default, and its sensor model (see the E2E test's own commentary below) is explicit about what triggers a drag rather than relying on ambiguous native drag events. |
| **@stomp/stompjs** | The same STOMP-over-WebSocket protocol the backend already speaks (see ADR-005) - no separate client protocol to introduce. |
| **Framer Motion** | Used sparingly (modal/drawer enter-exit transitions, the auth page's feature list) - motion that communicates state change (something opened, something moved), not decoration for its own sake. |

## What's implemented

- **Auth**: register/login with a split-screen layout, automatic token refresh (a single
  shared in-flight refresh promise so concurrent 401s don't each try to rotate the same
  already-single-use refresh token and lock each other out - see `lib/api.ts`).
- **Teams**: create, list (sidebar), member management (invite by email, change role, remove),
  respecting the same OWNER/ADMIN/MEMBER policy the backend enforces (the UI hides
  actions a MEMBER couldn't perform anyway, but the backend is still the actual authority -
  see `docs/security.md`'s "never enforced only on the frontend" principle).
- **Projects**: create within a team, archive/unarchive.
- **Task board**: a real Kanban board (To Do / In Progress / In Review / Done / Blocked),
  drag-and-drop status changes, task creation/editing, labels, assignee, due date, full-text
  search bar (`GET /api/v1/tasks/search`).
- **Comments**: threaded per task, author-only edit, author-or-admin delete - matching
  `CommentService`'s policy exactly.
- **Real-time**: the board subscribes to `/topic/projects/{id}` and invalidates its task query
  on any update from a teammate; a global notification bell subscribes to
  `/user/queue/notifications` for this session's own push notifications.
- **Dark mode**: a manual toggle (persisted), defaulting to the OS preference on first visit.

## Two real bugs, found by an automated end-to-end test, not by inspection

`frontend/e2e/smoke.spec.ts` (Playwright) drives the real app against the real backend -
register, create a team/project/task, move it through the board, comment, verify persistence
across a full page reload. While building it, it caught two genuine bugs before any user would
have:

1. **`TaskDetailDrawer` didn't close on `Escape`**, unlike `Modal`. Its full-screen backdrop
   then kept intercepting clicks on everything behind it with no keyboard way to dismiss it -
   caught when the test's very next step (clicking the theme toggle) timed out because the
   drawer was still silently blocking the whole page. Fixed by giving the drawer the same
   Escape-listener/body-scroll-lock effect `Modal` already had.
2. **A drag gesture starting inside a `select-none` card could still trigger the browser's
   native text-selection drag across *other*, unrelated text elsewhere on the board.**
   `select-none` on the card stops that element's own text from being selected, but doesn't
   stop a selection gesture from extending into nearby selectable text (a column label, a "No
   tasks" placeholder) as the pointer moves past it - the gesture begins before `@dnd-kit`'s
   `PointerSensor` has confirmed a drag past its activation distance. A screenshot taken
   mid-drag during test debugging showed exactly this: unrelated column labels highlighted as
   if selected, the card never actually picked up. Fixed by disabling selection across the
   whole board, not just per-card.

## What the E2E suite deliberately does *not* automate, and why

The actual drag-and-drop **mouse gesture** itself isn't driven by the automated test, even
though the feature works - manually verified and screenshotted, including confirming a
dragged task's new status survives a full page reload (so it's a real server-side write, not
optimistic-only client state). `@dnd-kit`'s `PointerSensor` depends on the browser's real
pointer-capture APIs, which behaved inconsistently under Playwright's synthetic input in this
environment: the same gesture that reliably drags a card for an actual person was flaky to
nonexistent whether driven via `page.mouse` or direct `PointerEvent` dispatch (the latter is
`isTrusted: false`, and `Element.setPointerCapture` silently no-ops for untrusted events in
Chromium - confirmed by trying it and watching the drag still not register at all).

Rather than ship a test that fails for reasons unrelated to the app's own correctness, the
E2E suite instead drives the task detail drawer's Status dropdown - the exact same
`changeStatus` mutation, and the same optimistic-update/rollback logic
(`useChangeTaskStatus`), that the board's drag handler calls. That's a deterministic,
real regression guard for the part of "drag-and-drop" that can actually break silently (the
mutation, its optimistic UI, its server-side persistence); the mouse choreography on top of it
is the part confirmed by hand. See `docs/testing.md` for how this fits the project's broader
"representative, not exhaustive" testing philosophy.

## Running the E2E suite

Needs the full stack up (same as any other manual check against this app):

```bash
docker compose up -d postgres redis kafka
cd backend && ./mvnw spring-boot:run &
cd frontend && npm run dev &
npm run test:e2e
```
