//! MySQL `StockStore` — `strategy_award` surplus + in-memory activity soft stock.

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
}

#[async_trait]
impl StockStore for MysqlStores {
    async fn get_stock(&self, key: &str) -> Result<i64, BmError> {
        let Some((is_activity, id)) = parse_stock_key(key) else {
            return Ok(0);
        };
        if is_activity {
            let g = self.activity_stocks.lock().await;
            return Ok(*g.get(&id).unwrap_or(&10_000));
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
}
