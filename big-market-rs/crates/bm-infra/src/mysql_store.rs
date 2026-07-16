//! MySQL adapters (sqlx) for credit account / order / credit_award_task.
//! Enabled when `BM_BACKEND=mysql` and `BM_MYSQL_URL` is set.

use async_trait::async_trait;
use bm_domain::{
    AwardStore, AwardTaskState, CreditAwardTask, CreditOrder, CreditStore, RebateMessage,
    SendAwardMessage, TradeType, UserAwardRecord,
};
use bm_types::{BmError, Money};
use chrono::Utc;
use rust_decimal::Decimal;
use sqlx::mysql::MySqlPoolOptions;
use sqlx::{MySql, Pool, Row};
use std::collections::{HashMap, HashSet, VecDeque};
use std::sync::Arc;
use tokio::sync::Mutex;

use crate::DbRouter;

#[derive(Clone)]
pub struct MysqlStores {
    pub pool: Pool<MySql>,
    pub router: DbRouter,
    /// Chat idempotent response cache (aligns Java Redis `chat:request:{userId}:{requestId}`).
    pub chat_idem: Arc<Mutex<HashMap<String, Decimal>>>,
    /// Activity-level soft stock (not in DB); flushed/cleared by worker.
    pub activity_stocks: Arc<Mutex<HashMap<i64, i64>>>,
    pub dirty_activity_stocks: Arc<Mutex<HashSet<i64>>>,
    /// Local rebate outbox before optional RabbitMQ publish.
    pub rebate_outbox: Arc<Mutex<VecDeque<RebateMessage>>>,
}

impl MysqlStores {
    pub async fn connect(url: &str) -> Result<Arc<Self>, BmError> {
        let pool = MySqlPoolOptions::new()
            .max_connections(10)
            .connect(url)
            .await
            .map_err(|e| BmError::Internal(format!("mysql connect: {e}")))?;
        Ok(Arc::new(Self {
            pool,
            router: DbRouter::default(),
            chat_idem: Arc::new(Mutex::new(HashMap::new())),
            activity_stocks: Arc::new(Mutex::new(HashMap::new())),
            dirty_activity_stocks: Arc::new(Mutex::new(HashSet::new())),
            rebate_outbox: Arc::new(Mutex::new(VecDeque::new())),
        }))
    }

    pub(crate) fn schema(&self, user_id: &str) -> String {
        self.router.schema_name(user_id)
    }

    pub(crate) fn tb(&self, user_id: &str) -> u32 {
        self.router.route(user_id).1
    }

    /// Shared catalog tables (`raffle_activity_sku`, `raffle_activity_stage`, …).
    pub(crate) fn catalog_schema(&self) -> &'static str {
        "big_market"
    }

    pub(crate) fn is_duplicate_key(err: &sqlx::Error) -> bool {
        err.as_database_error()
            .is_some_and(|e| e.is_unique_violation())
    }
}

#[async_trait]
impl CreditStore for MysqlStores {
    async fn get_balance(&self, user_id: &str) -> Result<Money, BmError> {
        let schema = self.schema(user_id);
        let sql = format!(
            "SELECT available_amount FROM `{schema}`.user_credit_account WHERE user_id = ?"
        );
        let row = sqlx::query(&sql)
            .bind(user_id)
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(row
            .map(|r| r.get::<Decimal, _>("available_amount"))
            .unwrap_or(Decimal::ZERO))
    }

    async fn ensure_account(&self, user_id: &str, initial: Money) -> Result<(), BmError> {
        let schema = self.schema(user_id);
        let sql = format!(
            "INSERT IGNORE INTO `{schema}`.user_credit_account \
             (user_id, total_amount, available_amount, account_status) VALUES (?, ?, ?, 'open')"
        );
        sqlx::query(&sql)
            .bind(user_id)
            .bind(initial)
            .bind(initial)
            .execute(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(())
    }

    async fn apply_trade(&self, order: CreditOrder) -> Result<Money, BmError> {
        let schema = self.schema(&order.user_id);
        let tb = self.tb(&order.user_id);
        let mut tx = self
            .pool
            .begin()
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;

        // Idempotency: existing out_business_no → return current balance.
        let check = format!(
            "SELECT 1 FROM `{schema}`.user_credit_order_{tb:03} WHERE out_business_no = ? LIMIT 1"
        );
        if sqlx::query(&check)
            .bind(&order.out_business_no)
            .fetch_optional(&mut *tx)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?
            .is_some()
        {
            let bal_sql = format!(
                "SELECT available_amount FROM `{schema}`.user_credit_account WHERE user_id = ?"
            );
            let bal = sqlx::query(&bal_sql)
                .bind(&order.user_id)
                .fetch_optional(&mut *tx)
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?
                .map(|r| r.get::<Decimal, _>("available_amount"))
                .unwrap_or(Decimal::ZERO);
            tx.commit()
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?;
            return Ok(bal);
        }

        let trade_type = match order.trade_type {
            TradeType::Forward => "forward",
            TradeType::Reverse => "reverse",
        };
        let delta = match order.trade_type {
            TradeType::Forward => order.trade_amount,
            TradeType::Reverse => -order.trade_amount,
        };

        let upd = if matches!(order.trade_type, TradeType::Reverse) {
            format!(
                "UPDATE `{schema}`.user_credit_account \
                 SET available_amount = available_amount + ?, update_time = NOW() \
                 WHERE user_id = ? AND available_amount >= ?"
            )
        } else {
            format!(
                "UPDATE `{schema}`.user_credit_account \
                 SET available_amount = available_amount + ?, total_amount = total_amount + ?, update_time = NOW() \
                 WHERE user_id = ?"
            )
        };

        let result = if matches!(order.trade_type, TradeType::Reverse) {
            sqlx::query(&upd)
                .bind(delta)
                .bind(&order.user_id)
                .bind(order.trade_amount)
                .execute(&mut *tx)
                .await
        } else {
            sqlx::query(&upd)
                .bind(delta)
                .bind(order.trade_amount)
                .bind(&order.user_id)
                .execute(&mut *tx)
                .await
        }
        .map_err(|e| BmError::Internal(e.to_string()))?;

        if result.rows_affected() == 0 {
            return Err(BmError::InsufficientCredit);
        }

        let ins = format!(
            "INSERT INTO `{schema}`.user_credit_order_{tb:03} \
             (user_id, order_id, trade_name, trade_type, trade_amount, out_business_no) \
             VALUES (?, ?, ?, ?, ?, ?)"
        );
        sqlx::query(&ins)
            .bind(&order.user_id)
            .bind(&order.order_id)
            .bind(&order.trade_name)
            .bind(trade_type)
            .bind(order.trade_amount)
            .bind(&order.out_business_no)
            .execute(&mut *tx)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;

        let bal_sql = format!(
            "SELECT available_amount FROM `{schema}`.user_credit_account WHERE user_id = ?"
        );
        let bal = sqlx::query(&bal_sql)
            .bind(&order.user_id)
            .fetch_one(&mut *tx)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?
            .get::<Decimal, _>("available_amount");

        tx.commit()
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(bal)
    }
}

#[async_trait]
impl AwardStore for MysqlStores {
    async fn save_award_record(&self, record: UserAwardRecord) -> Result<(), BmError> {
        let schema = self.schema(&record.user_id);
        let tb = self.tb(&record.user_id);
        // Best-effort upsert into user_award_record shard if table exists.
        let sql = format!(
            "INSERT INTO `{schema}`.user_award_record_{tb:03} \
             (user_id, activity_id, order_id, award_id, award_title, award_state) \
             VALUES (?, ?, ?, ?, ?, ?) \
             ON DUPLICATE KEY UPDATE award_state = VALUES(award_state), award_title = VALUES(award_title)"
        );
        let _ = sqlx::query(&sql)
            .bind(&record.user_id)
            .bind(record.activity_id)
            .bind(&record.order_id)
            .bind(record.award_id)
            .bind(&record.award_title)
            .bind(&record.award_state)
            .execute(&self.pool)
            .await;
        Ok(())
    }

