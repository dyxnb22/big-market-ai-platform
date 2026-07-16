use anyhow::Context;
use axum::{routing::get, Json, Router};
use bm_domain::AwardDispatchService;
use bm_infra::{SharedMemory, WorkerConfig};
use bm_types::money;
use std::net::SocketAddr;
use std::sync::Arc;
use std::time::Duration;
use tower_http::trace::TraceLayer;
use tracing_subscriber::EnvFilter;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("info".parse()?))
        .init();

    let mut cfg = WorkerConfig::default();
    if let Ok(p) = std::env::var("BM_WORKER_PORT") {
        if let Ok(n) = p.parse() {
            cfg.port = n;
        }
    }
    if let Ok(s) = std::env::var("BM_WORKER_POLL_SECS") {
        if let Ok(n) = s.parse() {
            cfg.poll_secs = n;
        }
    }

    // Standalone worker uses its own memory unless BM_APP shares via mysql (future).
    // For local memory demos, prefer BM_EMBED_WORKER=1 inside bm-app.
    let memory = SharedMemory::seeded(money(&cfg.initial_credit));
    let dispatch = Arc::new(AwardDispatchService {
        award: memory.backend.clone(),
        credit: memory.backend.clone(),
    });

    let d = dispatch.clone();
    let poll = cfg.poll_secs;
    tokio::spawn(async move {
        loop {
            if let Err(e) = d.consume_send_award(50).await {
                tracing::warn!(error=%e, "consume_send_award");
            }
            if let Err(e) = d.dispatch_pending(50).await {
                tracing::warn!(error=%e, "dispatch_pending");
            }
            tokio::time::sleep(Duration::from_secs(poll)).await;
        }
    });

    let app = Router::new()
        .route(
            "/health",
            get(|| async { Json(serde_json::json!({"status":"UP"})) }),
        )
        .layer(TraceLayer::new_for_http());

    let addr: SocketAddr = format!("{}:{}", cfg.host, cfg.port).parse()?;
    tracing::info!(%addr, "bm-worker listening (poll loop active)");
    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await.context("serve")?;
    Ok(())
}
