# Cursor project config

| Path | Purpose |
| --- | --- |
| `../AGENTS.md` | Repo entry for agents (architecture, docs, readiness) |
| `rules/*.mdc` | Persistent rules (always-on or glob-scoped) |
| `skills/*/SKILL.md` | Task skills (remediation, boot fix, money paths, verify, docs) |

## Rules

| File | When |
| --- | --- |
| `project-core.mdc` | Always — topology, docs, readiness |
| `remediation-priority.mdc` | Always — BM phase order |
| `java-conventions.mdc` | `**/*.java` |
| `money-path-safety.mdc` | Credit/award/rebate/quota/stock paths |
| `service-boot-scan.mdc` | Service `*Application.java` / boot config |
| `mapper-and-xxl.mdc` | MyBatis XML + XXL Job |
| `frontend-web.mdc` | `big-market-web/**` |

## Skills (invoke by name or when triggers match)

| Skill | Use when |
| --- | --- |
| `audit-remediation` | Following BM backlog / phase fixes |
| `microservice-boot-fix` | market / message-job won't start, scan/mapper/XXL |
| `money-path-change` | Changing credit, quota, award, rebate, stock |
| `local-verify` | Choosing smoke/validate/tests after a change |
| `docs-sync` | Updating docs after behavior changes |

Personal/global Cursor skills live under `~/.cursor/skills-cursor/` and are unrelated to this repo.
