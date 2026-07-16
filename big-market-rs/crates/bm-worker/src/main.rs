use anyhow::Context;
use axum::{routing::get, Json, Router};
use bm_domain::AwardDispatchService;
use bm_infra::{bootstrap, spawn_persist_loop, RuntimeConfig, WorkerConfig};
use std::net::SocketAddr;
use std::path::PathBuf;
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
    if let Ok(b) = std::env::var("BM_BACKEND") {
        cfg.backend = b;
    }
    if let Ok(d) = std::env::var("BM_DATA_DIR") {
        cfg.data_dir = d;
    }

    let runtime = RuntimeConfig {
        backend: cfg.backend.clone(),
        data_dir: PathBuf::from(&cfg.data_dir),
        initial_credit: cfg.initial_credit.clone(),
        mysql_url: std::env::var("BM_MYSQL_URL").ok(),
        redis_url: std::env::var("BM_REDIS_URL").ok(),
    };
    let boot = bootstrap(&runtime)
        .await
        .map_err(|e| anyhow::anyhow!(e.to_string()))?;
    if let Some(path) = &boot.persist_path {
        spawn_persist_loop(boot.memory.clone(), path.clone(), 500);
    }

    let dispatch = Arc::new(AwardDispatchService {
        award: boot.memory.backend.clone(),
        credit: boot.memory.backend.clone(),
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
    tracing::info!(%addr, backend=%cfg.backend, "bm-worker listening");
    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await.context("serve")?;
    Ok(())
}
