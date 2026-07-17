# Cursor project config

The final runtime topology has seven application services on ports `8080`–`8086`:
gateway, auth, admin, market, chatbot, message-job, and account. Rebate and
strategy are market-local capabilities; award credit dispatch is message-job
local. Standalone Provider applications and their mode switches are not part of
the final topology.

| Path | Purpose |
| --- | --- |
| `../AGENTS.md` | Repo entry for agents (architecture, docs, readiness) |
| `rules/*.mdc` | Persistent rules (always-on or glob-scoped) |
| `skills/*/SKILL.md` | Task skills (boot fix, money paths, verify, docs) |

## Rules

| File | When |
| --- | --- |
| `project-core.mdc` | Always — topology, docs, readiness |
| `java-conventions.mdc` | `**/*.java` |
| `money-path-safety.mdc` | Credit/award/rebate/quota/stock paths |
| `service-boot-scan.mdc` | Service `*Application.java` / boot config |
| `mapper-and-xxl.mdc` | MyBatis XML + XXL Job |
| `frontend-web.mdc` | `big-market-web/**` |

## Skills (invoke by name or when triggers match)

| Skill | Use when |
| --- | --- |
| `microservice-boot-fix` | market / message-job won't start, scan/mapper/XXL |
| `money-path-change` | Changing credit, quota, award, rebate, stock |
| `local-verify` | Choosing smoke/validate/tests after a change |
| `docs-sync` | Updating docs after behavior changes |

Personal/global Cursor skills live under `~/.cursor/skills-cursor/` and are unrelated to this repo.
