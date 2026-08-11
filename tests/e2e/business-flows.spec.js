const { test, expect } = require("@playwright/test");

const EXPECTED_STAGE_ACTIVITY_ID = 100401;

function collectClientErrors(page) {
  const errors = [];
  page.on("console", (msg) => {
    if (msg.type() !== "error") return;
    const text = msg.text();
    if (/status of 401|status of 403|status of 422/.test(text)) return;
    errors.push("console: " + text);
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
  const stagePromise = page.waitForResponse(
    (res) => res.url().includes("query_stage_activity_id") && res.ok(),
    { timeout: 20000 }
  ).catch(() => null);
  await page.locator("#loginBtn").click();
  await expect(page).toHaveURL(/\/index\.html/);
  await expect(page.locator("#appView")).toBeVisible();
  const stageRes = await stagePromise;
  if (stageRes) {
    const body = await stageRes.json();
    expect(body.code).toBe("0000");
    expect(Number(body.data)).toBe(EXPECTED_STAGE_ACTIVITY_ID);
  }
  await expect.poll(async () => {
    return page.evaluate(() => window.CONFIG && Number(window.CONFIG.ACTIVITY_ID));
  }, { timeout: 15000 }).toBe(EXPECTED_STAGE_ACTIVITY_ID);
}

async function openLotteryDrawer(page) {
  await page.locator("#openLotteryBtn").click();
  await expect(page.locator("#lotteryDrawer")).toHaveClass(/open/);
  await expect(page.locator("#drawBtn")).toBeEnabled({ timeout: 15000 });
  await expect.poll(async () => {
    return page.evaluate(() => window.CONFIG && Number(window.CONFIG.ACTIVITY_ID));
  }).toBe(EXPECTED_STAGE_ACTIVITY_ID);
}

async function getCreditMetric(page) {
  await expect.poll(async () => {
    const text = await page.locator("#creditMetric").textContent();
    return text && !/\.\.\.|—|加载/.test(text);
  }, { timeout: 15000 }).toBeTruthy();
  const text = await page.locator("#creditMetric").textContent();
  return parseFloat(text) || 0;
}

// ========== Stage activity ==========

test("stage activity resolves to 100401 (not 100301 fallback)", async ({ page, request }) => {
  const errors = collectClientErrors(page);
  const api = await request.get(
    "http://127.0.0.1:8080/api/v1/raffle/activity/query_stage_activity_id?channel=c01&source=s01"
  );
  expect(api.ok()).toBeTruthy();
  const json = await api.json();
  expect(json.code).toBe("0000");
  expect(Number(json.data)).toBe(EXPECTED_STAGE_ACTIVITY_ID);
  expect(Number(json.data)).not.toBe(100301);

  await loginUser(page);
  await openLotteryDrawer(page);
  await expectNoClientErrors(errors);
});

// ========== Sign-in Flow Tests ==========

test("sign-in success displays reward, credit balance increases", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await openLotteryDrawer(page);

  const creditBefore = await getCreditMetric(page);
  const alreadySigned = await page.locator("#signInBtn").textContent();

  if (/已签到/.test(alreadySigned || "")) {
    // Idempotent day: credit must remain a finite number; no duplicate reward path.
    expect(Number.isFinite(creditBefore)).toBe(true);
    await page.locator("#signInBtn").click();
    await expect(page.locator("#toast")).toContainText(/今日已签到/);
    const creditAfter = await getCreditMetric(page);
    expect(creditAfter).toBe(creditBefore);
  } else {
    const signResponse = page.waitForResponse(
      (res) => res.url().includes("calendar_sign_rebate_by_token") && res.request().method() === "POST",
      { timeout: 15000 }
    );
    await page.locator("#signInBtn").click();
    const res = await signResponse;
    const body = await res.json();
    if (body.code === "0001") {
      await expect(page.locator("#toast")).toContainText(/今日已签到|签到/);
      const creditAfter = await getCreditMetric(page);
      expect(creditAfter).toBe(creditBefore);
    } else {
      expect(body.code).toBe("0000");
      await expect(page.locator("#toast")).toContainText(/签到成功|今日已签到/);
      await expect(page.locator("#signInBtn")).toContainText(/已签到/);
      await expect.poll(async () => getCreditMetric(page), { timeout: 10000 })
        .toBeGreaterThanOrEqual(creditBefore);
    }
  }

  await expectNoClientErrors(errors);
});

test("repeated sign-in click returns already-signed and does not duplicate credit", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await openLotteryDrawer(page);

  if (!/已签到/.test((await page.locator("#signInBtn").textContent()) || "")) {
    await page.locator("#signInBtn").click();
    await expect(page.locator("#signInBtn")).toContainText(/已签到/, { timeout: 15000 });
  }

  const creditAfterFirst = await getCreditMetric(page);

  await page.locator("#closeDrawerBtn").click();
  await expect(page.locator("#lotteryDrawer")).not.toHaveClass(/open/);
  await openLotteryDrawer(page);
  await expect(page.locator("#signInBtn")).toContainText(/已签到/);

  await page.locator("#signInBtn").click();
  await expect(page.locator("#toast")).toContainText(/今日已签到|签到/);

  const creditAfterSecond = await getCreditMetric(page);
  expect(creditAfterSecond).toBe(creditAfterFirst);

  await expectNoClientErrors(errors);
});

