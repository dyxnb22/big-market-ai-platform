use bm_domain::{
    parse_dev_users, AuthFacade, AwardDispatchService, ChatBillingService, ChatbotService,
    JwtService, RaffleService, RebateService, TokenRevocation,
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
    pub chatbot: Arc<ChatbotService>,
    pub dispatch: Arc<AwardDispatchService>,
    pub admin: Arc<dyn bm_domain::AdminStore>,
    pub stock: Arc<dyn bm_domain::StockStore>,
    pub stages: Arc<dyn bm_domain::StageStore>,
    pub orders: Arc<dyn bm_domain::OrderQueryStore>,
    pub strategy: Arc<dyn bm_domain::StrategyStore>,
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
            outbox: backend.clone(),
        });
        let chatbot = Arc::new(ChatbotService {
            chat: chat.clone(),
            admin: backend.clone(),
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
            chatbot,
            dispatch,
            admin: backend.clone(),
            stock: backend.clone(),
            stages: backend.clone(),
            orders: backend.clone(),
            strategy: backend,
        }
    }
}
