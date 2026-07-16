//! MySQL `OrderQueryStore` — scan `user_award_record` shards.

use async_trait::async_trait;
use bm_domain::{OrderQueryStore, UserRaffleOrderView};
use bm_types::BmError;
use sqlx::Row;

use crate::mysql_store::MysqlStores;

#[async_trait]
impl OrderQueryStore for MysqlStores {
    async fn list_raffle_orders(&self, limit: usize) -> Result<Vec<UserRaffleOrderView>, BmError> {
        let mut out = Vec::new();
        for db in 1..=2u32 {
            let schema = format!("big_market_{db:02}");
            for tb in 0..4u32 {
                let sql = format!(
                    "SELECT user_id, activity_id, order_id, award_id, award_title \
                     FROM `{schema}`.user_award_record_{tb:03} \
                     ORDER BY create_time DESC LIMIT ?"
                );
                let rows = sqlx::query(&sql)
                    .bind(limit as i64)
                    .fetch_all(&self.pool)
                    .await;
                if let Ok(rows) = rows {
                    for r in rows {
                        out.push(UserRaffleOrderView {
                            user_id: r.get("user_id"),
                            activity_id: r.get("activity_id"),
                            order_id: r.get("order_id"),
                            award_id: r.get("award_id"),
                            award_title: r.get("award_title"),
                        });
                        if out.len() >= limit {
                            return Ok(out);
                        }
                    }
                }
            }
        }
        Ok(out)
    }
}