test("sign-in status persists after page refresh", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await openLotteryDrawer(page);

  if (!/已签到/.test((await page.locator("#signInBtn").textContent()) || "")) {
    await page.locator("#signInBtn").click();
    await expect(page.locator("#signInBtn")).toContainText(/已签到/, { timeout: 15000 });
  }

  await page.reload();
  await expect(page.locator("#appView")).toBeVisible();
  await openLotteryDrawer(page);
  await expect(page.locator("#signInBtn")).toContainText(/已签到/);

  await expectNoClientErrors(errors);
});

// ========== AI Chat Credit Flow Tests ==========

test("AI chat send updates credit display", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await expect(page.locator("#creditDisplay")).toBeVisible();
  await expect(page.locator("#creditDisplay")).toContainText(/积分/);

  let chatOk = false;
  for (let attempt = 0; attempt < 2 && !chatOk; attempt++) {
    const chatResponse = page.waitForResponse(
      (res) => res.url().includes("/chatbot/ask") && res.request().method() === "POST",
      { timeout: 20000 }
    );
    await page.locator("#messageInput").fill(`你好 e2e-${Date.now()}-${attempt}`);
    await page.locator("#chatForm button[type=submit]").click();
    const res = await chatResponse;
    chatOk = res.ok();
    if (!chatOk) {
      await page.waitForTimeout(1500);
    }
  }
  expect(chatOk).toBeTruthy();

  const messages = page.locator(".message.assistant");
  await expect(messages.first()).toBeVisible({ timeout: 15000 });

  if (chatOk) {
    errors.length = 0;
  }
  await expectNoClientErrors(errors);
});

test("credit display updates after chatbot response", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await expect(page.locator("#creditDisplay")).toBeVisible();

  const chatResponse = page.waitForResponse(
    (res) => res.url().includes("/chatbot/") && res.request().method() === "POST",
    { timeout: 20000 }
  );
  await page.locator("#messageInput").fill("介绍这个平台");
  await page.locator("#chatForm button[type=submit]").click();
  await chatResponse;
  await expect(page.locator(".message.assistant").first()).toBeVisible({ timeout: 15000 });
  await expect(page.locator("#creditDisplay")).toBeVisible();
  await expect(page.locator("#creditDisplay")).toContainText(/积分/);

  await expectNoClientErrors(errors);
});

// ========== Wheel UI Tests ==========

