/**
 * 管理后台脚本：活动展示配置、奖品预览、Chatbot 开关、运维探活。
 *
 * 鉴权约定：
 * - adminRequest：仅用于 /admin/*，鉴权失败会跳转登录
 * - safeRequest：用于 raffle/ERP 等业务接口，失败只 toast、不跳转
 */
var dom = {
  adminUserBadge: document.getElementById("adminUserBadge"),
  adminLoginLink: document.getElementById("adminLoginLink"),
  adminLoginBtn: document.getElementById("adminLoginBtn"),
  activityIdInput: document.getElementById("activityIdInput"),
  activityTitleInput: document.getElementById("activityTitleInput"),
  activityStateInput: document.getElementById("activityStateInput"),
  activityCopyInput: document.getElementById("activityCopyInput"),
  loadActivityBtn: document.getElementById("loadActivityBtn"),
  saveActivityConfigBtn: document.getElementById("saveActivityConfigBtn"),
  armoryBtn: document.getElementById("armoryBtn"),
  skuTable: document.getElementById("skuTable"),
  loadAwardsBtn: document.getElementById("loadAwardsBtn"),
  awardTable: document.getElementById("awardTable"),
  awardNoteInput: document.getElementById("awardNoteInput"),
  saveAwardConfigBtn: document.getElementById("saveAwardConfigBtn"),
  loadConfigsBtn: document.getElementById("loadConfigsBtn"),
  chatbotSwitch: document.getElementById("chatbotSwitch"),
  configList: document.getElementById("configList"),
  toast: document.getElementById("toast")
};

var auth = readAuth();
var redirectingToLogin = false;

// ===== Auth helpers =====
function requireLogin() {
  if (auth.token) return true;
  location.href = adminLoginUrl();
  return false;
}

function adminLoginUrl() {
  return "./admin-login.html?redirect=" + encodeURIComponent("./admin.html" + location.search + location.hash);
}

if (!auth.token) {
  location.replace(adminLoginUrl());
}

function redirectExpiredLogin(message) {
  if (redirectingToLogin) return;
  redirectingToLogin = true;
  clearAuth();
  auth = {token: "", userId: ""};
  if (message) toast(message);
  setTimeout(function() { location.replace(adminLoginUrl()); }, message ? 500 : 0);
}

function redirectUnauthorized(message) {
  if (redirectingToLogin) return;
  redirectingToLogin = true;
  if (message) toast(message);
  // Keep the normal user's session intact and return to the user app.
  setTimeout(function() {
    location.replace("./index.html");
  }, 800);
}

// adminRequest: wraps apiRequest and redirects on auth/permission errors.
// Use ONLY for /admin/* endpoints (admin-service). For raffle/ERP endpoints
// use safeRequest() which shows a toast instead of redirecting.
function adminRequest(path, opts) {
  return apiRequest(path, opts, {
    onAuthExpired: function() {
      redirectExpiredLogin("登录已过期，请重新登录");
    }
  }).catch(function(error) {
    if (error.code === "0008") {
      redirectUnauthorized("当前账号无管理员权限");
    } else if (error.code === "0009") {
      redirectExpiredLogin("登录已过期，请重新登录");
    }
    throw error;
  });
}

// safeRequest: for non-admin-service endpoints (raffle, ERP) where errors
// should show a toast but never trigger a redirect.
function safeRequest(path, opts) {
  return apiRequest(path, opts);
}

// ===== Platform config / activity / awards =====
async function saveConfig(namespace, configKey, configValue, description) {
  await adminRequest("/admin/config/save", {
    method: "POST",
    body: JSON.stringify({namespace: namespace, configKey: configKey, configValue: configValue, description: description})
  });
}

async function loadConfigs() {
  var res = await adminRequest("/admin/config/list", {method: "GET"});
  dom.configList.innerHTML = "";
  (res.data || []).forEach(function(item) {
    var row = document.createElement("div");
    row.className = "config-item";
    row.innerHTML = "<strong>" + esc(item.namespace) + " / " + esc(item.configKey) + "</strong><span>" + esc(item.configValue ?? "") + "</span>";
    dom.configList.appendChild(row);
  });
  var chatbot = (res.data || []).find(function(item) { return item.namespace === "chatbot" && item.configKey === "enabled"; });
  setSwitch(chatbot?.configValue !== "false");
}

