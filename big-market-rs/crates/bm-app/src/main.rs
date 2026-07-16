mod http;
mod state;

use anyhow::Context;
use axum::Router;
use bm_infra::{bootstrap, spawn_persist_loop, spawn_stock_flush_loop, AppConfig, RuntimeConfig};
use state::AppState;
use std::net::SocketAddr;
use std::path::PathBuf;
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
    let runtime = RuntimeConfig {
        backend: cfg.backend.clone(),
        data_dir: PathBuf::from(&cfg.data_dir),
        initial_credit: cfg.initial_credit.clone(),
        mysql_url: cfg
            .mysql_url
            .clone()
            .or_else(|| std::env::var("BM_MYSQL_URL").ok()),
        redis_url: cfg
            .redis_url
            .clone()
            .or_else(|| std::env::var("BM_REDIS_URL").ok()),
    };
    let boot = bootstrap(&runtime)
        .await
        .map_err(|e| anyhow::anyhow!(e.to_string()))?;

    if let Some(path) = &boot.persist_path {
        spawn_persist_loop(boot.memory.clone(), path.clone(), 500);
    }

    let revocation = boot.revocation.clone();
    let state = AppState::from_bootstrapped(cfg.clone(), &boot, revocation);
    spawn_stock_flush_loop(state.stock.clone(), 5);

    let recorder = metrics_exporter_prometheus::PrometheusBuilder::new()
        .install_recorder()
        .context("prometheus")?;

    let embed_worker = std::env::var("BM_EMBED_WORKER").unwrap_or_else(|_| "1".into()) != "0";
    if embed_worker {
        metrics::describe_counter!("bm_outbox_consume_total", "Local outbox consume ticks");
        let dispatch = state.dispatch.clone();
        tokio::spawn(async move {
            loop {
                if let Ok(n) = dispatch.consume_send_award(50).await {
                    if n > 0 {
                        metrics::counter!("bm_outbox_consume_total", "kind" => "send_award")
                            .increment(n as u64);
                    }
                }
                if let Ok(n) = dispatch.dispatch_pending(50).await {
                    if n > 0 {
                        metrics::counter!("bm_outbox_consume_total", "kind" => "credit_dispatch")
                            .increment(n as u64);
                    }
                }
                tokio::time::sleep(Duration::from_millis(200)).await;
            }
        });
        let chat = state.chat.clone();
        let rebate = state.rebate.clone();
        tokio::spawn(async move {
            loop {
                let _ = chat.reconcile_pending(20).await;
                let _ = rebate.consume_rebate(50).await;
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
