# Architecture (Rust)

**Authoritative doc:** [`docs/MICROSERVICES-RUST.md`](../docs/MICROSERVICES-RUST.md)

## Summary

- **3 processes max:** `bm-gateway`, `bm-app`, `bm-worker` (worker embeddable in app).
- **Not a 1:1 Java microservice clone** — domain boundaries are `bm-domain` traits, not RPC services.
- **Sync path** in app; **async/outbox** in worker tick (`bm-domain/src/worker.rs`, shared by `bm-app` embed + `bm-worker`).

## Crates

See workspace `Cargo.toml` and the authoritative doc § Crate 分层.
