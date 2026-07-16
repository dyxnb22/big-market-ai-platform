//! In-memory backend implementing all domain ports. Seeds demo learning data.
//! Supports JSON snapshot for file-backed durability (`persist` / `load_snapshot`).

use async_trait::async_trait;
use bm_domain::*;
use bm_types::{money, BmError, Money};
use chrono::{DateTime, Utc};
use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet, VecDeque};
use std::sync::Arc;
use tokio::sync::Mutex;

#[derive(Default, Serialize, Deserialize)]
struct Inner {
    credits: HashMap<String, Money>,
    credit_orders: HashSet<String>,
    #[serde(default)]
    quotas: HashMap<String, ActivityAccount>,
    quota_ops: HashSet<String>,
    awards: HashMap<String, UserAwardRecord>,
    credit_tasks: HashMap<String, CreditAwardTask>,
    send_award_q: VecDeque<SendAwardMessage>,
    chat_idem: HashMap<String, Money>,
    chat_sessions: HashMap<String, ChatCreditSession>,
    sign_days: HashSet<String>,
    admin: HashMap<String, String>,
    revoked: HashMap<String, i64>,
    skus: HashMap<i64, SkuProduct>,
    #[serde(default)]
    stage: HashMap<String, i64>,
    #[serde(default)]
    stocks: HashMap<String, i64>,
    #[serde(default)]
    dirty_stocks: HashSet<String>,
}

fn quota_key(user_id: &str, activity_id: i64) -> String {
    format!("{user_id}:{activity_id}")
}

fn stage_key(channel: &str, source: &str) -> String {
    format!("{channel}:{source}")
}

pub struct MemoryBackend {
    inner: Mutex<Inner>,
}

impl MemoryBackend {
    pub fn new_seeded(initial_credit: Money) -> Arc<Self> {
        let mut inner = Inner::default();
        seed_demo(&mut inner, initial_credit);
        Arc::new(Self {
            inner: Mutex::new(inner),
        })
    }

    pub async fn snapshot_json(&self) -> Result<String, BmError> {
        let g = self.inner.lock().await;
        serde_json::to_string_pretty(&*g).map_err(|e| BmError::Internal(e.to_string()))
    }

    pub async fn load_snapshot_json(&self, json: &str) -> Result<(), BmError> {
        let snap: Inner =
            serde_json::from_str(json).map_err(|e| BmError::Internal(e.to_string()))?;
        let mut g = self.inner.lock().await;
        *g = snap;
        Ok(())
    }
}

fn seed_demo(inner: &mut Inner, initial_credit: Money) {
    inner.credits.insert("xiaofuge".into(), initial_credit);
    inner.credits.insert("admin".into(), initial_credit);
    inner.stage.insert(stage_key("c01", "s01"), 100401);
    inner.skus.insert(
        9901,
        SkuProduct {
            sku: 9901,
            activity_id: 100401,
            product_name: "抽奖次数兑换".into(),
            product_amount: money("5.00"),
            quota_count: 1,
        },
    );
    inner
        .admin
        .insert("stage.activity.c01.s01".into(), "100401".into());
    inner.stocks.insert(activity_stock_key(100401), 10_000);
    inner.stocks.insert(award_stock_key(101), 1_000);
}

#[async_trait]
impl CreditStore for MemoryBackend {
    async fn get_balance(&self, user_id: &str) -> Result<Money, BmError> {
        let g = self.inner.lock().await;
        Ok(*g.credits.get(user_id).unwrap_or(&Decimal::ZERO))
    }

    async fn ensure_account(&self, user_id: &str, initial: Money) -> Result<(), BmError> {
        let mut g = self.inner.lock().await;
        g.credits.entry(user_id.into()).or_insert(initial);
        Ok(())
    }

    async fn apply_trade(&self, order: CreditOrder) -> Result<Money, BmError> {
        let mut g = self.inner.lock().await;
        if g.credit_orders.contains(&order.out_business_no) {
            return Ok(*g.credits.get(&order.user_id).unwrap_or(&Decimal::ZERO));
        }
        let bal = g
            .credits
            .entry(order.user_id.clone())
            .or_insert(Decimal::ZERO);
        match order.trade_type {
            TradeType::Forward => {
                *bal += order.trade_amount;
            }
            TradeType::Reverse => {
                if *bal < order.trade_amount {
                    return Err(BmError::InsufficientCredit);
                }
                *bal -= order.trade_amount;
            }
        }
        let result = *bal;
        g.credit_orders.insert(order.out_business_no);
        Ok(result)
    }
}

