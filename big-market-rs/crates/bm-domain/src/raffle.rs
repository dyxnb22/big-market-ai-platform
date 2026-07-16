use crate::auth::{JwtService, TOKEN_TTL_SECS};
use crate::ports::*;
use bm_types::{money, BmError, Money};
use chrono::Utc;
use std::sync::Arc;
use uuid::Uuid;

/// Deterministic award for default learning stage (strategy 10007 → award 101).
pub const DEFAULT_ACTIVITY_ID: i64 = 100401;
pub const DEFAULT_AWARD_ID: i32 = 101;
pub const DEFAULT_AWARD_TITLE: &str = "1等奖：积分5";
pub const DEFAULT_SKU: i64 = 9901;

pub struct RaffleService {
    pub catalog: Arc<dyn CatalogStore>,
    pub quota: Arc<dyn QuotaStore>,
    pub credit: Arc<dyn CreditStore>,
    pub award: Arc<dyn AwardStore>,
    pub strategy: Arc<dyn StrategyStore>,
    pub stock: Arc<dyn StockStore>,
}

impl RaffleService {
    pub async fn stage_activity_id(&self, channel: &str, source: &str) -> Result<i64, BmError> {
        self.catalog.stage_activity_id(channel, source).await
    }

    pub async fn armory(&self, activity_id: i64) -> Result<bool, BmError> {
        let ok = self.catalog.armory(activity_id).await?;
        // Seed activity/award stock counters if missing (align Update*StockJob learning path).
        let key = activity_stock_key(activity_id);
        if self.stock.get_stock(&key).await? <= 0 {
            self.stock.set_stock(&key, 10_000).await?;
        }
        for w in self.strategy.award_weights(activity_id).await? {
            let ak = award_stock_key(w.award_id);
            if self.stock.get_stock(&ak).await? <= 0 {
                self.stock.set_stock(&ak, 1_000).await?;
            }
        }
        Ok(ok)
    }

    pub async fn query_credit(&self, user_id: &str) -> Result<Money, BmError> {
        self.credit.get_balance(user_id).await
    }

    pub async fn query_account(
        &self,
        user_id: &str,
        activity_id: i64,
    ) -> Result<ActivityAccount, BmError> {
        self.quota.get_account(user_id, activity_id).await
    }

    pub async fn list_sku(&self, activity_id: i64) -> Result<Vec<SkuProduct>, BmError> {
        self.catalog.list_sku(activity_id).await
    }

    pub async fn exchange_sku(
        &self,
        user_id: &str,
        sku: i64,
        request_id: &str,
    ) -> Result<bool, BmError> {
        if request_id.trim().is_empty() {
            return Err(BmError::IllegalParam("requestId required".into()));
        }
        let product = self.catalog.get_sku(sku).await?;
        let out_no = sku_out_business_no(user_id, sku, request_id);
        let order = CreditOrder {
            user_id: user_id.into(),
            order_id: Uuid::new_v4().to_string(),
            out_business_no: out_no.clone(),
            trade_name: format!("sku_exchange_{sku}"),
            trade_type: TradeType::Reverse,
            trade_amount: product.product_amount,
        };
        self.credit.apply_trade(order).await?;
        self.quota
            .add_quota(user_id, product.activity_id, product.quota_count, &out_no)
            .await?;
        Ok(true)
    }

    pub async fn draw(&self, user_id: &str, activity_id: i64) -> Result<DrawResult, BmError> {
        let consume_no = format!("draw_{user_id}_{activity_id}_{}", Uuid::new_v4());
        self.quota
            .consume_one(user_id, activity_id, &consume_no)
            .await?;

        let weights = self.strategy.award_weights(activity_id).await?;
        let picked = crate::strategy::pick_weighted(&weights)
            .ok_or_else(|| BmError::Internal("no award weight".into()))?;

        // Stock gate (soft): refuse if award stock exhausted.
        let ak = award_stock_key(picked.award_id);
        if !self.stock.decr_stock(&ak, 1).await? {
            return Err(BmError::IllegalParam("奖品库存不足".into()));
        }
        let _ = self
            .stock
            .decr_stock(&activity_stock_key(activity_id), 1)
            .await;

        let award_id = picked.award_id;
        let award_title = picked.award_title.clone();
        let award_index = picked.award_index;
        let order_id = Uuid::new_v4().to_string();
        let credit_amount = picked.credit_amount;

        self.award
            .save_award_record(UserAwardRecord {
                user_id: user_id.into(),
                activity_id,
                order_id: order_id.clone(),
                award_id,
                award_title: award_title.clone(),
                award_state: "create".into(),
            })
            .await?;

        if credit_amount > money("0") {
            self.award
                .enqueue_send_award_message(user_id, &order_id, award_id, credit_amount)
                .await?;
        }

        self.award
            .save_award_record(UserAwardRecord {
                user_id: user_id.into(),
                activity_id,
                order_id: order_id.clone(),
                award_id,
                award_title: award_title.clone(),
                award_state: "completed".into(),
            })
            .await?;

        Ok(DrawResult {
            award_id,
            award_title,
            award_index,
            order_id,
        })
    }
}

