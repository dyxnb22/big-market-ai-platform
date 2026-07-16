use figment::{
    providers::{Env, Format, Serialized, Toml},
    Figment,
};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppConfig {
    pub host: String,
    pub port: u16,
    pub jwt_secret: String,
    pub dev_users: String,
    pub backend: String,
    pub mysql_url: Option<String>,
    pub redis_url: Option<String>,
    pub rabbit_url: Option<String>,
    pub gateway_app_url: String,
    pub internal_token: String,
    pub initial_credit: String,
}

impl Default for AppConfig {
    fn default() -> Self {
        Self {
            host: "0.0.0.0".into(),
            port: 8083,
            jwt_secret: "change-me-in-dev-only".into(),
            dev_users: "xiaofuge:demo,admin:admin".into(),
            backend: "memory".into(),
            mysql_url: None,
            redis_url: None,
            rabbit_url: None,
            gateway_app_url: "http://127.0.0.1:8083".into(),
            internal_token: "dev-internal-token".into(),
            initial_credit: "100.00".into(),
        }
    }
}

impl AppConfig {
    pub fn load() -> anyhow::Result<Self> {
        let figment = Figment::new()
            .merge(Serialized::defaults(AppConfig::default()))
            .merge(Toml::file("config/default.toml").nested())
            .merge(Env::prefixed("BM_").split("__"));
        Ok(figment.extract()?)
    }

    pub fn gateway_load() -> anyhow::Result<GatewayConfig> {
        let figment = Figment::new()
            .merge(Serialized::defaults(GatewayConfig::default()))
            .merge(Toml::file("config/gateway.toml").nested())
            .merge(Env::prefixed("BM_GW_").split("__"));
        Ok(figment.extract()?)
    }

    pub fn worker_load() -> anyhow::Result<WorkerConfig> {
        let figment = Figment::new()
            .merge(Serialized::defaults(WorkerConfig::default()))
            .merge(Toml::file("config/worker.toml").nested())
            .merge(Env::prefixed("BM_WORKER_").split("__"));
        Ok(figment.extract()?)
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GatewayConfig {
    pub host: String,
    pub port: u16,
    pub app_url: String,
    pub jwt_secret: String,
}

impl Default for GatewayConfig {
    fn default() -> Self {
        Self {
            host: "0.0.0.0".into(),
            port: 8080,
            app_url: "http://127.0.0.1:8083".into(),
            jwt_secret: "change-me-in-dev-only".into(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WorkerConfig {
    pub host: String,
    pub port: u16,
    pub backend: String,
    pub poll_secs: u64,
    pub jwt_secret: String,
    pub initial_credit: String,
}

impl Default for WorkerConfig {
    fn default() -> Self {
        Self {
            host: "0.0.0.0".into(),
            port: 8085,
            backend: "memory".into(),
            poll_secs: 1,
            jwt_secret: "change-me-in-dev-only".into(),
            initial_credit: "100.00".into(),
        }
    }
}
