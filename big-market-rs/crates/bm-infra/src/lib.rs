//! Infrastructure adapters. Default backend is file-backed memory (no Docker required).

pub mod config;
pub mod factory;
pub mod memory;
pub mod router;

#[cfg(feature = "redis")]
pub mod redis_revocation;

#[cfg(feature = "mysql")]
pub mod mysql_store;

pub use config::{AppConfig, GatewayConfig, WorkerConfig};
pub use factory::{
    bootstrap, spawn_persist_loop, spawn_stock_flush_loop, BackendKind, Bootstrapped, RuntimeConfig,
};
pub use memory::{MemoryBackend, SharedMemory};
pub use router::DbRouter;