async function loadActivity() {
  if (!requireLogin()) return;
  var activityId = dom.activityIdInput.value.trim() || "100301";
  dom.activityIdInput.value = activityId;
  var stageRes = await safeRequest("/raffle/erp/query_raffle_activity_stage_list", {method: "GET"});
  var skuRes = await safeRequest("/raffle/activity/query_sku_product_list_by_activity_id?activityId=" + activityId, {method: "POST", body: "{}"});
  var stage = (stageRes.data || []).find(function(item) { return String(item.activityId) === String(activityId); });
  dom.activityStateInput.value = stage?.state || "online";
  if (dom.activityStateInput.tagName === "SELECT") {
    var opt = dom.activityStateInput.querySelector('option[value="' + (stage?.state || "online") + '"]');
    if (!opt) dom.activityStateInput.value = "online";
  }
  renderSkuTable(skuRes.data || []);
  toast("已读取活动 " + activityId + "，SKU 数量：" + (skuRes.data?.length || 0));
}

async function loadAwards() {
  if (!requireLogin()) return;
  var activityId = Number(dom.activityIdInput.value || 100301);
  try {
    var res = await safeRequest("/raffle/strategy/query_raffle_award_list_by_token", {
      method: "POST",
      body: JSON.stringify({activityId: activityId})
    });
    if (res.data && res.data.length) {
      renderAwardTable(res.data);
    } else {
      dom.awardTable.innerHTML = '<div style="padding:20px;text-align:center;color:var(--muted);font-size:13px;">暂无奖品数据</div>';
    }
  } catch (e) {
    toast("奖品接口加载失败: " + e.message);
    dom.awardTable.innerHTML = '<div style="padding:20px;text-align:center;color:var(--muted);font-size:13px;">奖品接口加载失败，请稍后重试。</div>';
  }
}

function renderSkuTable(items) {
  var rows = items.map(function(item) {
    return "<tr><td>" + esc(String(item.sku || "")) + "</td><td>" + Number(item.productAmount || 0) + "</td><td>" + (item.activityCount?.totalCount || 0) + "</td><td>" + (item.activityCount?.dayCount || 0) + "</td><td>" + (item.stockCountSurplus ?? 0) + "</td></tr>";
  }).join("");
  dom.skuTable.innerHTML = "<table><thead><tr><th>SKU</th><th>积分价格</th><th>总次数</th><th>日次数</th><th>剩余库存</th></tr></thead><tbody>" + (rows || '<tr><td colspan="5">暂无 SKU 数据</td></tr>') + "</tbody></table>";
}

function renderAwardTable(items) {
  var rows = items.map(function(item) {
    return "<tr><td>" + item.awardId + "</td><td>" + esc(item.awardTitle || "") + "</td><td>" + esc(item.awardSubtitle || "") + "</td><td>" + (item.isAwardUnlock ? "已解锁" : "待解锁") + "</td></tr>";
  }).join("");
  dom.awardTable.innerHTML = "<table><thead><tr><th>ID</th><th>奖品</th><th>说明</th><th>状态</th></tr></thead><tbody>" + (rows || '<tr><td colspan="4">暂无奖品数据</td></tr>') + "</tbody></table>";
}

async function saveActivityDisplay() {
  if (!requireLogin()) return;
  var activityId = dom.activityIdInput.value.trim();
  await saveConfig("activity." + activityId, "title", dom.activityTitleInput.value, "用户端活动标题");
  await saveConfig("activity." + activityId, "state", dom.activityStateInput.value, "用户端活动状态");
  await saveConfig("activity." + activityId, "copy", dom.activityCopyInput.value, "用户端活动文案");
  toast("活动展示配置已保存");
  await loadConfigs();
}

async function saveAwardDisplay() {
  if (!requireLogin()) return;
  var activityId = dom.activityIdInput.value.trim();
  await saveConfig("award." + activityId, "note", dom.awardNoteInput.value, "奖品展示备注");
  toast("奖品展示配置已保存");
  await loadConfigs();
}

async function armory() {
  if (!requireLogin()) return;
  await safeRequest("/raffle/activity/armory?activityId=" + dom.activityIdInput.value, {method: "GET"});
  toast("活动预热成功");
}

async function saveChatbotEnabled(enabled) {
  if (!requireLogin()) return;
  await saveConfig("chatbot", "enabled", String(enabled), "AI 对话入口开关");
  setSwitch(enabled);
  toast(enabled ? "AI 对话已开启" : "AI 对话已关闭");
  await loadConfigs();
}

function setSwitch(enabled) {
  [].slice.call(dom.chatbotSwitch.querySelectorAll("button")).forEach(function(button) {
    button.classList.toggle("active", button.dataset.value === String(enabled));
  });
}

