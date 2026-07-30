# Implementation Tracking: Backend Project Base & Role-Based Login

Started at: 2026/07/08 17:14:20

## Confirmed Decisions (from Planning)
| # | Decision | Rationale | Confirmed by user |
|---|----------|-----------|-------------------|
| 1 | Java 17 + Spring Boot 3.3.x | Current codebase design baseline. | 2026-07-08 |
| 2 | Option A: Contract-First & Core Domain First | Build database and security core before controllers to avoid rework. | 2026-07-08 |
| 3 | HttpOnly Cookie for Refresh Token | Chose Cookie for refresh token and json body for access token to prevent XSS. | 2026-07-08 |
| 4 | In-Memory Token Revocation Map | Simplifies token revocation for MVP. Redis will be introduced in Phase 2. | 2026-07-08 |

## Scope Confirmation
- Files affected: Base maven settings, flyway scripts, configuration files, entities, repos, security filters, DTOs, AuthService, AuthController, unit and integration tests.
- Size verdict: L (Large)
- Approach: TDD (mandatory)

## Task Progress
| ID | Task | Group | Depends on | Status | TDD Phase | Sub-agent | Files touched | Commit | Notes |
|----|------|-------|------------|--------|-----------|-----------|---------------|--------|-------|
| TASK-001 | Scaffolding Maven pom.xml & configuration | A | [] | Not started | - | - | - | - | - |
| TASK-002 | Database Migrations via Flyway | A | [] | Not started | - | - | - | - | - |
| TASK-003 | Core Entities and JPA Repositories | B | [TASK-001, TASK-002] | Not started | - | - | - | - | - |
| TASK-004 | Cryptographic & Token Primitives | C | [TASK-001, TASK-003] | Not started | - | - | - | - | - |
| TASK-005 | Tenant Context & JWT Security Filter | C | [TASK-003] | Not started | - | - | - | - | - |
| TASK-006 | Security and Web configs | D | [TASK-004, TASK-005] | Not started | - | - | - | - | - |
| TASK-007 | Authentication & Token Services | E | [TASK-003, TASK-004, TASK-005] | Not started | - | - | - | - | - |
| TASK-008 | DTOs, Exception handlers & API Response DTOs | B | [TASK-001] | Not started | - | - | - | - | - |
| TASK-009 | Auth Controller & Integration Tests | F | [TASK-006, TASK-007, TASK-008] | Not started | - | - | - | - | - |

## Group Status
| Group | Tasks | Status | Started | Finished | Gate approved |
|-------|-------|--------|---------|----------|---------------|
| A | [TASK-001, TASK-002] | Pending | - | - | - |
| B | [TASK-003, TASK-008] | Pending | - | - | - |
| C | [TASK-004, TASK-005] | Pending | - | - | - |
| D | [TASK-006] | Pending | - | - | - |
| E | [TASK-007] | Pending | - | - | - |
| F | [TASK-009] | Pending | - | - | - |

## Open Questions / Risks
| # | Question | Status | Resolution |
|---|----------|--------|------------|
| 1 | Is there a local PostgreSQL instance running to connect to? | Open | We will verify DB connectivity during scaffolding/seeding. |

## Change Log
| Timestamp | Event | Detail |
|-----------|-------|--------|
| 2026-07-08 17:14 | Tracking document created | Scope confirmed, entering Implementation |
