//! MySQL `StockStore` — `strategy_award` surplus + activity soft stock (memory + DB flush).

use async_trait::async_trait;
use bm_domain::{activity_stock_key, StockStore};
use bm_types::BmError;
use sqlx::Row;

use crate::mysql_store::MysqlStores;

fn parse_stock_key(key: &str) -> Option<(bool, i64)> {
    if let Some(id) = key.strip_prefix("activity_stock:") {
        return id.parse().ok().map(|n| (true, n));
    }
    if let Some(id) = key.strip_prefix("award_stock:") {
        return id.parse().ok().map(|n| (false, n));
    }
    None
}

impl MysqlStores {
    async fn read_award_stock(&self, award_id: i64) -> Result<i64, BmError> {
        let schema = self.catalog_schema();
        let sql = format!(
            "SELECT award_count_surplus FROM `{schema}`.strategy_award \
             WHERE award_id = ? ORDER BY award_count_surplus DESC LIMIT 1"
        );
        let row = sqlx::query(&sql)
            .bind(award_id as i32)
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(row
            .map(|r| r.get::<i32, _>("award_count_surplus") as i64)
            .unwrap_or(0))
    }

    async fn read_activity_soft_stock(&self, activity_id: i64) -> Result<Option<i64>, BmError> {
        let schema = self.catalog_schema();
        let sql = format!(
            "SELECT surplus FROM `{schema}`.activity_soft_stock WHERE activity_id = ?"
        );
        match sqlx::query(&sql)
            .bind(activity_id)
            .fetch_optional(&self.pool)
            .await
        {
            Ok(row) => Ok(row.map(|r| r.get::<i64, _>("surplus"))),
            Err(_) => Ok(None), // table may be missing until reconcile SQL
        }
    }

    async fn upsert_activity_soft_stock(&self, activity_id: i64, qty: i64) -> Result<(), BmError> {
        let schema = self.catalog_schema();
        let sql = format!(
            "INSERT INTO `{schema}`.activity_soft_stock (activity_id, surplus) VALUES (?, ?) \
             ON DUPLICATE KEY UPDATE surplus = VALUES(surplus), update_time = NOW()"
        );
        // Best-effort: table may be missing on older volumes until reconcile SQL runs.
        let _ = sqlx::query(&sql)
            .bind(activity_id)
            .bind(qty)
            .execute(&self.pool)
            .await;
        Ok(())
    }
}

#[async_trait]
impl StockStore for MysqlStores {
    async fn get_stock(&self, key: &str) -> Result<i64, BmError> {
        let Some((is_activity, id)) = parse_stock_key(key) else {
            return Ok(0);
        };
        if is_activity {
            {
                let g = self.activity_stocks.lock().await;
                if let Some(v) = g.get(&id) {
                    return Ok(*v);
                }
            }
            if let Some(v) = self.read_activity_soft_stock(id).await? {
                self.activity_stocks.lock().await.insert(id, v);
                return Ok(v);
            }
            return Ok(10_000);
        }
        self.read_award_stock(id).await
    }

    async fn set_stock(&self, key: &str, qty: i64) -> Result<(), BmError> {
        let Some((is_activity, id)) = parse_stock_key(key) else {
            return Ok(());
        };
        if is_activity {
            let mut g = self.activity_stocks.lock().await;
            g.insert(id, qty);
            self.dirty_activity_stocks.lock().await.insert(id);
            return Ok(());
        }
        let schema = self.catalog_schema();
        let sql = format!(
            "UPDATE `{schema}`.strategy_award SET award_count_surplus = ?, update_time = NOW() \
             WHERE award_id = ?"
        );
        sqlx::query(&sql)
            .bind(qty as i32)
            .bind(id as i32)
            .execute(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(())
    }

    async fn decr_stock(&self, key: &str, delta: i64) -> Result<bool, BmError> {
        let Some((is_activity, id)) = parse_stock_key(key) else {
            return Ok(false);
        };
        if is_activity {
            let need_seed = {
                let g = self.activity_stocks.lock().await;
                !g.contains_key(&id)
            };
            if need_seed {
                let seeded = self
                    .read_activity_soft_stock(id)
                    .await?
                    .unwrap_or(10_000);
                self.activity_stocks.lock().await.entry(id).or_insert(seeded);
            }
            let mut g = self.activity_stocks.lock().await;
            let cur = g.entry(id).or_insert(10_000);
            if *cur < delta {
                return Ok(false);
            }
            *cur -= delta;
            self.dirty_activity_stocks.lock().await.insert(id);
            return Ok(true);
        }
        let schema = self.catalog_schema();
        let sql = format!(
            "UPDATE `{schema}`.strategy_award \
             SET award_count_surplus = award_count_surplus - ?, update_time = NOW() \
             WHERE award_id = ? AND award_count_surplus >= ?"
        );
        let n = sqlx::query(&sql)
            .bind(delta as i32)
            .bind(id as i32)
            .bind(delta as i32)
            .execute(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?
            .rows_affected();
        Ok(n > 0)
    }

    async fn list_dirty(&self) -> Result<Vec<(String, i64)>, BmError> {
        let dirty = self.dirty_activity_stocks.lock().await.clone();
        let stocks = self.activity_stocks.lock().await;
        Ok(dirty
            .into_iter()
            .filter_map(|id| stocks.get(&id).map(|v| (activity_stock_key(id), *v)))
            .collect())
    }

    async fn clear_dirty(&self, keys: &[String]) -> Result<(), BmError> {
        let mut dirty = self.dirty_activity_stocks.lock().await;
        for key in keys {
            if let Some(id) = key.strip_prefix("activity_stock:") {
                if let Ok(n) = id.parse::<i64>() {
                    dirty.remove(&n);
                }
            }
        }
        Ok(())
    }

    async fn flush_dirty(&self) -> Result<usize, BmError> {
        let dirty = self.list_dirty().await?;
        let n = dirty.len();
        if n == 0 {
            return Ok(0);
        }
        for (key, qty) in &dirty {
            if let Some(id) = key.strip_prefix("activity_stock:") {
                if let Ok(activity_id) = id.parse::<i64>() {
                    self.upsert_activity_soft_stock(activity_id, *qty).await?;
                }
            }
        }
        let keys: Vec<String> = dirty.into_iter().map(|(k, _)| k).collect();
        self.clear_dirty(&keys).await?;
        Ok(n)
    }
}
