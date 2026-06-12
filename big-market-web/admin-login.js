var userIdInput = document.getElementById("adminUserIdInput");
var passwordInput = document.getElementById("adminPasswordInput");
var loginBtn = document.getElementById("adminLoginBtn");

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
  return "./admin.html";
})();

var existingAuth = readAuth();
if (existingAuth.token) {
  verifyAdminToken(existingAuth.token).then(function() {
    location.replace(redirectUrl);
  }).catch(function(err) {
    // 0008 = valid token but not admin — do NOT clearAuth, just stay on login page
    var msg = err && err.message;
    if (msg && msg.indexOf("无管理员权限") >= 0) {
      toast("当前账号不是管理员，请使用管理员账号登录");
    } else {
      // 0009 or network error — token is unusable, clean up
      clearAuth();
    }
  });
}

function verifyAdminToken(token) {
  return fetch(CONFIG.API_BASE + "/admin/config/list", {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
      "Authorization": token
    }
  }).then(function(response) {
    return response.json().catch(function() { return {code: String(response.status), info: "管理员权限校验失败"}; });
  }).then(function(data) {
    if (data.code !== "0000") {
      throw new Error(data.code === "0008" ? "当前账号无管理员权限" : (data.info || "管理员权限校验失败"));
    }
    return data;
  });
}

async function login() {
  var userId = userIdInput.value.trim();
  if (!userId) { toast("请输入管理员 ID"); return; }
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

    await verifyAdminToken(data.data.token);

    saveAuth(data.data.token, data.data.userId);
    toast("登录成功，正在跳转...");
    setTimeout(function() { location.href = redirectUrl; }, 400);
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
