//! MySQL `RebateStore` + `RebateOutbox`.

use async_trait::async_trait;
use bm_domain::{RebateMessage, RebateOutbox, RebateStore};
use bm_types::BmError;
use uuid::Uuid;

use crate::mysql_store::MysqlStores;

#[async_trait]
impl RebateStore for MysqlStores {
    async fn has_signed_today(&self, user_id: &str, day: &str) -> Result<bool, BmError> {
        let schema = self.schema(user_id);
        let tb = self.tb(user_id);
        let biz = format!("sign_{user_id}_{day}");
        let sql = format!(
            "SELECT 1 FROM `{schema}`.user_behavior_rebate_order_{tb:03} \
             WHERE user_id = ? AND behavior_type = 'sign' AND biz_id = ? LIMIT 1"
        );
        let row = sqlx::query(&sql)
            .bind(user_id)
            .bind(&biz)
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(row.is_some())
    }

    async fn mark_signed(&self, user_id: &str, day: &str) -> Result<(), BmError> {
        let schema = self.schema(user_id);
        let tb = self.tb(user_id);
        let biz = format!("sign_{user_id}_{day}");
        let order_id: String = Uuid::new_v4().simple().to_string()[..12].into();
        let sql = format!(
            "INSERT IGNORE INTO `{schema}`.user_behavior_rebate_order_{tb:03} \
             (user_id, order_id, behavior_type, rebate_desc, rebate_type, rebate_config, \
              out_business_no, biz_id) \
             VALUES (?, ?, 'sign', '签到返利-积分', 'integral', '1', ?, ?)"
        );
        if let Err(e) = sqlx::query(&sql)
            .bind(user_id)
            .bind(&order_id)
            .bind(day)
            .bind(&biz)
            .execute(&self.pool)
            .await
        {
            if !MysqlStores::is_duplicate_key(&e) {
                return Err(BmError::Internal(e.to_string()));
            }
        }
        Ok(())
    }
}

#[async_trait]
impl RebateOutbox for MysqlStores {
    async fn enqueue_rebate(&self, msg: RebateMessage) -> Result<(), BmError> {
        self.rebate_outbox.lock().await.push_back(msg);
        Ok(())
    }

    async fn take_rebate_messages(&self, limit: usize) -> Result<Vec<RebateMessage>, BmError> {
        let mut q = self.rebate_outbox.lock().await;
        let mut out = Vec::new();
        for _ in 0..limit {
            if let Some(m) = q.pop_front() {
                out.push(m);
            } else {
                break;
            }
        }
        Ok(out)
    }
}
