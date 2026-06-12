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
  await expect(page.locator("#landingView")).toBeVisible();

  await page.goto("/index.html");
  await expect(page.locator("#landingView")).toBeVisible();
  await expectNoClientErrors(errors);
});

test("admin access is isolated from normal users", async ({ page }) => {
  const errors = collectClientErrors(page);

  await loginUser(page);
  await page.goto("/admin.html");
  await expect(page).toHaveURL(/\/admin-login\.html/);

  await page.locator("#adminUserIdInput").fill("xiaofuge");
  await page.locator("#adminPasswordInput").fill("demo");
  await page.locator("#adminLoginBtn").click();
  await expect(page.locator("#toast")).toContainText("当前账号无管理员权限");
  await expect(page).toHaveURL(/\/admin-login\.html/);

  await loginAdmin(page);
  await expect(page.locator("#configList")).toContainText("chatbot");

  await page.reload();
  await expect(page.locator("#adminUserBadge")).toContainText("admin");
  await expect(page.locator("#opsGatewayStatus")).toContainText("正常");

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

test("frontend assets are cache-safe and legacy 8098 API remains compatible", async ({ request, baseURL }) => {
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

  const legacyApiBase = new URL(baseURL);
  legacyApiBase.port = "8098";
  const legacyLogin = await request.post(legacyApiBase.origin + "/api/v1/auth/login", {
    data: { userId: "xiaofuge", password: "demo" }
  });
  await expect(legacyLogin).toBeOK();
  expect(await legacyLogin.json()).toMatchObject({ code: "0000" });
});
