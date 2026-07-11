# P3 deferred governance (GOV-Z)

Milestone name: `governance-p3-deferred`  
Registered: 2026-07-11  
Source: `docs/TEMP-project-governance-checklist.md` §16

These items must **not** be mixed into ordinary BM/GOV defect PRs. Open a dedicated issue/PR only when goal, owner, and acceptance environment are explicit.

| ID | Item |
| --- | --- |
| GOV-Z01 | Physical DB-per-service split |
| GOV-Z02 | Further split market into fully autonomous services |
| GOV-Z03 | CDC/events replacing cross-service shared-table reads |
| GOV-Z04 | Service mesh / mTLS / centralized policy auth |
| GOV-Z05 | Kubernetes deploy, rolling upgrade, autoscaling |
| GOV-Z06 | Multi-AZ, failover, cross-region DR |
| GOV-Z07 | Blue/green or canary with auto-rollback |
| GOV-Z08 | Real production SLO, error budget, observation window |
| GOV-Z09 | Large-scale shard expansion and data migration |
| GOV-Z10 | Full-link load test / capacity platform |
| GOV-Z11 | Strip all framework annotations from domain models |
| GOV-Z12 | Move frontend history ledger from localStorage to server |
| GOV-Z13 | Real multi-provider AI routing, cost control, content safety |

If GitHub milestone API is available, mirror this list under milestone `governance-p3-deferred`.
