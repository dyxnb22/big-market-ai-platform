/**
 * 用户登录页：已登录则校验 Token 后跳转；否则走 LoginCommon 密码登录。
 * 依赖：config.js → api-client.js → login-common.js
 */
const userIdInput = document.getElementById("userIdInput");
const passwordInput = document.getElementById("passwordInput");
const loginBtn = document.getElementById("loginBtn");

const redirectUrl = LoginCommon.parseRedirectUrl("./index.html");

// 已有有效 Token 时直接进入业务页，避免重复登录
const existingAuth = readAuth();
if (existingAuth.token) {
  apiRequest("/auth/verify", {}, {
    onAuthExpired: function() { clearAuth(); }
  }).then(function() {
    location.replace(redirectUrl);
  }).catch(function(e) {
    if (e.code) {
      clearAuth();
    }
  });
}

async function login() {
  if (!userIdInput.value.trim()) {
    toast("请输入用户 ID");
    return;
  }
  if (!passwordInput.value) {
    toast("请输入密码");
    return;
  }
  loginBtn.disabled = true;
  loginBtn.textContent = "登录中...";
  try {
    await LoginCommon.loginWithPassword(userIdInput.value.trim(), passwordInput.value);
    toast("登录成功，正在跳转...");
    setTimeout(function() { location.href = LoginCommon.withCacheBuster(redirectUrl); }, 400);
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