function bind(button, action) {
  button.addEventListener("click", async function() {
    button.disabled = true;
    try {
      await action();
    } catch (error) {
      toast(error.message);
    } finally {
      button.disabled = false;
    }
  });
}

// ===== Sidebar / button bindings =====
if (dom.adminUserBadge) dom.adminUserBadge.textContent = auth.token ? "已登录: " + (auth.userId || "") : "未登录";
if (dom.adminLoginLink) dom.adminLoginLink.textContent = auth.token ? "🔑 切换账号" : "🔑 登录";

dom.adminLoginBtn.textContent = auth.token ? "退出登录" : "登录";
dom.adminLoginBtn.addEventListener("click", function() {
  if (!auth.token) {
    location.href = "./admin-login.html";
  } else {
    clearAuth();
    auth = {token: "", userId: ""};
    if (dom.adminUserBadge) dom.adminUserBadge.textContent = "未登录";
    if (dom.adminLoginLink) dom.adminLoginLink.textContent = "🔑 登录";
    dom.adminLoginBtn.textContent = "登录";
    toast("已退出");
    setTimeout(function() { location.replace(adminLoginUrl()); }, 300);
  }
});
bind(dom.loadActivityBtn, loadActivity);
bind(dom.saveActivityConfigBtn, saveActivityDisplay);
bind(dom.armoryBtn, armory);
bind(dom.loadAwardsBtn, loadAwards);
bind(dom.saveAwardConfigBtn, saveAwardDisplay);
bind(dom.loadConfigsBtn, loadConfigs);
dom.chatbotSwitch.addEventListener("click", function(event) {
  var button = event.target.closest("button[data-value]");
  if (!button) return;
  saveChatbotEnabled(button.dataset.value === "true").catch(function(error) { toast(error.message); });
});

// ===== Ops Monitoring =====
var opsDom = {
  gatewayStatus: document.getElementById("opsGatewayStatus"),
  loginStatus: document.getElementById("opsLoginStatus"),
  apiBase: document.getElementById("opsApiBase"),
  opsUser: document.getElementById("opsUser"),
  refreshTime: document.getElementById("opsRefreshTime"),
  healthBody: document.getElementById("opsHealthBody"),
  refreshBtn: document.getElementById("refreshOpsBtn"),
  diagGateway: document.getElementById("diagGateway"),
  diagLogin: document.getElementById("diagLogin"),
  diagAdmin: document.getElementById("diagAdmin"),
  diagChatbot: document.getElementById("diagChatbot"),
};

function setDiag(el, ok, msg) {
  if (!el) return;
  el.className = "diag-item";
  el.classList.add(ok ? "diag-ok" : "diag-fail");
  el.querySelector(".diag-result").textContent = msg;
}

