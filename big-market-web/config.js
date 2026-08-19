/**
 * big-market-web 前端共用配置。
 *
 * API_BASE 解析顺序：
 *   1. 端口 80/443（nginx/生产环境）→ /api/v1（同源反向代理）
 *   2. 设置 window.API_BASE_OVERRIDE → 使用覆盖值（测试环境注入）
 *   3. 非 localhost 主机名（IP/域名）→ /api/v1（避免跨域访问 127.0.0.1）
 *   4. localhost/127.0.0.1 加自定义端口 → http://127.0.0.1:8080/api/v1（python server.py 开发服务）
 */
var CONFIG = {
  API_BASE: (function() {
    var port = location.port;
    if (!port || port === "80" || port === "443") return "/api/v1";
    // 允许通过查询参数或全局对象动态重写 (适配不同测试环境)
    if (window.API_BASE_OVERRIDE) return window.API_BASE_OVERRIDE;
    // 如果是通过IP地址或非本地localhost直接访问的，尽量使用相对路径，避免写死127.0.0.1跨域
    if (location.hostname !== "127.0.0.1" && location.hostname !== "localhost") return "/api/v1";
    return "http://127.0.0.1:8080/api/v1";
  })(),
  AUTH_KEY: "lucky-draw-auth",
  CHANNEL: "c01",
  SOURCE: "s01",
  /** 动态活动 ID 解析失败时的兜底值；正常路径以 stage(c01/s01→100401) 为准 */
  DEFAULT_ACTIVITY_ID: 100401,
  ACTIVITY_ID: 100401,
};
