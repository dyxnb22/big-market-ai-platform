//! Redis-backed JWT revocation using fred.
//! Key: `jwt:revoked:{jti}` — aligns with Java RedisTokenRevocationService.

use async_trait::async_trait;
use bm_domain::TokenRevocation;
use bm_types::BmError;
use fred::interfaces::{ClientLike, KeysInterface};
use fred::prelude::*;
use std::sync::Arc;
use std::time::Duration;

pub struct RedisRevocation {
    client: RedisClient,
}

impl RedisRevocation {
    pub async fn connect(url: &str) -> Result<Arc<Self>, BmError> {
        let config = RedisConfig::from_url(url).map_err(|e| BmError::Internal(e.to_string()))?;
        let client = RedisClient::new(config, None, None, None);
        client
            .init()
            .await
            .map_err(|e| BmError::Internal(format!("redis init: {e}")))?;
        Ok(Arc::new(Self { client }))
    }
}

#[async_trait]
impl TokenRevocation for RedisRevocation {
    async fn revoke(&self, jti: &str, ttl_secs: u64) -> Result<(), BmError> {
        let key = format!("jwt:revoked:{jti}");
        let _: () = self
            .client
            .set(
                key,
                "revoked",
                Some(Expiration::EX(ttl_secs.max(1) as i64)),
                None,
                false,
            )
            .await
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok(())
    }

    async fn is_revoked(&self, jti: &str) -> Result<bool, BmError> {
        let key = format!("jwt:revoked:{jti}");
        match self.client.get::<Option<String>, _>(key).await {
            Ok(Some(_)) => Ok(true),
            Ok(None) => Ok(false),
            // Fail-closed like Java RedisTokenRevocationService.
            Err(_) => Ok(true),
        }
    }
}

/// Optional overlay: try Redis first; if connect fails at boot, caller falls back to memory.
pub async fn try_connect(url: &str) -> Option<Arc<RedisRevocation>> {
    match tokio::time::timeout(Duration::from_secs(2), RedisRevocation::connect(url)).await {
        Ok(Ok(c)) => Some(c),
        _ => {
            tracing::warn!("redis unavailable; JWT revoke stays on memory/file backend");
            None
        }
    }
}
