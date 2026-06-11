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

function requireLogin() {
  if (auth.token) return true;
  location.href = "./admin-login.html?redirect=" + encodeURIComponent("./admin.html");
  return false;
}

// Wrap apiRequest to add auth-expired guard
function adminRequest(path, opts) {
  return apiRequest(path, opts, {
    onAuthExpired: function() {
      clearAuth();
      auth = {token: "", userId: ""};
      toast("登录已过期，请重新登录");
    }
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

if (auth.token) {
  loadConfigs().catch(function() {});
  loadActivity().catch(function() {});
  loadAwards().catch(function() {});
}
