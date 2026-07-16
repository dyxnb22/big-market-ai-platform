//! Infrastructure adapters. Default backend is in-memory for local/CI without Docker.

pub mod config;
pub mod memory;
pub mod router;

pub use config::{AppConfig, GatewayConfig, WorkerConfig};
pub use memory::{MemoryBackend, SharedMemory};
pub use router::DbRouter;
