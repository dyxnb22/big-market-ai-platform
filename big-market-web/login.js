var userIdInput = document.getElementById("userIdInput");
var passwordInput = document.getElementById("passwordInput");
var loginBtn = document.getElementById("loginBtn");

// Read redirect from URL query; only allow same-origin destinations.
var redirectUrl = (function() {
  var p = new URLSearchParams(location.search);
  var r = p.get("redirect");
  if (r) {
    try {
      var url = new URL(r, location.href);
      if (url.origin === location.origin) return url.pathname + url.search + url.hash;
    } catch (e) {}
  }
  return "./index.html";
})();

var existingAuth = readAuth();
if (existingAuth.token) {
  apiRequest("/auth/verify", {}, {
    onAuthExpired: function() { clearAuth(); }
  }).then(function() {
    location.replace(redirectUrl);
  }).catch(function(e) {
    // onAuthExpired already handles 0009 (token expired).
    // For other API error codes, the token is unusable — clear it.
    // For network errors (no code), keep the token so the user isn't
    // logged out just because the server is temporarily unreachable.
    if (e.code) {
      clearAuth();
    }
  });
}

function withCacheBuster(url) {
  var sep = url.indexOf("?") >= 0 ? "&" : "?";
  return url + sep + "t=" + Date.now();
}

async function login() {
  var userId = userIdInput.value.trim();
  if (!userId) { toast("请输入用户 ID"); return; }
  if (!passwordInput.value) { toast("请输入密码"); passwordInput.focus(); return; }

  loginBtn.disabled = true;
  loginBtn.textContent = "登录中...";
  try {
    var data = await apiRequest("/auth/login", {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({userId: userId, password: passwordInput.value})
    });
    if (!data.data?.token) throw new Error(data.info || "登录失败");

    saveAuth(data.data.token, data.data.userId);
    toast("登录成功，正在跳转...");
    setTimeout(function() { location.href = withCacheBuster(redirectUrl); }, 400);
  } catch (error) {
    toast(error.message);
    loginBtn.disabled = false;
    loginBtn.textContent = "登 录";
  }
}

loginBtn.addEventListener("click", login);
userIdInput.addEventListener("keydown", function(e) { if (e.key === "Enter") passwordInput.focus(); });
passwordInput.addEventListener("keydown", function(e) { if (e.key === "Enter") login(); });
userIdInput.focus();
