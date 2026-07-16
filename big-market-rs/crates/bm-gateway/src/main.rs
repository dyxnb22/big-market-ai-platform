use anyhow::Context;
use axum::body::Body;
use axum::extract::State;
use axum::http::{Request, Response, StatusCode, Uri};
use axum::routing::any;
use axum::{routing::get, Json, Router};
use bm_infra::GatewayConfig;
use std::net::SocketAddr;
use tower_http::cors::CorsLayer;
use tower_http::trace::TraceLayer;
use tracing_subscriber::EnvFilter;

#[derive(Clone)]
struct GwState {
    client: reqwest::Client,
    app_url: String,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("info".parse()?))
        .init();

    let cfg = GatewayConfig::default();
    // allow env override without full figment path issues
    let cfg = GatewayConfig {
        host: std::env::var("BM_GW_HOST").unwrap_or(cfg.host),
        port: std::env::var("BM_GW_PORT")
            .ok()
            .and_then(|p| p.parse().ok())
            .unwrap_or(cfg.port),
        app_url: std::env::var("BM_GW_APP_URL").unwrap_or(cfg.app_url),
        jwt_secret: std::env::var("BM_GW_JWT_SECRET").unwrap_or(cfg.jwt_secret),
    };

    let state = GwState {
        client: reqwest::Client::new(),
        app_url: cfg.app_url.clone(),
    };

    let app = Router::new()
        .route(
            "/health",
            get(|| async { Json(serde_json::json!({"status":"UP"})) }),
        )
        .fallback(any(proxy))
        .layer(CorsLayer::permissive())
        .layer(TraceLayer::new_for_http())
        .with_state(state);

    let addr: SocketAddr = format!("{}:{}", cfg.host, cfg.port).parse()?;
    tracing::info!(%addr, upstream=%cfg.app_url, "bm-gateway listening");
    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await.context("serve")?;
    Ok(())
}

async fn proxy(
    State(state): State<GwState>,
    req: Request<Body>,
) -> Result<Response<Body>, StatusCode> {
    let path_and_query = req
        .uri()
        .path_and_query()
        .map(|pq| pq.as_str())
        .unwrap_or("/");
    let url = format!("{}{}", state.app_url.trim_end_matches('/'), path_and_query);
    let method = req.method().clone();
    let headers = req.headers().clone();
    let body_bytes = axum::body::to_bytes(req.into_body(), 10 * 1024 * 1024)
        .await
        .map_err(|_| StatusCode::BAD_REQUEST)?;

    let mut builder = state.client.request(method, &url);
    for (k, v) in headers.iter() {
        if k == axum::http::header::HOST {
            continue;
        }
        builder = builder.header(k, v);
    }
    let upstream = builder
        .body(body_bytes)
        .send()
        .await
        .map_err(|_| StatusCode::BAD_GATEWAY)?;

    let status =
        StatusCode::from_u16(upstream.status().as_u16()).unwrap_or(StatusCode::BAD_GATEWAY);
    let mut response = Response::builder().status(status);
    for (k, v) in upstream.headers().iter() {
        if k == axum::http::header::TRANSFER_ENCODING {
            continue;
        }
        response = response.header(k, v);
    }
    let bytes = upstream
        .bytes()
        .await
        .map_err(|_| StatusCode::BAD_GATEWAY)?;
    response
        .body(Body::from(bytes))
        .map_err(|_| StatusCode::INTERNAL_SERVER_ERROR)
}

#[allow(dead_code)]
fn _uri(_: Uri) {}
