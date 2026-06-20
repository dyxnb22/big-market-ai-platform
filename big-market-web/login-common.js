/**
 * Shared login helpers for user and admin login pages.
 */
(function(global) {
  function parseRedirectUrl(defaultPath) {
    var p = new URLSearchParams(location.search);
    var r = p.get("redirect");
    if (r) {
      try {
        var url = new URL(r, location.href);
        if (url.origin === location.origin) return url.pathname + url.search + url.hash;
      } catch (e) {}
    }
    return defaultPath;
  }

  function withCacheBuster(url) {
    var sep = url.indexOf("?") >= 0 ? "&" : "?";
    return url + sep + "t=" + Date.now();
  }

  async function loginWithPassword(userId, password, options) {
    options = options || {};
    if (!userId) throw new Error(options.emptyUserMessage || "请输入用户 ID");
    if (!password) throw new Error(options.emptyPasswordMessage || "请输入密码");
    var data = await apiRequest("/auth/login", {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({userId: userId, password: password})
    });
    if (!data.data?.token) throw new Error(data.info || "登录失败");
    if (options.verifyToken) {
      await options.verifyToken(data.data.token);
    }
    saveAuth(data.data.token, data.data.userId);
    return data;
  }

  global.LoginCommon = {
    parseRedirectUrl: parseRedirectUrl,
    withCacheBuster: withCacheBuster,
    loginWithPassword: loginWithPassword
  };
})(window);
