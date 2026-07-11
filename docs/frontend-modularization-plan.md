# Frontend Modularization Plan (`big-market-web`)

Status: plan only (GOV-D06). No risky split of page scripts in this pass.

## Current layout

| File | Role | Approx. size |
| --- | ---: | ---: |
| `config.js` | API base, auth key, feature flags | small |
| `api-client.js` | Shared `readAuth` / `saveAuth` / `clearAuth`, `apiRequest`, toast, `esc` | ~100 lines |
| `login-common.js` | Shared login form helpers | small |
| `login.js` / `admin-login.js` | Page-specific login | small |
| `app.js` | User app (wheel, rebate, chat, center) | ~930 lines |
| `admin.js` | Admin console | ~475 lines |

Script order on pages: `config.js` → `api-client.js` → page script. Globals are intentional (no bundler).

## Why not extract `auth.js` / `api.js` now

- Auth + API are **already** factored into `api-client.js`; renaming/splitting would force HTML include churn across four pages with little gain.
- Further carving `app.js` (draw / chat / credit) needs careful global and DOM coupling; high regression risk without Playwright coverage on every path.

## Recommended next steps (low → higher risk)

1. Keep `api-client.js` as the only shared network/auth module; document public globals in a short header comment (done in spirit today).
2. When touching a feature area, extract **one** cohesive helper file (e.g. `chat-ui.js` or `draw-wheel.js`) and add a Playwright smoke for that page before merging.
3. Defer npm/bundler/TypeScript until acceptance and BM closed-loop evidence are stable; static HTML/JS remains the product constraint.
4. Optional later: ES modules + `type="module"` only if all pages and `server.py` / nginx cache-busting are updated together.

## Non-goals

- React/Vite migration.
- CDN framework additions.
- Breaking BM-012 stage activity or BM-014 logout flows during refactors.
