const { test, expect } = require("@playwright/test");

function collectClientErrors(page) {
  const errors = [];
  page.on("console", (msg) => {
    if (msg.type() !== "error") return;
    const text = msg.text();
    // 错误密码/非管理员探测期间预期出现的错误。
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

  // 访问 admin：普通用户会被重定向回 index，同时保留令牌。
  await page.goto("/admin.html");
  await expect(page).toHaveURL(/\/index\.html/);
  await expect(page.locator("#appView")).toBeVisible();
  await expect(page.locator("#userNameBadge")).toContainText("xiaofuge");

  // 完成 admin 重定向后，用户仍可访问用户端页面。
  await page.locator("#userMenuBtn").click();
  await expect(page.locator("#userCenterDrawer")).toHaveClass(/open/);

  // 携带普通用户令牌访问管理员登录页时只显示提示并停留在当前页（不清除令牌）。
  await page.goto("/admin-login.html");
  await expect(page.locator("#toast")).toContainText("当前账号不是管理员");
  // 此时仍应位于管理员登录页。
  await expect(page).toHaveURL(/\/admin-login\.html/);

  // 管理员登录仍然可用。
  await loginAdmin(page);
  await expect(page.locator("#configList")).toContainText("chatbot");

  await page.reload();
  await expect(page.locator("#adminUserBadge")).toContainText("admin");
  await expect(page.locator("#opsGatewayStatus")).toContainText("正常");

  // 注销后不能通过返回操作重新进入。
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

  // 点击后按钮文本发生变化。
  await page.locator("#drawBtn").click();
  await expect(page.locator("#drawBtn")).toHaveText("抽奖中...");
  await expect(page.locator("#drawBtn")).toBeDisabled();

  // 抽奖完成后按钮恢复为 GO。
  await expect(page.locator("#drawBtn")).toHaveText("GO", { timeout: 15000 });
  await expect(page.locator("#drawBtn")).not.toBeDisabled();
  await expectNoClientErrors(errors);
});

test("login redirect param only allows same-origin destinations", async ({ page }) => {
  const errors = collectClientErrors(page);

  // 相对的同源路径应被接受。
  await page.goto("/login.html?redirect=./index.html?from=login");
  await page.locator("#userIdInput").fill("xiaofuge");
  await page.locator("#passwordInput").fill("demo");
  await page.locator("#loginBtn").click();
  await expect(page).toHaveURL(/\/index\.html\?from=login/);

  // 使用产品注销路径，使重置过程与真实用户会话执行相同的清理，
  // 避免存储清理与页面跳转发生竞态。
  await page.locator("#userMenuBtn").click();
  await expect(page.locator("#userCenterDrawer")).toHaveClass(/open/);
  await page.locator("#logoutBtn").click();
  await expect(page.locator("#landingView")).toBeVisible();

  // 外部 URL 参数会被忽略，并回退到 index.html。
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

  // 当前拓扑通过 gateway:8080 暴露鉴权服务（旧单体端口 :8098 已移除）。
  const apiBase = new URL(baseURL);
  apiBase.port = "8080";
  const gatewayLogin = await request.post(apiBase.origin + "/api/v1/auth/login", {
    data: { userId: "xiaofuge", password: "demo" }
  });
  await expect(gatewayLogin).toBeOK();
  expect(await gatewayLogin.json()).toMatchObject({ code: "0000" });
});