    async fn enqueue_credit_award(&self, task: CreditAwardTask) -> Result<(), BmError> {
        let schema = self.schema(&task.user_id);
        let tb = self.tb(&task.user_id);
        let sql = format!(
            "INSERT IGNORE INTO `{schema}`.credit_award_task_{tb:03} \
             (user_id, award_order_id, credit_amount, state, retry_count) \
             VALUES (?, ?, ?, 'pending', 0)"
        );
        sqlx::query(&sql)
            .bind(&task.user_id)
            .bind(&task.award_order_id)
            .bind(task.credit_amount)
            .execute(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(())
    }

    async fn list_pending_credit_awards(
        &self,
        limit: usize,
    ) -> Result<Vec<CreditAwardTask>, BmError> {
        // Scan both schemas / tables for pending (learning topology).
        let mut out = Vec::new();
        for db in 1..=2u32 {
            for tb in 0..4u32 {
                let schema = format!("big_market_{db:02}");
                let sql = format!(
                    "SELECT user_id, award_order_id, credit_amount, state, retry_count, create_time \
                     FROM `{schema}`.credit_award_task_{tb:03} WHERE state = 'pending' LIMIT ?"
                );
                let rows = sqlx::query(&sql)
                    .bind(limit as i64)
                    .fetch_all(&self.pool)
                    .await;
                if let Ok(rows) = rows {
                    for r in rows {
                        out.push(CreditAwardTask {
                            user_id: r.get("user_id"),
                            award_order_id: r.get("award_order_id"),
                            credit_amount: r.get("credit_amount"),
                            state: AwardTaskState::Pending,
                            retry_count: r.get::<i8, _>("retry_count") as u32,
                            created_at: r.try_get("create_time").unwrap_or_else(|_| Utc::now()),
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

    async fn mark_credit_award(
        &self,
        user_id: &str,
        award_order_id: &str,
        state: AwardTaskState,
    ) -> Result<(), BmError> {
        let schema = self.schema(user_id);
        let tb = self.tb(user_id);
        let st = match state {
            AwardTaskState::Pending => "pending",
            AwardTaskState::Dispatched => "dispatched",
            AwardTaskState::Failed => "failed",
        };
        let sql = format!(
            "UPDATE `{schema}`.credit_award_task_{tb:03} \
             SET state = ?, retry_count = retry_count + 1, update_time = NOW() \
             WHERE user_id = ? AND award_order_id = ?"
        );
        sqlx::query(&sql)
            .bind(st)
            .bind(user_id)
            .bind(award_order_id)
            .execute(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(())
    }

    async fn enqueue_send_award_message(
        &self,
        user_id: &str,
        order_id: &str,
        award_id: i32,
        credit_amount: Money,
    ) -> Result<(), BmError> {
        // Directly write credit_award_task (skip MQ when using mysql path without RabbitMQ).
        self.enqueue_credit_award(CreditAwardTask {
            user_id: user_id.into(),
            award_order_id: order_id.into(),
            credit_amount,
            state: AwardTaskState::Pending,
            retry_count: 0,
            created_at: Utc::now(),
        })
        .await?;
        let _ = award_id;
        Ok(())
    }

    async fn take_send_award_messages(
        &self,
        _limit: usize,
    ) -> Result<Vec<SendAwardMessage>, BmError> {
        // MySQL path writes outbox directly in enqueue_send_award_message.
        Ok(vec![])
    }
}
