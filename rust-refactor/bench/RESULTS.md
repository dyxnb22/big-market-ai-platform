# Bench results (Rust vs Java) — placeholder calibrated locally

Environment: cloud agent host without Docker Java stack; Rust memory backend.

| Metric | Rust (bm-gateway+bm-app) | Java default compose (reference) |
| --- | --- | --- |
| Process count | 2 | ~8–10 JVMs |
| RSS (rss approx) | ~tens of MB (debug/release binary) | multi-GB typical locally |
| Cold ready | <2s after binary start | tens of seconds–minutes |
| Closed-loop correctness | PASS (`acceptance-rust.sh`) | PASS (`acceptance.sh --reuse`, 2026-07-11) |

Full side-by-side P99 requires both stacks on the same machine with Docker; record numbers here when available.
