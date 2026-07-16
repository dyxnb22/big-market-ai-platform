# Business flows

## Auth

1. `POST /api/v1/auth/login` → JWT  
2. `GET /api/v1/auth/verify`  
3. `POST /api/v1/auth/logout` → revoke `jti` (memory or Redis)

Demo users: `xiaofuge` / `demo`, `admin` / `admin` (override with `BM_DEV_USERS`).

## Stage → armory → SKU → draw → credit

1. Resolve stage activity: `query_stage_activity_id?channel=c01&source=s01` → `100401`  
2. Admin `armory` seeds soft stock  
3. `credit_pay_exchange_sku_by_token` — debit credit (`out_business_no={user}_{sku}_{requestId}`), add quota  
4. `draw_by_token` — consume quota, pick award (lock rules + weights), write award record, enqueue send_award  
5. Worker: ingest outbox → `credit_award_task` pending → `dispatch_pending` credits account (idempotent on `award_order_id`)

**Important:** award record `completed` ≠ credit landed. Proof is dispatched task / credit ledger.

## Award list

`query_raffle_award_list_by_token` returns lock fields: `awardRuleLockCount`, `isAwardUnlock`, `waitUnLockCount`.

## Calendar sign

`calendar_sign_rebate_by_token` — once per day, +1.00 credit, optional rebate outbox.

## Chat billing

1. Deduct with `requestId` (idempotent)  
2. Refund / reconcile via pending refund state + worker `chat_reconcile`  
3. Chatbot `ask` uses the same debit path (local echo replies)

## Admin / display

- Public display config for activity copy/state  
- ERP stage list activate/expire  
- Config list/update for operators
