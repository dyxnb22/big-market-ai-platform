/**
 * Shared API client & utilities for big-market-web frontend.
 * Pure JS, no framework — works with all three pages.
 */

// ===== Auth =====
function readAuth() {
  try {
    var v = localStorage.getItem(CONFIG.AUTH_KEY);
    return v ? JSON.parse(v) : {token: "", userId: ""};
  } catch (e) { return {token: "", userId: ""}; }
}

function saveAuth(token, userId) {
  localStorage.setItem(CONFIG.AUTH_KEY, JSON.stringify({token: token, userId: userId}));
}

function clearAuth() {
  localStorage.removeItem(CONFIG.AUTH_KEY);
}

// ===== Unified API Request =====
/**
 * Unified API request with consistent error handling.
 *
 * - HTTP non-2xx → throws Error with server message
 * - JSON parse failure → throws Error with status text
 * - Response code !== "0000" → throws Error with code + info
 * - Token expired / 0009 → optional onAuthExpired callback
 *
 * @param {string} path  e.g. "/auth/login"
 * @param {object} opts  fetch options (method, body, headers)
 * @param {object} [ext] {onAuthExpired: function}
 * @returns {Promise<object>} parsed response body (code === "0000")
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
        // Token expired or invalid
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

// ===== Toast =====
function toast(msg) {
  var el = document.getElementById("toast");
  if (!el) return;
  el.textContent = msg;
  el.classList.add("show");
  clearTimeout(el._timer);
  el._timer = setTimeout(function() { el.classList.remove("show"); }, 2200);
}

// ===== Escape HTML =====
function esc(v) {
  return String(v).replace(/[&<>"']/g, function(c) {
    return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c];
  });
}

// ===== randomUUID polyfill =====
if (!crypto.randomUUID) {
  crypto.randomUUID = function() {
    return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function(c) {
      var r = Math.random() * 16 | 0;
      return (c === "x" ? r : (r & 0x3 | 0x8)).toString(16);
    });
  };
}