async function refreshOps() {
  var now = new Date();
  var gatewayBase = CONFIG.API_BASE.replace(/\/api\/v1$/, "");
  opsDom.refreshTime.textContent = now.toLocaleTimeString();
  opsDom.apiBase.textContent = CONFIG.API_BASE;
  opsDom.opsUser.textContent = auth.userId || "-";

  // 1. Gateway health
  var gwOk = false;
  try {
    var res = await fetch(gatewayBase + "/actuator/health");
    var data = await res.json();
    gwOk = data.status === "UP";
    opsDom.gatewayStatus.textContent = gwOk ? "正常" : "异常";
    opsDom.gatewayStatus.className = "ops-value " + (gwOk ? "ops-ok" : "ops-fail");
    setDiag(opsDom.diagGateway, gwOk, gwOk ? "UP (" + data.status + ")" : (data.status || "DOWN"));
  } catch (e) {
    opsDom.gatewayStatus.textContent = "不可达";
    opsDom.gatewayStatus.className = "ops-value ops-fail";
    setDiag(opsDom.diagGateway, false, "请求失败: " + e.message);
  }

  // 2. Login API availability (OPTIONS preflight)
  var loginOk = false;
  try {
    var optRes = await fetch(CONFIG.API_BASE + "/auth/login", {method: "OPTIONS"});
    loginOk = optRes.ok || optRes.status === 200;
    opsDom.loginStatus.textContent = loginOk ? "可达" : "异常";
    opsDom.loginStatus.className = "ops-value " + (loginOk ? "ops-ok" : "ops-fail");
    setDiag(opsDom.diagLogin, loginOk, "OPTIONS " + optRes.status + " " + (optRes.ok ? "OK" : ""));
  } catch (e) {
    opsDom.loginStatus.textContent = "不可达";
    opsDom.loginStatus.className = "ops-value ops-fail";
    setDiag(opsDom.diagLogin, false, "请求失败: " + e.message);
  }

  // 3. Admin config/list — use safeRequest so a 0008 here doesn't trigger a redirect
  try {
    var adminRes = await safeRequest("/admin/config/list", {method: "GET"});
    setDiag(opsDom.diagAdmin, true, "OK (" + (adminRes.data?.length || 0) + " items)");
  } catch (e) {
    setDiag(opsDom.diagAdmin, false, e.message || "请求失败");
  }

  // 4. Chatbot API availability
  try {
    var cbRes = await fetch(CONFIG.API_BASE + "/chatbot/ask", {method: "OPTIONS"});
    setDiag(opsDom.diagChatbot, cbRes.ok, "OPTIONS " + cbRes.status + (cbRes.ok ? " OK" : ""));
  } catch (e) {
    setDiag(opsDom.diagChatbot, false, "不可达: " + e.message);
  }

  // 5. Health table (gateway-routed API checks — no side effects).
  // Auth-required endpoints send the stored token; public endpoints
  // (actuator, OPTIONS) check gateway-level reachability.
  var services = [
    {name: "gateway", url: gatewayBase + "/actuator/health"},
    {name: "auth-service", path: "/auth/login", method: "OPTIONS"},
    {name: "market-service", path: "/raffle/activity/query_user_credit_account_by_token", method: "POST", needsAuth: true},
    {name: "admin-service", path: "/admin/config/list", needsAuth: true},
    {name: "chatbot-service", path: "/chatbot/ask", method: "OPTIONS"},
  ];
  var rows = "";
  for (var i = 0; i < services.length; i++) {
    var svc = services[i];
    try {
      var reqHeaders = {"Content-Type": "application/json"};
      if (svc.needsAuth && auth.token) reqHeaders.Authorization = auth.token;
      var opts = {method: svc.method || "GET", headers: reqHeaders};
      if (svc.method === "POST") opts.body = JSON.stringify({activityId: CONFIG.ACTIVITY_ID});
      var sr = await fetch(svc.url || (CONFIG.API_BASE + svc.path), opts);
      var status = sr.ok ? "UP" : "DOWN";
      var detail = sr.status;
      if (sr.headers.get("content-type") && sr.headers.get("content-type").includes("json")) {
        try { var sj = await sr.clone().json(); status = sj.status === "UP" ? "UP" : (sj.code === "0000" ? "UP" : "ERR"); detail = sj.status || sj.code || sr.status; } catch(e2) { /* ignore */ }
      }
      rows += "<tr><td>" + esc(svc.name) + "</td><td class='" + (status === "UP" ? "ops-ok" : "ops-fail") + "'>" + status + "</td><td>" + esc(detail) + "</td></tr>";
    } catch (e) {
      rows += "<tr><td>" + esc(svc.name) + "</td><td class='ops-fail'>DOWN</td><td>" + esc(e.message) + "</td></tr>";
    }
  }
  opsDom.healthBody.innerHTML = rows;
}

if (opsDom.refreshBtn) opsDom.refreshBtn.addEventListener("click", function() {
  refreshOps().catch(function(e) { toast("运维监控刷新失败: " + e.message); });
});

async function initializeAdmin() {
  if (!auth.token) {
    location.replace(adminLoginUrl());
    return;
  }
  bindAdminNav();
  await loadConfigs();
  await Promise.all([
    loadActivity().catch(function(e) { toast("活动数据加载失败: " + (e.message || "")); }),
    loadAwards().catch(function() {}),
    refreshOps().catch(function() {})
  ]);
}

initializeAdmin().catch(function(error) {
  if (error.code !== "0008" && error.code !== "0009") {
    toast(error.message || "管理后台初始化失败");
  }
});

function bindAdminNav() {
  var links = document.querySelectorAll(".admin-sidebar nav a[href^='#']");
  var scrollRoot = document.querySelector(".admin-main");
  if (!links.length || !scrollRoot) return;
  var sections = [];
  links.forEach(function(link) {
    var id = link.getAttribute("href").slice(1);
    var sec = document.getElementById(id);
    if (sec) sections.push({ link: link, sec: sec });
  });
  function setActive() {
    var y = scrollRoot.scrollTop + 100;
    var current = sections[0];
    sections.forEach(function(item) {
      if (item.sec.offsetTop <= y) current = item;
    });
    links.forEach(function(l) { l.classList.remove("active"); });
    if (current) current.link.classList.add("active");
  }
  scrollRoot.addEventListener("scroll", setActive, { passive: true });
  setActive();
}
