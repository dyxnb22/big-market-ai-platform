//! MySQL `AdminStore` — `platform_config` KV with learning defaults.

use async_trait::async_trait;
use bm_domain::AdminStore;
use bm_types::BmError;
use sqlx::Row;

use crate::mysql_store::MysqlStores;

fn default_admin_value(key: &str) -> Option<String> {
    match key {
        "chatbot::enabled" => Some("true".into()),
        "activity.100401::title" => Some("幸运轮盘活动".into()),
        "activity.100401::copy" => {
            Some("登录参与抽奖；对话计费真实，回复为本地 echo。".into())
        }
        "activity.100401::state" => Some("online".into()),
        "activity.100402::title" => Some("锁奖演示活动".into()),
        "activity.100402::copy" => {
            Some("多权重奖池 + tree_lock 解锁；用于面试演示规则链。".into())
        }
        "activity.100402::state" => Some("online".into()),
        "stage.activity.c01.s01" => Some("100401".into()),
        "stage.activity.c02.s02" => Some("100402".into()),
        _ => None,
    }
}

#[async_trait]
impl AdminStore for MysqlStores {
    async fn get(&self, key: &str) -> Result<Option<String>, BmError> {
        let schema = self.catalog_schema();
        let sql = format!(
            "SELECT cfg_value FROM `{schema}`.platform_config WHERE cfg_key = ? LIMIT 1"
        );
        let row = sqlx::query(&sql)
            .bind(key)
            .fetch_optional(&self.pool)
            .await;
        match row {
            Ok(Some(r)) => Ok(Some(r.get("cfg_value"))),
            Ok(None) => Ok(default_admin_value(key)),
            Err(_) => Ok(default_admin_value(key)),
        }
    }

    async fn set(&self, key: &str, value: &str) -> Result<(), BmError> {
        let schema = self.catalog_schema();
        let sql = format!(
            "INSERT INTO `{schema}`.platform_config (cfg_key, cfg_value) VALUES (?, ?) \
             ON DUPLICATE KEY UPDATE cfg_value = VALUES(cfg_value), update_time = NOW()"
        );
        sqlx::query(&sql)
            .bind(key)
            .bind(value)
            .execute(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(())
    }

    async fn list(&self) -> Result<Vec<(String, String)>, BmError> {
        let schema = self.catalog_schema();
        let sql = format!(
            "SELECT cfg_key, cfg_value FROM `{schema}`.platform_config ORDER BY cfg_key"
        );
        let mut map = std::collections::HashMap::new();
        if let Ok(rows) = sqlx::query(&sql).fetch_all(&self.pool).await {
            for r in rows {
                map.insert(r.get::<String, _>("cfg_key"), r.get("cfg_value"));
            }
        }
        for (k, v) in [
            ("chatbot::enabled", "true"),
            ("activity.100401::title", "幸运轮盘活动"),
            (
                "activity.100401::copy",
                "登录参与抽奖；对话计费真实，回复为本地 echo。",
            ),
            ("activity.100401::state", "online"),
            ("activity.100402::title", "锁奖演示活动"),
            (
                "activity.100402::copy",
                "多权重奖池 + tree_lock 解锁；用于面试演示规则链。",
            ),
            ("activity.100402::state", "online"),
            ("stage.activity.c01.s01", "100401"),
            ("stage.activity.c02.s02", "100402"),
        ] {
            map.entry(k.into()).or_insert_with(|| v.into());
        }
        let mut out: Vec<_> = map.into_iter().collect();
        out.sort_by(|a, b| a.0.cmp(&b.0));
        Ok(out)
    }

    async fn delete(&self, key: &str) -> Result<(), BmError> {
        let schema = self.catalog_schema();
        let sql = format!("DELETE FROM `{schema}`.platform_config WHERE cfg_key = ?");
        sqlx::query(&sql)
            .bind(key)
            .execute(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(())
    }
}
