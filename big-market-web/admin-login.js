/**
 * 管理员登录页：登录后额外调用 /admin/config/list 校验管理员权限。
 * 依赖：config.js → api-client.js → login-common.js
 */
const userIdInput = document.getElementById("adminUserIdInput");
const passwordInput = document.getElementById("adminPasswordInput");
const loginBtn = document.getElementById("adminLoginBtn");

const redirectUrl = LoginCommon.parseRedirectUrl("./admin.html");
const noPerm = new URLSearchParams(location.search).get("noperm") === "1";

/** 用管理端接口探测 Token 是否具备管理员权限（0008 = 无权限）。 */
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

const existingAuth = readAuth();
if (!noPerm && existingAuth.token) {
  verifyAdminToken(existingAuth.token).then(function() {
    location.replace(redirectUrl);
  }).catch(function(err) {
    const msg = err?.message;
    if (msg?.includes("无管理员权限")) {
      toast("当前账号不是管理员，请使用管理员账号登录");
    } else {
      clearAuth();
    }
  });
} else if (noPerm && existingAuth.token) {
  toast("当前账号不是管理员，请使用管理员账号登录");
}

async function login() {
  loginBtn.disabled = true;
  loginBtn.textContent = "登录中...";
  try {
    await LoginCommon.loginWithPassword(userIdInput.value.trim(), passwordInput.value, {
      emptyUserMessage: "请输入管理员 ID",
      verifyToken: verifyAdminToken
    });
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