#[async_trait]
impl QuotaStore for MemoryBackend {
    async fn get_account(
        &self,
        user_id: &str,
        activity_id: i64,
    ) -> Result<ActivityAccount, BmError> {
        let g = self.inner.lock().await;
        Ok(g.quotas
            .get(&quota_key(user_id, activity_id))
            .cloned()
            .unwrap_or_else(|| ActivityAccount::empty(user_id, activity_id)))
    }

    async fn add_quota(
        &self,
        user_id: &str,
        activity_id: i64,
        count: i32,
        out_business_no: &str,
    ) -> Result<ActivityAccount, BmError> {
        let mut g = self.inner.lock().await;
        let key = quota_key(user_id, activity_id);
        if g.quota_ops.contains(out_business_no) {
            return Ok(g
                .quotas
                .get(&key)
                .cloned()
                .unwrap_or_else(|| ActivityAccount::empty(user_id, activity_id)));
        }
        let acc = g
            .quotas
            .entry(key)
            .or_insert_with(|| ActivityAccount::empty(user_id, activity_id));
        acc.total_count += count;
        acc.total_count_surplus += count;
        acc.day_count += count;
        acc.day_count_surplus += count;
        acc.month_count += count;
        acc.month_count_surplus += count;
        let result = acc.clone();
        g.quota_ops.insert(out_business_no.into());
        Ok(result)
    }

    async fn consume_one(
        &self,
        user_id: &str,
        activity_id: i64,
        out_business_no: &str,
    ) -> Result<(), BmError> {
        let mut g = self.inner.lock().await;
        if g.quota_ops.contains(out_business_no) {
            return Ok(());
        }
        let acc = g
            .quotas
            .entry(quota_key(user_id, activity_id))
            .or_insert_with(|| ActivityAccount::empty(user_id, activity_id));
        if acc.total_count_surplus <= 0 {
            return Err(BmError::IllegalParam("抽奖次数不足".into()));
        }
        acc.total_count_surplus -= 1;
        if acc.day_count_surplus > 0 {
            acc.day_count_surplus -= 1;
        }
        if acc.month_count_surplus > 0 {
            acc.month_count_surplus -= 1;
        }
        g.quota_ops.insert(out_business_no.into());
        Ok(())
    }
}

#[async_trait]
impl CatalogStore for MemoryBackend {
    async fn stage_activity_id(&self, channel: &str, source: &str) -> Result<i64, BmError> {
        let g = self.inner.lock().await;
        g.stage
            .get(&stage_key(channel, source))
            .copied()
            .ok_or_else(|| BmError::NotFound("stage activity missing".into()))
    }

    async fn list_sku(&self, activity_id: i64) -> Result<Vec<SkuProduct>, BmError> {
        let g = self.inner.lock().await;
        Ok(g.skus
            .values()
            .filter(|s| s.activity_id == activity_id)
            .cloned()
            .collect())
    }

    async fn get_sku(&self, sku: i64) -> Result<SkuProduct, BmError> {
        let g = self.inner.lock().await;
        g.skus
            .get(&sku)
            .cloned()
            .ok_or_else(|| BmError::NotFound(format!("sku {sku}")))
    }

    async fn armory(&self, _activity_id: i64) -> Result<bool, BmError> {
        Ok(true)
    }
}

#[async_trait]
impl AwardStore for MemoryBackend {
    async fn save_award_record(&self, record: UserAwardRecord) -> Result<(), BmError> {
        let mut g = self.inner.lock().await;
        g.awards.insert(record.order_id.clone(), record);
        Ok(())
    }

    async fn enqueue_credit_award(&self, task: CreditAwardTask) -> Result<(), BmError> {
        let mut g = self.inner.lock().await;
        g.credit_tasks
            .entry(task.award_order_id.clone())
            .or_insert(task);
        Ok(())
    }

