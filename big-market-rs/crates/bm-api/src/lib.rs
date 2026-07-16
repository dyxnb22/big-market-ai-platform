//! HTTP DTOs aligned with Java big-market-api.

use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};

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
