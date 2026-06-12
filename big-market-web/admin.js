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

function redirectToAdminLogin(message) {
  if (redirectingToLogin) return;
  redirectingToLogin = true;
  clearAuth();
  auth = {token: "", userId: ""};
  if (message) toast(message);
  setTimeout(function() { location.replace(adminLoginUrl()); }, message ? 500 : 0);
}

// Wrap apiRequest to add auth-expired guard
function adminRequest(path, opts) {
  return apiRequest(path, opts, {
    onAuthExpired: function() {
      redirectToAdminLogin("登录已过期，请重新登录");
    }
  }).catch(function(error) {
    if (error.code === "0008") {
      redirectToAdminLogin("当前账号无管理员权限");
    } else if (error.code === "0009") {
      redirectToAdminLogin("登录已过期，请重新登录");
    }
    throw error;
  });
}

async function saveConfig(namespace, configKey, configValue, description) {
  var res = await adminRequest("/admin/config/save", {
    method: "POST",
    body: JSON.stringify({namespace: namespace, configKey: configKey, configValue: configValue, description: description})
  });
  if (res.code !== "0000") throw new Error(res.info || "保存失败");
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
  var activityId = dom.activityIdInput.value.trim();
  var stageRes = await adminRequest("/raffle/erp/query_raffle_activity_stage_list", {method: "GET"});
  var skuRes = await adminRequest("/raffle/activity/query_sku_product_list_by_activity_id?activityId=" + activityId, {method: "POST"});
  var stage = (stageRes.data || []).find(function(item) { return String(item.activityId) === String(activityId); });
  dom.activityStateInput.value = stage?.state || "online";
  renderSkuTable(skuRes.data || []);
  toast("已读取活动 " + activityId + "，SKU 数量：" + (skuRes.data?.length || 0));
}

// Fixed: use query_raffle_award_list_by_token (authenticated, only needs activityId)
async function loadAwards() {
  if (!requireLogin()) return;
  var activityId = Number(dom.activityIdInput.value || 100301);
  try {
    var res = await adminRequest("/raffle/strategy/query_raffle_award_list_by_token", {
      method: "POST",
      body: JSON.stringify({activityId: activityId})
    });
    if (res.code === "0000" && res.data?.length) {
      renderAwardTable(res.data);
      return;
    }
  } catch (e) {
    // fall through to SKU-based fallback
  }
  // Sku fallback
  var skuRes = await adminRequest("/raffle/activity/query_sku_product_list_by_activity_id?activityId=" + activityId, {method: "POST"});
  var skuAwards = (skuRes.data || []).map(function(item) {
    return {
      awardId: item.sku,
      awardTitle: "抽奖权益包 " + item.sku,
      awardSubtitle: "售价 " + item.productAmount + " 积分，含 " + (item.activityCount?.totalCount || 0) + " 次总抽奖次数",
      isAwardUnlock: item.stockCountSurplus > 0
    };
  });
  renderAwardTable(skuAwards);
}

function renderSkuTable(items) {
  var rows = items.map(function(item) {
    return "<tr><td>" + item.sku + "</td><td>" + item.productAmount + "</td><td>" + (item.activityCount?.totalCount || 0) + "</td><td>" + (item.activityCount?.dayCount || 0) + "</td><td>" + (item.stockCountSurplus ?? 0) + "</td></tr>";
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
  var res = await adminRequest("/raffle/activity/armory?activityId=" + dom.activityIdInput.value, {method: "GET"});
  if (res.code !== "0000") throw new Error(res.info || "预热失败");
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

// Update sidebar user badge
if (dom.adminUserBadge) dom.adminUserBadge.textContent = auth.token ? "已登录: " + (auth.userId || "") : "未登录";
if (dom.adminLoginLink) dom.adminLoginLink.textContent = auth.token ? "🔑 切换账号" : "🔑 登录";

dom.adminLoginBtn.textContent = auth.token ? "退出登录" : "登录";
dom.adminLoginBtn.addEventListener("click", function() {
  if (!auth.token) {
    location.href = "./admin-login.html";
  } else {
    clearAuth();
    auth = {token: "", userId: ""};
    dom.adminUserBadge.textContent = "未登录";
    dom.adminLoginLink.textContent = "🔑 登录";
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
  logQueryBtn: document.getElementById("logQueryBtn"),
  logService: document.getElementById("logService"),
  logLevel: document.getElementById("logLevel"),
  logKeyword: document.getElementById("logKeyword"),
  logLines: document.getElementById("logLines"),
  logOutput: document.getElementById("logOutput"),
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

  // 3. Admin config/list
  try {
    var adminRes = await adminRequest("/admin/config/list", {method: "GET"});
    if (adminRes.code === "0000") {
      setDiag(opsDom.diagAdmin, true, "OK (" + (adminRes.data?.length || 0) + " items)");
    } else {
      setDiag(opsDom.diagAdmin, false, adminRes.info || "业务错误");
    }
  } catch (e) {
    setDiag(opsDom.diagAdmin, false, e.message);
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

// Log query (placeholder — backend endpoint not yet implemented)
function queryLogs() {
  var service = opsDom.logService ? opsDom.logService.value : "";
  var level = opsDom.logLevel ? opsDom.logLevel.value : "";
  var keyword = opsDom.logKeyword ? opsDom.logKeyword.value : "";
  var lines = opsDom.logLines ? opsDom.logLines.value : "50";
  var params = "?service=" + encodeURIComponent(service) + "&level=" + encodeURIComponent(level) + "&keyword=" + encodeURIComponent(keyword) + "&lines=" + encodeURIComponent(lines);
  var msg = "后端日志接口未接入。预留端点：GET /admin/ops/logs" + params.replace("&", "&amp;");
  if (opsDom.logOutput) opsDom.logOutput.textContent = msg;
}

if (opsDom.refreshBtn) opsDom.refreshBtn.addEventListener("click", function() {
  refreshOps().catch(function(e) { toast("运维监控刷新失败: " + e.message); });
});
if (opsDom.logQueryBtn) opsDom.logQueryBtn.addEventListener("click", queryLogs);

async function initializeAdmin() {
  if (!auth.token) return;
  await loadConfigs();
  await Promise.all([
    loadActivity().catch(function() {}),
    loadAwards().catch(function() {}),
    refreshOps().catch(function() {})
  ]);
}

initializeAdmin().catch(function(error) {
  if (error.code !== "0008" && error.code !== "0009") {
    toast(error.message || "管理后台初始化失败");
  }
});
