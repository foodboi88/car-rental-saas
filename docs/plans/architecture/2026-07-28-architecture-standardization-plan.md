# Implementation Plan: Architecture Standardization - Car Rental SaaS

Spec Source: `docs/plans/architecture/2026-07-28-architecture-standardization-spec.md`  
Owner: Backend & System Engineering Team  
Last Updated: 2026-07-28  
Status: `Approved`

---

# 1. Context

**Problem:**  
File kiến trúc cũ chứa nhiều linh kiện thừa (Redis, Google Maps, CDN, Docker containerization, vẽ lại Database schema) chưa phản ánh đúng nhu cầu thực tế và định hướng vận hành dự án. Cần chuẩn hóa kiến trúc tập trung vào 2 nghiệp vụ cốt lõi: **Giữ đơn xe (Hold status DB-backed)** và **Tích hợp PaddleOCR tự dựng quét giấy tờ khách hàng (CCCD/GPLX)**, cùng quy trình triển khai SSH/Systemd tối giản.

**Affected Modules:**
- `booking` - Quản lý đơn xe, trạng thái `HOLD` (15m expiration) & Spring Boot `@Scheduled` release task.
- `customer` - Tích hợp dịch vụ PaddleOCR bóc tách giấy tờ CCCD/GPLX.
- `docs` - Cập nhật [Architecture-Diagram.md](file:///f:/backend-training/private-car-rental/docs/Architecture-Diagram.md) & [Database-Schema.md](file:///f:/backend-training/private-car-rental/docs/Database-Schema.md).
- `deployment` - Nginx configuration & Linux `systemd` service setup.

**Non-Goals:**
- KHÔNG sử dụng Redis, CDN, Google Maps API hay Docker Container trong quá trình triển khai production.
- KHÔNG vẽ lại ERD hay định nghĩa lại các bảng cơ sở dữ liệu đã có sẵn trong `Database-Schema.md`.

---

# 2. Constraints

**Language:** Java 21 (Spring Boot 3.3) / Python 3.10 (FastAPI for PaddleOCR Engine)  
**Architecture:** Layered Monolith Architecture, Column-based Multi-Tenancy (`tenant_id`).

**Rules:**
- Tuân thủ nghiêm ngặt bảo mật Multi-Tenant (Column-based `tenant_id` isolation).
- Không tự ý thêm thư viện cache hay dependency ngoài khi chưa cần thiết.
- Lưu trữ toàn bộ ảnh xe & ảnh scan giấy tờ trên S3 / MinIO Object Storage.

---

# 3. Conventions

**Naming:**
- Classes: `PascalCase` (e.g. `HoldReleaseTask`, `CustomerOcrService`).
- Enums: `UPPER_SNAKE_CASE` (e.g. `HOLD(1)`, `CONFIRMED(2)`).
- SQL Scripts: `V3__update_booking_status_and_hold.sql`.

**Error Handling:**
- Throw `ResourceNotFoundException` khi không tìm thấy tài nguyên.
- Throw `BadRequestException` hoặc `InvalidOperationException` khi đơn `HOLD` hết hạn hoặc vi phạm trạng thái.

---

# 4. Contracts

## Interface: `CustomerOcrService`

```text
scanCustomerDocument(file: MultipartFile): CustomerOcrResultDTO
  - pre: file must not be null or empty; image format must be JPG/PNG
  - post: returns extracted fields (idCardNumber, fullName, dob, address, driverLicenseNumber)
  - throws: BadRequestException when OCR fails or file is invalid
```

## Data Structure: `Booking`

```text
Booking {
  id:             UUID              - PK
  tenantId:       UUID              - FK to tenants
  branchId:       UUID              - FK to branches
  customerId:     UUID              - FK to customers
  vehicleId:      UUID              - FK to vehicles
  status:         BookingStatus     - HOLD(1), CONFIRMED(2), HANDED_OVER(3), RETURNED(4), CANCELLED(5)
  holdExpiresAt:  OffsetDateTime    - expiry timestamp for HOLD status (NOW() + 15m)
  createdAt:      OffsetDateTime    - auto-generated
}
```

---

# 5. Target Architecture

```text
Client (Web/Mobile SPA)
  -> Nginx (HTTPS, Port 80/443, Serves Static Assets)
    -> Spring Boot Backend (Port 8080)
      -> JwtAuthFilter -> TenantContext.set(tenantId)
      -> CustomerController -> CustomerOcrService -> PaddleOCR Microservice (Python FastAPI)
      -> BookingController -> BookingService -> PostgreSQL (Save Booking with status=HOLD, holdExpiresAt=NOW()+15m)
      -> HoldReleaseTask (@Scheduled cron="0 */1 * * * *") -> Release expired HOLD bookings
```

---

# 6. Artifact Registry

| Artifact | Type | Owner Task | Implements |
|----------|------|------------|------------|
| `backend/.../booking/entity/BookingStatus.java` | enum | TASK-001 | Core Enum |
| `backend/.../booking/entity/Booking.java` | entity | TASK-001 | `Booking` |
| `backend/.../booking/service/impl/BookingServiceImpl.java` | service | TASK-001 | `BookingService` |
| `backend/.../booking/task/HoldReleaseTask.java` | scheduled task | TASK-002 | `HoldReleaseTask` |
| `backend/.../customer/service/CustomerOcrService.java` | service | TASK-003 | `CustomerOcrService` |
| `backend/src/main/resources/db/migration/V3__update_booking_status_and_hold.sql` | flyway | TASK-001 | Migration |
| `docs/Architecture-Diagram.md` | markdown | TASK-004 | System Doc |

---

# 7. Task Graph

**User-Approved Phase Strategy: Option A (Core Domain & Backend First)**

| Phase | Goal | Testable/Demoable Outcome |
|-------|------|---------------------------|
| Phase 1 | Core Hold Engine & DB Updates | Booking created with `HOLD` (15m expiry) & auto-cancelled by Scheduled task |
| Phase 2 | PaddleOCR Service Integration | API upload CCCD/GPLX photo -> JSON autofill result |
| Phase 3 | Deployment Scripts & Docs | Automated SSH deployment script & complete updated docs |

| ID | Phase | Name | Depends On | Effort |
|----|-------|------|------------|--------|
| TASK-001 | Phase 1 | Update BookingStatus Enum & DB Schema Migration | - | S |
| TASK-002 | Phase 1 | Implement Spring Boot `@Scheduled` HoldReleaseTask | TASK-001 | M |
| TASK-003 | Phase 2 | Implement Customer OCR Service Client (PaddleOCR Integration) | TASK-001 | M |
| TASK-004 | Phase 3 | Update Architecture-Diagram & Deployment Scripts | TASK-002, TASK-003 | S |

---

# 8. Task Specifications

## TASK-001: Update BookingStatus Enum & DB Schema Migration

**Phase:** Phase 1  
**Files:**
- `backend/.../booking/entity/BookingStatus.java` - **modify**
- `backend/.../booking/entity/Booking.java` - **modify**
- `backend/.../db/migration/V3__update_booking_status_and_hold.sql` - **create**

**Acceptance Criteria:**
- [x] `BookingStatus` updated with `HOLD(1)`, `CONFIRMED(2)`, `HANDED_OVER(3)`, `RETURNED(4)`, `CANCELLED(5)`.
- [x] `Booking` entity has `holdExpiresAt` column mapped.
- [x] V3 Flyway migration script executes cleanly.

---

## TASK-002: Implement Spring Boot `@Scheduled` HoldReleaseTask

**Phase:** Phase 1  
**Files:**
- `backend/.../booking/task/HoldReleaseTask.java` - **create**

**Acceptance Criteria:**
- [ ] Task runs every 1 minute (`@Scheduled(cron = "0 */1 * * * *")`).
- [ ] Queries `bookings` table where `status = 1 (HOLD)` and `hold_expires_at < NOW()`.
- [ ] Updates status to `5 (CANCELLED)`.

---

## TASK-003: Implement Customer OCR Service Client

**Phase:** Phase 2  
**Files:**
- `backend/.../customer/service/CustomerOcrService.java` - **create**

**Acceptance Criteria:**
- [ ] Accepts image `MultipartFile`.
- [ ] Calls PaddleOCR FastAPI service at `http://localhost:8000/api/v1/ocr/scan`.
- [ ] Parses response into `CustomerOcrResultDTO`.

---

## TASK-004: Update Architecture-Diagram & Deployment Scripts

**Phase:** Phase 3  
**Files:**
- `docs/Architecture-Diagram.md` - **modify**
- `scripts/deploy.sh` - **create**

**Acceptance Criteria:**
- [x] `docs/Architecture-Diagram.md` updated and synchronized.
- [ ] `deploy.sh` SSH deploy script created and tested.

---

# 9. Edge Cases

| # | Scenario | Expected Behavior | Handled In |
|---|----------|-------------------|------------|
| 1 | Staff attempts to confirm booking after 15m hold expired | Reject with `InvalidOperationException: Hold duration expired` | TASK-002 |
| 2 | OCR service receives blurry or unsupported image | Return 400 Bad Request with clear error message | TASK-003 |
| 3 | Concurrent hold attempt on same vehicle for overlapping dates | PostgreSQL `SELECT FOR UPDATE` prevents double booking | TASK-001 |

---

# 10. Verification Plan

**Automated Tests:**
- `mvn test` - Runs all 62 backend unit & integration tests (Status: **PASSED**).

**Success Criteria:**
- All automated tests pass with 0 failures.
- Architecture diagram matches exact project scope.
