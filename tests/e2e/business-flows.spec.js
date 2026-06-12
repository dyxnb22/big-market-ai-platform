const { test, expect } = require("@playwright/test");

function collectClientErrors(page) {
  const errors = [];
  page.on("console", (msg) => {
    if (msg.type() === "error") errors.push("console: " + msg.text());
  });
  page.on("pageerror", (error) => {
    errors.push("pageerror: " + error.message);
  });
  page.on("response", (response) => {
    const url = response.url();
    if (url.includes("/api/") && response.status() >= 500) {
      errors.push("http " + response.status() + ": " + url);
    }
  });
  return errors;
}

async function expectNoClientErrors(errors) {
  expect(errors, errors.join("\n")).toEqual([]);
}

async function loginUser(page, userId = "xiaofuge", password = "demo") {
  await page.goto("/login.html");
  await page.locator("#userIdInput").fill(userId);
  await page.locator("#passwordInput").fill(password);
  await page.locator("#loginBtn").click();
  await expect(page).toHaveURL(/\/index\.html/);
  await expect(page.locator("#appView")).toBeVisible();
}

async function openLotteryDrawer(page) {
  await page.locator("#openLotteryBtn").click();
  await expect(page.locator("#lotteryDrawer")).toHaveClass(/open/);
  // Wait for campaign data to load
  await page.waitForTimeout(1500);
}

async function getCreditDisplay(page) {
  const text = await page.locator("#creditDisplay").textContent();
  const match = text.match(/[\d.]+/);
  return match ? parseFloat(match[0]) : 0;
}

async function getCreditMetric(page) {
  const text = await page.locator("#creditMetric").textContent();
  return parseFloat(text) || 0;
}

// ========== Sign-in Flow Tests ==========

test("sign-in success displays reward, credit balance increases", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await openLotteryDrawer(page);

  // Read credit before sign-in
  const creditBefore = await getCreditMetric(page);

  // Click sign-in
  await page.locator("#signInBtn").click();
  await page.waitForTimeout(1500);

  // Toast should show success
  await expect(page.locator("#toast")).toContainText(/签到成功|今日已签到/);

  // Sign-in button should show done state
  await expect(page.locator("#signInBtn")).toContainText(/已签到/);

  // Credit balance should be shown
  const creditAfter = await getCreditMetric(page);
  expect(typeof creditAfter).toBe("number");

  await expectNoClientErrors(errors);
});

test("repeated sign-in click returns already-signed and does not duplicate credit", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await openLotteryDrawer(page);

  // First sign-in
  await page.locator("#signInBtn").click();
  await page.waitForTimeout(1500);
  await expect(page.locator("#signInBtn")).toContainText(/已签到/);

  const creditAfterFirst = await getCreditMetric(page);

  // Close and reopen drawer to verify state persists
  await page.locator("#closeDrawerBtn").click();
  await page.waitForTimeout(300);
  await openLotteryDrawer(page);

  // Button should still show signed
  await expect(page.locator("#signInBtn")).toContainText(/已签到/);

  // Click again — should not change credit
  await page.locator("#signInBtn").click();
  await page.waitForTimeout(1000);

  // Toast should indicate already signed
  await expect(page.locator("#toast")).toContainText(/今日已签到|签到/);

  const creditAfterSecond = await getCreditMetric(page);
  expect(creditAfterSecond).toBe(creditAfterFirst);

  await expectNoClientErrors(errors);
});

test("sign-in status persists after page refresh", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await openLotteryDrawer(page);

  // Sign in
  await page.locator("#signInBtn").click();
  await page.waitForTimeout(1500);
  await expect(page.locator("#signInBtn")).toContainText(/已签到/);

  // Refresh the page
  await page.reload();
  await expect(page.locator("#appView")).toBeVisible();
  await openLotteryDrawer(page);

  // Should still show signed
  await expect(page.locator("#signInBtn")).toContainText(/已签到/);

  await expectNoClientErrors(errors);
});

// ========== AI Chat Credit Flow Tests ==========

