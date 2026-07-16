//! Worker tick — shared between `bm-worker` and embedded worker in `bm-app`.

use crate::{AwardDispatchService, ChatBillingService, RebateService, StockStore};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;
use tokio::time::sleep;

/// Registered background jobs (semantic equivalent of XXL handlers, no console).
/// Out of scope vs full Java XXL set: UpdateAwardStockJob fan-out, Nacos refresh, remote write reconcile UI.
pub const JOB_CATALOG: &[(&str, &str)] = &[
    ("consume_send_award", "Local outbox → credit_award_task ingest (skipped when Rabbit active)"),
    ("consume_rebate", "Local rebate outbox ingest (skipped when Rabbit active)"),
    ("dispatch_credit_award", "Pending credit_award_task → account credit"),
    ("chat_reconcile", "Pending chat refund sessions"),
    ("stock_flush", "Persist dirty activity soft-stock then clear"),
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
                    metrics::counter!("bm_worker_tick_error_total").increment(1);
                }
                sleep(Duration::from_secs(self.poll_secs)).await;
            }
        });
    }

    /// One poll: local outbox ingest (when no Rabbit), dispatch, reconcile, stock flush.
    pub async fn tick(&self) -> Result<(), bm_types::BmError> {
        metrics::counter!("bm_worker_tick_total").increment(1);
        if !self.rabbit_active.load(Ordering::SeqCst) {
            let n = self.dispatch.consume_send_award(50).await?;
            if n > 0 {
                metrics::counter!("bm_outbox_consume_total", "kind" => "send_award").increment(n as u64);
            }
            let n = self.rebate.consume_rebate(50).await?;
            if n > 0 {
                metrics::counter!("bm_outbox_consume_total", "kind" => "rebate").increment(n as u64);
            }
        }
        let dispatched = self.dispatch.dispatch_pending(50).await?;
        if dispatched > 0 {
            tracing::debug!(dispatched, "credit_award dispatched");
            metrics::counter!("bm_outbox_consume_total", "kind" => "credit_dispatch")
                .increment(dispatched as u64);
        }
        let reconciled = self.chat.reconcile_pending(20).await?;
        if reconciled > 0 {
            tracing::debug!(reconciled, "chat refunds reconciled");
            metrics::counter!("bm_outbox_consume_total", "kind" => "chat_reconcile")
                .increment(reconciled as u64);
        }
        let flushed = self.stock.flush_dirty().await?;
        if flushed > 0 {
            tracing::debug!(flushed, "stock flush persisted");
            metrics::counter!("bm_outbox_consume_total", "kind" => "stock_flush")
                .increment(flushed as u64);
        }
        Ok(())
    }
}
