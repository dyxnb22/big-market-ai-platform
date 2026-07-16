//! MySQL `QuotaStore` — `raffle_activity_account` + decrement ledger + activity orders.

use async_trait::async_trait;
use bm_domain::{ActivityAccount, QuotaStore};
use bm_types::BmError;
use chrono::Utc;
use sqlx::{MySql, Row, Transaction};
use uuid::Uuid;

use crate::mysql_store::MysqlStores;

impl MysqlStores {
    fn current_month() -> String {
        Utc::now().format("%Y-%m").to_string()
    }

    fn current_day() -> String {
        Utc::now().format("%Y-%m-%d").to_string()
    }

    async fn load_account(
        &self,
        schema: &str,
        user_id: &str,
        activity_id: i64,
    ) -> Result<ActivityAccount, BmError> {
        Self::map_account_row(
            sqlx::query(&format!(
                "SELECT user_id, activity_id, total_count, total_count_surplus, day_count, \
                 day_count_surplus, month_count, month_count_surplus \
                 FROM `{schema}`.raffle_activity_account \
                 WHERE user_id = ? AND activity_id = ?"
            ))
            .bind(user_id)
            .bind(activity_id)
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?,
            user_id,
            activity_id,
        )
    }

    async fn load_account_tx(
        tx: &mut Transaction<'_, MySql>,
        schema: &str,
        user_id: &str,
        activity_id: i64,
    ) -> Result<ActivityAccount, BmError> {
        Self::map_account_row(
            sqlx::query(&format!(
                "SELECT user_id, activity_id, total_count, total_count_surplus, day_count, \
                 day_count_surplus, month_count, month_count_surplus \
                 FROM `{schema}`.raffle_activity_account \
                 WHERE user_id = ? AND activity_id = ?"
            ))
            .bind(user_id)
            .bind(activity_id)
            .fetch_optional(&mut **tx)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?,
            user_id,
            activity_id,
        )
    }

    fn map_account_row(
        row: Option<sqlx::mysql::MySqlRow>,
        user_id: &str,
        activity_id: i64,
    ) -> Result<ActivityAccount, BmError> {
        Ok(row
            .map(|r| ActivityAccount {
                user_id: r.get("user_id"),
                activity_id: r.get("activity_id"),
                total_count: r.get("total_count"),
                total_count_surplus: r.get("total_count_surplus"),
                day_count: r.get("day_count"),
                day_count_surplus: r.get("day_count_surplus"),
                month_count: r.get("month_count"),
                month_count_surplus: r.get("month_count_surplus"),
            })
            .unwrap_or_else(|| ActivityAccount::empty(user_id, activity_id)))
    }

    async fn add_month_day_quota(
        &self,
        tx: &mut Transaction<'_, MySql>,
        schema: &str,
        user_id: &str,
        activity_id: i64,
        count: i32,
    ) -> Result<(), BmError> {
        let month = Self::current_month();
        let day = Self::current_day();

        let month_upd = format!(
            "UPDATE `{schema}`.raffle_activity_account_month \
             SET month_count = month_count + ?, month_count_surplus = month_count_surplus + ?, \
                 update_time = NOW() \
             WHERE user_id = ? AND activity_id = ? AND month = ?"
        );
        let month_rows = sqlx::query(&month_upd)
            .bind(count)
            .bind(count)
            .bind(user_id)
            .bind(activity_id)
            .bind(&month)
            .execute(&mut **tx)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?
            .rows_affected();
        if month_rows == 0 {
            let month_ins = format!(
                "INSERT INTO `{schema}`.raffle_activity_account_month \
                 (user_id, activity_id, month, month_count, month_count_surplus) \
                 VALUES (?, ?, ?, ?, ?)"
            );
            sqlx::query(&month_ins)
                .bind(user_id)
                .bind(activity_id)
                .bind(&month)
                .bind(count)
                .bind(count)
                .execute(&mut **tx)
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?;
        }

        let day_upd = format!(
            "UPDATE `{schema}`.raffle_activity_account_day \
             SET day_count = day_count + ?, day_count_surplus = day_count_surplus + ?, \
                 update_time = NOW() \
             WHERE user_id = ? AND activity_id = ? AND day = ?"
        );
        let day_rows = sqlx::query(&day_upd)
            .bind(count)
            .bind(count)
            .bind(user_id)
            .bind(activity_id)
            .bind(&day)
            .execute(&mut **tx)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?
            .rows_affected();
        if day_rows == 0 {
            let day_ins = format!(
                "INSERT INTO `{schema}`.raffle_activity_account_day \
                 (user_id, activity_id, day, day_count, day_count_surplus) \
                 VALUES (?, ?, ?, ?, ?)"
            );
            sqlx::query(&day_ins)
                .bind(user_id)
                .bind(activity_id)
                .bind(&day)
                .bind(count)
                .bind(count)
                .execute(&mut **tx)
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?;
        }
        Ok(())
    }

