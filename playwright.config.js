// @ts-check
const { defineConfig, devices } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./tests/e2e",
  // 验收套件有意使用同一个共享演示账户。
  // 单 worker 串行执行，保证积分、配额和签到断言具有确定性。
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
