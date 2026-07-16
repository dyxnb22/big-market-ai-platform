//! Backend bootstrap: memory | file | mysql(+optional redis revoke).

use crate::{redis_revocation, SharedMemory};
use bm_domain::StockStore;
use bm_types::{money, BmError, Money};
use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RuntimeConfig {
    pub backend: String,
    pub data_dir: PathBuf,
    pub initial_credit: String,
    pub mysql_url: Option<String>,
    pub redis_url: Option<String>,
}

impl Default for RuntimeConfig {
    fn default() -> Self {
        Self {
            backend: std::env::var("BM_BACKEND").unwrap_or_else(|_| "file".into()),
            data_dir: PathBuf::from(
                std::env::var("BM_DATA_DIR").unwrap_or_else(|_| "data/bm-rs".into()),
            ),
            initial_credit: std::env::var("BM_INITIAL_CREDIT").unwrap_or_else(|_| "100.00".into()),
            mysql_url: std::env::var("BM_MYSQL_URL").ok(),
            redis_url: std::env::var("BM_REDIS_URL").ok(),
        }
    }
}

pub enum BackendKind {
    Memory(SharedMemory),
    File(SharedMemory, PathBuf),
    #[cfg(feature = "mysql")]
    Mysql(Arc<crate::mysql_store::MysqlStores>, SharedMemory),
}

pub struct Bootstrapped {
    pub kind: BackendKind,
    pub memory: SharedMemory,
    pub revocation: Arc<dyn bm_domain::TokenRevocation>,
    pub persist_path: Option<PathBuf>,
}

pub async fn bootstrap(cfg: &RuntimeConfig) -> Result<Bootstrapped, BmError> {
    let initial: Money = money(&cfg.initial_credit);
    match cfg.backend.as_str() {
        "mysql" => {
            #[cfg(feature = "mysql")]
            {
                let url = cfg
                    .mysql_url
                    .as_deref()
                    .ok_or_else(|| BmError::IllegalParam("BM_MYSQL_URL required".into()))?;
                let mysql = crate::mysql_store::MysqlStores::connect(url).await?;
                let mem = SharedMemory::seeded(initial);
                let revocation = resolve_revocation(cfg, mem.backend.clone()).await;
                Ok(Bootstrapped {
                    kind: BackendKind::Mysql(mysql, mem.clone()),
                    memory: mem,
                    revocation,
                    persist_path: None,
                })
            }
            #[cfg(not(feature = "mysql"))]
            {
                Err(BmError::Internal(
                    "mysql feature not enabled at compile time".into(),
                ))
            }
        }
        "memory" => {
            let mem = SharedMemory::seeded(initial);
            let revocation = resolve_revocation(cfg, mem.backend.clone()).await;
            Ok(Bootstrapped {
                kind: BackendKind::Memory(mem.clone()),
                memory: mem,
                revocation,
                persist_path: None,
            })
        }
        _ => {
            // default: file
            std::fs::create_dir_all(&cfg.data_dir).map_err(|e| BmError::Internal(e.to_string()))?;
            let path = cfg.data_dir.join("state.json");
            let mem = SharedMemory::load_or_seed(&path, initial).await?;
            let revocation = resolve_revocation(cfg, mem.backend.clone()).await;
            Ok(Bootstrapped {
                kind: BackendKind::File(mem.clone(), path.clone()),
                memory: mem,
                revocation,
                persist_path: Some(path),
            })
        }
    }
}

async fn resolve_revocation(
    cfg: &RuntimeConfig,
    fallback: Arc<crate::MemoryBackend>,
) -> Arc<dyn bm_domain::TokenRevocation> {
    #[cfg(feature = "redis")]
    if let Some(url) = &cfg.redis_url {
        if let Some(r) = redis_revocation::try_connect(url).await {
            return r;
        }
    }
    #[cfg(not(feature = "redis"))]
    let _ = &cfg.redis_url;
    fallback
}

pub fn spawn_persist_loop(memory: SharedMemory, path: PathBuf, every_ms: u64) {
    tokio::spawn(async move {
        loop {
            tokio::time::sleep(Duration::from_millis(every_ms)).await;
            if let Err(e) = memory.persist(&path).await {
                tracing::warn!(error=%e, "persist failed");
            }
        }
    });
}

pub fn spawn_stock_flush_loop(stock: Arc<dyn StockStore>, every_secs: u64) {
    tokio::spawn(async move {
        loop {
            tokio::time::sleep(Duration::from_secs(every_secs)).await;
            match stock.list_dirty().await {
                Ok(dirty) if !dirty.is_empty() => {
                    tracing::debug!(count = dirty.len(), "stock flush mark clean");
                    let keys: Vec<String> = dirty.into_iter().map(|(k, _)| k).collect();
                    let _ = stock.clear_dirty(&keys).await;
                }
                _ => {}
            }
        }
    });
}
