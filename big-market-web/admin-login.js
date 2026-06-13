var userIdInput = document.getElementById("adminUserIdInput");
var passwordInput = document.getElementById("adminPasswordInput");
var loginBtn = document.getElementById("adminLoginBtn");

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

// ?noperm=1 means admin.html redirected us because the stored token lacked admin privilege.
// In this case, do NOT auto-verify (would just loop back), show a helpful message instead.
var noPerm = new URLSearchParams(location.search).get("noperm") === "1";

function verifyAdminToken(token) {
  return fetch(CONFIG.API_BASE + "/admin/config/list", {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
      "Authorization": token
    }
  }).then(function(response) {
    return response.json().catch(function() {
      return {code: String(response.status), info: "管理员权限校验失败"};
    });
  }).then(function(data) {
    if (data.code !== "0000") {
      throw new Error(data.code === "0008" ? "当前账号无管理员权限" : (data.info || "管理员权限校验失败"));
    }
    return data;
  });
}

var existingAuth = readAuth();
if (!noPerm && existingAuth.token) {
  // Normal entry (not a redirect-from-noperm): auto-verify existing token.
  verifyAdminToken(existingAuth.token).then(function() {
    location.replace(redirectUrl);
  }).catch(function(err) {
    var msg = err && err.message;
    if (msg && msg.indexOf("无管理员权限") >= 0) {
      toast("当前账号不是管理员，请使用管理员账号登录");
    } else {
      // Token invalid/expired — clear it
      clearAuth();
    }
  });
} else if (noPerm && existingAuth.token) {
  // Redirected here because stored token has no admin role.
  toast("当前账号不是管理员，请使用管理员账号登录");
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

    // Verify admin privilege before saving
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