    async fn list_pending_credit_awards(
        &self,
        limit: usize,
    ) -> Result<Vec<CreditAwardTask>, BmError> {
        let g = self.inner.lock().await;
        Ok(g.credit_tasks
            .values()
            .filter(|t| t.state == AwardTaskState::Pending)
            .take(limit)
            .cloned()
            .collect())
    }

    async fn mark_credit_award(
        &self,
        _user_id: &str,
        award_order_id: &str,
        state: AwardTaskState,
    ) -> Result<(), BmError> {
        let mut g = self.inner.lock().await;
        if let Some(t) = g.credit_tasks.get_mut(award_order_id) {
            t.state = state;
            t.retry_count += 1;
        }
        Ok(())
    }

    async fn enqueue_send_award_message(
        &self,
        user_id: &str,
        order_id: &str,
        award_id: i32,
        credit_amount: Money,
    ) -> Result<(), BmError> {
        let mut g = self.inner.lock().await;
        g.send_award_q.push_back(SendAwardMessage {
            user_id: user_id.into(),
            order_id: order_id.into(),
            award_id,
            credit_amount,
        });
        Ok(())
    }

    async fn take_send_award_messages(
        &self,
        limit: usize,
    ) -> Result<Vec<SendAwardMessage>, BmError> {
        let mut g = self.inner.lock().await;
        let mut out = Vec::new();
        for _ in 0..limit {
            if let Some(m) = g.send_award_q.pop_front() {
                out.push(m);
            } else {
                break;
            }
        }
        Ok(out)
    }
}

#[async_trait]
impl ChatStore for MemoryBackend {
    async fn get_idempotent(
        &self,
        user_id: &str,
        request_id: &str,
    ) -> Result<Option<Money>, BmError> {
        let g = self.inner.lock().await;
        Ok(g.chat_idem.get(&format!("{user_id}:{request_id}")).copied())
    }

    async fn put_idempotent(
        &self,
        user_id: &str,
        request_id: &str,
        balance: Money,
    ) -> Result<(), BmError> {
        let mut g = self.inner.lock().await;
        g.chat_idem
            .insert(format!("{user_id}:{request_id}"), balance);
        Ok(())
    }

    async fn record_deduction(&self, session: ChatCreditSession) -> Result<(), BmError> {
        let mut g = self.inner.lock().await;
        let key = format!("{}:{}", session.user_id, session.request_id);
        if g.chat_sessions.contains_key(&key) {
            return Ok(());
        }
        g.chat_sessions.insert(key, session);
        Ok(())
    }

    async fn get_session(
        &self,
        user_id: &str,
        request_id: &str,
    ) -> Result<Option<ChatCreditSession>, BmError> {
        let g = self.inner.lock().await;
        Ok(g.chat_sessions
            .get(&format!("{user_id}:{request_id}"))
            .cloned())
    }

    async fn set_refund_state(
        &self,
        user_id: &str,
        request_id: &str,
        state: RefundState,
    ) -> Result<(), BmError> {
        let mut g = self.inner.lock().await;
        if let Some(s) = g.chat_sessions.get_mut(&format!("{user_id}:{request_id}")) {
            s.refund_state = state;
        }
        Ok(())
    }

    async fn list_pending_refunds(&self, limit: usize) -> Result<Vec<ChatCreditSession>, BmError> {
        let g = self.inner.lock().await;
        Ok(g.chat_sessions
            .values()
            .filter(|s| {
                s.refund_state == RefundState::Pending || s.refund_state == RefundState::Refunding
            })
            .take(limit)
            .cloned()
            .collect())
    }
}

#[async_trait]
impl RebateStore for MemoryBackend {
    async fn has_signed_today(&self, user_id: &str, day: &str) -> Result<bool, BmError> {
        let g = self.inner.lock().await;
        Ok(g.sign_days.contains(&format!("{user_id}:{day}")))
    }

    async fn mark_signed(&self, user_id: &str, day: &str) -> Result<(), BmError> {
        let mut g = self.inner.lock().await;
        g.sign_days.insert(format!("{user_id}:{day}"));
        Ok(())
    }
}

