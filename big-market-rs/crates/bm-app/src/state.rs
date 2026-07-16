use bm_domain::{
    parse_dev_users, AuthFacade, AwardDispatchService, ChatBillingService, ChatbotService,
    JwtService, RaffleService, RebateService, TokenRevocation,
};
use bm_infra::{AppConfig, Bootstrapped, ServiceStores};
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
    pub fn from_bootstrapped(
        cfg: AppConfig,
        boot: &Bootstrapped,
        revocation: Arc<dyn TokenRevocation>,
    ) -> Self {
        let stores = ServiceStores::from_bootstrapped(boot);
        let jwt = JwtService::new(&cfg.jwt_secret);
        let auth = Arc::new(AuthFacade {
            jwt,
            users: parse_dev_users(&cfg.dev_users),
            revoked: revocation,
        });
        let raffle = Arc::new(RaffleService {
            catalog: stores.catalog.clone(),
            quota: stores.quota.clone(),
            credit: stores.credit.clone(),
            award: stores.award.clone(),
            strategy: stores.strategy.clone(),
            stock: stores.stock.clone(),
        });
        let chat = Arc::new(ChatBillingService {
            credit: stores.credit.clone(),
            chat: stores.chat.clone(),
        });
        let rebate = Arc::new(RebateService {
            rebate: stores.rebate.clone(),
            credit: stores.credit.clone(),
            outbox: stores.outbox.clone(),
        });
        let chatbot = Arc::new(ChatbotService {
            chat: chat.clone(),
            admin: stores.admin.clone(),
            credit: stores.credit.clone(),
        });
        let dispatch = Arc::new(AwardDispatchService {
            award: stores.award.clone(),
            credit: stores.credit.clone(),
        });
        Self {
            cfg,
            auth,
            raffle,
            chat,
            rebate,
            chatbot,
            dispatch,
            admin: stores.admin.clone(),
            stock: stores.stock.clone(),
            stages: stores.stages.clone(),
            orders: stores.orders.clone(),
            strategy: stores.strategy.clone(),
        }
    }
}
