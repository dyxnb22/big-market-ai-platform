mod scheduler;

use anyhow::Context;
use axum::{routing::get, Json, Router};
use bm_domain::{AwardDispatchService, ChatBillingService, RebateService, SendAwardMessage};
use bm_infra::{bootstrap, spawn_persist_loop, RuntimeConfig, ServiceStores, WorkerConfig};
use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
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

    let stores = ServiceStores::from_bootstrapped(&boot);

    let dispatch = Arc::new(AwardDispatchService {
        award: stores.award.clone(),
        credit: stores.credit.clone(),
    });
    let rebate = Arc::new(RebateService {
        rebate: stores.rebate.clone(),
        credit: stores.credit.clone(),
        outbox: stores.outbox.clone(),
    });
    let chat = Arc::new(ChatBillingService {
        credit: stores.credit.clone(),
        chat: stores.chat.clone(),
    });

    let rabbit_active = Arc::new(AtomicBool::new(false));
    let rabbit_url = std::env::var("BM_RABBIT_URL").ok().filter(|s| !s.is_empty());

    #[cfg(feature = "rabbit")]
    if let Some(url) = rabbit_url.clone() {
        match bm_infra::RabbitBridge::connect(&url).await {
            Ok(bridge) => {
                rabbit_active.store(true, Ordering::SeqCst);
                tracing::info!(%url, "rabbit bridge enabled");
                let d = dispatch.clone();
                let r = rebate.clone();
                let b_pub = bridge.clone();
                let poll = cfg.poll_secs;
                tokio::spawn(async move {
                    loop {
                        if let Ok(msgs) = d.take_send_award_for_publish(50).await {
                            for m in msgs {
                                if let Err(e) = b_pub.publish_send_award(&m).await {
                                    tracing::warn!(error=%e, "publish send_award failed");
                                }
                            }
                        }
                        if let Ok(msgs) = r.take_rebate_for_publish(50).await {
                            for m in msgs {
                                if let Err(e) = b_pub.publish_rebate(&m).await {
                                    tracing::warn!(error=%e, "publish rebate failed");
                                }
                            }
                        }
                        tokio::time::sleep(Duration::from_secs(poll)).await;
                    }
                });

                let d2 = dispatch.clone();
                let b_award = bridge.clone();
                tokio::spawn(async move {
                    let _ = b_award
                        .consume_send_award(move |m: SendAwardMessage| {
                            let d = d2.clone();
                            async move { d.ingest_send_award(m).await }
                        })
                        .await;
                });

                let r2 = rebate.clone();
                let b_rebate = bridge;
                tokio::spawn(async move {
                    let _ = b_rebate
                        .consume_rebate(move |m| {
                            let r = r2.clone();
                            async move { r.ingest_rebate(m).await }
                        })
                        .await;
                });
            }
            Err(e) => {
                tracing::warn!(error=%e, "rabbit connect failed; falling back to local outbox");
            }
        }
    }

    #[cfg(not(feature = "rabbit"))]
    let _ = rabbit_url;

    let rabbit_for_scheduler = rabbit_active.clone();
    scheduler::WorkerScheduler {
        dispatch,
        rebate,
        chat,
        stock: stores.stock.clone(),
        rabbit_active: rabbit_for_scheduler,
        poll_secs: cfg.poll_secs,
    }
    .spawn();

    let app = Router::new()
        .route(
            "/health",
            get(|| async { Json(serde_json::json!({"status":"UP"})) }),
        )
        .route(
            "/actuator/health",
            get(|| async { Json(serde_json::json!({"status":"UP"})) }),
        )
        .layer(TraceLayer::new_for_http());

    let addr: SocketAddr = format!("{}:{}", cfg.host, cfg.port).parse()?;
    tracing::info!(
        %addr,
        backend=%cfg.backend,
        rabbit=rabbit_active.load(Ordering::SeqCst),
        "bm-worker listening"
    );
    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await.context("serve")?;
    Ok(())
}
