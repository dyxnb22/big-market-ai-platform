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

async function login() {
  var userId = userIdInput.value.trim();
  if (!userId) { toast("请输入管理员 ID"); return; }

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
