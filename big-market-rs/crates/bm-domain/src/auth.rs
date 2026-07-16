use bm_types::BmError;
use chrono::{Duration, Utc};
use jsonwebtoken::{decode, encode, Algorithm, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use uuid::Uuid;

pub const TOKEN_TTL_SECS: u64 = 24 * 60 * 60;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Claims {
    pub open_id: String,
    pub sub: String,
    pub jti: String,
    pub iat: i64,
    pub exp: i64,
}

pub struct JwtService {
    encoding: EncodingKey,
    decoding: DecodingKey,
    validation: Validation,
}

impl JwtService {
    pub fn new(secret: &str) -> Self {
        let key = materialize_secret(secret);
        let mut validation = Validation::new(Algorithm::HS256);
        validation.validate_exp = true;
        Self {
            encoding: EncodingKey::from_secret(&key),
            decoding: DecodingKey::from_secret(&key),
            validation,
        }
    }

    pub fn create_token(&self, user_id: &str) -> Result<(String, String), BmError> {
        let now = Utc::now();
        let jti = Uuid::new_v4().to_string();
        let claims = Claims {
            open_id: user_id.to_string(),
            sub: user_id.to_string(),
            jti: jti.clone(),
            iat: now.timestamp(),
            exp: (now + Duration::seconds(TOKEN_TTL_SECS as i64)).timestamp(),
        };
        // Use custom claim name openId via serde rename in encode — jwt crate uses serde
        #[derive(Serialize)]
        struct WireClaims<'a> {
            #[serde(rename = "openId")]
            open_id: &'a str,
            sub: &'a str,
            jti: &'a str,
            iat: i64,
            exp: i64,
        }
        let wire = WireClaims {
            open_id: user_id,
            sub: user_id,
            jti: &jti,
            iat: claims.iat,
            exp: claims.exp,
        };
        let token = encode(&Header::new(Algorithm::HS256), &wire, &self.encoding)
            .map_err(|e| BmError::Internal(e.to_string()))?;
        Ok((token, jti))
    }

    pub fn verify(&self, token: &str) -> Result<Claims, BmError> {
        let raw = strip_bearer(token);
        #[derive(Deserialize)]
        struct WireClaims {
            #[serde(rename = "openId")]
            open_id: String,
            sub: String,
            jti: String,
            iat: i64,
            exp: i64,
        }
        let data = decode::<WireClaims>(raw, &self.decoding, &self.validation)
            .map_err(|_| BmError::Unauthorized("token invalid".into()))?;
        Ok(Claims {
            open_id: data.claims.open_id,
            sub: data.claims.sub,
            jti: data.claims.jti,
            iat: data.claims.iat,
            exp: data.claims.exp,
        })
    }
}

pub fn strip_bearer(token: &str) -> &str {
    let t = token.trim();
    t.strip_prefix("Bearer ")
        .or_else(|| t.strip_prefix("bearer "))
        .unwrap_or(t)
        .trim()
}

fn materialize_secret(secret: &str) -> Vec<u8> {
    let bytes = secret.as_bytes();
    if bytes.len() >= 32 {
        bytes.to_vec()
    } else {
        let mut hasher = Sha256::new();
        hasher.update(bytes);
        hasher.finalize().to_vec()
    }
}

pub fn parse_dev_users(raw: &str) -> std::collections::HashMap<String, String> {
    raw.split(',')
        .filter_map(|item| {
            let item = item.trim();
            if item.is_empty() {
                return None;
            }
            let mut parts = item.splitn(2, ':');
            let u = parts.next()?.trim();
            let p = parts.next()?.trim();
            if u.is_empty() {
                return None;
            }
            Some((u.to_string(), p.to_string()))
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn jwt_roundtrip() {
        let jwt = JwtService::new("change-me-in-dev-only");
        let (token, jti) = jwt.create_token("xiaofuge").unwrap();
        let claims = jwt.verify(&token).unwrap();
        assert_eq!(claims.open_id, "xiaofuge");
        assert_eq!(claims.jti, jti);
        let claims2 = jwt.verify(&format!("Bearer {token}")).unwrap();
        assert_eq!(claims2.open_id, "xiaofuge");
    }
}
