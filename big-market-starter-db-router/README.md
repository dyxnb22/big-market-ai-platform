# DB Router Starter

This module replaces the opaque external `db-router-spring-boot-starter` for learning.

It provides:

- `@DBRouter` for routed DAO methods.
- `@DBRouterStrategy(splitTable = true)` for sharded mapper interfaces.
- `IDBRouterStrategy` for manual route control in repository and job code.
- `DBRouterTemplate.executeOnShard(router, userId, callback)` / `executeOnDb(...)` — try/finally clear with nested save/restore.
- A dynamic datasource based on `mini-db-router.jdbc.datasource.*`.
- A small MyBatis plugin that appends table suffixes such as `_000`.

The implementation is intentionally compact. It is designed to explain the routing idea before being hardened for production.
