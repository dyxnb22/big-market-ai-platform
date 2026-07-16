use crate::state::AppState;
use axum::extract::{Query, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::IntoResponse;
use axum::routing::{get, post};
use axum::{Json, Router};
use bm_api::*;
use bm_domain::DEFAULT_ACTIVITY_ID;
use bm_types::{ApiResponse, BmError, CODE_LOGIN_ERROR, CODE_PERMISSION_DENIED, INFO_LOGIN_ERROR};
use chrono::Utc;
use rust_decimal::Decimal;
use serde::Deserialize;
use sha2::{Digest, Sha256};

pub fn routes(state: AppState) -> Router {
    Router::new()
        .route("/health", get(health))
        .route("/actuator/health", get(health))
        // auth
        .route("/api/v1/auth/login", post(login))
        .route("/api/v1/auth/verify", get(verify))
        .route("/api/v1/auth/logout", post(logout))
        // activity
        .route(
            "/api/v1/raffle/activity/query_stage_activity_id",
            get(stage_activity),
        )
        .route("/api/v1/raffle/activity/armory", get(armory))
        .route("/api/v1/raffle/activity/draw_by_token", post(draw_by_token))
        .route(
            "/api/v1/raffle/activity/query_user_activity_account_by_token",
            post(query_account),
        )
        .route(
            "/api/v1/raffle/activity/query_user_credit_account_by_token",
            post(query_credit),
        )
        .route(
            "/api/v1/raffle/activity/query_sku_product_list_by_activity_id",
            post(list_sku),
        )
        .route(
            "/api/v1/raffle/activity/credit_pay_exchange_sku_by_token",
            post(exchange_sku),
        )
        .route(
            "/api/v1/raffle/activity/calendar_sign_rebate_by_token",
            post(sign_in),
        )
        .route(
            "/api/v1/raffle/activity/is_calendar_sign_rebate_by_token",
            post(is_signed),
        )
        .route(
            "/api/v1/raffle/activity/chat_credit_deduct_by_token",
            post(chat_deduct),
        )
        .route("/api/v1/raffle/activity/query_stock", get(query_stock))
        // strategy
        .route(
            "/api/v1/raffle/strategy/strategy_armory",
            get(strategy_armory),
        )
        .route(
            "/api/v1/raffle/strategy/query_raffle_award_list_by_token",
            post(query_award_list),
        )
        // erp
        .route(
            "/api/v1/raffle/erp/query_raffle_activity_stage_list",
            get(erp_stage_list),
        )
        .route(
            "/api/v1/raffle/erp/update_stage_activity_2_active",
            post(erp_stage_active),
        )
        .route(
            "/api/v1/raffle/erp/update_stage_activity_2_expire",
            post(erp_stage_expire),
        )
        .route(
            "/api/v1/raffle/erp/query_user_raffle_order",
            get(erp_orders),
        )
        // dcc (Java path)
        .route(
            "/api/v1/raffle/dcc/update_config",
            get(dcc_update_get).post(dcc_update_post),
        )
        .route("/api/v1/dcc/value", get(dcc_get))
        // internal chat
        .route(
            "/api/v1/internal/raffle/activity/chat_credit_refund_by_token",
            post(chat_refund_internal),
        )
        .route(
            "/api/v1/internal/raffle/activity/chat_credit_mark_refund_pending_by_token",
            post(chat_mark_pending),
        )
        .route("/api/v1/internal/worker/tick", post(worker_tick))
        // admin
        .route("/api/v1/admin/config/list", get(admin_list))
        .route("/api/v1/admin/config/get", get(admin_get))
        .route("/api/v1/admin/config/save", post(admin_save))
        .route("/api/v1/admin/config/delete", post(admin_delete))
        .route(
            "/api/v1/admin/config/public/display",
            get(admin_public_display),
        )
        .route("/api/v1/admin/config", get(admin_list).post(admin_save_legacy))
        // chatbot
        .route("/api/v1/chatbot/ask", post(chatbot_ask))
        .route("/api/v1/chatbot/health", get(health))
        .with_state(state)
}

async fn health() -> impl IntoResponse {
    Json(serde_json::json!({"status":"UP"}))
}

async fn login(State(state): State<AppState>, Json(req): Json<LoginRequest>) -> impl IntoResponse {
    match state.auth.login(&req.user_id, &req.password).await {
        Ok((token, expires_in)) => Json(ApiResponse::ok(LoginResponse {
            user_id: req.user_id,
            token,
            expires_in,
        }))
        .into_response(),
        Err(BmError::Unauthorized(_)) => Json(ApiResponse::<LoginResponse>::err(
            CODE_LOGIN_ERROR,
            INFO_LOGIN_ERROR,
        ))
        .into_response(),
        Err(e) => err_response(e),
    }
}

async fn verify(State(state): State<AppState>, headers: HeaderMap) -> impl IntoResponse {
    match state.auth.verify(&auth_header(&headers)).await {
        Ok(open_id) => Json(ApiResponse::ok(open_id)).into_response(),
        Err(_) => (
            StatusCode::UNAUTHORIZED,
            Json(ApiResponse::<String>::err(CODE_LOGIN_ERROR, "Token校验失败")),
        )
            .into_response(),
    }
}

async fn logout(State(state): State<AppState>, headers: HeaderMap) -> impl IntoResponse {
    let _ = state.auth.logout(&auth_header(&headers)).await;
    Json(ApiResponse::ok(true)).into_response()
}

#[derive(Deserialize)]
struct StageQuery {
    channel: Option<String>,
    source: Option<String>,
}

async fn stage_activity(
    State(state): State<AppState>,
    Query(q): Query<StageQuery>,
) -> impl IntoResponse {
    let channel = q.channel.unwrap_or_else(|| "c01".into());
    let source = q.source.unwrap_or_else(|| "s01".into());
    match state.raffle.stage_activity_id(&channel, &source).await {
        Ok(id) => Json(ApiResponse::ok(id)).into_response(),
        Err(e) => err_response(e),
    }
}

#[derive(Deserialize)]
struct ActivityQuery {
    #[serde(rename = "activityId")]
    activity_id: i64,
}

async fn armory(
    State(state): State<AppState>,
    Query(q): Query<ActivityQuery>,
) -> impl IntoResponse {
    match state.raffle.armory(q.activity_id).await {
        Ok(v) => Json(ApiResponse::ok(v)).into_response(),
        Err(e) => err_response(e),
    }
}

async fn draw_by_token(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<ActivityDrawRequest>,
) -> impl IntoResponse {
    let user = match require_user(&state, &headers).await {
        Ok(u) => u,
        Err(r) => return r,
    };
    if let Ok(ms) = std::env::var("BM_DEV_SLOW_DRAW_MS") {
        if let Ok(n) = ms.parse::<u64>() {
            if n > 0 {
                tokio::time::sleep(std::time::Duration::from_millis(n)).await;
            }
        }
    }
    match state.raffle.draw(&user, req.activity_id).await {
        Ok(d) => Json(ApiResponse::ok(ActivityDrawResponse {
            award_id: d.award_id,
            award_title: d.award_title,
            award_index: d.award_index,
        }))
        .into_response(),
        Err(e) => err_response(e),
    }
}

async fn query_account(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<ActivityAccountRequest>,
) -> impl IntoResponse {
    let user = match require_user(&state, &headers).await {
        Ok(u) => u,
        Err(r) => return r,
    };
    let activity_id = req.activity_id.unwrap_or(DEFAULT_ACTIVITY_ID);
    match state.raffle.query_account(&user, activity_id).await {
        Ok(a) => Json(ApiResponse::ok(UserActivityAccountResponse {
            total_count: a.total_count,
            total_count_surplus: a.total_count_surplus,
            day_count: a.day_count,
            day_count_surplus: a.day_count_surplus,
            month_count: a.month_count,
            month_count_surplus: a.month_count_surplus,
        }))
        .into_response(),
        Err(e) => err_response(e),
    }
}

async fn query_credit(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(_req): Json<serde_json::Value>,
) -> impl IntoResponse {
    let user = match require_user(&state, &headers).await {
        Ok(u) => u,
        Err(r) => return r,
    };
    match state.raffle.query_credit(&user).await {
        Ok(b) => Json(ApiResponse::ok(b)).into_response(),
        Err(e) => err_response(e),
    }
}

async fn list_sku(
    State(state): State<AppState>,
    Query(q): Query<ActivityQuery>,
) -> impl IntoResponse {
    match state.raffle.list_sku(q.activity_id).await {
        Ok(list) => {
            let data: Vec<SkuProductResponse> = list
                .into_iter()
                .map(|s| SkuProductResponse {
                    sku: s.sku,
                    activity_id: s.activity_id,
                    product_name: s.product_name,
                    product_amount: s.product_amount,
                    quota_count: s.quota_count,
                })
                .collect();
            Json(ApiResponse::ok(data)).into_response()
        }
        Err(e) => err_response(e),
    }
}

async fn exchange_sku(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<SkuExchangeRequest>,
) -> impl IntoResponse {
    let user = match require_user(&state, &headers).await {
        Ok(u) => u,
        Err(r) => return r,
    };
    match state
        .raffle
        .exchange_sku(&user, req.sku, &req.request_id)
        .await
    {
        Ok(v) => Json(ApiResponse::ok(v)).into_response(),
        Err(e) => err_response(e),
    }
}

async fn sign_in(State(state): State<AppState>, headers: HeaderMap) -> impl IntoResponse {
    let user = match require_user(&state, &headers).await {
        Ok(u) => u,
        Err(r) => return r,
    };
    match state.rebate.calendar_sign(&user).await {
        Ok((signed_today, reward, bal)) => Json(ApiResponse::ok(SignInResponse {
            signed_today,
            reward_credit: reward,
            credit_balance: bal,
            message: if signed_today {
                "今日已签到".into()
            } else {
                "签到成功".into()
            },
        }))
        .into_response(),
        Err(e) => err_response(e),
    }
}

async fn is_signed(State(state): State<AppState>, headers: HeaderMap) -> impl IntoResponse {
    let user = match require_user(&state, &headers).await {
        Ok(u) => u,
        Err(r) => return r,
    };
    match state.rebate.is_signed_today(&user).await {
        Ok(v) => Json(ApiResponse::ok(v)).into_response(),
        Err(e) => err_response(e),
    }
}

#[derive(Deserialize)]
struct ChatQuery {
    amount: Option<Decimal>,
    #[serde(rename = "requestId")]
    request_id: String,
}

async fn chat_deduct(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(q): Query<ChatQuery>,
) -> impl IntoResponse {
    let user = match require_user(&state, &headers).await {
        Ok(u) => u,
        Err(r) => return r,
    };
    let amount = q.amount.unwrap_or(Decimal::ONE);
    match state.chat.deduct(&user, &q.request_id, amount).await {
        Ok(bal) => Json(ApiResponse::ok(bal)).into_response(),
        Err(e) => err_response(e),
    }
}

#[derive(Deserialize)]
struct StrategyQuery {
    #[serde(rename = "strategyId")]
    strategy_id: Option<i64>,
}

async fn strategy_armory(
    State(state): State<AppState>,
    Query(q): Query<StrategyQuery>,
) -> impl IntoResponse {
    let activity_id = q.strategy_id.unwrap_or(100401);
    // Treat strategyId ~= activity for learning demo.
    match state.raffle.armory(activity_id).await {
        Ok(v) => Json(ApiResponse::ok(v)).into_response(),
        Err(e) => err_response(e),
    }
}

async fn query_award_list(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<RaffleAwardListRequest>,
) -> impl IntoResponse {
    if let Err(r) = require_user(&state, &headers).await {
        return r;
    }
    match state.strategy.award_weights(req.activity_id).await {
        Ok(weights) => {
            let data: Vec<RaffleAwardListResponse> = weights
                .into_iter()
                .map(|w| RaffleAwardListResponse {
                    award_id: w.award_id,
                    award_title: w.award_title,
                    award_subtitle: String::new(),
                    sort: w.award_index,
                    award_rule_lock_count: None,
                    is_award_unlock: true,
                    wait_unlock_count: None,
                })
                .collect();
            Json(ApiResponse::ok(data)).into_response()
        }
        Err(e) => err_response(e),
    }
}

async fn erp_stage_list(State(state): State<AppState>) -> impl IntoResponse {
    match state.stages.list_stages().await {
        Ok(list) => {
            let data: Vec<RaffleActivityStageResponse> = list
                .into_iter()
                .map(|s| RaffleActivityStageResponse {
                    id: s.id,
                    channel: s.channel,
                    source: s.source,
                    activity_id: s.activity_id,
                    state: s.state,
                })
                .collect();
            Json(ApiResponse::ok(data)).into_response()
        }
        Err(e) => err_response(e),
    }
}

async fn erp_stage_active(
    State(state): State<AppState>,
    Json(req): Json<UpdateStageRequest>,
) -> impl IntoResponse {
    match state.stages.set_stage_state(req.id, "active").await {
        Ok(v) => Json(ApiResponse::ok(v)).into_response(),
        Err(e) => err_response(e),
    }
}

async fn erp_stage_expire(
    State(state): State<AppState>,
    Json(req): Json<UpdateStageRequest>,
) -> impl IntoResponse {
    match state.stages.set_stage_state(req.id, "expire").await {
        Ok(v) => Json(ApiResponse::ok(v)).into_response(),
        Err(e) => err_response(e),
    }
}

async fn erp_orders(State(state): State<AppState>) -> impl IntoResponse {
    match state.orders.list_raffle_orders(50).await {
        Ok(list) => {
            let data: Vec<EsUserRaffleOrderResponse> = list
                .into_iter()
                .map(|o| EsUserRaffleOrderResponse {
                    user_id: o.user_id,
                    activity_id: o.activity_id,
                    order_id: o.order_id,
                    award_id: o.award_id,
                    award_title: o.award_title,
                })
                .collect();
            Json(ApiResponse::ok(data)).into_response()
        }
        Err(e) => err_response(e),
    }
}

fn admin_key(ns: &str, key: &str) -> String {
    format!("{ns}::{key}")
}

fn to_admin_dto(ns: &str, key: &str, value: &str) -> AdminConfigResponse {
    let mut hasher = Sha256::new();
    hasher.update(value.as_bytes());
    let hash = format!("{:x}", hasher.finalize());
    AdminConfigResponse {
        namespace: ns.into(),
        config_key: key.into(),
        config_value: value.into(),
        description: String::new(),
        update_time: Utc::now().timestamp_millis(),
        content_hash: hash.chars().take(16).collect(),
        nacos_published: false,
        source: "local".into(),
    }
}

#[derive(Deserialize)]
struct AdminNsQuery {
    namespace: Option<String>,
}

async fn admin_list(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(q): Query<AdminNsQuery>,
) -> impl IntoResponse {
    let user = match require_user(&state, &headers).await {
        Ok(u) => u,
        Err(r) => return r,
    };
    if user != "admin" {
        return (
            StatusCode::FORBIDDEN,
            Json(ApiResponse::<Vec<AdminConfigResponse>>::err(CODE_PERMISSION_DENIED, "forbidden")),
        )
            .into_response();
    }
    match state.admin.list().await {
        Ok(list) => {
            let data: Vec<AdminConfigResponse> = list
                .into_iter()
                .filter_map(|(k, v)| {
                    let (ns, key) = k.split_once("::")?;
                    if let Some(filter) = &q.namespace {
                        if ns != filter {
                            return None;
                        }
                    }
                    Some(to_admin_dto(ns, key, &v))
                })
                .collect();
            Json(ApiResponse::ok(data)).into_response()
        }
        Err(e) => err_response(e),
    }
}

#[derive(Deserialize)]
struct AdminGetQuery {
    namespace: String,
    #[serde(rename = "configKey")]
    config_key: String,
}

async fn admin_get(
    State(state): State<AppState>,
    headers: HeaderMap,
    Query(q): Query<AdminGetQuery>,
) -> impl IntoResponse {
    let user = match require_user(&state, &headers).await {
        Ok(u) => u,
        Err(r) => return r,
    };
    if user != "admin" {
        return (
            StatusCode::FORBIDDEN,
            Json(ApiResponse::<AdminConfigResponse>::err(CODE_PERMISSION_DENIED, "forbidden")),
        )
            .into_response();
    }
    let key = admin_key(&q.namespace, &q.config_key);
    match state.admin.get(&key).await {
        Ok(Some(v)) => Json(ApiResponse::ok(to_admin_dto(&q.namespace, &q.config_key, &v)))
            .into_response(),
        Ok(None) => Json(ApiResponse::ok(to_admin_dto(
            &q.namespace,
            &q.config_key,
            "",
        )))
        .into_response(),
        Err(e) => err_response(e),
    }
}

async fn admin_save(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<AdminConfigRequest>,
) -> impl IntoResponse {
    let user = match require_user(&state, &headers).await {
        Ok(u) => u,
        Err(r) => return r,
    };
    if user != "admin" {
        return (
            StatusCode::FORBIDDEN,
            Json(ApiResponse::<AdminConfigResponse>::err(CODE_PERMISSION_DENIED, "forbidden")),
        )
            .into_response();
    }
    let value = req.config_value.unwrap_or_default();
    let key = admin_key(&req.namespace, &req.config_key);
    match state.admin.set(&key, &value).await {
        Ok(()) => Json(ApiResponse::ok(to_admin_dto(
            &req.namespace,
            &req.config_key,
            &value,
        )))
        .into_response(),
        Err(e) => err_response(e),
    }
}

async fn admin_save_legacy(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<AdminConfigUpsert>,
) -> impl IntoResponse {
    let user = match require_user(&state, &headers).await {
        Ok(u) => u,
        Err(r) => return r,
    };
    if user != "admin" {
        return (
            StatusCode::FORBIDDEN,
            Json(ApiResponse::<bool>::err(CODE_PERMISSION_DENIED, "forbidden")),
        )
            .into_response();
    }
    match state.admin.set(&req.key, &req.value).await {
        Ok(()) => Json(ApiResponse::ok(true)).into_response(),
        Err(e) => err_response(e),
    }
}

async fn admin_delete(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<AdminConfigRequest>,
) -> impl IntoResponse {
    let user = match require_user(&state, &headers).await {
        Ok(u) => u,
        Err(r) => return r,
    };
    if user != "admin" {
        return (
            StatusCode::FORBIDDEN,
            Json(ApiResponse::<bool>::err(CODE_PERMISSION_DENIED, "forbidden")),
        )
            .into_response();
    }
    let key = admin_key(&req.namespace, &req.config_key);
    match state.admin.delete(&key).await {
        Ok(()) => Json(ApiResponse::ok(true)).into_response(),
        Err(e) => err_response(e),
    }
}

#[derive(Deserialize)]
struct DisplayQuery {
    #[serde(rename = "activityId")]
    activity_id: i64,
}

async fn admin_public_display(
    State(state): State<AppState>,
    Query(q): Query<DisplayQuery>,
) -> impl IntoResponse {
    let ns = format!("activity.{}", q.activity_id);
    let title = state
        .admin
        .get(&admin_key(&ns, "title"))
        .await
        .ok()
        .flatten()
        .unwrap_or_else(|| "幸运轮盘活动".into());
    let copy = state
        .admin
        .get(&admin_key(&ns, "copy"))
        .await
        .ok()
        .flatten()
        .unwrap_or_else(|| "登录参与抽奖，AI 帮你解读活动权益。".into());
    let st = state
        .admin
        .get(&admin_key(&ns, "state"))
        .await
        .ok()
        .flatten()
        .unwrap_or_else(|| "online".into());
    let chatbot_enabled = state
        .admin
        .get("chatbot::enabled")
        .await
        .ok()
        .flatten()
        .unwrap_or_else(|| "true".into());
    Json(ApiResponse::ok(ActivityDisplayConfigResponse {
        activity_id: q.activity_id,
        title,
        copy,
        state: st,
        chatbot_enabled: !chatbot_enabled.eq_ignore_ascii_case("false"),
    }))
    .into_response()
}

async fn chatbot_ask(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(req): Json<ChatbotAskRequest>,
) -> impl IntoResponse {
    let user = match require_user(&state, &headers).await {
        Ok(u) => u,
        Err(r) => return r,
    };
    if req.request_id.trim().is_empty() || req.message.trim().is_empty() {
        return err_response(BmError::IllegalParam("message/requestId required".into()));
    }
    match state
        .chatbot
        .ask(&user, &req.request_id, &req.message)
        .await
    {
        Ok((intent, tool, answer, success, deducted, bal)) => {
            Json(ApiResponse::ok(ChatbotAskResponse {
                intent,
                tool_name: tool,
                answer,
                success,
                data: None,
                credit_deducted: deducted,
                credit_balance: bal,
            }))
            .into_response()
        }
        Err(e) => err_response(e),
    }
}

#[derive(Deserialize)]
struct RefundBody {
    #[serde(rename = "userId")]
    user_id: String,
    #[serde(rename = "requestId")]
    request_id: String,
}

#[allow(clippy::result_large_err)]
fn require_internal(state: &AppState, headers: &HeaderMap) -> Result<(), axum::response::Response> {
    let token = headers
        .get("x-internal-token")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("");
    if token != state.cfg.internal_token {
        return Err((
            StatusCode::UNAUTHORIZED,
            Json(ApiResponse::<()>::err(CODE_LOGIN_ERROR, "unauthorized internal")),
        )
            .into_response());
    }
    Ok(())
}

async fn chat_refund_internal(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(body): Json<RefundBody>,
) -> impl IntoResponse {
    if let Err(r) = require_internal(&state, &headers) {
        return r;
    }
    match state.chat.refund(&body.user_id, &body.request_id).await {
        Ok(bal) => Json(ApiResponse::ok(bal)).into_response(),
        Err(e) => err_response(e),
    }
}

async fn chat_mark_pending(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(body): Json<RefundBody>,
) -> impl IntoResponse {
    if let Err(r) = require_internal(&state, &headers) {
        return r;
    }
    match state
        .chat
        .mark_refund_pending(&body.user_id, &body.request_id)
        .await
    {
        Ok(v) => Json(ApiResponse::ok(v)).into_response(),
        Err(e) => err_response(e),
    }
}

#[derive(Deserialize)]
struct DccBody {
    key: Option<String>,
    value: Option<String>,
}

async fn dcc_update_get(
    State(state): State<AppState>,
    Query(q): Query<DccBody>,
) -> impl IntoResponse {
    let key = q.key.unwrap_or_default();
    match state.admin.get(&key).await {
        Ok(Some(v)) => Json(ApiResponse::ok(v)).into_response(),
        Ok(None) => Json(ApiResponse::<String>::err("0001", "not found")).into_response(),
        Err(e) => err_response(e),
    }
}

async fn dcc_update_post(
    State(state): State<AppState>,
    Json(body): Json<DccBody>,
) -> impl IntoResponse {
    let key = body.key.unwrap_or_default();
    let value = body.value.unwrap_or_default();
    if key.is_empty() {
        return err_response(BmError::IllegalParam("key required".into()));
    }
    match state.admin.set(&key, &value).await {
        Ok(()) => Json(ApiResponse::ok(true)).into_response(),
        Err(e) => err_response(e),
    }
}

#[derive(Deserialize)]
struct DccQuery {
    key: String,
}

async fn dcc_get(State(state): State<AppState>, Query(q): Query<DccQuery>) -> impl IntoResponse {
    match state.admin.get(&q.key).await {
        Ok(Some(v)) => Json(ApiResponse::ok(v)).into_response(),
        Ok(None) => Json(ApiResponse::<String>::err("0001", "not found")).into_response(),
        Err(e) => err_response(e),
    }
}

async fn worker_tick(State(state): State<AppState>, headers: HeaderMap) -> impl IntoResponse {
    if let Err(r) = require_internal(&state, &headers) {
        return r;
    }
    let consumed = state.dispatch.consume_send_award(50).await.unwrap_or(0);
    let dispatched = state.dispatch.dispatch_pending(50).await.unwrap_or(0);
    let refunded = state.chat.reconcile_pending(20).await.unwrap_or(0);
    let rebates = state.rebate.consume_rebate(50).await.unwrap_or(0);
    Json(ApiResponse::ok(serde_json::json!({
        "consumed": consumed,
        "dispatched": dispatched,
        "refunded": refunded,
        "rebates": rebates,
    })))
    .into_response()
}

#[derive(Deserialize)]
struct StockQuery {
    key: String,
}

async fn query_stock(
    State(state): State<AppState>,
    Query(q): Query<StockQuery>,
) -> impl IntoResponse {
    match state.stock.get_stock(&q.key).await {
        Ok(v) => Json(ApiResponse::ok(v)).into_response(),
        Err(e) => err_response(e),
    }
}

fn auth_header(headers: &HeaderMap) -> String {
    headers
        .get(axum::http::header::AUTHORIZATION)
        .and_then(|v| v.to_str().ok())
        .unwrap_or("")
        .to_string()
}

#[allow(clippy::result_large_err)]
async fn require_user(
    state: &AppState,
    headers: &HeaderMap,
) -> Result<String, axum::response::Response> {
    state.auth.verify(&auth_header(headers)).await.map_err(|_| {
        (
            StatusCode::UNAUTHORIZED,
            Json(ApiResponse::<()>::err(CODE_LOGIN_ERROR, "Token校验失败")),
        )
            .into_response()
    })
}

fn err_response(e: BmError) -> axum::response::Response {
    let status = match &e {
        BmError::Unauthorized(_) => StatusCode::UNAUTHORIZED,
        BmError::IllegalParam(_) => StatusCode::BAD_REQUEST,
        _ => StatusCode::OK,
    };
    (status, Json(ApiResponse::<()>::err(e.code(), e.info()))).into_response()
}
