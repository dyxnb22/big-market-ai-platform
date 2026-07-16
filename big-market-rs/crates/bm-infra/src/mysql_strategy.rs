//! MySQL `StrategyStore` — `strategy_award` + `raffle_activity`.

use async_trait::async_trait;
use bm_domain::ports::{AwardWeight, StrategyStore};
use bm_domain::strategy::default_stage_weights;
use bm_types::{money, BmError, Money};
use rust_decimal::Decimal;
use sqlx::Row;

use crate::mysql_store::MysqlStores;

fn credit_for_award(award_id: i32, title: &str) -> Money {
    if award_id == 101 {
        return money("5.00");
    }
    if title.contains("积分") {
        for token in title.split(|c: char| !c.is_ascii_digit() && c != '.') {
            if let Ok(v) = token.parse::<f64>() {
                if v > 0.0 {
                    return money(&format!("{v:.2}"));
                }
            }
        }
    }
    money("0.00")
}

fn rate_to_weight(rate: Decimal) -> u32 {
    let scaled = rate * Decimal::from(10_000);
    scaled.to_string().parse::<f64>().unwrap_or(0.0).round() as u32
}

#[async_trait]
impl StrategyStore for MysqlStores {
    async fn award_weights(&self, activity_id: i64) -> Result<Vec<AwardWeight>, BmError> {
        let schema = self.catalog_schema();
        let sql = format!(
            "SELECT sa.award_id, sa.award_title, sa.award_rate, sa.sort, sa.rule_models \
             FROM `{schema}`.strategy_award sa \
             JOIN `{schema}`.raffle_activity ra ON ra.strategy_id = sa.strategy_id \
             WHERE ra.activity_id = ? AND sa.award_rate > 0 \
             ORDER BY sa.sort, sa.award_id"
        );
        let rows = sqlx::query(&sql)
            .bind(activity_id)
            .fetch_all(&self.pool)
            .await;
        let rows = match rows {
            Ok(r) if !r.is_empty() => r,
            _ => return Ok(default_stage_weights(activity_id)),
        };
        let mut out = Vec::with_capacity(rows.len());
        for (idx, r) in rows.iter().enumerate() {
            let award_id: i32 = r.get("award_id");
            let title: String = r.get("award_title");
            let rate: Decimal = r.get("award_rate");
            let rule_model: Option<String> = r.try_get("rule_models").ok();
            let weight = rate_to_weight(rate);
            if weight == 0 {
                continue;
            }
            out.push(AwardWeight {
                award_id,
                award_title: title.clone(),
                award_index: (idx + 1) as i32,
                weight,
                credit_amount: credit_for_award(award_id, &title),
                rule_model,
            });
        }
        if out.is_empty() {
            return Ok(default_stage_weights(activity_id));
        }
        Ok(out)
    }

    async fn rule_weight_value(&self, activity_id: i64) -> Result<Option<String>, BmError> {
        if let Ok(v) = std::env::var("BM_RULE_WEIGHT") {
            if !v.is_empty() {
                return Ok(Some(v));
            }
        }
        let schema = self.catalog_schema();
        let sql = format!(
            "SELECT sr.rule_value FROM `{schema}`.strategy_rule sr \
             JOIN `{schema}`.raffle_activity ra ON ra.strategy_id = sr.strategy_id \
             WHERE ra.activity_id = ? AND sr.rule_model = 'rule_weight' LIMIT 1"
        );
        match sqlx::query(&sql)
            .bind(activity_id)
            .fetch_optional(&self.pool)
            .await
        {
            Ok(Some(row)) => Ok(row.try_get::<String, _>("rule_value").ok()),
            _ => Ok(None),
        }
    }
}
