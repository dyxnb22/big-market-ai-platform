use bm_domain::{
    parse_dev_users, AuthFacade, AwardDispatchService, ChatBillingService, JwtService,
    RaffleService, RebateService, TokenRevocation,
};
use bm_infra::{AppConfig, SharedMemory};
use std::sync::Arc;

#[derive(Clone)]
pub struct AppState {
    pub cfg: AppConfig,
    pub auth: Arc<AuthFacade>,
    pub raffle: Arc<RaffleService>,
    pub chat: Arc<ChatBillingService>,
    pub rebate: Arc<RebateService>,
    pub dispatch: Arc<AwardDispatchService>,
    pub admin: Arc<dyn bm_domain::AdminStore>,
    pub stock: Arc<dyn bm_domain::StockStore>,
}

impl AppState {
    pub fn from_shared(
        cfg: AppConfig,
        memory: SharedMemory,
        revocation: Arc<dyn TokenRevocation>,
    ) -> Self {
        let backend = memory.backend.clone();
        let jwt = JwtService::new(&cfg.jwt_secret);
        let auth = Arc::new(AuthFacade {
            jwt,
            users: parse_dev_users(&cfg.dev_users),
            revoked: revocation,
        });
        let raffle = Arc::new(RaffleService {
            catalog: backend.clone(),
            quota: backend.clone(),
            credit: backend.clone(),
            award: backend.clone(),
            strategy: backend.clone(),
            stock: backend.clone(),
        });
        let chat = Arc::new(ChatBillingService {
            credit: backend.clone(),
            chat: backend.clone(),
        });
        let rebate = Arc::new(RebateService {
            rebate: backend.clone(),
            credit: backend.clone(),
        });
        let dispatch = Arc::new(AwardDispatchService {
            award: backend.clone(),
            credit: backend.clone(),
        });
        Self {
            cfg,
            auth,
            raffle,
            chat,
            rebate,
            dispatch,
            admin: backend.clone(),
            stock: backend,
        }
    }
}
