use crate::state::AppState;
use axum::extract::{Query, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::IntoResponse;
use axum::routing::{get, post};
use axum::{Json, Router};
use bm_api::*;
use bm_domain::DEFAULT_ACTIVITY_ID;
use bm_types::{
    ApiResponse, BmError, CODE_LOGIN_ERROR, CODE_SUCCESS, INFO_LOGIN_ERROR, INFO_SUCCESS,
};
use rust_decimal::Decimal;
use serde::Deserialize;
use std::collections::HashMap;

pub fn routes(state: AppState) -> Router {
    Router::new()
        .route("/health", get(health))
        .route("/api/v1/auth/login", post(login))
        .route("/api/v1/auth/verify", get(verify))
        .route("/api/v1/auth/logout", post(logout))
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
        .route(
            "/api/v1/internal/raffle/activity/chat_credit_refund_by_token",
            post(chat_refund_internal),
        )
        .route("/api/v1/admin/config", get(admin_list).post(admin_upsert))
        .route("/api/v1/chatbot/health", get(health))
        .route("/api/v1/dcc/value", get(dcc_get))
        .route("/api/v1/internal/worker/tick", post(worker_tick))
        .route("/api/v1/raffle/activity/query_stock", get(query_stock))
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
    let token = auth_header(&headers);
    match state.auth.verify(&token).await {
        Ok(open_id) => Json(ApiResponse::ok(open_id)).into_response(),
        Err(_) => (
            StatusCode::UNAUTHORIZED,
            Json(ApiResponse::<String>::err(
                CODE_LOGIN_ERROR,
                "Token校验失败",
            )),
        )
            .into_response(),
    }
}

async fn logout(State(state): State<AppState>, headers: HeaderMap) -> impl IntoResponse {
    let token = auth_header(&headers);
    match state.auth.logout(&token).await {
        Ok(()) => Json(ApiResponse::ok(true)).into_response(),
        Err(BmError::Unauthorized(_)) => {
            // idempotent success on bad token for logout (Java returns success-ish)
            Json(ApiResponse::ok(true)).into_response()
        }
        Err(e) => err_response(e),
    }
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
struct RefundBody {
    #[serde(rename = "userId")]
    user_id: String,
    #[serde(rename = "requestId")]
    request_id: String,
}

async fn chat_refund_internal(
    State(state): State<AppState>,
    headers: HeaderMap,
    Json(body): Json<RefundBody>,
) -> impl IntoResponse {
    let token = headers
        .get("x-internal-token")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("");
    if token != state.cfg.internal_token {
        return (
            StatusCode::UNAUTHORIZED,
            Json(ApiResponse::<Decimal>::err(
                CODE_LOGIN_ERROR,
                "unauthorized internal",
            )),
        )
            .into_response();
    }
    match state.chat.refund(&body.user_id, &body.request_id).await {
        Ok(bal) => Json(ApiResponse::ok(bal)).into_response(),
        Err(e) => err_response(e),
    }
}

async fn admin_list(State(state): State<AppState>, headers: HeaderMap) -> impl IntoResponse {
    let user = match require_user(&state, &headers).await {
        Ok(u) => u,
        Err(r) => return r,
    };
    if user != "admin" {
        return (
            StatusCode::FORBIDDEN,
            Json(ApiResponse::<Vec<(String, String)>>::err(
                "0001",
                "forbidden",
            )),
        )
            .into_response();
    }
    match state.admin.list().await {
        Ok(list) => {
            let map: HashMap<String, String> = list.into_iter().collect();
            Json(ApiResponse::ok(map)).into_response()
        }
        Err(e) => err_response(e),
    }
}

async fn admin_upsert(
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
            Json(ApiResponse::<bool>::err("0001", "forbidden")),
        )
            .into_response();
    }
    match state.admin.set(&req.key, &req.value).await {
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
    let token = headers
        .get("x-internal-token")
        .and_then(|v| v.to_str().ok())
        .unwrap_or("");
    if token != state.cfg.internal_token {
        return (
            StatusCode::UNAUTHORIZED,
            Json(ApiResponse::<serde_json::Value>::err(
                CODE_LOGIN_ERROR,
                "unauthorized",
            )),
        )
            .into_response();
    }
    let consumed = state.dispatch.consume_send_award(50).await.unwrap_or(0);
    let dispatched = state.dispatch.dispatch_pending(50).await.unwrap_or(0);
    let refunded = state.chat.reconcile_pending(20).await.unwrap_or(0);
    Json(ApiResponse::ok(serde_json::json!({
        "consumed": consumed,
        "dispatched": dispatched,
        "refunded": refunded,
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

async fn require_user(
    state: &AppState,
    headers: &HeaderMap,
) -> Result<String, axum::response::Response> {
    let token = auth_header(headers);
    state.auth.verify(&token).await.map_err(|_| {
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
        BmError::InsufficientCredit => StatusCode::OK, // business code in body
        _ => StatusCode::OK,
    };
    (status, Json(ApiResponse::<()>::err(e.code(), e.info()))).into_response()
}

#[allow(dead_code)]
fn _success_meta() -> (&'static str, &'static str) {
    (CODE_SUCCESS, INFO_SUCCESS)
}