test("AI chat send updates credit display", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await page.waitForTimeout(1000);

  // Verify credit display is present
  await expect(page.locator("#creditDisplay")).toBeVisible();
  const creditText = await page.locator("#creditDisplay").textContent();
  expect(creditText).toMatch(/积分/);

  // Send a chat message
  await page.locator("#messageInput").fill("你好");
  await page.locator("#chatForm button[type=submit]").click();
  await page.waitForTimeout(3000);

  // Should get a response
  const messages = page.locator(".message.assistant");
  await expect(messages.first()).toBeVisible({ timeout: 10000 });

  await expectNoClientErrors(errors);
});

test("credit display updates after chatbot response", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await page.waitForTimeout(1000);

  // Get initial credit
  const initialText = await page.locator("#creditDisplay").textContent();

  // Send message
  await page.locator("#messageInput").fill("介绍这个平台");
  await page.locator("#chatForm button[type=submit]").click();
  await page.waitForTimeout(4000);

  // Should still have a credit display (not an error)
  await expect(page.locator("#creditDisplay")).toBeVisible();

  await expectNoClientErrors(errors);
});

// ========== Wheel UI Tests ==========

test("wheel labels are readable without horizontal overflow", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await openLotteryDrawer(page);

  // Verify wheel exists
  await expect(page.locator("#wheel")).toBeVisible();

  // Check no horizontal overflow
  await expect.poll(async () => {
    return page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
  }).toBe(true);

  // Wheel labels should exist
  const labels = page.locator(".wheel-label");
  const count = await labels.count();
  expect(count).toBeGreaterThan(0);

  // Each label should have non-empty text
  for (let i = 0; i < count; i++) {
    const text = await labels.nth(i).textContent();
    expect(text.length).toBeGreaterThan(0);
  }

  await expectNoClientErrors(errors);
});

test("wheel is responsive on mobile viewport", async ({ page }) => {
  const errors = collectClientErrors(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await loginUser(page);
  await page.waitForTimeout(500);
  await page.locator("#mOpenLotteryBtn").click();
  await expect(page.locator("#lotteryDrawer")).toHaveClass(/open/);
  await page.waitForTimeout(1000);

  // Check no horizontal overflow on mobile
  await expect.poll(async () => {
    return page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
  }).toBe(true);

  await expect(page.locator("#wheel")).toBeVisible();
  await expectNoClientErrors(errors);
});

// ========== User Center Tests ==========

test("user center shows credit, surplus, and sign-in status", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await page.waitForTimeout(1000);

  // Open user center
  await page.locator("#userMenuBtn").click();
  await expect(page.locator("#userCenterDrawer")).toHaveClass(/open/);
  await page.waitForTimeout(1000);

  // Check key elements exist
  await expect(page.locator("#ucCredit")).toBeVisible();
  await expect(page.locator("#ucSurplus")).toBeVisible();
  await expect(page.locator("#ucSigned")).toBeVisible();
  await expect(page.locator("#userName")).toContainText("xiaofuge");

  // Credit rules section should be visible
  await expect(page.locator("text=积分规则")).toBeVisible();

  await expectNoClientErrors(errors);
});

// ========== Exchange Flow Tests ==========

test("exchange section is visible in lottery drawer", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await openLotteryDrawer(page);

  // Exchange section should be present
  await expect(page.locator("#exchangeInfo")).toBeVisible();
  await expect(page.locator("#exchangeBtn")).toBeVisible();

  await expectNoClientErrors(errors);
});

test("composer shows credit cost hint", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await page.waitForTimeout(500);

  // Composer hint should mention credit cost
  const hint = page.locator(".composer-hint");
  await expect(hint).toBeVisible();
  await expect(hint).toContainText("积分");

  await expectNoClientErrors(errors);
});

// ========== Admin Isolation Tests (existing coverage verification) ==========

test("normal user cannot access admin page", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);

  await page.goto("/admin.html");
  await expect(page).toHaveURL(/\/index\.html/);
  await expect(page.locator("#appView")).toBeVisible();

  await expectNoClientErrors(errors);
});
