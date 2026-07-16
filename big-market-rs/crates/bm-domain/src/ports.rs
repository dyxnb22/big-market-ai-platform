use async_trait::async_trait;
use bm_types::{money, BmError, Money};
use chrono::{DateTime, Utc};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone)]
pub struct CreditAccount {
    pub user_id: String,
    pub total_amount: Money,
    pub available_amount: Money,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum TradeType {
    Forward,
    Reverse,
}

#[derive(Debug, Clone)]
pub struct CreditOrder {
    pub user_id: String,
    pub order_id: String,
    pub out_business_no: String,
    pub trade_name: String,
    pub trade_type: TradeType,
    pub trade_amount: Money,
}

#[async_trait]
pub trait CreditStore: Send + Sync {
    async fn get_balance(&self, user_id: &str) -> Result<Money, BmError>;
    async fn ensure_account(&self, user_id: &str, initial: Money) -> Result<(), BmError>;
    /// Idempotent credit/debit by out_business_no. Returns resulting available balance.
    async fn apply_trade(&self, order: CreditOrder) -> Result<Money, BmError>;
}

pub fn sku_out_business_no(user_id: &str, sku: i64, request_id: &str) -> String {
    format!("{user_id}_{sku}_{request_id}")
}

pub fn chat_out_business_no(user_id: &str, request_id: &str) -> String {
    format!("chat_{user_id}_{request_id}")
}

pub fn chat_refund_out_business_no(user_id: &str, request_id: &str) -> String {
    format!("chat_refund_{user_id}_{request_id}")
}

pub fn award_credit_amount_default() -> Money {
    money("5.00")
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActivityAccount {
    pub user_id: String,
    pub activity_id: i64,
    pub total_count: i32,
    pub total_count_surplus: i32,
    pub day_count: i32,
    pub day_count_surplus: i32,
    pub month_count: i32,
    pub month_count_surplus: i32,
}

impl ActivityAccount {
    pub fn empty(user_id: &str, activity_id: i64) -> Self {
        Self {
            user_id: user_id.into(),
            activity_id,
            total_count: 0,
            total_count_surplus: 0,
            day_count: 0,
            day_count_surplus: 0,
            month_count: 0,
            month_count_surplus: 0,
        }
    }
}

#[async_trait]
pub trait QuotaStore: Send + Sync {
    async fn get_account(
        &self,
        user_id: &str,
        activity_id: i64,
    ) -> Result<ActivityAccount, BmError>;
    async fn add_quota(
        &self,
        user_id: &str,
        activity_id: i64,
        count: i32,
        out_business_no: &str,
    ) -> Result<ActivityAccount, BmError>;
    async fn consume_one(
        &self,
        user_id: &str,
        activity_id: i64,
        out_business_no: &str,
    ) -> Result<(), BmError>;
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SkuProduct {
    pub sku: i64,
    pub activity_id: i64,
    pub product_name: String,
    pub product_amount: Money,
    pub quota_count: i32,
}

#[async_trait]
pub trait CatalogStore: Send + Sync {
    async fn stage_activity_id(&self, channel: &str, source: &str) -> Result<i64, BmError>;
    async fn list_sku(&self, activity_id: i64) -> Result<Vec<SkuProduct>, BmError>;
    async fn get_sku(&self, sku: i64) -> Result<SkuProduct, BmError>;
    async fn armory(&self, activity_id: i64) -> Result<bool, BmError>;
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum AwardTaskState {
    Pending,
    Dispatched,
    Failed,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreditAwardTask {
    pub user_id: String,
    pub award_order_id: String,
    pub credit_amount: Money,
    pub state: AwardTaskState,
    pub retry_count: u32,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserAwardRecord {
    pub user_id: String,
    pub activity_id: i64,
    pub order_id: String,
    pub award_id: i32,
    pub award_title: String,
    pub award_state: String,
}

#[async_trait]
pub trait AwardStore: Send + Sync {
    async fn save_award_record(&self, record: UserAwardRecord) -> Result<(), BmError>;
    async fn get_award_record(
        &self,
        user_id: &str,
        order_id: &str,
    ) -> Result<Option<UserAwardRecord>, BmError>;
    async fn enqueue_credit_award(&self, task: CreditAwardTask) -> Result<(), BmError>;
    async fn get_credit_award(
        &self,
        user_id: &str,
        award_order_id: &str,
    ) -> Result<Option<CreditAwardTask>, BmError>;
    async fn list_pending_credit_awards(
        &self,
        limit: usize,
    ) -> Result<Vec<CreditAwardTask>, BmError>;
    async fn mark_credit_award(
        &self,
        user_id: &str,
        award_order_id: &str,
        state: AwardTaskState,
    ) -> Result<(), BmError>;
    async fn enqueue_send_award_message(
        &self,
        user_id: &str,
        order_id: &str,
        award_id: i32,
        credit_amount: Money,
    ) -> Result<(), BmError>;
    async fn take_send_award_messages(
        &self,
        limit: usize,
    ) -> Result<Vec<SendAwardMessage>, BmError>;
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SendAwardMessage {
    pub user_id: String,
    pub order_id: String,
    pub award_id: i32,
    pub credit_amount: Money,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum RefundState {
    None,
    Pending,
    Refunding,
    Refunded,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChatCreditSession {
    pub user_id: String,
    pub request_id: String,
    pub amount: Money,
    pub refund_state: RefundState,
}

#[async_trait]
pub trait ChatStore: Send + Sync {
    async fn get_idempotent(
        &self,
        user_id: &str,
        request_id: &str,
    ) -> Result<Option<Money>, BmError>;
    async fn put_idempotent(
        &self,
        user_id: &str,
        request_id: &str,
        balance: Money,
    ) -> Result<(), BmError>;
    async fn record_deduction(&self, session: ChatCreditSession) -> Result<(), BmError>;
    async fn get_session(
        &self,
        user_id: &str,
        request_id: &str,
    ) -> Result<Option<ChatCreditSession>, BmError>;
    async fn set_refund_state(
        &self,
        user_id: &str,
        request_id: &str,
        state: RefundState,
    ) -> Result<(), BmError>;
    async fn list_pending_refunds(&self, limit: usize) -> Result<Vec<ChatCreditSession>, BmError>;
}

#[async_trait]
pub trait RebateStore: Send + Sync {
    async fn has_signed_today(&self, user_id: &str, day: &str) -> Result<bool, BmError>;
    async fn mark_signed(&self, user_id: &str, day: &str) -> Result<(), BmError>;
}

#[async_trait]
pub trait AdminStore: Send + Sync {
    async fn get(&self, key: &str) -> Result<Option<String>, BmError>;
    async fn set(&self, key: &str, value: &str) -> Result<(), BmError>;
    async fn list(&self) -> Result<Vec<(String, String)>, BmError>;
    async fn delete(&self, key: &str) -> Result<(), BmError>;
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ActivityStage {
    pub id: i64,
    pub channel: String,
    pub source: String,
    pub activity_id: i64,
    pub state: String,
}

#[async_trait]
pub trait StageStore: Send + Sync {
    async fn list_stages(&self) -> Result<Vec<ActivityStage>, BmError>;
    async fn set_stage_state(&self, id: i64, state: &str) -> Result<bool, BmError>;
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RebateMessage {
    pub user_id: String,
    pub day: String,
    pub amount: Money,
}

#[async_trait]
pub trait RebateOutbox: Send + Sync {
    async fn enqueue_rebate(&self, msg: RebateMessage) -> Result<(), BmError>;
    async fn take_rebate_messages(&self, limit: usize) -> Result<Vec<RebateMessage>, BmError>;
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserRaffleOrderView {
    pub user_id: String,
    pub activity_id: i64,
    pub order_id: String,
    pub award_id: i32,
    pub award_title: String,
}

#[async_trait]
pub trait OrderQueryStore: Send + Sync {
    async fn list_raffle_orders(&self, limit: usize) -> Result<Vec<UserRaffleOrderView>, BmError>;
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AwardWeight {
    pub award_id: i32,
    pub award_title: String,
    pub award_index: i32,
    pub weight: u32,
    pub credit_amount: Money,
    /// From `strategy_award.rule_models` (e.g. `tree_luck_award`, `tree_lock_3`).
    #[serde(default)]
    pub rule_model: Option<String>,
}

#[async_trait]
pub trait StrategyStore: Send + Sync {
    async fn award_weights(&self, activity_id: i64) -> Result<Vec<AwardWeight>, BmError>;
    /// Raw `strategy_rule.rule_value` for `rule_weight` (e.g. `60:102,103 200:106`), if any.
    async fn rule_weight_value(&self, activity_id: i64) -> Result<Option<String>, BmError>;
}

/// User participation signal for lock/unlock rules (prior completed draws).
#[async_trait]
pub trait ParticipationStore: Send + Sync {
    async fn count_draws(&self, user_id: &str, activity_id: i64) -> Result<i32, BmError>;
}

#[async_trait]
pub trait StockStore: Send + Sync {
    async fn get_stock(&self, key: &str) -> Result<i64, BmError>;
    async fn set_stock(&self, key: &str, qty: i64) -> Result<(), BmError>;
    /// Atomically decrement; returns false if insufficient.
    async fn decr_stock(&self, key: &str, delta: i64) -> Result<bool, BmError>;
    async fn list_dirty(&self) -> Result<Vec<(String, i64)>, BmError>;
    async fn clear_dirty(&self, keys: &[String]) -> Result<(), BmError>;
    /// Persist dirty soft-stock then clear. Returns number of keys flushed.
    async fn flush_dirty(&self) -> Result<usize, BmError>;
}

pub struct AppStores {
    // marker for grouping — actual wiring in infra/app
}

pub fn sku_price_9901() -> Decimal {
    money("5.00")
}

pub fn activity_stock_key(activity_id: i64) -> String {
    format!("activity_stock:{activity_id}")
}

pub fn award_stock_key(award_id: i32) -> String {
    format!("award_stock:{award_id}")
}
