/**
 * Shared configuration for big-market-web frontend.
 *
 * Development (python server.py):           API → http://127.0.0.1:8080/api/v1  (gateway)
 * Docker / nginx:                           Dockerfile pins API → /api/v1
 */
var CONFIG = {
  // DEV: direct to gateway. The Docker image rewrites this branch to same-origin.
  API_BASE: (function() {
    // Detect docker/nginx same-origin: if the page itself was served from port 80/443 or
    // by the nginx container (no port in host), use relative path.
    var port = location.port;
    if (!port || port === "80" || port === "443") return "/api/v1";
    return "http://127.0.0.1:8080/api/v1";
  })(),
  AUTH_KEY: "lucky-draw-auth",
  ACTIVITY_ID: 100301,
};
