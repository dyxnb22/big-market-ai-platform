// @ts-check
const { defineConfig, devices } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests/e2e",
  // The acceptance suite intentionally exercises one shared demo account.
  // Serial workers keep credit, quota, and sign-in assertions deterministic.
  workers: 1,
  timeout: 30 * 1000,
  expect: {
    timeout: 5 * 1000
  },
  use: {
    baseURL: process.env.E2E_BASE_URL || "http://127.0.0.1:5173",
    trace: "retain-on-failure",
    screenshot: "only-on-failure"
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] }
    }
  ]
});