#[derive(Debug, Clone)]
pub struct DrawResult {
    pub award_id: i32,
    pub award_title: String,
    pub award_index: i32,
    pub order_id: String,
}

pub struct AwardDispatchService {
    pub award: Arc<dyn AwardStore>,
    pub credit: Arc<dyn CreditStore>,
}

impl AwardDispatchService {
    /// Ingest one send_award message → write credit_award_task (pending).
    /// Idempotent when the same award_order_id is delivered twice.
    pub async fn ingest_send_award(&self, m: SendAwardMessage) -> Result<(), BmError> {
        self.award
            .enqueue_credit_award(CreditAwardTask {
                user_id: m.user_id,
                award_order_id: m.order_id,
                credit_amount: m.credit_amount,
                state: AwardTaskState::Pending,
                retry_count: 0,
                created_at: Utc::now(),
            })
            .await
    }

    /// Consume send_award messages → write credit_award_task (pending).
    pub async fn consume_send_award(&self, limit: usize) -> Result<usize, BmError> {
        let msgs = self.award.take_send_award_messages(limit).await?;
        let n = msgs.len();
        for m in msgs {
            self.ingest_send_award(m).await?;
        }
        Ok(n)
    }

    /// Drain local outbox without creating tasks (for RabbitMQ publish bridge).
    pub async fn take_send_award_for_publish(
        &self,
        limit: usize,
    ) -> Result<Vec<SendAwardMessage>, BmError> {
        self.award.take_send_award_messages(limit).await
    }

    /// pending → account credit → dispatched (idempotent via award_order_id as out_business_no).
    pub async fn dispatch_pending(&self, limit: usize) -> Result<usize, BmError> {
        let tasks = self.award.list_pending_credit_awards(limit).await?;
        let mut done = 0;
        for t in tasks {
            let order = CreditOrder {
                user_id: t.user_id.clone(),
                order_id: Uuid::new_v4().to_string(),
                out_business_no: t.award_order_id.clone(),
                trade_name: "award_credit".into(),
                trade_type: TradeType::Forward,
                trade_amount: t.credit_amount,
            };
            match self.credit.apply_trade(order).await {
                Ok(_) => {
                    self.award
                        .mark_credit_award(
                            &t.user_id,
                            &t.award_order_id,
                            AwardTaskState::Dispatched,
                        )
                        .await?;
                    done += 1;
                }
                Err(e) => {
                    tracing::warn!(error=%e, order=%t.award_order_id, "dispatch failed");
                    self.award
                        .mark_credit_award(&t.user_id, &t.award_order_id, AwardTaskState::Failed)
                        .await?;
                }
            }
        }
        Ok(done)
    }
}

pub struct ChatBillingService {
    pub credit: Arc<dyn CreditStore>,
    pub chat: Arc<dyn ChatStore>,
}

impl ChatBillingService {
    pub async fn deduct(
        &self,
        user_id: &str,
        request_id: &str,
        amount: Money,
    ) -> Result<Money, BmError> {
        if request_id.trim().is_empty() {
            return Err(BmError::IllegalParam("requestId required".into()));
        }
        if let Some(bal) = self.chat.get_idempotent(user_id, request_id).await? {
            return Ok(bal);
        }
        let out_no = chat_out_business_no(user_id, request_id);
        let bal = self
            .credit
            .apply_trade(CreditOrder {
                user_id: user_id.into(),
                order_id: Uuid::new_v4().to_string(),
                out_business_no: out_no,
                trade_name: "chat_deduct".into(),
                trade_type: TradeType::Reverse,
                trade_amount: amount,
            })
            .await?;
        self.chat
            .record_deduction(ChatCreditSession {
                user_id: user_id.into(),
                request_id: request_id.into(),
                amount,
                refund_state: RefundState::None,
            })
            .await?;
        self.chat.put_idempotent(user_id, request_id, bal).await?;
        Ok(bal)
    }

