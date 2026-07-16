# Bench results (Rust vs Java)

Environment: cloud agent host, Rust **release** binaries, `BM_BACKEND=file`, 2026-07-16.

| Metric | Rust (`bm-gateway` + `bm-app`) | Java default compose (reference) |
| --- | --- | --- |
| Process count | 2 | ~8–10 JVMs |
| Combined RSS | **~12 MiB** (`bench-rust.sh`) | multi-GB typical locally |
| `bm-app` RSS | ~7 MiB | — |
| `bm-gateway` RSS | ~6 MiB | — |
| Cold ready (health) | **~9 ms** after process start | tens of seconds–minutes |
| API smoke | PASS (`smoke-rust-api.sh`) | PASS (`acceptance.sh --reuse`) |
| Playwright E2E | **17 PASS ×2** (`acceptance-rust-e2e.sh`, 1 skipped legacy :8098) | 18 PASS ×2 (freeze audit) |

## Draw latency P50/P99 (exemption)

Same-host side-by-side draw P50/P99 vs full Java compose was **not** re-measured in the Phase F freeze pass (Java stack not required for Rust default path). RSS and cold-ready above remain the accepted D5 evidence for learning freeze. Re-run both stacks on one machine and append numbers here when available:

```bash
./scripts/bench-rust.sh
# optional: Java acceptance + custom draw timing harness
```

Commands:

```bash
./scripts/bench-rust.sh
./scripts/acceptance-rust.sh --e2e
```

