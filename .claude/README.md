# Claude local settings

`.claude/settings.local.json` is a **Claude Code permission allowlist**, not
architecture documentation. The current repository topology is seven
application services on `8080`–`8086`, with the static frontend on `5173`.
Market owns local rebate/strategy capabilities, message-job owns local award
credit dispatch, and account is the remaining credit/quota RPC boundary.

For agent guidance use:

- `AGENTS.md`
- `.cursor/rules/`
- `.cursor/skills/`
- `docs/MICROSERVICES.md`
- `docs/LEARNING-FREEZE.md`
- `docs/data-and-outbox.md`

Use the current final-topology audit and freeze documents as the source of
truth. Standalone Provider applications are not part of the final topology.
