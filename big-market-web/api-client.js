/**
 * big-market-web 前端共用 API 客户端与工具函数。
 * 纯 JavaScript、无框架，供用户端、管理端和登录页共用。
 */

// ===== 鉴权 =====
function readAuth() {
  try {
    // Token 只属于当前会话；删除旧的持久化副本，不将其静默迁移到生命周期更长的浏览器会话。
    localStorage.removeItem(CONFIG.AUTH_KEY);
    var v = sessionStorage.getItem(CONFIG.AUTH_KEY);
    return v ? JSON.parse(v) : {token: "", userId: ""};
  } catch (e) { return {token: "", userId: ""}; }
}

function saveAuth(token, userId) {
  sessionStorage.setItem(CONFIG.AUTH_KEY, JSON.stringify({token: token, userId: userId}));
}

function clearAuth() {
  sessionStorage.removeItem(CONFIG.AUTH_KEY);
  localStorage.removeItem(CONFIG.AUTH_KEY);
}

// ===== 统一 API 请求 =====
/**
 * 发送统一 API 请求并集中处理错误。
 *
 * - HTTP 非 2xx：抛出包含服务端提示的 Error
 * - JSON 解析失败：抛出包含 HTTP 状态文本的 Error
 * - 业务码不等于 "0000"：抛出包含业务码和提示的 Error
 * - Token 过期或业务码 0009：按需调用 onAuthExpired 回调
 *
 * @param {string} path 例如 "/auth/login"
 * @param {object} opts fetch 配置（method、body、headers）
 * @param {object} [ext] 扩展配置，例如 {onAuthExpired: function}
 * @returns {Promise<object>} 解析后的响应体（code === "0000"）
 */
function apiRequest(path, opts, ext) {
  ext = ext || {};
  opts = opts || {};
  var headers = Object.assign({"Content-Type": "application/json"}, opts.headers || {});
  var auth = readAuth();
  if (auth.token) headers.Authorization = auth.token;

  return fetch(CONFIG.API_BASE + path, Object.assign({}, opts, {headers: headers}))
    .then(function(response) {
      if (!response.ok) {
        return response.json().then(function(data) {
          var err = new Error(data && data.info ? data.info : "HTTP " + response.status);
          err.code = data && data.code ? data.code : String(response.status);
          err.status = response.status;
          throw err;
        }).catch(function(e) {
          if (e instanceof Error && e.code) throw e;
          var err = new Error("HTTP " + response.status + " " + response.statusText);
          err.code = String(response.status);
          err.status = response.status;
          throw err;
        });
      }
      return response.json().catch(function() {
        var err = new Error("响应解析失败");
        err.code = "PARSE_ERR";
        throw err;
      });
    })
    .then(function(data) {
      if (data.code !== "0000") {
        // Token 已过期或无效。
        if (data.code === "0009" && ext.onAuthExpired) {
          ext.onAuthExpired(data);
        }
        var err = new Error(data.info || "业务错误 (" + data.code + ")");
        err.code = data.code;
        err.raw = data;
        throw err;
      }
      return data;
    });
}

// ===== 提示消息 =====
function toast(msg) {
  var el = document.getElementById("toast");
  if (!el) return;
  el.textContent = msg;
  el.classList.add("show");
  clearTimeout(el._timer);
  el._timer = setTimeout(function() { el.classList.remove("show"); }, 2200);
}

// ===== HTML 转义 =====
function esc(v) {
  return String(v).replace(/[&<>"'`]/g, function(c) {
    return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;","`":"&#96;"}[c];
  });
}

// ===== randomUUID 兼容实现 =====
window.crypto = window.crypto || {};
if (!window.crypto.randomUUID) {
  window.crypto.randomUUID = function() {
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function(c) {
      var r = Math.random() * 16 | 0;
      return (c === "x" ? r : (r & 0x3 | 0x8)).toString(16);
    });
  };
}
