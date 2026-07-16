//! Infrastructure adapters. Default backend is file-backed memory (no Docker required).

pub mod config;
pub mod factory;
pub mod memory;
pub mod router;
pub mod stores;

#[cfg(feature = "redis")]
pub mod redis_revocation;

#[cfg(feature = "mysql")]
pub mod mysql_store;

#[cfg(feature = "mysql")]
pub mod mysql_quota;

#[cfg(feature = "mysql")]
pub mod mysql_chat;

#[cfg(feature = "mysql")]
pub mod mysql_catalog;

#[cfg(feature = "mysql")]
pub mod mysql_admin;

#[cfg(feature = "mysql")]
pub mod mysql_strategy;

#[cfg(feature = "mysql")]
pub mod mysql_stock;

#[cfg(feature = "mysql")]
pub mod mysql_rebate;

#[cfg(feature = "mysql")]
pub mod mysql_orders;

#[cfg(feature = "mysql")]
pub mod mysql_participation;

#[cfg(feature = "rabbit")]
pub mod rabbit;

pub use config::{AppConfig, GatewayConfig, WorkerConfig};
pub use factory::{
    bootstrap, spawn_persist_loop, spawn_stock_flush_loop, BackendKind, Bootstrapped, RuntimeConfig,
};
pub use memory::{MemoryBackend, SharedMemory};
pub use router::DbRouter;
pub use stores::ServiceStores;

#[cfg(feature = "rabbit")]
pub use rabbit::{RabbitBridge, QUEUE_SEND_AWARD, QUEUE_SEND_REBATE};
