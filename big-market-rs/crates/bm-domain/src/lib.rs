//! Pure / ports-facing domain: auth, raffle, credit, award, chat, rebate.

pub mod auth;
pub mod award;
pub mod chat;
pub mod credit;
pub mod ports;
pub mod raffle;
pub mod rebate;

pub use auth::*;
pub use chat::*;
pub use credit::*;
pub use ports::*;
pub use raffle::*;
// award/rebate modules re-export through raffle/ports; keep modules for clarity.