#[async_trait]
impl AdminStore for MemoryBackend {
    async fn get(&self, key: &str) -> Result<Option<String>, BmError> {
        let g = self.inner.lock().await;
        Ok(g.admin.get(key).cloned())
    }

    async fn set(&self, key: &str, value: &str) -> Result<(), BmError> {
        let mut g = self.inner.lock().await;
        g.admin.insert(key.into(), value.into());
        Ok(())
    }

    async fn list(&self) -> Result<Vec<(String, String)>, BmError> {
        let g = self.inner.lock().await;
        Ok(g.admin
            .iter()
            .map(|(k, v)| (k.clone(), v.clone()))
            .collect())
    }
}

#[async_trait]
impl TokenRevocation for MemoryBackend {
    async fn revoke(&self, jti: &str, ttl_secs: u64) -> Result<(), BmError> {
        let mut g = self.inner.lock().await;
        let exp = Utc::now().timestamp() + ttl_secs as i64;
        g.revoked.insert(jti.into(), exp);
        Ok(())
    }

    async fn is_revoked(&self, jti: &str) -> Result<bool, BmError> {
        let mut g = self.inner.lock().await;
        let now = Utc::now().timestamp();
        if let Some(exp) = g.revoked.get(jti).copied() {
            if exp >= now {
                return Ok(true);
            }
            g.revoked.remove(jti);
        }
        Ok(false)
    }
}

#[async_trait]
impl StrategyStore for MemoryBackend {
    async fn award_weights(&self, activity_id: i64) -> Result<Vec<AwardWeight>, BmError> {
        Ok(default_stage_weights(activity_id))
    }
}

#[async_trait]
impl StockStore for MemoryBackend {
    async fn get_stock(&self, key: &str) -> Result<i64, BmError> {
        let g = self.inner.lock().await;
        Ok(*g.stocks.get(key).unwrap_or(&0))
    }

    async fn set_stock(&self, key: &str, qty: i64) -> Result<(), BmError> {
        let mut g = self.inner.lock().await;
        g.stocks.insert(key.into(), qty);
        g.dirty_stocks.insert(key.into());
        Ok(())
    }

    async fn decr_stock(&self, key: &str, delta: i64) -> Result<bool, BmError> {
        let mut g = self.inner.lock().await;
        let cur = g.stocks.entry(key.into()).or_insert(0);
        if *cur < delta {
            return Ok(false);
        }
        *cur -= delta;
        g.dirty_stocks.insert(key.into());
        Ok(true)
    }

    async fn list_dirty(&self) -> Result<Vec<(String, i64)>, BmError> {
        let g = self.inner.lock().await;
        Ok(g.dirty_stocks
            .iter()
            .filter_map(|k| g.stocks.get(k).map(|v| (k.clone(), *v)))
            .collect())
    }

    async fn clear_dirty(&self, keys: &[String]) -> Result<(), BmError> {
        let mut g = self.inner.lock().await;
        for k in keys {
            g.dirty_stocks.remove(k);
        }
        Ok(())
    }
}

/// Shared handle implementing every store trait via Arc.
#[derive(Clone)]
pub struct SharedMemory {
    pub backend: Arc<MemoryBackend>,
}

impl SharedMemory {
    pub fn seeded(initial: Money) -> Self {
        Self {
            backend: MemoryBackend::new_seeded(initial),
        }
    }

    pub async fn load_or_seed(path: &std::path::Path, initial: Money) -> Result<Self, BmError> {
        let backend = MemoryBackend::new_seeded(initial);
        if path.exists() {
            let json =
                std::fs::read_to_string(path).map_err(|e| BmError::Internal(e.to_string()))?;
            if !json.trim().is_empty() {
                backend.load_snapshot_json(&json).await?;
            }
        }
        Ok(Self { backend })
    }

    pub async fn persist(&self, path: &std::path::Path) -> Result<(), BmError> {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| BmError::Internal(e.to_string()))?;
        }
        let json = self.backend.snapshot_json().await?;
        let tmp = path.with_extension("json.tmp");
        std::fs::write(&tmp, json).map_err(|e| BmError::Internal(e.to_string()))?;
        std::fs::rename(&tmp, path).map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(())
    }
}

#[allow(dead_code)]
fn _keep_datetime(_: DateTime<Utc>) {}
