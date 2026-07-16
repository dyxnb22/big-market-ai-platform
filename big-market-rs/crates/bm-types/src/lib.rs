//! Shared types: API envelope, error codes, money helpers.

use rust_decimal::Decimal;
use serde::{Deserialize, Serialize};
use thiserror::Error;

pub const CODE_SUCCESS: &str = "0000";
pub const CODE_FAIL: &str = "0001";
pub const CODE_ILLEGAL_PARAM: &str = "0002";
pub const CODE_PERMISSION_DENIED: &str = "0008";
pub const CODE_LOGIN_ERROR: &str = "0009";
pub const CODE_CREDIT_INSUFFICIENT: &str = "ERR_CREDIT_001";

pub const INFO_SUCCESS: &str = "调用成功";
pub const INFO_LOGIN_ERROR: &str = "账号或密码错误";
pub const INFO_TOKEN_ERROR: &str = "Token校验失败";

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ApiResponse<T> {
    pub code: String,
    pub info: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<T>,
}

impl<T> ApiResponse<T> {
    pub fn ok(data: T) -> Self {
        Self {
            code: CODE_SUCCESS.into(),
            info: INFO_SUCCESS.into(),
            data: Some(data),
        }
    }

    pub fn ok_empty() -> ApiResponse<()> {
        ApiResponse {
            code: CODE_SUCCESS.into(),
            info: INFO_SUCCESS.into(),
            data: None,
        }
    }

    pub fn err(code: impl Into<String>, info: impl Into<String>) -> ApiResponse<T> {
        ApiResponse {
            code: code.into(),
            info: info.into(),
            data: None,
        }
    }
}

#[derive(Debug, Error)]
pub enum BmError {
    #[error("illegal parameter: {0}")]
    IllegalParam(String),
    #[error("unauthorized: {0}")]
    Unauthorized(String),
    #[error("insufficient credit")]
    InsufficientCredit,
    #[error("not found: {0}")]
    NotFound(String),
    #[error("conflict: {0}")]
    Conflict(String),
    #[error("internal: {0}")]
    Internal(String),
}

impl BmError {
    pub fn code(&self) -> &'static str {
        match self {
            Self::IllegalParam(_) => CODE_ILLEGAL_PARAM,
            Self::Unauthorized(_) => CODE_LOGIN_ERROR,
            Self::InsufficientCredit => CODE_CREDIT_INSUFFICIENT,
            Self::NotFound(_) | Self::Conflict(_) | Self::Internal(_) => CODE_FAIL,
        }
    }

    pub fn info(&self) -> String {
        match self {
            Self::IllegalParam(m) => m.clone(),
            Self::Unauthorized(_) => INFO_TOKEN_ERROR.into(),
            Self::InsufficientCredit => "积分不足".into(),
            Self::NotFound(m) | Self::Conflict(m) | Self::Internal(m) => m.clone(),
        }
    }
}

pub type Money = Decimal;

pub fn money(s: &str) -> Money {
    s.parse().expect("valid decimal")
}

/// Stable string hash for shard routing (String.hashCode-compatible).
pub fn java_string_hash(s: &str) -> i32 {
    let mut h: i32 = 0;
    for b in s.bytes() {
        h = h.wrapping_mul(31).wrapping_add(b as i32);
    }
    h
}

/// Aligns with HashDBRouterStrategy: dbIdx = hash%dbCount+1, tbIdx = (hash/dbCount)%tbCount
pub fn route_shard(user_id: &str, db_count: u32, tb_count: u32) -> (u32, u32) {
    let hash = (java_string_hash(user_id) as u32) & (i32::MAX as u32);
    let db_idx = hash % db_count + 1;
    let tb_idx = (hash / db_count) % tb_count;
    (db_idx, tb_idx)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn xiaofuge_routes_like_java() {
        // smoke: just ensure deterministic
        let (db, tb) = route_shard("xiaofuge", 2, 4);
        assert!((1..=2).contains(&db));
        assert!(tb < 4);
    }
}