    pub async fn refund(&self, user_id: &str, request_id: &str) -> Result<Money, BmError> {
        let session = self
            .chat
            .get_session(user_id, request_id)
            .await?
            .ok_or_else(|| BmError::NotFound("chat session missing".into()))?;
        if session.refund_state == RefundState::Refunded {
            return self.credit.get_balance(user_id).await;
        }
        self.chat
            .set_refund_state(user_id, request_id, RefundState::Refunding)
            .await?;
        let out_no = chat_refund_out_business_no(user_id, request_id);
        let bal = self
            .credit
            .apply_trade(CreditOrder {
                user_id: user_id.into(),
                order_id: Uuid::new_v4().to_string(),
                out_business_no: out_no,
                trade_name: "chat_refund".into(),
                trade_type: TradeType::Forward,
                trade_amount: session.amount,
            })
            .await?;
        self.chat
            .set_refund_state(user_id, request_id, RefundState::Refunded)
            .await?;
        Ok(bal)
    }

    pub async fn reconcile_pending(&self, limit: usize) -> Result<usize, BmError> {
        let pending = self.chat.list_pending_refunds(limit).await?;
        let mut n = 0;
        for s in pending {
            self.refund(&s.user_id, &s.request_id).await?;
            n += 1;
        }
        Ok(n)
    }
}

pub struct RebateService {
    pub rebate: Arc<dyn RebateStore>,
    pub credit: Arc<dyn CreditStore>,
    pub outbox: Arc<dyn RebateOutbox>,
}

impl RebateService {
    pub async fn is_signed_today(&self, user_id: &str) -> Result<bool, BmError> {
        let day = Utc::now().format("%Y-%m-%d").to_string();
        self.rebate.has_signed_today(user_id, &day).await
    }

    pub async fn calendar_sign(&self, user_id: &str) -> Result<(bool, Money, Money), BmError> {
        let day = Utc::now().format("%Y-%m-%d").to_string();
        if self.rebate.has_signed_today(user_id, &day).await? {
            let bal = self.credit.get_balance(user_id).await?;
            return Ok((true, rust_decimal::Decimal::ZERO, bal));
        }
        self.rebate.mark_signed(user_id, &day).await?;
        let reward = money("1.00");
        let out_no = format!("sign_{user_id}_{day}");
        let bal = self
            .credit
            .apply_trade(CreditOrder {
                user_id: user_id.into(),
                order_id: Uuid::new_v4().to_string(),
                out_business_no: out_no,
                trade_name: "calendar_sign".into(),
                trade_type: TradeType::Forward,
                trade_amount: reward,
            })
            .await?;
        // Align Java send_rebate async path (idempotent via same out_business_no semantics).
        let _ = self
            .outbox
            .enqueue_rebate(RebateMessage {
                user_id: user_id.into(),
                day: day.clone(),
                amount: reward,
            })
            .await;
        Ok((false, reward, bal))
    }

    pub async fn ingest_rebate(&self, m: RebateMessage) -> Result<(), BmError> {
        // Idempotent: same business key as sign credit.
        let _ = self
            .credit
            .apply_trade(CreditOrder {
                user_id: m.user_id.clone(),
                order_id: Uuid::new_v4().to_string(),
                out_business_no: format!("sign_{}_{}", m.user_id, m.day),
                trade_name: "rebate_consume".into(),
                trade_type: TradeType::Forward,
                trade_amount: m.amount,
            })
            .await?;
        Ok(())
    }

    pub async fn consume_rebate(&self, limit: usize) -> Result<usize, BmError> {
        let msgs = self.outbox.take_rebate_messages(limit).await?;
        let n = msgs.len();
        for m in msgs {
            self.ingest_rebate(m).await?;
        }
        Ok(n)
    }

