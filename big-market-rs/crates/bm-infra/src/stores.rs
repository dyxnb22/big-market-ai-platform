//! Wired domain stores from bootstrap (memory/file vs mysql hybrid).

use crate::{BackendKind, Bootstrapped, MemoryBackend};
use bm_domain::*;
use std::sync::Arc;

/// All ports needed by bm-app / bm-worker.
#[derive(Clone)]
pub struct ServiceStores {
    pub credit: Arc<dyn CreditStore>,
    pub award: Arc<dyn AwardStore>,
    pub quota: Arc<dyn QuotaStore>,
    pub catalog: Arc<dyn CatalogStore>,
    pub chat: Arc<dyn ChatStore>,
    pub rebate: Arc<dyn RebateStore>,
    pub admin: Arc<dyn AdminStore>,
    pub stock: Arc<dyn StockStore>,
    pub stages: Arc<dyn StageStore>,
    pub orders: Arc<dyn OrderQueryStore>,
    pub strategy: Arc<dyn StrategyStore>,
    pub outbox: Arc<dyn RebateOutbox>,
}

impl ServiceStores {
    pub fn from_bootstrapped(boot: &Bootstrapped) -> Self {
        let mem = boot.memory.backend.clone();
        match &boot.kind {
            #[cfg(feature = "mysql")]
            BackendKind::Mysql(mysql, _) => Self {
                credit: mysql.clone(),
                award: mysql.clone(),
                // Quota/catalog/chat stay on file companion until full MySQL port.
                quota: mem.clone(),
                catalog: mem.clone(),
                chat: mem.clone(),
                rebate: mem.clone(),
                admin: mem.clone(),
                stock: mem.clone(),
                stages: mem.clone(),
                orders: mem.clone(),
                strategy: mem.clone(),
                outbox: mem.clone(),
            },
            _ => Self::all_memory(mem),
        }
    }

    fn all_memory(mem: Arc<MemoryBackend>) -> Self {
        Self {
            credit: mem.clone(),
            award: mem.clone(),
            quota: mem.clone(),
            catalog: mem.clone(),
            chat: mem.clone(),
            rebate: mem.clone(),
            admin: mem.clone(),
            stock: mem.clone(),
            stages: mem.clone(),
            orders: mem.clone(),
            strategy: mem.clone(),
            outbox: mem.clone(),
        }
    }
}