    async fn decrement_month_day_quota(
        &self,
        tx: &mut Transaction<'_, MySql>,
        schema: &str,
        user_id: &str,
        activity_id: i64,
        total: &ActivityAccount,
    ) -> Result<bool, BmError> {
        let month = Self::current_month();
        let day = Self::current_day();

        let month_sel = format!(
            "SELECT month_count, month_count_surplus FROM `{schema}`.raffle_activity_account_month \
             WHERE user_id = ? AND activity_id = ? AND month = ?"
        );
        let month_row = sqlx::query(&month_sel)
            .bind(user_id)
            .bind(activity_id)
            .bind(&month)
            .fetch_optional(&mut **tx)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;

        if month_row.is_some() {
            let month_sub = format!(
                "UPDATE `{schema}`.raffle_activity_account_month \
                 SET month_count_surplus = month_count_surplus - 1, update_time = NOW() \
                 WHERE user_id = ? AND activity_id = ? AND month = ? AND month_count_surplus > 0"
            );
            if sqlx::query(&month_sub)
                .bind(user_id)
                .bind(activity_id)
                .bind(&month)
                .execute(&mut **tx)
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?
                .rows_affected()
                != 1
            {
                return Ok(false);
            }
            let acc_month_sub = format!(
                "UPDATE `{schema}`.raffle_activity_account \
                 SET month_count_surplus = month_count_surplus - 1, update_time = NOW() \
                 WHERE user_id = ? AND activity_id = ? AND month_count_surplus > 0"
            );
            if sqlx::query(&acc_month_sub)
                .bind(user_id)
                .bind(activity_id)
                .execute(&mut **tx)
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?
                .rows_affected()
                != 1
            {
                return Ok(false);
            }
        } else {
            let month_ins = format!(
                "INSERT INTO `{schema}`.raffle_activity_account_month \
                 (user_id, activity_id, month, month_count, month_count_surplus) \
                 VALUES (?, ?, ?, ?, ?)"
            );
            let surplus = total.month_count.saturating_sub(1);
            sqlx::query(&month_ins)
                .bind(user_id)
                .bind(activity_id)
                .bind(&month)
                .bind(total.month_count)
                .bind(surplus)
                .execute(&mut **tx)
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?;
            let acc_month_img = format!(
                "UPDATE `{schema}`.raffle_activity_account \
                 SET month_count_surplus = ? - 1, update_time = NOW() \
                 WHERE user_id = ? AND activity_id = ? AND ? > 0"
            );
            sqlx::query(&acc_month_img)
                .bind(total.month_count)
                .bind(user_id)
                .bind(activity_id)
                .bind(total.month_count)
                .execute(&mut **tx)
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?;
        }

        let day_sel = format!(
            "SELECT day_count, day_count_surplus FROM `{schema}`.raffle_activity_account_day \
             WHERE user_id = ? AND activity_id = ? AND day = ?"
        );
        let day_row = sqlx::query(&day_sel)
            .bind(user_id)
            .bind(activity_id)
            .bind(&day)
            .fetch_optional(&mut **tx)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;

        if day_row.is_some() {
            let day_sub = format!(
                "UPDATE `{schema}`.raffle_activity_account_day \
                 SET day_count_surplus = day_count_surplus - 1, update_time = NOW() \
                 WHERE user_id = ? AND activity_id = ? AND day = ? AND day_count_surplus > 0"
            );
            if sqlx::query(&day_sub)
                .bind(user_id)
                .bind(activity_id)
                .bind(&day)
                .execute(&mut **tx)
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?
                .rows_affected()
                != 1
            {
                return Ok(false);
            }
            let acc_day_sub = format!(
                "UPDATE `{schema}`.raffle_activity_account \
                 SET day_count_surplus = day_count_surplus - 1, update_time = NOW() \
                 WHERE user_id = ? AND activity_id = ? AND day_count_surplus > 0"
            );
            if sqlx::query(&acc_day_sub)
                .bind(user_id)
                .bind(activity_id)
                .execute(&mut **tx)
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?
                .rows_affected()
                != 1
            {
                return Ok(false);
            }
        } else {
            let day_ins = format!(
                "INSERT INTO `{schema}`.raffle_activity_account_day \
                 (user_id, activity_id, day, day_count, day_count_surplus) \
                 VALUES (?, ?, ?, ?, ?)"
            );
            let surplus = total.day_count.saturating_sub(1);
            sqlx::query(&day_ins)
                .bind(user_id)
                .bind(activity_id)
                .bind(&day)
                .bind(total.day_count)
                .bind(surplus)
                .execute(&mut **tx)
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?;
            let acc_day_img = format!(
                "UPDATE `{schema}`.raffle_activity_account \
                 SET day_count_surplus = ? - 1, update_time = NOW() \
                 WHERE user_id = ? AND activity_id = ? AND ? > 0"
            );
            sqlx::query(&acc_day_img)
                .bind(total.day_count)
                .bind(user_id)
                .bind(activity_id)
                .bind(total.day_count)
                .execute(&mut **tx)
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?;
        }
        Ok(true)
    }
}

