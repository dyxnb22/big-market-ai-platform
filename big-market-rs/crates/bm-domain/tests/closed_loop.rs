//! Domain closed-loop tests (memory backend) — no Docker required.

use bm_domain::*;
use bm_infra::SharedMemory;
use bm_types::money;

#[tokio::test]
async fn raffle_award_credit_closed_loop() {
    let mem = SharedMemory::seeded(money("100.00"));
    let backend = mem.backend.clone();
    let raffle = RaffleService {
        catalog: backend.clone(),
        quota: backend.clone(),
        credit: backend.clone(),
        award: backend.clone(),
        strategy: backend.clone(),
        stock: backend.clone(),
        participation: backend.clone(),
    };
    let dispatch = AwardDispatchService {
        award: backend.clone(),
        credit: backend.clone(),
    };

    let before = raffle.query_credit("xiaofuge").await.unwrap();
    assert_eq!(before, money("100.00"));

    raffle.armory(100401).await.unwrap();
    raffle
        .exchange_sku("xiaofuge", 9901, "req-1")
        .await
        .unwrap();
    let after_ex = raffle.query_credit("xiaofuge").await.unwrap();
    assert_eq!(before - after_ex, money("5.00"));

    // idempotent exchange
    raffle
        .exchange_sku("xiaofuge", 9901, "req-1")
        .await
        .unwrap();
    assert_eq!(raffle.query_credit("xiaofuge").await.unwrap(), after_ex);

    let draw = raffle.draw("xiaofuge", 100401).await.unwrap();
    assert_eq!(draw.award_id, 101);

    assert_eq!(dispatch.consume_send_award(10).await.unwrap(), 1);
    assert_eq!(dispatch.dispatch_pending(10).await.unwrap(), 1);

    let final_bal = raffle.query_credit("xiaofuge").await.unwrap();
    assert_eq!(final_bal, money("100.00")); // -5 +5
}

#[tokio::test]
async fn file_persist_roundtrip() {
    let dir = std::env::temp_dir().join(format!("bm-rs-test-{}", uuid::Uuid::new_v4()));
    let path = dir.join("state.json");
    let mem = SharedMemory::seeded(money("40.00"));
    mem.backend
        .apply_trade(CreditOrder {
            user_id: "xiaofuge".into(),
            order_id: "o1".into(),
            out_business_no: "persist-1".into(),
            trade_name: "t".into(),
            trade_type: TradeType::Reverse,
            trade_amount: money("7.00"),
        })
        .await
        .unwrap();
    mem.persist(&path).await.unwrap();

    let loaded = SharedMemory::load_or_seed(&path, money("100.00"))
        .await
        .unwrap();
    let bal = loaded.backend.get_balance("xiaofuge").await.unwrap();
    assert_eq!(bal, money("33.00"));
    let _ = std::fs::remove_dir_all(dir);
}

#[tokio::test]
async fn chat_deduct_refund_idempotent() {
    let mem = SharedMemory::seeded(money("50.00"));
    let backend = mem.backend.clone();
    let chat = ChatBillingService {
        credit: backend.clone(),
        chat: backend.clone(),
    };
    let bal = chat.deduct("xiaofuge", "c1", money("3.00")).await.unwrap();
    assert_eq!(bal, money("47.00"));
    let bal2 = chat.deduct("xiaofuge", "c1", money("3.00")).await.unwrap();
    assert_eq!(bal2, money("47.00"));
    let after = chat.refund("xiaofuge", "c1").await.unwrap();
    assert_eq!(after, money("50.00"));
    let after2 = chat.refund("xiaofuge", "c1").await.unwrap();
    assert_eq!(after2, money("50.00"));
}

#[tokio::test]
async fn auth_revoke() {
    let mem = SharedMemory::seeded(money("1.00"));
    let auth = AuthFacade {
        jwt: JwtService::new("change-me-in-dev-only"),
        users: parse_dev_users("xiaofuge:demo,admin:admin"),
        revoked: mem.backend.clone(),
    };
    let (token, _) = auth.login("xiaofuge", "demo").await.unwrap();
    assert_eq!(auth.verify(&token).await.unwrap(), "xiaofuge");
    auth.logout(&token).await.unwrap();
    assert!(auth.verify(&token).await.is_err());
}

#[tokio::test]
async fn sign_in_once_per_day() {
    let mem = SharedMemory::seeded(money("10.00"));
    let backend = mem.backend.clone();
    let rebate = RebateService {
        rebate: backend.clone(),
        credit: backend.clone(),
        outbox: backend,
    };
    let (signed, reward, bal) = rebate.calendar_sign("xiaofuge").await.unwrap();
    assert!(!signed);
    assert_eq!(reward, money("1.00"));
    assert_eq!(bal, money("11.00"));
    let (signed2, reward2, _) = rebate.calendar_sign("xiaofuge").await.unwrap();
    assert!(signed2);
    assert_eq!(reward2, money("0"));
}
