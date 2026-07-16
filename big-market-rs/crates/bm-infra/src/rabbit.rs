//! Optional RabbitMQ bridge (lapin) for send_award / send_rebate.
//!
//! When `BM_RABBIT_URL` is unset, workers use the in-process outbox only.
//! When set, `bm-worker` publishes local outbox messages to dedicated queues
//! (`bm.send_award`, `bm.send_rebate`) and consumes them — separate from any
//! Java learning-stack queues so dual-run does not steal messages.

use bm_domain::{RebateMessage, SendAwardMessage};
use bm_types::BmError;
use futures_lite::stream::StreamExt;
use lapin::options::{
    BasicAckOptions, BasicConsumeOptions, BasicPublishOptions, QueueDeclareOptions,
};
use lapin::types::FieldTable;
use lapin::{BasicProperties, Channel, Connection, ConnectionProperties};
use std::sync::Arc;

pub const QUEUE_SEND_AWARD: &str = "bm.send_award";
pub const QUEUE_SEND_REBATE: &str = "bm.send_rebate";

pub struct RabbitBridge {
    channel: Channel,
}

impl RabbitBridge {
    pub async fn connect(url: &str) -> Result<Arc<Self>, BmError> {
        let conn = Connection::connect(url, ConnectionProperties::default())
            .await
            .map_err(|e| BmError::Internal(format!("rabbit connect: {e}")))?;
        let channel = conn
            .create_channel()
            .await
            .map_err(|e| BmError::Internal(format!("rabbit channel: {e}")))?;
        for q in [QUEUE_SEND_AWARD, QUEUE_SEND_REBATE] {
            channel
                .queue_declare(
                    q,
                    QueueDeclareOptions {
                        durable: true,
                        ..QueueDeclareOptions::default()
                    },
                    FieldTable::default(),
                )
                .await
                .map_err(|e| BmError::Internal(format!("rabbit declare {q}: {e}")))?;
        }
        Ok(Arc::new(Self { channel }))
    }

    pub async fn publish_send_award(&self, msg: &SendAwardMessage) -> Result<(), BmError> {
        let payload = serde_json::to_vec(msg).map_err(|e| BmError::Internal(e.to_string()))?;
        self.channel
            .basic_publish(
                "",
                QUEUE_SEND_AWARD,
                BasicPublishOptions::default(),
                &payload,
                BasicProperties::default().with_delivery_mode(2),
            )
            .await
            .map_err(|e| BmError::Internal(format!("publish award: {e}")))?
            .await
            .map_err(|e| BmError::Internal(format!("publish award confirm: {e}")))?;
        Ok(())
    }

    pub async fn publish_rebate(&self, msg: &RebateMessage) -> Result<(), BmError> {
        let payload = serde_json::to_vec(msg).map_err(|e| BmError::Internal(e.to_string()))?;
        self.channel
            .basic_publish(
                "",
                QUEUE_SEND_REBATE,
                BasicPublishOptions::default(),
                &payload,
                BasicProperties::default().with_delivery_mode(2),
            )
            .await
            .map_err(|e| BmError::Internal(format!("publish rebate: {e}")))?
            .await
            .map_err(|e| BmError::Internal(format!("publish rebate confirm: {e}")))?;
        Ok(())
    }

    /// Consume send_award deliveries and invoke `handler`. ACK only on success.
    pub async fn consume_send_award<F, Fut>(self: Arc<Self>, handler: F) -> Result<(), BmError>
    where
        F: Fn(SendAwardMessage) -> Fut + Send + Sync + 'static,
        Fut: std::future::Future<Output = Result<(), BmError>> + Send,
    {
        let mut consumer = self
            .channel
            .basic_consume(
                QUEUE_SEND_AWARD,
                "bm-worker-award",
                BasicConsumeOptions::default(),
                FieldTable::default(),
            )
            .await
            .map_err(|e| BmError::Internal(format!("consume award: {e}")))?;
        while let Some(delivery) = consumer.next().await {
            let delivery = delivery.map_err(|e| BmError::Internal(format!("delivery: {e}")))?;
            match serde_json::from_slice::<SendAwardMessage>(&delivery.data) {
                Ok(msg) => match handler(msg).await {
                    Ok(()) => {
                        delivery
                            .ack(BasicAckOptions::default())
                            .await
                            .map_err(|e| BmError::Internal(format!("ack: {e}")))?;
                    }
                    Err(e) => {
                        tracing::warn!(error=%e, "send_award handler failed; leave unacked");
                    }
                },
                Err(e) => {
                    tracing::warn!(error=%e, "bad send_award payload; ack+drop");
                    let _ = delivery.ack(BasicAckOptions::default()).await;
                }
            }
        }
        Ok(())
    }

    pub async fn consume_rebate<F, Fut>(self: Arc<Self>, handler: F) -> Result<(), BmError>
    where
        F: Fn(RebateMessage) -> Fut + Send + Sync + 'static,
        Fut: std::future::Future<Output = Result<(), BmError>> + Send,
    {
        let mut consumer = self
            .channel
            .basic_consume(
                QUEUE_SEND_REBATE,
                "bm-worker-rebate",
                BasicConsumeOptions::default(),
                FieldTable::default(),
            )
            .await
            .map_err(|e| BmError::Internal(format!("consume rebate: {e}")))?;
        while let Some(delivery) = consumer.next().await {
            let delivery = delivery.map_err(|e| BmError::Internal(format!("delivery: {e}")))?;
            match serde_json::from_slice::<RebateMessage>(&delivery.data) {
                Ok(msg) => match handler(msg).await {
                    Ok(()) => {
                        delivery
                            .ack(BasicAckOptions::default())
                            .await
                            .map_err(|e| BmError::Internal(format!("ack: {e}")))?;
                    }
                    Err(e) => {
                        tracing::warn!(error=%e, "rebate handler failed; leave unacked");
                    }
                },
                Err(e) => {
                    tracing::warn!(error=%e, "bad rebate payload; ack+drop");
                    let _ = delivery.ack(BasicAckOptions::default()).await;
                }
            }
        }
        Ok(())
    }
}