#[async_trait]
impl QuotaStore for MysqlStores {
    async fn get_account(
        &self,
        user_id: &str,
        activity_id: i64,
    ) -> Result<ActivityAccount, BmError> {
        self.load_account(&self.schema(user_id), user_id, activity_id)
            .await
    }

    async fn add_quota(
        &self,
        user_id: &str,
        activity_id: i64,
        count: i32,
        out_business_no: &str,
    ) -> Result<ActivityAccount, BmError> {
        let schema = self.schema(user_id);
        let tb = self.tb(user_id);

        let dup_check = format!(
            "SELECT 1 FROM `{schema}`.raffle_activity_order_{tb:03} \
             WHERE out_business_no = ? LIMIT 1"
        );
        if sqlx::query(&dup_check)
            .bind(out_business_no)
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?
            .is_some()
        {
            return self.load_account(&schema, user_id, activity_id).await;
        }

        let mut tx = self
            .pool
            .begin()
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;

        let order_id: String = Uuid::new_v4().simple().to_string()[..12].into();
        let order_ins = format!(
            "INSERT INTO `{schema}`.raffle_activity_order_{tb:03} \
             (user_id, sku, activity_id, activity_name, strategy_id, order_id, order_time, \
              total_count, day_count, month_count, pay_amount, state, out_business_no) \
             VALUES (?, 0, ?, 'rust_quota', 0, ?, NOW(), ?, ?, ?, 0.00, 'complete', ?)"
        );
        if let Err(e) = sqlx::query(&order_ins)
            .bind(user_id)
            .bind(activity_id)
            .bind(&order_id)
            .bind(count)
            .bind(count)
            .bind(count)
            .bind(out_business_no)
            .execute(&mut *tx)
            .await
        {
            if MysqlStores::is_duplicate_key(&e) {
                tx.rollback()
                    .await
                    .map_err(|e| BmError::Internal(e.to_string()))?;
                return self.load_account(&schema, user_id, activity_id).await;
            }
            return Err(BmError::Internal(e.to_string()));
        }

        let acc_sel = format!(
            "SELECT 1 FROM `{schema}`.raffle_activity_account \
             WHERE user_id = ? AND activity_id = ?"
        );
        let exists = sqlx::query(&acc_sel)
            .bind(user_id)
            .bind(activity_id)
            .fetch_optional(&mut *tx)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?
            .is_some();

        if exists {
            let acc_upd = format!(
                "UPDATE `{schema}`.raffle_activity_account \
                 SET total_count = total_count + ?, total_count_surplus = total_count_surplus + ?, \
                     day_count = day_count + ?, day_count_surplus = day_count_surplus + ?, \
                     month_count = month_count + ?, month_count_surplus = month_count_surplus + ?, \
                     update_time = NOW() \
                 WHERE user_id = ? AND activity_id = ?"
            );
            sqlx::query(&acc_upd)
                .bind(count)
                .bind(count)
                .bind(count)
                .bind(count)
                .bind(count)
                .bind(count)
                .bind(user_id)
                .bind(activity_id)
                .execute(&mut *tx)
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?;
        } else {
            let acc_ins = format!(
                "INSERT INTO `{schema}`.raffle_activity_account \
                 (user_id, activity_id, total_count, total_count_surplus, day_count, day_count_surplus, \
                  month_count, month_count_surplus) \
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            );
            sqlx::query(&acc_ins)
                .bind(user_id)
                .bind(activity_id)
                .bind(count)
                .bind(count)
                .bind(count)
                .bind(count)
                .bind(count)
                .bind(count)
                .execute(&mut *tx)
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?;
        }

        self.add_month_day_quota(&mut tx, &schema, user_id, activity_id, count)
            .await?;

        tx.commit()
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        self.load_account(&schema, user_id, activity_id).await
    }

    async fn consume_one(
        &self,
        user_id: &str,
        activity_id: i64,
        out_business_no: &str,
    ) -> Result<(), BmError> {
        let schema = self.schema(user_id);
        let tb = self.tb(user_id);

        let mut tx = self
            .pool
            .begin()
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;

        let ledger_ins = format!(
            "INSERT INTO `{schema}`.raffle_quota_decrement_ledger_{tb:03} \
             (user_id, activity_id, out_business_no, status) VALUES (?, ?, ?, 'applied')"
        );
        match sqlx::query(&ledger_ins)
            .bind(user_id)
            .bind(activity_id)
            .bind(out_business_no)
            .execute(&mut *tx)
            .await
        {
            Ok(_) => {}
            Err(e) if MysqlStores::is_duplicate_key(&e) => {
                tx.commit()
                    .await
                    .map_err(|e| BmError::Internal(e.to_string()))?;
                return Ok(());
            }
            Err(e) => return Err(BmError::Internal(e.to_string())),
        }

        let total_sub = format!(
            "UPDATE `{schema}`.raffle_activity_account \
             SET total_count_surplus = total_count_surplus - 1, update_time = NOW() \
             WHERE user_id = ? AND activity_id = ? AND total_count_surplus > 0"
        );
        if sqlx::query(&total_sub)
            .bind(user_id)
            .bind(activity_id)
            .execute(&mut *tx)
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?
            .rows_affected()
            != 1
        {
            tx.rollback()
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?;
            return Err(BmError::IllegalParam("抽奖次数不足".into()));
        }

        let total = Self::load_account_tx(&mut tx, &schema, user_id, activity_id).await?;
        if !self
            .decrement_month_day_quota(&mut tx, &schema, user_id, activity_id, &total)
            .await?
        {
            tx.rollback()
                .await
                .map_err(|e| BmError::Internal(e.to_string()))?;
            return Err(BmError::IllegalParam("抽奖次数不足".into()));
        }

        tx.commit()
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(())
    }
}