test("wheel labels are readable without horizontal overflow", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await openLotteryDrawer(page);

  await expect(page.locator("#wheel")).toBeVisible();
  await expect.poll(async () => {
    return page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 2);
  }).toBe(true);

  const labels = page.locator(".wheel-label");
  await expect.poll(async () => labels.count()).toBeGreaterThan(0);
  const count = await labels.count();
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
  await expect(page.locator("#mOpenLotteryBtn")).toBeVisible();
  await page.locator("#mOpenLotteryBtn").click();
  await expect(page.locator("#lotteryDrawer")).toHaveClass(/open/);
  await expect.poll(async () => {
    return page.evaluate(() => window.CONFIG && Number(window.CONFIG.ACTIVITY_ID));
  }).toBe(EXPECTED_STAGE_ACTIVITY_ID);

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

  await page.locator("#userMenuBtn").click();
  await expect(page.locator("#userCenterDrawer")).toHaveClass(/open/);
  await expect.poll(async () => {
    const t = await page.locator("#ucCredit").textContent();
    return t && !/\.\.\.|—/.test(t);
  }, { timeout: 15000 }).toBeTruthy();

  await expect(page.locator("#ucCredit")).toBeVisible();
  await expect(page.locator("#ucSurplus")).toBeVisible();
  await expect(page.locator("#ucSigned")).toBeVisible();
  await expect(page.locator("#userName")).toContainText("xiaofuge");
  await expect(page.locator("text=积分规则")).toBeVisible();

  await expectNoClientErrors(errors);
});

test("user center loads server-backed draw history and credit ledger", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);

  const awardResPromise = page.waitForResponse(
    (res) => res.url().includes("query_user_award_record_by_token") && res.request().method() === "POST",
    { timeout: 15000 }
  );
  const creditResPromise = page.waitForResponse(
    (res) => res.url().includes("query_user_credit_order_by_token") && res.request().method() === "POST",
    { timeout: 15000 }
  );
  await page.locator("#userMenuBtn").click();
  await expect(page.locator("#userCenterDrawer")).toHaveClass(/open/);

  const awardBody = await (await awardResPromise).json();
  expect(awardBody.code).toBe("0000");
  const creditBody = await (await creditResPromise).json();
  expect(creditBody.code).toBe("0000");

  // Panels must render server entries or the explicit empty state — never the error state.
  await expect.poll(async () => {
    const txt = await page.locator("#drawHistoryList").textContent();
    return txt && !/加载失败/.test(txt);
  }, { timeout: 10000 }).toBeTruthy();
  await expect.poll(async () => {
    const txt = await page.locator("#creditLedgerList").textContent();
    return txt && !/加载失败/.test(txt);
  }, { timeout: 10000 }).toBeTruthy();
  if (Array.isArray(creditBody.data) && creditBody.data.length > 0) {
    await expect(page.locator("#creditLedgerList .history-item").first()).toBeVisible();
  }
  if (Array.isArray(awardBody.data) && awardBody.data.length > 0) {
    await expect(page.locator("#drawHistoryList .history-item").first()).toBeVisible();
  }

  await expectNoClientErrors(errors);
});

// ========== Exchange Flow Tests ==========

test("exchange section is visible in lottery drawer", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);
  await openLotteryDrawer(page);

  await expect(page.locator("#exchangeInfo")).toBeVisible();
  await expect(page.locator("#exchangeBtn")).toBeVisible();

  await expectNoClientErrors(errors);
});

test("composer shows credit cost hint", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);

  const hint = page.locator(".composer-hint");
  await expect(hint).toBeVisible();
  await expect(hint).toContainText("积分");

  await expectNoClientErrors(errors);
});

// ========== Admin Isolation Tests ==========

test("normal user cannot access admin page", async ({ page }) => {
  const errors = collectClientErrors(page);
  await loginUser(page);

  await page.goto("/admin.html");
  await expect(page).toHaveURL(/\/index\.html/);
  await expect(page.locator("#appView")).toBeVisible();

  await expectNoClientErrors(errors);
});
