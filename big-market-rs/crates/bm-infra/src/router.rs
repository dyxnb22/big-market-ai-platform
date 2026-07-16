use bm_types::route_shard;

#[derive(Debug, Clone)]
pub struct DbRouter {
    pub db_count: u32,
    pub tb_count: u32,
}

impl Default for DbRouter {
    fn default() -> Self {
        Self {
            db_count: 2,
            tb_count: 4,
        }
    }
}

impl DbRouter {
    pub fn route(&self, user_id: &str) -> (u32, u32) {
        route_shard(user_id, self.db_count, self.tb_count)
    }

    pub fn schema_name(&self, user_id: &str) -> String {
        let (db, _) = self.route(user_id);
        format!("big_market_{db:02}")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn schema_format() {
        let r = DbRouter::default();
        let s = r.schema_name("xiaofuge");
        assert!(s.starts_with("big_market_0"));
    }
}
