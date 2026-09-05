import { test, expect } from "@playwright/test";

// End-to-end, against the real backend - not component tests with mocked API calls. This is
// deliberately a small, representative flow (the same scope philosophy as the backend's own
// test suite - see docs/testing.md), not exhaustive UI coverage: it exists to catch the class
// of bug backend unit/integration tests structurally cannot, and already has, twice, while this
// suite itself was being written:
//
// 1. TaskDetailDrawer didn't close on Escape (unlike Modal) - its full-screen backdrop kept
//    intercepting clicks on everything behind it once you couldn't dismiss it, discovered when
//    this test's own next step (clicking the theme toggle) timed out.
// 2. A drag gesture starting inside a select-none card could still trigger the browser's
//    native text-selection drag across *other*, unrelated text elsewhere on the board -
//    select-none on one element doesn't stop a selection gesture from extending past it.
//    Fixed by disabling selection across the whole board, not just per-card.
//
// Both are real, now-fixed bugs - see the corresponding comments in TaskDetailDrawer.tsx and
// KanbanBoard.tsx - not hypothetical ones invented to justify this test's existence.
//
// What this test does *not* automate, and why: the actual drag-and-drop mouse gesture itself.
// @dnd-kit's PointerSensor relies on the browser's real pointer-capture machinery, which behaves
// inconsistently under Playwright's synthetic input in this environment - the same gesture that
// reliably drags a card in a real browser (manually verified, screenshotted, and confirmed to
// persist across a reload while building this feature) was flaky to nonexistent when driven by
// both page.mouse and direct PointerEvent dispatch here. Rather than ship a test that's flaky
// for reasons unrelated to the app's own correctness, this test instead drives the *same*
// underlying status-change code path (TaskService.changeStatus via the drawer's Status
// dropdown, not the drag gesture) - a deterministic, real regression guard for the mutation and
// its persistence, which is the part of "drag-and-drop" that can actually break silently.
test("register, create a team/project/task, move it through the drawer, and see it persist", async ({ page }) => {
  const email = `e2e-${Date.now()}@example.com`;
  const consoleErrors: string[] = [];
  page.on("console", (msg) => msg.type() === "error" && consoleErrors.push(msg.text()));
  page.on("pageerror", (err) => consoleErrors.push(err.message));

  await page.goto("/register");
  await page.fill("#full-name", "E2E Test User");
  await page.fill("#email", email);
  await page.fill("#password", "SuperSecret123");
  await page.click('button[type="submit"]');
  await page.waitForURL("/");
  await expect(page.getByText(/Good (morning|afternoon|evening)/)).toBeVisible();

  await page.click('button[aria-label="Create team"]');
  await page.fill("#team-name", "E2E Team");
  await page.click('button:has-text("Create team")');
  await page.waitForURL(/\/teams\//);

  await page.click('button:has-text("New project")');
  await page.fill("#project-name", "E2E Project");
  await page.click('button:has-text("Create project")');
  await page.waitForURL(/\/projects\//);
  await expect(page.getByText("To Do")).toBeVisible();

  await page.click('button:has-text("New task")');
  await page.fill("#title", "Move me to In Progress");
  await page.click('button:has-text("Create task")');
  await expect(page.getByText("Move me to In Progress")).toBeVisible();

  // Open the task and change its status via the drawer's Status dropdown - the deterministic
  // equivalent of dragging the card, exercising the exact same changeStatus mutation and
  // optimistic-update/rollback logic (useChangeTaskStatus) that the Kanban board's drag handler
  // calls too.
  await page.click("text=Move me to In Progress");
  await expect(page.getByText("Task details")).toBeVisible();
  await page.locator("select").first().selectOption("IN_PROGRESS");
  await page.keyboard.press("Escape");
  await expect(page.getByText("Task details")).not.toBeVisible();

  // Reload from scratch: proves the status change genuinely persisted server-side (see
  // TaskConcurrencyIntegrationTest's backend-side equivalent proof for the same underlying
  // @Version-guarded update path), not just React Query's client-side cache.
  await page.reload();
  await expect(page.getByText("Move me to In Progress")).toBeVisible();
  const todoCount = await page.getByText("To Do").locator("xpath=following-sibling::span[1]").innerText();
  const inProgressCount = await page.getByText("In Progress").locator("xpath=following-sibling::span[1]").innerText();
  expect(todoCount).toBe("0");
  expect(inProgressCount).toBe("1");

  // Comment, verified against the backend directly (not just the client render) the same way
  // docs/performance.md's N+1 fix was verified - by asking the server, not trusting the UI alone.
  await page.click("text=Move me to In Progress");
  await expect(page.getByText("Task details")).toBeVisible();
  await page.fill('textarea[placeholder="Write a comment..."]', "e2e comment");
  const [commentResponse] = await Promise.all([
    page.waitForResponse((r) => r.url().includes("/api/v1/comments") && r.request().method() === "POST"),
    page.click('button:has-text("Comment")'),
  ]);
  expect(commentResponse.status()).toBe(201);
  await expect(page.getByText("e2e comment")).toBeVisible();

  // Escape must close the drawer (regression guard for bug #1 above).
  await page.keyboard.press("Escape");
  await expect(page.getByText("Task details")).not.toBeVisible();

  // Dark mode toggles without anything left blocking the page underneath it.
  await page.click('button[aria-label="Toggle theme"]');
  await expect(page.locator("html")).toHaveClass(/dark/);
  await page.click('button[aria-label="Notifications"]');
  await expect(page.getByText("You're all caught up.")).toBeVisible();

  expect(consoleErrors, `Unexpected browser console errors: ${consoleErrors.join(", ")}`).toHaveLength(0);
});
