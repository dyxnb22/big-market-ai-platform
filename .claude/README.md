# Claude local settings (stale)

`.claude/settings.local.json` is a **Claude Code permission allowlist** from older sessions.

It still references legacy patterns (e.g. monolith `big-market-app.jar`, frontend port `3000`) and is **not** the source of truth for this repo.

For agent guidance use:

- `AGENTS.md`
- `.cursor/rules/`
- `.cursor/skills/`
- `docs/MICROSERVICES.md`
- `docs/audit-remediation-plan.md`

Do not treat `.claude/settings.local.json` as architecture documentation. Regenerate permissions in Claude Code against the microservice stack if you still use Claude Code here.
