//! Worker tick — shared between `bm-worker` and embedded worker in `bm-app`.

use crate::{AwardDispatchService, ChatBillingService, RebateService, StockStore};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;
use tokio::time::sleep;

/// Registered background jobs (semantic equivalent of XXL handlers, no console).
pub const JOB_CATALOG: &[(&str, &str)] = &[
    ("consume_send_award", "Local outbox → credit_award_task ingest"),
    ("consume_rebate", "Local rebate outbox ingest"),
    ("dispatch_credit_award", "Pending credit_award_task → account credit"),
    ("chat_reconcile", "Pending chat refund sessions"),
    ("stock_flush", "Mark activity soft-stock dirty keys clean"),
];

/// Shared services for one scheduler poll cycle.
pub struct WorkerScheduler {
    pub dispatch: Arc<AwardDispatchService>,
    pub rebate: Arc<RebateService>,
    pub chat: Arc<ChatBillingService>,
    pub stock: Arc<dyn StockStore>,
    pub rabbit_active: Arc<AtomicBool>,
    pub poll_secs: u64,
}

impl WorkerScheduler {
    pub fn spawn(self) {
        tokio::spawn(async move {
            loop {
                if let Err(e) = self.tick().await {
                    tracing::warn!(error=%e, "worker tick error");
                }
                sleep(Duration::from_secs(self.poll_secs)).await;
            }
        });
    }

    /// One poll: local outbox ingest (when no Rabbit), dispatch, reconcile, stock flush.
    pub async fn tick(&self) -> Result<(), bm_types::BmError> {
        if !self.rabbit_active.load(Ordering::SeqCst) {
            let _ = self.dispatch.consume_send_award(50).await?;
            let _ = self.rebate.consume_rebate(50).await?;
        }
        let dispatched = self.dispatch.dispatch_pending(50).await?;
        if dispatched > 0 {
            tracing::debug!(dispatched, "credit_award dispatched");
        }
        let reconciled = self.chat.reconcile_pending(20).await?;
        if reconciled > 0 {
            tracing::debug!(reconciled, "chat refunds reconciled");
        }
        if let Ok(dirty) = self.stock.list_dirty().await {
            if !dirty.is_empty() {
                let keys: Vec<String> = dirty.into_iter().map(|(k, _)| k).collect();
                tracing::debug!(count = keys.len(), "stock flush mark clean");
                self.stock.clear_dirty(&keys).await?;
            }
        }
        Ok(())
    }
}
