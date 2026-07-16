//! MySQL `ParticipationStore` — count prior draws from `user_award_record`.

use async_trait::async_trait;
use bm_domain::ParticipationStore;
use bm_types::BmError;
use sqlx::Row;

use crate::mysql_store::MysqlStores;

#[async_trait]
impl ParticipationStore for MysqlStores {
    async fn count_draws(&self, user_id: &str, activity_id: i64) -> Result<i32, BmError> {
        let schema = self.schema(user_id);
        let tb = self.tb(user_id);
        let sql = format!(
            "SELECT COUNT(*) AS c FROM `{schema}`.user_award_record_{tb:03} \
             WHERE user_id = ? AND activity_id = ?"
        );
        let row = sqlx::query(&sql)
            .bind(user_id)
            .bind(activity_id)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(row.get::<i64, _>("c") as i32)
    }
}
