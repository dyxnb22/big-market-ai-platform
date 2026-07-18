const { test, expect } = require("@playwright/test");

function collectClientErrors(page) {
  const errors = [];
  page.on("console", (msg) => {
    if (msg.type() !== "error") return;
    const text = msg.text();
    // Expected during wrong-password / non-admin probes
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
  await page.locator("#loginBtn").click();
  await expect(page).toHaveURL(/\/index\.html/);
  await expect(page.locator("#appView")).toBeVisible();
}

async function loginAdmin(page) {
  await page.goto("/admin-login.html");
  await page.locator("#adminUserIdInput").fill("admin");
  await page.locator("#adminPasswordInput").fill("admin");
  await page.locator("#adminLoginBtn").click();
  await expect(page).toHaveURL(/\/admin\.html/);
  await expect(page.locator("#adminUserBadge")).toContainText("admin");
}

async function expectNoHorizontalOverflow(page) {
  await expect.poll(async () => {
    return page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
  }).toBe(true);
}

test("user login validation, session refresh, and logout", async ({ page }) => {
  const errors = collectClientErrors(page);

  await page.goto("/login.html");
  await page.locator("#userIdInput").fill("xiaofuge");
  await page.locator("#passwordInput").fill("wrong-password");
  await page.locator("#loginBtn").click();
  await expect(page.locator("#toast")).toContainText("账号或密码错误");

  await page.locator("#passwordInput").fill("");
  await page.locator("#loginBtn").click();
  await expect(page.locator("#toast")).toContainText("请输入密码");

  await loginUser(page);
  await expect(page.locator("#userNameBadge")).toContainText("xiaofuge");
  await expect(page.locator("#apiStatusText")).not.toContainText("失败");

  await page.reload();
  await expect(page.locator("#appView")).toBeVisible();
  await expect(page.locator("#userNameBadge")).toContainText("xiaofuge");

  await page.locator("#userMenuBtn").click();
  await expect(page.locator("#userCenterDrawer")).toHaveClass(/open/);
  await page.locator("#logoutBtn").click();
  await expect(page.locator("#landingView")).toBeVisible({ timeout: 10000 });

  await page.goto("/index.html");
  await expect(page.locator("#landingView")).toBeVisible();
  await expectNoClientErrors(errors);
});

test("admin access is isolated from normal users", async ({ page }) => {
  const errors = collectClientErrors(page);

  await loginUser(page);
  await expect(page.locator("#appView")).toBeVisible();

  // Navigate to admin — user is redirected back to index with token preserved
  await page.goto("/admin.html");
  await expect(page).toHaveURL(/\/index\.html/);
  await expect(page.locator("#appView")).toBeVisible();
  await expect(page.locator("#userNameBadge")).toContainText("xiaofuge");

  // User can still access user pages after the admin redirect
  await page.locator("#userMenuBtn").click();
  await expect(page.locator("#userCenterDrawer")).toHaveClass(/open/);

  // Admin-login page with user token shows toast but stays on page (token not cleared)
  await page.goto("/admin-login.html");
  await expect(page.locator("#toast")).toContainText("当前账号不是管理员");
  // Should still be on admin-login page
  await expect(page).toHaveURL(/\/admin-login\.html/);

  // Admin login still works
  await loginAdmin(page);
  await expect(page.locator("#configList")).toContainText("chatbot");

  await page.reload();
  await expect(page.locator("#adminUserBadge")).toContainText("admin");
  await expect(page.locator("#opsGatewayStatus")).toContainText("正常");

  // Logout — cannot go back
  await page.locator("#adminLoginBtn").click();
  await expect(page).toHaveURL(/\/admin-login\.html/);
  await page.goBack();
  await expect(page).toHaveURL(/\/admin-login\.html/);
  await expectNoClientErrors(errors);
});

test("desktop and narrow layouts do not horizontally overflow", async ({ page }) => {
  const errors = collectClientErrors(page);

  await loginUser(page);
  await expectNoHorizontalOverflow(page);
  await page.locator("#openLotteryBtn").click();
  await expect(page.locator("#lotteryDrawer")).toHaveClass(/open/);
  await expectNoHorizontalOverflow(page);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  await expect(page.locator("#appView")).toBeVisible();
  await expectNoHorizontalOverflow(page);
  await page.locator("#mOpenLotteryBtn").click();
  await expect(page.locator("#lotteryDrawer")).toHaveClass(/open/);
  await expectNoHorizontalOverflow(page);

  await page.setViewportSize({ width: 1280, height: 720 });
  await loginAdmin(page);
  await expectNoHorizontalOverflow(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload();
  await expect(page.locator("#adminUserBadge")).toContainText("admin");
  await expectNoHorizontalOverflow(page);
  await expectNoClientErrors(errors);
});

test("draw button shows 抽奖中... while drawing and restores GO", async ({ page }) => {
  const errors = collectClientErrors(page);

  await loginUser(page);
  await page.locator("#openLotteryBtn").click();
  await expect(page.locator("#lotteryDrawer")).toHaveClass(/open/);
  await expect(page.locator("#drawBtn")).toBeEnabled({ timeout: 15000 });

  // Button text changes on click
  await page.locator("#drawBtn").click();
  await expect(page.locator("#drawBtn")).toHaveText("抽奖中...");
  await expect(page.locator("#drawBtn")).toBeDisabled();

  // After the draw completes the button restores to GO
  await expect(page.locator("#drawBtn")).toHaveText("GO", { timeout: 15000 });
  await expect(page.locator("#drawBtn")).not.toBeDisabled();
  await expectNoClientErrors(errors);
});

test("login redirect param only allows same-origin destinations", async ({ page }) => {
  const errors = collectClientErrors(page);

  // Relative same-origin path is honored.
  await page.goto("/login.html?redirect=./index.html?from=login");
  await page.locator("#userIdInput").fill("xiaofuge");
  await page.locator("#passwordInput").fill("demo");
  await page.locator("#loginBtn").click();
  await expect(page).toHaveURL(/\/index\.html\?from=login/);

  // Logout to reset state for next test
  await page.evaluate(() => { localStorage.clear(); location.reload(); });
  await page.waitForLoadState("networkidle");

  await page.goto("/index.html");
  await expect(page.locator("#landingView")).toBeVisible();

  // External URL param is ignored — falls back to index.html
  await page.goto("/login.html?redirect=http://evil.com");
  await page.locator("#userIdInput").fill("xiaofuge");
  await page.locator("#passwordInput").fill("demo");
  await page.locator("#loginBtn").click();
  await expect(page).toHaveURL(/\/index\.html/);
  await expectNoClientErrors(errors);
});

test("frontend assets are cache-safe and gateway API remains compatible", async ({ request, baseURL }) => {
  const login = await request.get("/login.html");
  await expect(login).toBeOK();
  expect(login.headers()["cache-control"]).toContain("no-store");

  const html = await login.text();
  expect(html).not.toContain("__APP_VERSION__");
  expect(html).not.toContain("v=4");
  expect(html).toMatch(/config\.js\?v=\d+/);
  expect(html).toMatch(/login\.js\?v=\d+/);

  const configPath = html.match(/\.\/config\.js\?v=\d+/)[0].replace("./", "/");
  const config = await request.get(configPath);
  await expect(config).toBeOK();
  expect(config.headers()["cache-control"]).toContain("no-store");
  await expect(await config.text()).toContain('return "/api/v1"');

  // Final topology exposes auth through gateway :8080 (legacy monolith :8098 removed).
  const apiBase = new URL(baseURL);
  apiBase.port = "8080";
  const gatewayLogin = await request.post(apiBase.origin + "/api/v1/auth/login", {
    data: { userId: "xiaofuge", password: "demo" }
  });
  await expect(gatewayLogin).toBeOK();
  expect(await gatewayLogin.json()).toMatchObject({ code: "0000" });
});
