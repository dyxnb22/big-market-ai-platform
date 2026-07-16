use bm_domain::{
    parse_dev_users, AuthFacade, AwardDispatchService, ChatBillingService, JwtService,
    RaffleService, RebateService,
};
use bm_infra::AppConfig;
use bm_infra::SharedMemory;
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
}

impl AppState {
    pub fn from_memory(cfg: AppConfig, memory: SharedMemory) -> Self {
        let backend = memory.backend.clone();
        let jwt = JwtService::new(&cfg.jwt_secret);
        let auth = Arc::new(AuthFacade {
            jwt,
            users: parse_dev_users(&cfg.dev_users),
            revoked: backend.clone(),
        });
        let raffle = Arc::new(RaffleService {
            catalog: backend.clone(),
            quota: backend.clone(),
            credit: backend.clone(),
            award: backend.clone(),
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
            admin: backend,
        }
    }
}
