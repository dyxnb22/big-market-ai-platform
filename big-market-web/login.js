const API_BASE = "http://127.0.0.1:8098/api/v1";
const AUTH_KEY = "lucky-draw-auth";

const userIdInput = document.getElementById("userIdInput");
const passwordInput = document.getElementById("passwordInput");
const loginBtn = document.getElementById("loginBtn");
const toastNode = document.getElementById("toast");

function toast(message) {
  toastNode.textContent = message;
  toastNode.classList.add("show");
  clearTimeout(toastNode._timer);
  toastNode._timer = setTimeout(() => toastNode.classList.remove("show"), 2200);
}

async function login() {
  const userId = userIdInput.value.trim();
  if (!userId) { toast("请输入用户 ID"); return; }

  loginBtn.disabled = true;
  loginBtn.textContent = "登录中...";
  try {
    const response = await fetch(API_BASE + "/auth/login", {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({userId: userId, password: passwordInput.value})
    });
    const data = await response.json();
    if (data.code !== "0000" || !data.data?.token) throw new Error(data.info || "登录失败");

    // Store auth — same key as app.js uses
    localStorage.setItem(AUTH_KEY, JSON.stringify({
      token: data.data.token,
      userId: data.data.userId
    }));

    toast("登录成功，正在跳转...");
    setTimeout(() => { location.href = "./index.html"; }, 400);
  } catch (error) {
    toast(error.message);
    loginBtn.disabled = false;
    loginBtn.textContent = "登 录";
  }
}

loginBtn.addEventListener("click", login);
userIdInput.addEventListener("keydown", e => { if (e.key === "Enter") passwordInput.focus(); });
passwordInput.addEventListener("keydown", e => { if (e.key === "Enter") login(); });
userIdInput.focus();
