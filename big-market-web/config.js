/**
 * Shared configuration for big-market-web frontend.
 *
 * Development (python server.py):           API → http://127.0.0.1:8080/api/v1  (gateway)
 * Docker / nginx:                           Dockerfile pins API → /api/v1
 */
var CONFIG = {
  API_BASE: (function() {
    var port = location.port;
    if (!port || port === "80" || port === "443") return "/api/v1";
    return "http://127.0.0.1:8080/api/v1";
  })(),
  AUTH_KEY: "lucky-draw-auth",
  CHANNEL: "c01",
  SOURCE: "s01",
  /** 动态活动 ID 解析失败时的兜底值（与演示数据、管理端默认一致） */
  DEFAULT_ACTIVITY_ID: 100301,
  ACTIVITY_ID: 100301,
};
