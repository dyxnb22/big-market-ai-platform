mod http;
mod state;

use anyhow::Context;
use axum::Router;
use bm_infra::{AppConfig, SharedMemory};
use bm_types::money;
use state::AppState;
use std::net::SocketAddr;
use std::time::Duration;
use tower_http::cors::CorsLayer;
use tower_http::trace::TraceLayer;
use tracing_subscriber::EnvFilter;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("info".parse()?))
        .init();

    let cfg = AppConfig::load().context("load config")?;
    let recorder = metrics_exporter_prometheus::PrometheusBuilder::new()
        .install_recorder()
        .context("prometheus")?;
    let state = AppState::from_memory(
        cfg.clone(),
        SharedMemory::seeded(money(&cfg.initial_credit)),
    );

    // Memory backend cannot share queues across processes: embed worker loops in-app.
    // Set BM_EMBED_WORKER=0 only when using a durable shared backend + dedicated bm-worker.
    let embed_worker = std::env::var("BM_EMBED_WORKER").unwrap_or_else(|_| "1".into()) != "0";
    if embed_worker {
        let dispatch = state.dispatch.clone();
        tokio::spawn(async move {
            loop {
                let _ = dispatch.consume_send_award(50).await;
                let _ = dispatch.dispatch_pending(50).await;
                tokio::time::sleep(Duration::from_millis(200)).await;
            }
        });
        let chat = state.chat.clone();
        tokio::spawn(async move {
            loop {
                let _ = chat.reconcile_pending(20).await;
                tokio::time::sleep(Duration::from_secs(2)).await;
            }
        });
    }

    let app = Router::new()
        .merge(http::routes(state.clone()))
        .route(
            "/metrics",
            axum::routing::get(move || {
                let body = recorder.render();
                async move { body }
            }),
        )
        .layer(CorsLayer::permissive())
        .layer(TraceLayer::new_for_http());

    let addr: SocketAddr = format!("{}:{}", cfg.host, cfg.port).parse()?;
    tracing::info!(%addr, backend=%cfg.backend, embed_worker, "bm-app listening");
    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await?;
    Ok(())
}
