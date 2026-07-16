mod http;
mod state;

use anyhow::Context;
use axum::Router;
use bm_domain::WorkerScheduler;
use bm_infra::{bootstrap, spawn_persist_loop, spawn_stock_flush_loop, AppConfig, RuntimeConfig};
use state::AppState;
use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Arc;
use std::sync::atomic::AtomicBool;
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

    // When Rabbit is configured, refuse embed outbox consume unless forced.
    // Standalone bm-worker owns MQ + local outbox to avoid double credit.
    let rabbit_url_set = std::env::var("BM_RABBIT_URL")
        .ok()
        .filter(|s| !s.is_empty())
        .is_some();
    let embed_requested = std::env::var("BM_EMBED_WORKER").unwrap_or_else(|_| "1".into()) != "0";
    let force_embed = std::env::var("BM_EMBED_WORKER_FORCE")
        .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
        .unwrap_or(false);
    let embed_worker = if embed_requested && rabbit_url_set && !force_embed {
        tracing::warn!(
            "BM_RABBIT_URL set → disabling BM_EMBED_WORKER (set BM_EMBED_WORKER_FORCE=1 to override)"
        );
        false
    } else {
        embed_requested
    };
    if embed_worker {
        metrics::describe_counter!("bm_worker_tick_total", "Embedded/standalone worker ticks");
        metrics::describe_counter!(
            "bm_worker_dispatch_failed_total",
            "credit_award_task dispatch failures"
        );
        let poll_secs = std::env::var("BM_WORKER_POLL_SECS")
            .ok()
            .and_then(|s| s.parse().ok())
            .unwrap_or(1);
        // Embed never owns Rabbit consumers; rabbit_active=false means local outbox consume.
        WorkerScheduler {
            dispatch: state.dispatch.clone(),
            rebate: state.rebate.clone(),
            chat: state.chat.clone(),
            stock: state.stock.clone(),
            rabbit_active: Arc::new(AtomicBool::new(false)),
            poll_secs,
        }
        .spawn();
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
