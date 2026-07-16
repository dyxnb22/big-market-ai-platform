//! HTTP DTOs HTTP DTOs for /api/v1.

use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Debug, Clone, Deserialize)]
pub struct LoginRequest {
    #[serde(rename = "userId")]
    pub user_id: String,
    pub password: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct LoginResponse {
    #[serde(rename = "userId")]
    pub user_id: String,
    pub token: String,
    #[serde(rename = "expiresIn")]
    pub expires_in: u64,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ActivityDrawRequest {
    #[serde(rename = "activityId")]
    pub activity_id: i64,
}

#[derive(Debug, Clone, Serialize)]
pub struct ActivityDrawResponse {
    #[serde(rename = "awardId")]
    pub award_id: i32,
    #[serde(rename = "awardTitle")]
    pub award_title: String,
    #[serde(rename = "awardIndex")]
    pub award_index: i32,
    #[serde(rename = "orderId")]
    pub order_id: String,
    #[serde(rename = "strategyTrace")]
    pub strategy_trace: StrategyTraceResponse,
}

#[derive(Debug, Clone, Serialize)]
pub struct StrategyTraceResponse {
    #[serde(rename = "priorDraws")]
    pub prior_draws: i32,
    #[serde(rename = "poolBefore")]
    pub pool_before: usize,
    #[serde(rename = "poolAfter")]
    pub pool_after: usize,
    #[serde(rename = "rulesApplied")]
    pub rules_applied: Vec<String>,
    #[serde(rename = "pickedRuleModel")]
    pub picked_rule_model: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CreditAwardTaskQuery {
    #[serde(rename = "awardOrderId")]
    pub award_order_id: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct CreditAwardTaskResponse {
    #[serde(rename = "awardOrderId")]
    pub award_order_id: String,
    #[serde(rename = "creditAmount")]
    pub credit_amount: Decimal,
    pub state: String,
    #[serde(rename = "retryCount")]
    pub retry_count: u32,
}

#[derive(Debug, Clone, Deserialize)]
pub struct SkuExchangeRequest {
    pub sku: i64,
    #[serde(rename = "requestId")]
    pub request_id: String,
}

#[derive(Debug, Clone, Deserialize, Default)]
pub struct ActivityAccountRequest {
    #[serde(rename = "activityId")]
    pub activity_id: Option<i64>,
}

#[derive(Debug, Clone, Serialize)]
pub struct UserActivityAccountResponse {
    #[serde(rename = "totalCount")]
    pub total_count: i32,
    #[serde(rename = "totalCountSurplus")]
    pub total_count_surplus: i32,
    #[serde(rename = "dayCount")]
    pub day_count: i32,
    #[serde(rename = "dayCountSurplus")]
    pub day_count_surplus: i32,
    #[serde(rename = "monthCount")]
    pub month_count: i32,
    #[serde(rename = "monthCountSurplus")]
    pub month_count_surplus: i32,
}

#[derive(Debug, Clone, Serialize)]
pub struct SignInResponse {
    #[serde(rename = "signedToday")]
    pub signed_today: bool,
    #[serde(rename = "rewardCredit")]
    pub reward_credit: Decimal,
    #[serde(rename = "creditBalance")]
    pub credit_balance: Decimal,
    pub message: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct SkuProductResponse {
    pub sku: i64,
    #[serde(rename = "activityId")]
    pub activity_id: i64,
    #[serde(rename = "productName")]
    pub product_name: String,
    #[serde(rename = "productAmount")]
    pub product_amount: Decimal,
    #[serde(rename = "quotaCount")]
    pub quota_count: i32,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ChatDeductQuery {
    pub amount: Option<Decimal>,
    #[serde(rename = "requestId")]
    pub request_id: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AdminConfigUpsert {
    pub key: String,
    pub value: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct AdminConfigRequest {
    pub namespace: String,
    #[serde(rename = "configKey")]
    pub config_key: String,
    #[serde(rename = "configValue")]
    pub config_value: Option<String>,
    pub description: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct AdminConfigResponse {
    pub namespace: String,
    #[serde(rename = "configKey")]
    pub config_key: String,
    #[serde(rename = "configValue")]
    pub config_value: String,
    pub description: String,
    #[serde(rename = "updateTime")]
    pub update_time: i64,
    #[serde(rename = "contentHash")]
    pub content_hash: String,
    #[serde(rename = "nacosPublished")]
    pub nacos_published: bool,
    pub source: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct ActivityDisplayConfigResponse {
    #[serde(rename = "activityId")]
    pub activity_id: i64,
    pub title: String,
    pub copy: String,
    pub state: String,
    #[serde(rename = "chatbotEnabled")]
    pub chatbot_enabled: bool,
}

#[derive(Debug, Clone, Deserialize)]
pub struct RaffleAwardListRequest {
    #[serde(rename = "userId")]
    pub user_id: Option<String>,
    #[serde(rename = "activityId")]
    pub activity_id: i64,
}

#[derive(Debug, Clone, Serialize)]
pub struct RaffleAwardListResponse {
    #[serde(rename = "awardId")]
    pub award_id: i32,
    #[serde(rename = "awardTitle")]
    pub award_title: String,
    #[serde(rename = "awardSubtitle")]
    pub award_subtitle: String,
    pub sort: i32,
    #[serde(rename = "awardRuleLockCount")]
    pub award_rule_lock_count: Option<i32>,
    #[serde(rename = "isAwardUnlock")]
    pub is_award_unlock: bool,
    #[serde(rename = "waitUnLockCount")]
    pub wait_unlock_count: Option<i32>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct RaffleStrategyRuleWeightRequest {
    #[serde(rename = "userId")]
    pub user_id: Option<String>,
    #[serde(rename = "activityId")]
    pub activity_id: i64,
}

#[derive(Debug, Clone, Serialize)]
pub struct RaffleStrategyRuleWeightAward {
    #[serde(rename = "awardId")]
    pub award_id: i32,
    #[serde(rename = "awardTitle")]
    pub award_title: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct RaffleStrategyRuleWeightResponse {
    #[serde(rename = "ruleWeightCount")]
    pub rule_weight_count: i32,
    #[serde(rename = "userActivityAccountTotalUseCount")]
    pub user_activity_account_total_use_count: i32,
    #[serde(rename = "strategyAwards")]
    pub strategy_awards: Vec<RaffleStrategyRuleWeightAward>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct ChatbotAskRequest {
    pub token: Option<String>,
    #[serde(rename = "userId")]
    pub user_id: Option<String>,
    #[serde(rename = "activityId")]
    pub activity_id: Option<i64>,
    pub message: String,
    #[serde(rename = "requestId")]
    pub request_id: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct ChatbotAskResponse {
    pub intent: String,
    #[serde(rename = "toolName")]
    pub tool_name: String,
    pub answer: String,
    pub success: bool,
    pub data: Option<Value>,
    #[serde(rename = "creditDeducted")]
    pub credit_deducted: Decimal,
    #[serde(rename = "creditBalance")]
    pub credit_balance: Decimal,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RaffleActivityStageResponse {
    pub id: i64,
    pub channel: String,
    pub source: String,
    #[serde(rename = "activityId")]
    pub activity_id: i64,
    pub state: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct UpdateStageRequest {
    pub id: i64,
}

#[derive(Debug, Clone, Serialize)]
pub struct EsUserRaffleOrderResponse {
    #[serde(rename = "userId")]
    pub user_id: String,
    #[serde(rename = "activityId")]
    pub activity_id: i64,
    #[serde(rename = "orderId")]
    pub order_id: String,
    #[serde(rename = "awardId")]
    pub award_id: i32,
    #[serde(rename = "awardTitle")]
    pub award_title: String,
}
