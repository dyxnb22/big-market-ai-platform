# 11 复核轮次与最终检查

# Review Round 1

## Scope

宏观发现：父 POM、模块、启动器、application yml、controller、domain、repository、listener、job、gateway、现有学习文档。

## Findings

- 旧学习文档没有覆盖当前多服务过渡态。
- 旧结构缺少 URL request flow、auth、concurrency、resilience、review report。
- 部分接口是内部方法，不应写成外部 URL。

## Decision

Fix now: 重建学习文档结构，先稳定架构和 URL 结论。

## Changes Made

新增 README、00、01、03、04、08、09；删除旧 01-06 文档。

## Documentation Updated

`docs/learning/*`

## Verification

使用 `rg`、`find`、`sed` 检查 controller、POM、配置、job/listener。

## Remaining Issues

业务流、鉴权、并发、失败、高风险整改文档待补齐。

# Review Round 2

## Scope

微观核对：`RaffleActivityController`、`RaffleStrategyController`、`AuthAccessController`、`ChatbotController`、`ErpOperateController`、`DCCController`、`RaffleApplicationService`、`ActivityRepository`、`CreditRepository`、`AwardRepository`、`BehaviorRebateRepository`。

## Findings

- 发现已有安全整改：token 版本接口覆盖 request userId，非 token 版本未暴露 `@RequestMapping`。
- 发现已有补偿机制：抽奖失败回滚额度、MQ task 补偿、DLQ、AI 失败退积分。
- 发现远程服务切换默认 false，不适合本轮直接启用。

## Decision

Fix now: 文档准确反映已整改与保留风险。High-risk phase: 只制定计划，不启用远程写或 outbox flag。

## Changes Made

新增 02、05、06、07、10。

## Documentation Updated

业务流、鉴权、并发、失败、问题整改文档。

## Verification

人工对照代码路径、注解、配置开关、MQ/job 实现。

## Remaining Issues

最终检查和高风险计划待写入。

# High-risk Remediation Phase

## Reviewed

远程 quota decrement、award credit outbox、服务数据所有权拆分、DLQ 自动重放、真实用户体系。

## Planned

详见 [12-risky-changes-and-final-remediation.md](12-risky-changes-and-final-remediation.md)。

## Changed

未做高风险代码切换；只做文档稳定化。原因：当前仓库有 proposed SQL 和 flag，但没有正在运行的完整基础设施验证环境；贸然启用会改变核心交易链路。

## Verification

通过静态检查确认 flag 默认 false，相关能力标为“未启用/计划”。

# Final Round

## Scope

复核全部学习文档是否覆盖要求：URL、业务、架构、边界、认证、并发、失败、技术栈、问题、整改、高风险、验证。

## Findings

未发现新的 P0/P1。未发现新的文档-代码矛盾。

## Decision

停止迭代，进入最终验证。

## Changes Made

新增 11、12 并补齐最终检查。

追加前端 E2E 整改后，更新验证记录：恢复用户端抽奖 drawer 的签到/兑换 DOM 和移动端入口，修正普通用户访问 admin 的回跳行为，修正 Python dev server 的资产版本占位符替换。

## Documentation Updated

`docs/learning/11-review-rounds-and-final-check.md`、`docs/learning/12-risky-changes-and-final-remediation.md`

## Verification

已运行：

- `find docs/learning -maxdepth 1 -type f | sort`
- `rg -n 'mermaid|Not found in current code|No real implementation found|Review Round|Final Stability Check' docs/learning`
- `mvn -q -DskipTests compile`
- `npm test -- --reporter=list` 初次运行：9 passed / 8 failed
- `E2E_BASE_URL=http://127.0.0.1:5174 npm test -- --reporter=list` 修复后运行

结果：

- 文档结构和关键词检查通过。
- Maven compile 通过。
- Playwright E2E 初次运行 17 个测试，9 passed / 8 failed。失败点集中在前端/浏览器流程：普通用户访问 admin 的期望跳转、`#signInBtn` / `#mOpenLotteryBtn` 等元素等待超时、`#exchangeInfo` 缺失。
- 修复前端后，由于本机 5173 被 OrbStack 中的旧容器占用并服务旧资产，另起当前工作区 Python server 到 5174；使用 `E2E_BASE_URL=http://127.0.0.1:5174` 复验，17 passed。

# Final Stability Check

## New P0/P1 issues found in final review

None after frontend E2E remediation.

## Documentation-code contradictions found in final review

None found in the reviewed scope.

## Architecture misunderstanding found in final review

None. 文档已明确该项目是“单体启动器 + 多服务启动器 + 共享 jar + 暗启动远程边界”的混合过渡架构。

## Business flow diagram coverage

- 总业务流程
- 活动装配
- 签到返利
- 积分兑换 SKU
- 抽奖状态
- 发奖
- AI Chat

## URL flow diagram coverage

- 网关总流
- 登录
- 抽奖
- 签到返利
- 积分兑换
- AI Chat
- ERP/DCC/Admin

## High-risk changes completed

None. 本轮高风险阶段没有直接启用核心交易链路切换；前端 E2E 修复属于安全修复，不改变核心交易语义。

## High-risk changes not completed

- 远程 quota decrement：需要 ledger DDL、provider 实现验证、回滚演练。
- award credit outbox：需要 applied SQL 和 staging 验证。
- 服务数据所有权拆分：需要架构迁移，不适合本轮。
- DLQ 自动重放：需要产品/运维策略。
- 真实用户体系：需要新增业务需求。

## Why this version is stable enough for learning

文档已经以当前仓库为唯一证据源，覆盖主 URL、主业务、架构、模块边界、认证、并发、失败、技术栈、代码地图和问题整改。对未实现/未启用能力做了明确标记，避免学习时把计划当现实。
