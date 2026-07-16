//! MySQL catalog + ERP stage ports (`big_market` shared tables).

use async_trait::async_trait;
use bm_domain::{ActivityStage, CatalogStore, SkuProduct, StageStore};
use bm_types::BmError;
use sqlx::Row;

use crate::mysql_store::MysqlStores;

fn map_sku_row(r: &sqlx::mysql::MySqlRow) -> bm_domain::SkuProduct {
    let quota_count: i32 = r.get("total_count");
    bm_domain::SkuProduct {
        sku: r.get("sku"),
        activity_id: r.get("activity_id"),
        product_name: format!("抽奖次数×{quota_count}"),
        product_amount: r.get("product_amount"),
        quota_count,
    }
}

#[async_trait]
impl CatalogStore for MysqlStores {
    async fn stage_activity_id(&self, channel: &str, source: &str) -> Result<i64, BmError> {
        let schema = self.catalog_schema();
        let sql = format!(
            "SELECT activity_id FROM `{schema}`.raffle_activity_stage \
             WHERE channel = ? AND source = ? AND state = 'active' \
             ORDER BY id DESC LIMIT 1"
        );
        let row = sqlx::query(&sql)
            .bind(channel)
            .bind(source)
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        row.map(|r| r.get::<i64, _>("activity_id"))
            .or(match (channel, source) {
                // Demo stage fallbacks when SQL seed is absent (file/mysql interview path).
                ("c01", "s01") => Some(100401),
                ("c02", "s02") => Some(100402),
                _ => None,
            })
            .ok_or_else(|| BmError::NotFound("stage activity missing".into()))
    }

    async fn list_sku(&self, activity_id: i64) -> Result<Vec<SkuProduct>, BmError> {
        let schema = self.catalog_schema();
        let sql = format!(
            "SELECT s.sku, s.activity_id, s.product_amount, c.total_count \
             FROM `{schema}`.raffle_activity_sku s \
             JOIN `{schema}`.raffle_activity_count c \
               ON s.activity_count_id = c.activity_count_id \
             WHERE s.activity_id = ?"
        );
        let rows = sqlx::query(&sql)
            .bind(activity_id)
            .fetch_all(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        let mut out = Vec::with_capacity(rows.len());
        for r in &rows {
            out.push(map_sku_row(r));
        }
        Ok(out)
    }

    async fn get_sku(&self, sku: i64) -> Result<SkuProduct, BmError> {
        let schema = self.catalog_schema();
        let sql = format!(
            "SELECT s.sku, s.activity_id, s.product_amount, c.total_count \
             FROM `{schema}`.raffle_activity_sku s \
             JOIN `{schema}`.raffle_activity_count c \
               ON s.activity_count_id = c.activity_count_id \
             WHERE s.sku = ?"
        );
        let row = sqlx::query(&sql)
            .bind(sku)
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        match row {
            Some(r) => Ok(map_sku_row(&r)),
            None => Err(BmError::NotFound(format!("sku {sku}"))),
        }
    }

    async fn armory(&self, activity_id: i64) -> Result<bool, BmError> {
        let schema = self.catalog_schema();
        let sql = format!(
            "SELECT 1 FROM `{schema}`.raffle_activity WHERE activity_id = ? AND state = 'open' LIMIT 1"
        );
        let ok = sqlx::query(&sql)
            .bind(activity_id)
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?
            .is_some();
        Ok(ok)
    }
}

#[async_trait]
impl StageStore for MysqlStores {
    async fn list_stages(&self) -> Result<Vec<ActivityStage>, BmError> {
        let schema = self.catalog_schema();
        let sql = format!(
            "SELECT id, channel, source, activity_id, state \
             FROM `{schema}`.raffle_activity_stage ORDER BY id"
        );
        let rows = sqlx::query(&sql)
            .fetch_all(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(rows
            .iter()
            .map(|r| ActivityStage {
                id: r.get("id"),
                channel: r.get("channel"),
                source: r.get("source"),
                activity_id: r.get("activity_id"),
                state: r.get("state"),
            })
            .collect())
    }

    async fn set_stage_state(&self, id: i64, state: &str) -> Result<bool, BmError> {
        let schema = self.catalog_schema();
        let sql = format!(
            "UPDATE `{schema}`.raffle_activity_stage SET state = ?, update_time = NOW() WHERE id = ?"
        );
        let n = sqlx::query(&sql)
            .bind(state)
            .bind(id)
            .execute(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?
            .rows_affected();
        Ok(n == 1)
    }
}
