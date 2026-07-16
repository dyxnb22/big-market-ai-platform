//! MySQL `ChatStore` — `chat_credit_session` + in-process idempotent cache.

use async_trait::async_trait;
use bm_domain::{ChatCreditSession, ChatStore, RefundState};
use bm_types::{BmError, Money};
use rust_decimal::Decimal;
use sqlx::Row;

use crate::mysql_store::MysqlStores;

fn refund_state_to_db(state: &RefundState) -> &'static str {
    match state {
        RefundState::None => "none",
        RefundState::Pending => "pending",
        RefundState::Refunding => "refunding",
        RefundState::Refunded => "refunded",
    }
}

fn refund_state_from_db(s: &str) -> RefundState {
    match s {
        "pending" => RefundState::Pending,
        "refunding" => RefundState::Refunding,
        "refunded" => RefundState::Refunded,
        _ => RefundState::None,
    }
}

impl MysqlStores {
    fn idem_key(user_id: &str, request_id: &str) -> String {
        format!("{user_id}:{request_id}")
    }

    async fn load_chat_session(
        &self,
        schema: &str,
        user_id: &str,
        request_id: &str,
    ) -> Result<Option<ChatCreditSession>, BmError> {
        let sql = format!(
            "SELECT user_id, request_id, deduct_amount, refund_state \
             FROM `{schema}`.chat_credit_session \
             WHERE user_id = ? AND request_id = ?"
        );
        let row = sqlx::query(&sql)
            .bind(user_id)
            .bind(request_id)
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(row.map(|r| ChatCreditSession {
            user_id: r.get("user_id"),
            request_id: r.get("request_id"),
            amount: Decimal::from(r.get::<i32, _>("deduct_amount")),
            refund_state: refund_state_from_db(r.get::<String, _>("refund_state").as_str()),
        }))
    }
}

#[async_trait]
impl ChatStore for MysqlStores {
    async fn get_idempotent(
        &self,
        user_id: &str,
        request_id: &str,
    ) -> Result<Option<Money>, BmError> {
        let key = Self::idem_key(user_id, request_id);
        let g = self.chat_idem.lock().await;
        Ok(g.get(&key).copied())
    }

    async fn put_idempotent(
        &self,
        user_id: &str,
        request_id: &str,
        balance: Money,
    ) -> Result<(), BmError> {
        let key = Self::idem_key(user_id, request_id);
        self.chat_idem.lock().await.insert(key, balance);
        Ok(())
    }

    async fn record_deduction(&self, session: ChatCreditSession) -> Result<(), BmError> {
        let schema = self.schema(&session.user_id);
        let sql = format!(
            "INSERT IGNORE INTO `{schema}`.chat_credit_session \
             (user_id, request_id, deducted, deduct_amount, deduct_state, refund_state) \
             VALUES (?, ?, 1, ?, 'deducted', ?)"
        );
        let amount_i32: i32 = session
            .amount
            .try_into()
            .map_err(|_| BmError::Internal("chat deduct amount overflow".into()))?;
        sqlx::query(&sql)
            .bind(&session.user_id)
            .bind(&session.request_id)
            .bind(amount_i32)
            .bind(refund_state_to_db(&session.refund_state))
            .execute(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(())
    }

    async fn get_session(
        &self,
        user_id: &str,
        request_id: &str,
    ) -> Result<Option<ChatCreditSession>, BmError> {
        self.load_chat_session(&self.schema(user_id), user_id, request_id)
            .await
    }

    async fn set_refund_state(
        &self,
        user_id: &str,
        request_id: &str,
        state: RefundState,
    ) -> Result<(), BmError> {
        let schema = self.schema(user_id);
        let sql = format!(
            "UPDATE `{schema}`.chat_credit_session \
             SET refund_state = ?, update_time = NOW() \
             WHERE user_id = ? AND request_id = ?"
        );
        sqlx::query(&sql)
            .bind(refund_state_to_db(&state))
            .bind(user_id)
            .bind(request_id)
            .execute(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(())
    }

    async fn list_pending_refunds(&self, limit: usize) -> Result<Vec<ChatCreditSession>, BmError> {
        let mut out = Vec::new();
        for db in 1..=2u32 {
            let schema = format!("big_market_{db:02}");
            let sql = format!(
                "SELECT user_id, request_id, deduct_amount, refund_state \
                 FROM `{schema}`.chat_credit_session \
                 WHERE refund_state IN ('pending', 'refunding') \
                 ORDER BY create_time LIMIT ?"
            );
            let rows = sqlx::query(&sql)
                .bind(limit as i64)
                .fetch_all(&self.pool)
                .await;
            if let Ok(rows) = rows {
                for r in rows {
                    out.push(ChatCreditSession {
                        user_id: r.get("user_id"),
                        request_id: r.get("request_id"),
                        amount: Decimal::from(r.get::<i32, _>("deduct_amount")),
                        refund_state: refund_state_from_db(
                            r.get::<String, _>("refund_state").as_str(),
                        ),
                    });
                    if out.len() >= limit {
                        return Ok(out);
                    }
                }
            }
        }
        Ok(out)
    }
}