    pub async fn take_rebate_for_publish(&self, limit: usize) -> Result<Vec<RebateMessage>, BmError> {
        self.outbox.take_rebate_messages(limit).await
    }
}

pub struct ChatbotService {
    pub chat: Arc<ChatBillingService>,
    pub admin: Arc<dyn AdminStore>,
    pub credit: Arc<dyn CreditStore>,
}

impl ChatbotService {
    pub async fn ask(
        &self,
        user_id: &str,
        request_id: &str,
        message: &str,
    ) -> Result<(String, String, String, bool, Money, Money), BmError> {
        let enabled = self
            .admin
            .get("chatbot::enabled")
            .await?
            .unwrap_or_else(|| "true".into());
        if enabled.eq_ignore_ascii_case("false") {
            let bal = self.credit.get_balance(user_id).await?;
            return Ok((
                "disabled".into(),
                "disabled".into(),
                "AI 对话当前不可用。".into(),
                false,
                rust_decimal::Decimal::ZERO,
                bal,
            ));
        }
        let amount = money("1.00");
        let bal = self.chat.deduct(user_id, request_id, amount).await?;
        let lower = message.to_lowercase();
        let (intent, tool, answer) = if lower.contains("积分") || lower.contains("credit") {
            (
                "query_credit",
                "credit_tool",
                format!("你当前可用积分为 {bal}。可通过签到或抽奖获得更多积分。"),
            )
        } else if lower.contains("签到") || lower.contains("sign") {
            (
                "sign_hint",
                "rebate_tool",
                "每日可在用户中心签到领取积分；同一天重复签到不会重复发放。".into(),
            )
        } else if lower.contains("抽奖") || lower.contains("draw") {
            (
                "draw_hint",
                "raffle_tool",
                "兑换 SKU 获得抽奖次数后，点击转盘即可抽奖；默认活动固定奖为积分。".into(),
            )
        } else {
            (
                "chat",
                "echo_tool",
                format!("已收到：{message}\n\n（学习版本地回复，未调用外部大模型）"),
            )
        };
        Ok((
            intent.into(),
            tool.into(),
            answer,
            true,
            amount,
            bal,
        ))
    }
}

impl ChatBillingService {
    pub async fn mark_refund_pending(
        &self,
        user_id: &str,
        request_id: &str,
    ) -> Result<bool, BmError> {
        let session = self
            .chat
            .get_session(user_id, request_id)
            .await?
            .ok_or_else(|| BmError::NotFound("chat session missing".into()))?;
        if session.refund_state == RefundState::Refunded {
            return Ok(true);
        }
        self.chat
            .set_refund_state(user_id, request_id, RefundState::Pending)
            .await?;
        Ok(true)
    }
}

pub struct AuthFacade {
    pub jwt: JwtService,
    pub users: std::collections::HashMap<String, String>,
    pub revoked: Arc<dyn TokenRevocation>,
}

#[async_trait::async_trait]
pub trait TokenRevocation: Send + Sync {
    async fn revoke(&self, jti: &str, ttl_secs: u64) -> Result<(), BmError>;
    async fn is_revoked(&self, jti: &str) -> Result<bool, BmError>;
}

impl AuthFacade {
    pub async fn login(&self, user_id: &str, password: &str) -> Result<(String, u64), BmError> {
        if user_id.trim().is_empty() || password.is_empty() {
            return Err(BmError::IllegalParam("userId/password required".into()));
        }
        match self.users.get(user_id) {
            Some(p) if p == password => {}
            _ => return Err(BmError::Unauthorized("bad credentials".into())),
        }
        let (token, _) = self.jwt.create_token(user_id)?;
        Ok((token, TOKEN_TTL_SECS))
    }

    pub async fn verify(&self, token: &str) -> Result<String, BmError> {
        let claims = self.jwt.verify(token)?;
        if self.revoked.is_revoked(&claims.jti).await? {
            return Err(BmError::Unauthorized("revoked".into()));
        }
        Ok(claims.open_id)
    }

    pub async fn logout(&self, token: &str) -> Result<(), BmError> {
        let claims = self.jwt.verify(token)?;
        let now = Utc::now().timestamp();
        let ttl = (claims.exp - now).max(1) as u64;
        self.revoked.revoke(&claims.jti, ttl).await
    }
}
