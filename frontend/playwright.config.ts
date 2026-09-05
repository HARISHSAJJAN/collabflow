import { defineConfig, devices } from "@playwright/test";

// Runs against an already-running dev server and backend (see e2e/README.md) rather than
// starting its own webServer: this suite exercises the real backend end to end (register,
// create real teams/projects/tasks, drag-and-drop, verify persistence across a reload), so it
// needs the full stack - Postgres/Redis/Kafka via docker-compose plus the Spring Boot app -
// already up, the same as any other manual check against this app would.
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false,
  retries: 0,
  reporter: "list",
  use: {
    baseURL: "http://localhost:5173",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
