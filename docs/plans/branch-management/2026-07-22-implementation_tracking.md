# Implementation Tracking: Quản lý Chi nhánh (Branch Management)

Started at: 2026/07/22 10:26:10
Completed at: 2026/07/22 10:35:45

## Confirmed Decisions (from Planning)
| # | Decision | Rationale | Confirmed by user |
|---|----------|-----------|-------------------|
| 1 | Hybrid SaaS Quota Model | Giới hạn `max_branches` & `max_vehicles` theo gói cước để bảo vệ tài nguyên server và ngăn rác dữ liệu | 2026-07-22 |
| 2 | Phân quyền TENANT_ADMIN duy nhất được ghi | `STAFF` và `SALE` chỉ được xem danh sách chi nhánh được phân công (Read-only) | 2026-07-22 |
| 3 | Soft Delete + Safety Guard | Không xóa cứng nếu chi nhánh đang chứa xe hoặc booking active | 2026-07-22 |
| 4 | Strategy: Contract-First & Core Domain First | Nâng cấp Flyway & Entity trước, xây dựng DTOs/Service TDD, sau đó đến Controller & Integration Tests | 2026-07-22 |

## Scope Confirmation
- Files affected: 12 files (Migration SQL, Entity, Repository, 4 DTOs, Service Interface + Impl, Controller, 2 Test files)
- Size verdict: Medium (M)
- Approach: TDD (mandatory Red → Green → Refactor)

## Task Progress

| ID | Task | Group | Depends on | Status | TDD Phase | Sub-agent | Files touched | Commit | Notes |
|----|------|-------|------------|--------|-----------|-----------|---------------|--------|-------|
| 1 | Flyway Migration (V3) | A | [] | Completed | Green | Main | `V3__add_branch_management_fields.sql` | - | Bổ sung cột code, city, status, is_deleted... |
| 2 | Update Branch Entity & Repo | B | [1] | Completed | Green | Main | `Branch.java`, `BranchRepository.java` | - | Bổ sung getter/setter & JPA query methods |
| 3 | Create Branch DTOs | A | [] | Completed | Green | Main | `CreateBranchRequestDTO.java`, `UpdateBranchRequestDTO.java`, `BranchStatusRequestDTO.java`, `BranchResponseDTO.java` | - | Data contracts & Validation annotations |
| 4 | Implement BranchService (TDD) | C | [2, 3] | Completed | Green | Main | `BranchService.java`, `BranchServiceImpl.java`, `BranchServiceTest.java` | - | Quota validation, Code check, Safety delete guard (8 tests passed) |
| 5 | Implement BranchController (TDD) | D | [4] | Completed | Green | Main | `BranchController.java`, `BranchControllerTest.java` | - | REST Endpoints, PreAuthorize TENANT_ADMIN (3 tests passed) |

## Group Status

| Group | Tasks | Status | Started | Finished | Gate approved |
|-------|-------|--------|---------|----------|---------------|
| A | [1, 3] | Completed | 10:27 | 10:28 | Yes |
| B | [2] | Completed | 10:28 | 10:29 | Yes |
| C | [4] | Completed | 10:29 | 10:31 | Yes |
| D | [5] | Completed | 10:31 | 10:35 | Yes |

## Open Questions / Risks
| # | Question / Risk | Status | Resolution |
|---|------------------|--------|------------|
| 1 | Postgres RLS context in Integration Tests | Resolved | Mock user JWT context chứa `tenant_id` và `role` để nạp vào TenantContext |

## Change Log
| Timestamp | Event | Detail |
|-----------|-------|--------|
| 2026-07-22 10:26 | Tracking document created | Scope confirmed, Strategy 1 confirmed by user |
| 2026-07-22 10:28 | Group A & B Completed | Database Migration V3, DTOs & Entity/Repository completed |
| 2026-07-22 10:31 | Group C Completed | BranchService & BranchServiceTest (8 unit tests) passed 100% |
| 2026-07-22 10:35 | Group D Completed | BranchController & BranchControllerTest passed 100%. Total 62 unit/controller tests passed |
