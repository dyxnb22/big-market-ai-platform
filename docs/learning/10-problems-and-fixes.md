# 10 问题、错误笔记与整改结果

## P0 - Must fix now

本轮最终复核未发现新的 P0。

## P1 - Should fix in this task if possible

### P1-1 旧学习文档结构与当前代码不匹配

- Evidence: 原 `docs/learning` 只有 6 个文档，缺少 URL 流、鉴权、并发、失败、review 轮次、高风险整改文档。
- Why it matters: 学习时容易把过渡态服务化当成已完成微服务。
- Fix decision: Fix now.
- Fixed now: Yes.
- Change summary: 重建 `docs/learning` 结构，新增 00-12 文档。
- Verification: 人工核对 controller、service、repository、config、scripts。

### P1-2 公开/内部接口容易误读

- Evidence: `RaffleActivityController`、`RaffleStrategyController` 中保留多个实现接口的方法，但注释说明 removed `@RequestMapping`，只允许 token 版本入口调用。
- Why it matters: 学习文档若列出非 token 版本 URL，会误导为可被外部调用。
- Fix decision: Fix docs now.
- Fixed now: Yes.
- Change summary: URL 文档只列出实际 `@RequestMapping` 暴露的 token 版本接口，并说明内部方法。

### P1-3 服务化暗启动边界容易误判

- Evidence: 多个远程 adapter/provider 存在，但配置默认 `enabled:false`；API 注释也标明 scaffold/dark launch。
- Why it matters: 误以为 account/rebate/strategy/fulfillment 已完全承接主链路。
- Fix decision: Fix docs now.
- Fixed now: Yes.
- Change summary: 架构、边界、高风险文档明确标出“部分实现/默认未启用”。

### P1-4 前端 E2E 业务流回归失败

- Evidence: 初次验证中 Playwright 17 个测试 9 passed / 8 failed；失败集中在普通用户访问 admin 的跳转、移动端打开抽奖入口、签到按钮、兑换区 DOM、前端资产版本占位符。
- File path: `big-market-web/index.html`、`big-market-web/app.js`、`big-market-web/admin.js`、`big-market-web/server.py`
- Why it matters: 学习文档虽然稳定，但用户明天实际联调前端会遇到无法完成签到/移动抽奖/兑换区验证的问题。
- Severity: P1
- Fix decision: Fix now.
- Fixed now: Yes.
- Change summary: 恢复 lottery drawer 中 `signInBtn`、`signInStatus`、`exchangeInfo`、`exchangeBtn` DOM；新增移动端 `mOpenLotteryBtn`；修复 `app.js` 对这些节点的引用和事件绑定；普通用户访问 `admin.html` 时回到 `index.html` 并保留用户会话；Python dev server 为 HTML 替换 `__APP_VERSION__`。
- Verification: `E2E_BASE_URL=http://127.0.0.1:5174 npm test -- --reporter=list` 通过，17 passed。

## P2 - Documentation/learning improvement

### P2-1 缺少业务视角流程图

- Fixed now: Yes.
- Change summary: 新增签到、兑换、抽奖、发奖、AI Chat、运营配置业务图。

### P2-2 缺少失败/降级学习入口

- Fixed now: Yes.
- Change summary: 新增网关熔断、抽奖补偿、MQ 补偿、DLQ、AI 退积分说明。

### P2-3 缺少技术栈证据表

- Fixed now: Yes.
- Change summary: 新增技术栈表，并列明未发现技术。

## P3 - Future architecture improvement

### P3-1 数据所有权边界仍不彻底

- Evidence: 多个独立服务仍依赖 `big-market-infrastructure` 的共享 DAO/repository。
- Fix decision: Later.
- Fixed now: No.
- Follow-up suggestion: 在每个服务拆出独立 repository/DAO 所有权前，不开启高风险流量切换。

### P3-2 DLQ 只有日志监控，无自动重放

- Evidence: `RabbitMQDlqConfig` 的 DLQ consumer 只 log。
- Fix decision: Later.
- Fixed now: No.
- Follow-up suggestion: 设计人工审核/幂等重放工具。

### P3-3 生产用户体系不足

- Evidence: 登录账号来自 `app.auth.dev-users`。
- Fix decision: Later.
- Fixed now: No.
- Follow-up suggestion: 接入真实用户表、密码哈希、账号状态。

## 整改结果

- 已完成文档整改：学习索引、URL 流、业务流、架构、边界、认证、并发、失败、技术栈、代码地图、问题报告、复核记录、高风险计划。
- 已完成安全文档整改：不再把内部方法写成外部 URL；标注默认凭据保护和保留风险。
- 已完成前端 E2E 整改：管理员隔离、签到、移动端抽奖入口、兑换区、资产版本替换均已通过 Playwright 回归。
- 未执行高风险代码切换：远程 quota decrement、award credit outbox、服务数据所有权拆分均需要完整环境和数据库变更验证，本轮只记录计划。
