# Archived Historical Notes

Status: archive only. This file keeps a compact record that the repository once
used incremental planning notes. It is not a current architecture source.

Current source of truth:

- `docs/MICROSERVICES.md`
- `docs/production-readiness-learning.md`
- `docs/operations-checklist.md`
- `docs/data-and-outbox.md`
- `docs/learning/README.md`

## Useful Historical Ideas

- Move service contracts into `big-market-api`.
- Keep domain behavior behind ports before wiring remote providers.
- Document DAO/table ownership before changing repositories.
- Protect write paths with idempotency keys and rollback notes.
- Keep local smoke validation green after each boundary change.

These ideas have been absorbed into the final learning architecture docs.
