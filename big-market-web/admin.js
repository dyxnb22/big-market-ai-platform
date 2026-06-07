const API_BASE = "http://127.0.0.1:8098/api/v1";
const AUTH_KEY = "lucky-draw-auth";

const dom = {
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

let auth = readJson(AUTH_KEY, {token: "", userId: ""});

function readJson(key, fallback) {
  try {
    return JSON.parse(localStorage.getItem(key)) || fallback;
  } catch (ignore) {
    return fallback;
  }
}

function requireLogin() {
  if (auth.token) return true;
  location.href = `./login.html?redirect=${encodeURIComponent("./admin.html")}`;
  return false;
}

async function request(path, options = {}) {
  const headers = Object.assign({"Content-Type": "application/json"}, options.headers || {});
  if (auth.token) headers.Authorization = auth.token;
  const response = await fetch(API_BASE + path, Object.assign({}, options, {headers}));
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.info || `HTTP ${response.status}`);
  return data;
}

async function saveConfig(namespace, configKey, configValue, description) {
  const res = await request("/admin/config/save", {
    method: "POST",
    body: JSON.stringify({namespace, configKey, configValue, description})
  });
  if (res.code !== "0000") throw new Error(res.info || "保存失败");
}

async function loadConfigs() {
  const res = await request("/admin/config/list", {method: "GET"});
  if (res.code !== "0000") throw new Error(res.info || "读取配置失败");
  dom.configList.innerHTML = "";
  (res.data || []).forEach(item => {
    const row = document.createElement("div");
    row.className = "config-item";
    row.innerHTML = `<strong>${escapeHtml(item.namespace)} / ${escapeHtml(item.configKey)}</strong><span>${escapeHtml(item.configValue ?? "")}</span>`;
    dom.configList.appendChild(row);
  });
  const chatbot = (res.data || []).find(item => item.namespace === "chatbot" && item.configKey === "enabled");
  setSwitch(chatbot?.configValue !== "false");
}

async function loadActivity() {
  if (!requireLogin()) return;
  const activityId = dom.activityIdInput.value.trim();
  const stageRes = await request("/raffle/erp/query_raffle_activity_stage_list", {method: "GET"});
  const skuRes = await request(`/raffle/activity/query_sku_product_list_by_activity_id?activityId=${activityId}`, {method: "POST"});
  const stage = (stageRes.data || []).find(item => String(item.activityId) === String(activityId));
  dom.activityStateInput.value = stage?.state || "online";
  renderSkuTable(skuRes.data || []);
  toast(`已读取活动 ${activityId}，SKU 数量：${skuRes.data?.length || 0}`);
}

async function loadAwards() {
  if (!requireLogin()) return;
  const activityId = Number(dom.activityIdInput.value || 100301);
  const res = await request("/raffle/strategy/query_raffle_award_list", {
    method: "POST",
    body: JSON.stringify({activityId})
  }).catch(() => null);
  if (res?.code === "0000" && res.data?.length) {
    renderAwardTable(res.data);
    return;
  }
  const skuRes = await request(`/raffle/activity/query_sku_product_list_by_activity_id?activityId=${activityId}`, {method: "POST"});
  const skuAwards = (skuRes.data || []).map(item => ({
    awardId: item.sku,
    awardTitle: `抽奖权益包 ${item.sku}`,
    awardSubtitle: `售价 ${item.productAmount} 积分，含 ${item.activityCount?.totalCount || 0} 次总抽奖次数`,
    isAwardUnlock: item.stockCountSurplus > 0
  }));
  renderAwardTable(skuAwards);
}

function renderSkuTable(items) {
  const rows = items.map(item => `
    <tr>
      <td>${item.sku}</td>
      <td>${item.productAmount}</td>
      <td>${item.activityCount?.totalCount || 0}</td>
      <td>${item.activityCount?.dayCount || 0}</td>
      <td>${item.stockCountSurplus ?? 0}</td>
    </tr>
  `).join("");
  dom.skuTable.innerHTML = `
    <table>
      <thead><tr><th>SKU</th><th>积分价格</th><th>总次数</th><th>日次数</th><th>剩余库存</th></tr></thead>
      <tbody>${rows || "<tr><td colspan=\"5\">暂无 SKU 数据</td></tr>"}</tbody>
    </table>
  `;
}

function renderAwardTable(items) {
  const rows = items.map(item => `
    <tr>
      <td>${item.awardId}</td>
      <td>${escapeHtml(item.awardTitle || "")}</td>
      <td>${escapeHtml(item.awardSubtitle || "")}</td>
      <td>${item.isAwardUnlock ? "已解锁" : "待解锁"}</td>
    </tr>
  `).join("");
  dom.awardTable.innerHTML = `
    <table>
      <thead><tr><th>ID</th><th>奖品</th><th>说明</th><th>状态</th></tr></thead>
      <tbody>${rows || "<tr><td colspan=\"4\">暂无奖品数据</td></tr>"}</tbody>
    </table>
  `;
}

async function saveActivityDisplay() {
  if (!requireLogin()) return;
  const activityId = dom.activityIdInput.value.trim();
  await saveConfig(`activity.${activityId}`, "title", dom.activityTitleInput.value, "用户端活动标题");
  await saveConfig(`activity.${activityId}`, "state", dom.activityStateInput.value, "用户端活动状态");
  await saveConfig(`activity.${activityId}`, "copy", dom.activityCopyInput.value, "用户端活动文案");
  toast("活动展示配置已保存");
  await loadConfigs();
}

async function saveAwardDisplay() {
  if (!requireLogin()) return;
  const activityId = dom.activityIdInput.value.trim();
  await saveConfig(`award.${activityId}`, "note", dom.awardNoteInput.value, "奖品展示备注");
  toast("奖品展示配置已保存");
  await loadConfigs();
}

async function armory() {
  if (!requireLogin()) return;
  const res = await request(`/raffle/activity/armory?activityId=${dom.activityIdInput.value}`, {method: "GET"});
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
  [...dom.chatbotSwitch.querySelectorAll("button")].forEach(button => {
    button.classList.toggle("active", button.dataset.value === String(enabled));
  });
}

function toast(message) {
  dom.toast.textContent = message;
  dom.toast.classList.add("show");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => dom.toast.classList.remove("show"), 2200);
}

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, char => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
  }[char]));
}

function bind(button, action) {
  button.addEventListener("click", async () => {
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

dom.adminLoginBtn.textContent = auth.token ? auth.userId || "已登录" : "登录";
dom.adminLoginBtn.addEventListener("click", () => requireLogin());
bind(dom.loadActivityBtn, loadActivity);
bind(dom.saveActivityConfigBtn, saveActivityDisplay);
bind(dom.armoryBtn, armory);
bind(dom.loadAwardsBtn, loadAwards);
bind(dom.saveAwardConfigBtn, saveAwardDisplay);
bind(dom.loadConfigsBtn, loadConfigs);
dom.chatbotSwitch.addEventListener("click", event => {
  const button = event.target.closest("button[data-value]");
  if (!button) return;
  saveChatbotEnabled(button.dataset.value === "true").catch(error => toast(error.message));
});

if (auth.token) {
  loadConfigs().catch(() => {});
  loadActivity().catch(() => {});
  loadAwards().catch(() => {});
}
