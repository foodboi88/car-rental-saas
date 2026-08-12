# Implementation Plan: Customer CRUD (Basic)

Spec Source: `docs/overviews/Database-Schema.md` (mục 2.11 `customers`), `docs/overviews/onboarding.md` (mục 3, business narrative)
Owner: huynq7
Last Updated: 2026-08-12
Status: Draft

---

# 1. Context

**Problem:**
Nhà xe (tenant) cần quản lý hồ sơ khách hàng thuê xe: tạo, xem, sửa thông tin liên hệ, đánh dấu mức độ rủi ro (SAFE/WARNING/BLACKLIST), và xóa hồ sơ khi cần. Đây là bước nền tảng cho module `bookings` sau này (mỗi đơn thuê xe phải gắn với một `customer_id`).

**Affected Modules:**
- `customer` (mới) - Entity, Repository, DTO, Service, Controller cho khách hàng
- `common.exception.ErrorCode` - thêm mã lỗi nghiệp vụ cho Customer
- `db/migration` - thêm bảng `customers` (chưa tồn tại trong DB thật)

**Non-Goals:**
- Mã hóa AES-256 cho `id_card`/`driver_license` — 2 field này **không có trong plan này**, sẽ làm ở plan riêng cùng lúc với cơ chế mã hóa (không tạo cột trước rồi mã hóa sau, tránh khoảng thời gian dữ liệu PII nằm plaintext).
- Upload/lưu ảnh CCCD/GPLX lên S3 (`id_card_images`, `driver_license_images`) — phụ thuộc field ở trên, cùng để dành cho plan sau.
- OCR tự động điền thông tin từ ảnh giấy tờ — tính năng tương lai, phụ thuộc S3 + mã hóa.
- Ràng buộc unique cho `phone`/`email` — schema hiện tại chỉ có index thường (không UNIQUE), nên không tự ý thêm ràng buộc nghiệp vụ chưa được xác nhận.
- Entity/Repository cho `Booking` — chưa tồn tại, chỉ dùng native query tối thiểu để kiểm tra ràng buộc xóa (giống cách `VehicleRepository`/`VehicleTypeRepository` đã làm với `vehicle_types`/`branches`).

---

# 2. Constraints

**Language:** `Java 21 / Spring Boot 4.0.7`
**Architecture:** Package-by-feature (module `customer/` chứa Entity, Repository, DTO, Service, Controller, Constant) - theo đúng cấu trúc `vehicle/` đã có.

**Rules:**
- Không sửa các module khác ngoài `customer/` và `common/exception/ErrorCode.java`.
- Không thêm dependency mới.
- Entity phải khớp chính xác với migration `V6` (Hibernate `ddl-auto: validate` sẽ fail nếu lệch).
- Mọi query phải lọc theo `tenantId` (multi-tenant isolation) - không có ngoại lệ.

---

# 3. Conventions

> Rút ra từ module `vehicle/` đã build trước đó trong project.

**Naming:**
- Class: `PascalCase` - `Customer`, `CustomerRepository`, `CustomerService`, `CustomerController`
- Method/field: `camelCase` - `riskLevel`, `blacklistReason`, `findByTenantIdAndId()`
- Constants: `UPPER_SNAKE_CASE` trong class `final` có constructor private - `CustomerMessage.CREATE_SUCCESS`
- Package: `com.carrental.car_rental_backend.customer.{entity,repository,dto,service,controller,constant}`

**Error Handling:**
- Ném `AppException(ErrorCode.XXX)` khi vi phạm nghiệp vụ, `ErrorCode` là enum có `code/message/httpStatus`.
- Validate cấu trúc/format ở DTO (`jakarta.validation`), validate nghiệp vụ (tồn tại, ràng buộc điều kiện) ở Service.
- Không trả message lộ chi tiết nội bộ (đúng theo `security.md`: error messages don't leak sensitive data).

**Logging:** Không dùng logging riêng cho module này (project hiện chưa có convention logging bắt buộc ở tầng Service).

**Testing:**
- Framework: JUnit 5 + Mockito (theo `pom.xml` hiện có)
- Naming: `should_<expected>_when_<condition>`
- Coverage: unit test cho toàn bộ method public của `CustomerService`

**Task Size:** Tối đa 200 LOC/task (không tính test).

---

# 4. Contracts

## Data Structure: `Customer` (Entity)

```text
Customer {
  id:              UUID       - PK, auto-generated
  tenantId:        UUID       - FK tenants, not null
  name:            String     - not null, max 255
  phone:           String     - nullable, max 20
  email:           String     - nullable, max 255
  address:         String     - nullable, TEXT
  riskLevel:       Integer    - not null, default 1 (1:SAFE, 2:WARNING, 3:BLACKLIST)
  blacklistReason: String     - nullable, TEXT (bắt buộc có giá trị khi riskLevel = 3)
  notes:           String     - nullable, TEXT
  createdAt:       Instant    - set on creation, immutable
  updatedAt:       Instant    - updated on every mutation
}
```

## Shared DTOs

```text
CreateCustomerRequestDTO {
  name:    String  - @NotBlank, @Size(max=255)
  phone:   String  - optional, @Size(max=20)
  email:   String  - optional, @Email, @Size(max=255)
  address: String  - optional
  notes:   String  - optional
}
// riskLevel KHÔNG có trong request tạo mới - luôn mặc định 1 (SAFE), đổi qua endpoint riêng.

UpdateCustomerRequestDTO {
  name:    String  - @NotBlank, @Size(max=255)
  phone:   String  - optional, @Size(max=20)
  email:   String  - optional, @Email, @Size(max=255)
  address: String  - optional
  notes:   String  - optional
}
// Không có riskLevel/blacklistReason - đổi qua endpoint riêng (đúng pattern VehicleTypeStatusRequestDTO).

ChangeRiskLevelRequestDTO {
  riskLevel:       Integer - @NotNull, phải thuộc {1,2,3}
  blacklistReason: String  - optional ở tầng DTO; Service bắt buộc phải có giá trị khi riskLevel=3
}

CustomerResponseDTO {
  id:              UUID
  tenantId:        UUID
  name:            String
  phone:           String   - nullable
  email:           String   - nullable
  address:         String   - nullable
  riskLevel:       Integer
  blacklistReason: String   - nullable
  notes:           String   - nullable
  createdAt:       Instant
  updatedAt:       Instant
}
```

## Interface: `CustomerRepository`

```text
findByTenantIdAndId(tenantId: UUID, id: UUID): Optional<Customer>
  - post: trả về Customer nếu tồn tại và thuộc đúng tenant, ngược lại Optional.empty()

findByTenantIdWithFilter(tenantId: UUID, search: String?, riskLevel: Integer?, pageable: Pageable): Page<Customer>
  - pre: tenantId not null
  - post: search khớp LIKE (case-insensitive) trên name OR phone OR email khi có giá trị;
          lọc thêm theo riskLevel khi có giá trị; luôn lọc theo tenantId

existsBookingForCustomer(customerId: UUID): boolean
  - post: true nếu tồn tại ít nhất 1 dòng trong bookings có customer_id = customerId (native query,
          không build Booking entity - dùng để chặn xóa khách hàng có lịch sử đơn thuê)
```

## Interface: `CustomerService` (outline - xem Section 8, tự thiết kế logic)

```text
createCustomer(tenantId: UUID, request: CreateCustomerRequestDTO): CustomerResponseDTO
getCustomers(tenantId: UUID, search: String?, riskLevel: Integer?, pageable: Pageable): Page<CustomerResponseDTO>
getCustomerById(tenantId: UUID, id: UUID): CustomerResponseDTO
updateCustomer(tenantId: UUID, id: UUID, request: UpdateCustomerRequestDTO): CustomerResponseDTO
changeRiskLevel(tenantId: UUID, id: UUID, request: ChangeRiskLevelRequestDTO): CustomerResponseDTO
deleteCustomer(tenantId: UUID, id: UUID): void
```

---

# 5. Target Architecture

**Components:**
- `CustomerController` - nhận request HTTP, lấy `tenantId` từ `TenantContext`, gọi Service
- `CustomerService` - xử lý nghiệp vụ, validate, gọi Repository
- `CustomerRepository` - truy vấn DB (JPA + native query cho check `bookings`)
- `Customer` (Entity) - map bảng `customers`

**Interaction Flow:**

```text
Client
  -> CustomerController.createCustomer(request: CreateCustomerRequestDTO)
    -> CustomerService.createCustomer(tenantId, request)
      -> CustomerRepository.save(customer) -> Customer
  <- ApiResponse.success(CustomerResponseDTO, CustomerMessage.CREATE_SUCCESS)

Client
  -> CustomerController.deleteCustomer(id)
    -> CustomerService.deleteCustomer(tenantId, id)
      -> CustomerRepository.findByTenantIdAndId(tenantId, id) -> Customer (or throw CUSTOMER_NOT_FOUND)
      -> CustomerRepository.existsBookingForCustomer(id) -> boolean (throw CUSTOMER_IN_USE if true)
      -> CustomerRepository.delete(customer)
  <- ApiResponse.success(null, CustomerMessage.DELETE_SUCCESS)
```

**Key Decisions:**
- **Xóa là hard-delete thật, có safety guard** - khác với `Vehicle` (dùng `status=INACTIVE`), bảng `customers` không có field `is_active`/`status` tương tự trong schema, nên không thể "soft delete". Guard bằng cách chặn xóa nếu khách hàng đã có `booking` - tránh mất dữ liệu lịch sử liên kết.
- **`riskLevel` đổi qua endpoint riêng** (`PATCH /{id}/risk-level`), không gộp vào `UpdateCustomerRequestDTO` - đúng pattern đã dùng cho `VehicleTypeStatusRequestDTO`/`QuickUpdateRequestDTO`, tách hành động "sửa thông tin liên hệ" khỏi "đánh giá rủi ro" (hai quyền hạn khác nhau về mặt nghiệp vụ).
- **Không enforce unique phone/email** - schema chỉ đánh index thường, không phải UNIQUE index; tự thêm ràng buộc sẽ là suy đoán ngoài spec.
- **Phân quyền**: Tạo/Sửa thông tin cơ bản cho phép cả `STAFF`/`SALE` (họ là người trực tiếp tiếp khách, tạo hồ sơ) nhưng đổi `riskLevel` và xóa chỉ `TENANT_ADMIN` (quyết định rủi ro/xóa dữ liệu là hành động nhạy cảm).

---

# 6. Artifact Registry

| Artifact | Type | Owner Task | Implements |
|----------|------|------------|------------|
| `backend/.../db/migration/V6__update_customers_columns.sql` | migration | TASK-001 | `Customer` schema |
| `backend/.../customer/entity/Customer.java` | class | TASK-002 | `Customer` |
| `backend/.../customer/repository/CustomerRepository.java` | interface | TASK-003 | `CustomerRepository` |
| `backend/.../customer/dto/CreateCustomerRequestDTO.java` | class | TASK-004 | `CreateCustomerRequestDTO` |
| `backend/.../customer/dto/UpdateCustomerRequestDTO.java` | class | TASK-004 | `UpdateCustomerRequestDTO` |
| `backend/.../customer/dto/ChangeRiskLevelRequestDTO.java` | class | TASK-004 | `ChangeRiskLevelRequestDTO` |
| `backend/.../customer/dto/CustomerResponseDTO.java` | class | TASK-004 | `CustomerResponseDTO` |
| `backend/.../customer/constant/CustomerMessage.java` | class | TASK-004 | - |
| `backend/.../common/exception/ErrorCode.java` | enum | TASK-004 | - (modify: thêm `CUSTOMER_NOT_FOUND`, `CUSTOMER_IN_USE`) |
| `backend/.../customer/service/CustomerService.java` | class | TASK-005, TASK-006, TASK-007 | `CustomerService` |
| `backend/.../customer/controller/CustomerController.java` | class | TASK-008 | HTTP endpoints |
| `backend/.../customer/service/CustomerServiceTest.java` | test | TASK-009 | - |

---

# 7. Task Graph

**User-Approved Phase/Sprint Strategy:**

Selected strategy: `Vertical slice theo nhóm chức năng` (Option A) - giống pattern đã dùng cho `VehicleType` và `Vehicle`.

Rationale:
Mỗi phase ra một nhóm API bấm thử được ngay qua Swagger, không phải quay lại sửa code phase trước. Phù hợp nhịp học hiện tại (code → review → commit theo từng nhóm chức năng nhỏ).

Rejected alternatives:
- `Risk-first` - xử lý rule đổi risk-level/xóa trước Create/Read khiến khó test tay (chưa có data), không phù hợp nhịp học từng bước.
- `MVP rồi hardening` - viết CRUD trước, rule sau sẽ phải sửa lại code Create/Delete đã viết ở Phase 1, gây rối khi đang học.

| Phase/Sprint | Goal | Testable/Demoable Outcome |
|--------------|------|---------------------------|
| Phase 1 | Nền tảng: migration, Entity, Repository, DTO, ErrorCode | Compile thành công, chưa có API |
| Phase 2 | Create Customer | `POST /api/v1/customers` tạo được khách hàng mới |
| Phase 3 | Read Customer (list + chi tiết) | `GET /api/v1/customers`, `GET /{id}` |
| Phase 4 | Update thông tin cơ bản | `PUT /api/v1/customers/{id}` |
| Phase 5 | Đổi risk-level + Xóa (có guard) | `PATCH /{id}/risk-level`, `DELETE /{id}` |
| Phase 6 | Unit Test cho Service | `CustomerServiceTest` pass toàn bộ |

| ID | Phase/Sprint | Name | Depends On | Effort |
|----|--------------|------|------------|--------|
| TASK-001 | Phase 1 | Migration `V6__update_customers_columns.sql` | - | S |
| TASK-002 | Phase 1 | `Customer` Entity | TASK-001 | S |
| TASK-003 | Phase 1 | `CustomerRepository` | TASK-002 | S |
| TASK-004 | Phase 1 | DTOs + `CustomerMessage` + `ErrorCode` | TASK-002 | S |
| TASK-005 | Phase 2 | `CustomerService.createCustomer` | TASK-003, TASK-004 | M |
| TASK-006 | Phase 3 | `CustomerService.getCustomers` + `getCustomerById` | TASK-003, TASK-004 | M |
| TASK-007 | Phase 4, 5 | `CustomerService.updateCustomer` + `changeRiskLevel` + `deleteCustomer` | TASK-005 | M |
| TASK-008 | Phase 2-5 | `CustomerController` (toàn bộ 6 endpoint) | TASK-005, TASK-006, TASK-007 | M |
| TASK-009 | Phase 6 | `CustomerServiceTest` | TASK-005, TASK-006, TASK-007 | M |

**Dependency Graph:**

```text
TASK-001 -- TASK-002 -- TASK-003 --.
                     `- TASK-004 --+-- TASK-005 -- TASK-007 --.
                                   `-- TASK-006 --------------+-- TASK-008
                                                               `-- TASK-009
```

**Execution Rules:**
- TASK-003 và TASK-004 có thể làm song song sau TASK-002.
- TASK-006 có thể làm song song với TASK-005/TASK-007 (đều chỉ phụ thuộc TASK-003, TASK-004) - nhưng theo phase strategy, thứ tự thực hiện thực tế vẫn đi tuần tự Phase 2 → 3 → 4 → 5 để giữ nhịp học.
- TASK-008 (Controller) chỉ viết sau khi Service tương ứng xong, nhưng có thể viết dần từng endpoint theo từng phase thay vì dồn hết vào cuối - do đây là tầng Controller nên có thể đưa code đầy đủ luôn.

---

# 8. Task Specifications

## TASK-001: Migration `V6__update_customers_columns.sql`

**Phase/Sprint:** `Phase 1`

**Description:**
Bảng `customers` **đã tồn tại sẵn từ `V1__init_schema.sql`** (schema cũ, có `id_card`, `driver_license`, `is_active`) - phát hiện khi chạy migration lần đầu bị lỗi `relation "customers" already exists`. Vì vậy TASK này dùng `ALTER TABLE` để nâng cấp cấu trúc cũ lên đúng schema mới, giống cách đã xử lý với bảng `vehicles` ở `V5`, thay vì `CREATE TABLE`.

**Input:** None (root task)

**Output:** Bảng `customers` sẵn sàng cho `Customer` Entity - dùng bởi TASK-002

**Files:**
- `backend/src/main/resources/db/migration/V6__update_customers_columns.sql` - **create**

**Responsibilities:**
- Xóa 3 cột thuộc schema cũ không còn dùng: `id_card`, `driver_license` (PII chưa mã hóa - Non-Goal), `is_active` (thay bằng `risk_level`).
- Thêm 2 cột mới: `risk_level` (`NOT NULL DEFAULT 1`, `CHECK IN (1,2,3)`), `blacklist_reason`.
- Thêm index `(tenant_id, risk_level)` - các index `tenant_id`/`phone`/`email` đã có sẵn từ V1, không cần tạo lại.

**Nội dung migration (đưa code đầy đủ vì đây là Data/Infra layer, không phải Service):**

```sql
-- Cập nhật bảng customers theo docs/overviews/Database-Schema.md (mục 2.11)
-- Bảng customers đã tồn tại từ V1 (schema cũ) nên dùng ALTER thay vì CREATE.
-- Phạm vi CRUD cơ bản: loại bỏ id_card, driver_license (được thiết kế để lưu dữ liệu
-- mã hóa AES-256) - việc mã hóa để dành cho plan riêng sau này, không thêm cột mới cho nó ở đây.

ALTER TABLE customers DROP COLUMN id_card;
ALTER TABLE customers DROP COLUMN driver_license;
ALTER TABLE customers DROP COLUMN is_active;

ALTER TABLE customers ADD COLUMN risk_level SMALLINT NOT NULL DEFAULT 1
    CHECK (risk_level IN (1, 2, 3)); -- 1:SAFE, 2:WARNING, 3:BLACKLIST
ALTER TABLE customers ADD COLUMN blacklist_reason TEXT;

CREATE INDEX idx_customers_risk_level ON customers(tenant_id, risk_level);
```

**Acceptance Criteria:**
- [ ] `mvn flyway:migrate` (hoặc app start) chạy migration thành công, không lỗi
- [ ] Bảng `customers` có đúng 10 cột sau migration (không còn `id_card`/`driver_license`/`is_active`)
- [ ] `\d customers` trong psql xác nhận `risk_level` có `CHECK` constraint `(1,2,3)`

---

## TASK-002: `Customer` Entity

**Phase/Sprint:** `Phase 1`

**Description:**
Entity JPA map 1-1 với bảng `customers` vừa tạo, theo đúng convention đã dùng ở `Vehicle.java` (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`, `@PrePersist`/`@PreUpdate` set default).

**Input:** Từ TASK-001 - bảng `customers` đã tồn tại

**Output:** `Customer` entity - dùng bởi TASK-003, TASK-004 (Response DTO), TASK-005/006/007 (Service)

**Files:**
- `backend/src/main/java/com/carrental/car_rental_backend/customer/entity/Customer.java` - **create**

**Responsibilities:**
- Implement `Customer` data structure per Section 4 contract.
- `@PrePersist`: set `createdAt`/`updatedAt = Instant.now()`; nếu `riskLevel == null` thì set `1` (SAFE) - đúng default DB.
- `@PreUpdate`: set `updatedAt = Instant.now()`.

**Code đầy đủ (tầng Entity):**

```java
package com.carrental.car_rental_backend.customer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "customers")
public class Customer {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "phone", length = 20)
  private String phone;

  @Column(name = "email", length = 255)
  private String email;

  @Column(name = "address", columnDefinition = "TEXT")
  private String address;

  @Column(name = "risk_level", nullable = false)
  private Integer riskLevel; // 1:SAFE, 2:WARNING, 3:BLACKLIST

  @Column(name = "blacklist_reason", columnDefinition = "TEXT")
  private String blacklistReason;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;

    if (riskLevel == null) {
      riskLevel = 1;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
```

**Acceptance Criteria:**
- [ ] App start với `ddl-auto: validate` không lỗi (Entity khớp `V6`)
- [ ] `new Customer()` rồi `save()` không set `riskLevel` -> DB lưu giá trị `1`
- [ ] Code compiles với zero warnings

---

## TASK-003: `CustomerRepository`

**Phase/Sprint:** `Phase 1`

**Description:**
Repository JPA cho `Customer`, gồm query tìm theo tenant, query lọc/tìm kiếm phân trang, và 1 native query kiểm tra ràng buộc `bookings` dùng cho xóa (Phase 5) - viết trước ở Phase 1 vì thuộc tầng Repository (đưa code đầy đủ luôn), dù được dùng ở TASK-007.

**Input:** Từ TASK-002 - `Customer` entity

**Output:** `CustomerRepository` - dùng bởi TASK-005, TASK-006, TASK-007

**Files:**
- `backend/src/main/java/com/carrental/car_rental_backend/customer/repository/CustomerRepository.java` - **create**

**Responsibilities:**
- Implement `CustomerRepository` interface per Section 4 contract.

**Code đầy đủ (tầng Repository):**

```java
package com.carrental.car_rental_backend.customer.repository;

import com.carrental.car_rental_backend.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
  Optional<Customer> findByTenantIdAndId(UUID tenantId, UUID id);

  // Xem danh sách khách hàng (tìm kiếm theo tên/sđt/email + lọc riskLevel + phân trang)
  @Query("SELECT c FROM Customer c WHERE c.tenantId = :tenantId " +
      "AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
      "     OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :search, '%')) " +
      "     OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
      "AND (:riskLevel IS NULL OR c.riskLevel = :riskLevel)")
  Page<Customer> findByTenantIdWithFilter(
      @Param("tenantId") UUID tenantId,
      @Param("search") String search,
      @Param("riskLevel") Integer riskLevel,
      Pageable pageable
  );

  // Kiểm tra khách hàng đã có lịch sử đơn thuê chưa, dùng khi xóa khách hàng
  // (chưa build Booking entity, dùng native query tối thiểu như VehicleRepository đã làm)
  @Query(value = "SELECT EXISTS(SELECT 1 FROM bookings WHERE customer_id = :customerId)",
      nativeQuery = true)
  boolean existsBookingForCustomer(@Param("customerId") UUID customerId);
}
```

**Acceptance Criteria:**
- [ ] `findByTenantIdAndId(tenantId, id)` -> trả về `Optional.empty()` khi khách hàng thuộc tenant khác (Edge Case MT-1)
- [ ] `findByTenantIdWithFilter(tenantId, "an", null, pageable)` -> chỉ trả các customer có `name`/`phone`/`email` chứa "an" (không phân biệt hoa thường)
- [ ] `existsBookingForCustomer(id)` -> trả `false` khi bảng `bookings` chưa tồn tại record nào cho `id` đó
- [ ] Code compiles với zero warnings

---

## TASK-004: DTOs + `CustomerMessage` + `ErrorCode`

**Phase/Sprint:** `Phase 1`

**Description:**
4 DTO (Create/Update/ChangeRiskLevel/Response), class hằng số message, và bổ sung 2 `ErrorCode` mới - toàn bộ thuộc tầng DTO/Constant nên đưa code đầy đủ.

**Input:** Từ TASK-002 - field list của `Customer`

**Output:** DTOs - dùng bởi TASK-005/006/007 (Service) và TASK-008 (Controller)

**Files:**
- `backend/src/main/java/com/carrental/car_rental_backend/customer/dto/CreateCustomerRequestDTO.java` - **create**
- `backend/src/main/java/com/carrental/car_rental_backend/customer/dto/UpdateCustomerRequestDTO.java` - **create**
- `backend/src/main/java/com/carrental/car_rental_backend/customer/dto/ChangeRiskLevelRequestDTO.java` - **create**
- `backend/src/main/java/com/carrental/car_rental_backend/customer/dto/CustomerResponseDTO.java` - **create**
- `backend/src/main/java/com/carrental/car_rental_backend/customer/constant/CustomerMessage.java` - **create**
- `backend/src/main/java/com/carrental/car_rental_backend/common/exception/ErrorCode.java` - **modify**: thêm 2 hằng số

**Responsibilities:**
- Implement 4 DTO đúng theo Section 4 Shared DTOs contract.
- Thêm `ErrorCode.CUSTOMER_NOT_FOUND` (404) và `ErrorCode.CUSTOMER_IN_USE` (409, dùng khi xóa khách hàng đã có booking).

**Code đầy đủ:**

```java
// CreateCustomerRequestDTO.java
package com.carrental.car_rental_backend.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCustomerRequestDTO {

  @NotBlank(message = "Tên khách hàng không được để trống")
  @Size(max = 255, message = "Tên khách hàng tối đa 255 ký tự")
  private String name;

  @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
  private String phone;

  @Email(message = "Email không đúng định dạng")
  @Size(max = 255, message = "Email tối đa 255 ký tự")
  private String email;

  private String address;

  private String notes;
}
```

```java
// UpdateCustomerRequestDTO.java
package com.carrental.car_rental_backend.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCustomerRequestDTO {

  @NotBlank(message = "Tên khách hàng không được để trống")
  @Size(max = 255, message = "Tên khách hàng tối đa 255 ký tự")
  private String name;

  @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
  private String phone;

  @Email(message = "Email không đúng định dạng")
  @Size(max = 255, message = "Email tối đa 255 ký tự")
  private String email;

  private String address;

  private String notes;
}
```

```java
// ChangeRiskLevelRequestDTO.java
package com.carrental.car_rental_backend.customer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeRiskLevelRequestDTO {

  @NotNull(message = "Mức độ rủi ro không được để trống")
  private Integer riskLevel; // 1:SAFE, 2:WARNING, 3:BLACKLIST

  private String blacklistReason; // Bắt buộc có giá trị khi riskLevel = 3, validate ở Service
}
```

```java
// CustomerResponseDTO.java
package com.carrental.car_rental_backend.customer.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class CustomerResponseDTO {

  private UUID id;
  private UUID tenantId;
  private String name;
  private String phone;
  private String email;
  private String address;
  private Integer riskLevel;
  private String blacklistReason;
  private String notes;
  private Instant createdAt;
  private Instant updatedAt;
}
```

```java
// CustomerMessage.java
package com.carrental.car_rental_backend.customer.constant;

public final class CustomerMessage {

  private CustomerMessage() {
  }

  public static final String CREATE_SUCCESS = "Tạo khách hàng thành công";
  public static final String LIST_SUCCESS = "Lấy danh sách khách hàng thành công";
  public static final String DETAIL_SUCCESS = "Lấy chi tiết khách hàng thành công";
  public static final String UPDATE_SUCCESS = "Cập nhật thông tin khách hàng thành công";
  public static final String CHANGE_RISK_LEVEL_SUCCESS = "Cập nhật mức độ rủi ro thành công";
  public static final String DELETE_SUCCESS = "Xóa khách hàng thành công";
}
```

`ErrorCode.java` - thêm 2 dòng vào enum hiện có (giữ nguyên toàn bộ constant cũ):

```java
CUSTOMER_NOT_FOUND("CUSTOMER_NOT_FOUND", "Không tìm thấy khách hàng", HttpStatus.NOT_FOUND),
CUSTOMER_IN_USE("CUSTOMER_IN_USE", "Không thể xóa khách hàng đã có lịch sử đơn thuê", HttpStatus.CONFLICT);
```

**Acceptance Criteria:**
- [ ] `CreateCustomerRequestDTO` với `name = ""` -> validation lỗi `NotBlank`
- [ ] `CreateCustomerRequestDTO` với `email = "abc"` -> validation lỗi `Email`
- [ ] `ErrorCode.CUSTOMER_IN_USE.getHttpStatus()` -> `409 CONFLICT`
- [ ] Code compiles với zero warnings

---

## TASK-005: `CustomerService.createCustomer` (outline - tự thiết kế)

**Phase/Sprint:** `Phase 2`

**Description:**
Tạo mới khách hàng. **Không đưa code - đây là tầng Service, tự thiết kế logic theo khung hướng đi dưới đây.**

**Input:** Từ TASK-003, TASK-004 - `CustomerRepository`, `CreateCustomerRequestDTO`, `Customer` entity

**Output:** `CustomerResponseDTO` - dùng bởi TASK-008 (Controller)

**Files:**
- `backend/src/main/java/com/carrental/car_rental_backend/customer/service/CustomerService.java` - **create**

**Khung hướng đi (guiding questions):**
1. Method nhận vào `tenantId` và `CreateCustomerRequestDTO` - có cần kiểm tra gì tồn tại trước không? (Gợi ý: so với `VehicleService.createVehicle` phải check `vehicleTypeId`/`branchId` tồn tại - `Customer` có FK nào bắt buộc phải tồn tại trước không? Nhìn lại contract Section 4, `Customer` không có FK nào ngoài `tenantId`.)
2. `riskLevel` có nên nhận từ request không, hay luôn để `null`/mặc định? (Xem Key Decision Section 5: luôn mặc định SAFE khi tạo mới.)
3. Build entity bằng `.builder()` như nào cho đủ field, field nào không set (để `@PrePersist` tự lo)?
4. Sau khi `save()`, cần map sang `CustomerResponseDTO` - có nên viết method `mapToResponseDTO(Customer)` riêng để tái dùng cho các method Read/Update sau này không? (Xem lại `VehicleTypeService` đã làm gì với việc này.)
5. Có cần `@Transactional` không? Vì sao?

**Responsibilities:**
- Implement `CustomerService.createCustomer()` per contract Section 4.

**Acceptance Criteria:**
- [ ] `createCustomer(tenantId, {name: "Nguyễn Văn A"})` -> trả về `CustomerResponseDTO` có `riskLevel = 1`
- [ ] `createCustomer(tenantId, {name: "Nguyễn Văn A", phone: "0901234567"})` -> `phone` được lưu đúng
- [ ] Entity lưu vào DB có `tenantId` đúng bằng tham số truyền vào (không lấy từ request body)
- [ ] Unit test pass với target coverage cho method này

---

## TASK-006: `CustomerService.getCustomers` + `getCustomerById` (outline - tự thiết kế)

**Phase/Sprint:** `Phase 3`

**Description:**
Đọc danh sách (tìm kiếm/lọc/phân trang) và chi tiết 1 khách hàng. **Không đưa code.**

**Input:** Từ TASK-003 - `CustomerRepository.findByTenantIdWithFilter`, `findByTenantIdAndId`

**Output:** `Page<CustomerResponseDTO>`, `CustomerResponseDTO` - dùng bởi TASK-008

**Files:**
- `backend/src/main/java/com/carrental/car_rental_backend/customer/service/CustomerService.java` - **modify**: thêm 2 method

**Khung hướng đi:**
1. `getCustomers` nhận `tenantId, search, riskLevel, pageable` - gọi thẳng `findByTenantIdWithFilter` rồi map từng phần tử `Page<Customer>` sang `Page<CustomerResponseDTO>` bằng gì? (Gợi ý: `Page` có method `.map()`.)
2. `getCustomerById` - nếu không tìm thấy thì làm gì? Có nên viết 1 private helper `findCustomerOrThrow(tenantId, id)` dùng chung cho `getCustomerById`, `updateCustomer`, `changeRiskLevel`, `deleteCustomer` không? (Đây chính là pattern đã refactor ở `VehicleTypeService` sau code review - tránh lặp code 4 lần.)
3. Có `@Transactional(readOnly = true)` không? Vì sao 2 method Read khác với Create ở annotation này?

**Responsibilities:**
- Implement `CustomerService.getCustomers()`, `getCustomerById()` per contract Section 4.

**Acceptance Criteria:**
- [ ] `getCustomers(tenantId, null, null, pageable)` -> trả về toàn bộ khách hàng của đúng tenant đó, không lẫn tenant khác (Edge Case MT-1)
- [ ] `getCustomers(tenantId, "0901", null, pageable)` -> chỉ trả khách hàng có `phone` chứa "0901"
- [ ] `getCustomerById(tenantId, idKhôngTồnTại)` -> throw `AppException(CUSTOMER_NOT_FOUND)`
- [ ] `getCustomerById(tenantId, idThuộcTenantKhác)` -> throw `AppException(CUSTOMER_NOT_FOUND)` (không phải 403 - tránh lộ thông tin tồn tại của resource, đúng pattern IDOR ở `VehicleTypeService`)
- [ ] Unit test pass cho cả 2 method

---

## TASK-007: `CustomerService.updateCustomer` + `changeRiskLevel` + `deleteCustomer` (outline - tự thiết kế)

**Phase/Sprint:** `Phase 4, 5`

**Description:**
Sửa thông tin cơ bản, đổi mức độ rủi ro (có rule điều kiện), và xóa (có safety guard). **Không đưa code.**

**Input:** Từ TASK-005 (helper `findCustomerOrThrow` nếu đã tách ở TASK-006), TASK-003 (`existsBookingForCustomer`)

**Output:** `CustomerResponseDTO` (update, changeRiskLevel), `void` (delete) - dùng bởi TASK-008

**Files:**
- `backend/src/main/java/com/carrental/car_rental_backend/customer/service/CustomerService.java` - **modify**: thêm 3 method

**Khung hướng đi:**

`updateCustomer(tenantId, id, request)`:
1. Tìm customer theo tenant, không có thì throw gì?
2. Set lại field nào từ request? (Không đụng `riskLevel`/`blacklistReason` - đổi qua method riêng.)
3. Có cần gọi `save()` tường minh không, hay JPA tự flush khi trong `@Transactional`? (So sánh với cách `VehicleTypeService.updateVehicleType` đã làm.)

`changeRiskLevel(tenantId, id, request)`:
1. Tìm customer theo tenant.
2. Rule nghiệp vụ: nếu `request.riskLevel == 3` (BLACKLIST) mà `request.blacklistReason` rỗng/null thì phải làm gì? (Gợi ý: đây không phải lỗi format nên không validate được ở DTO bằng annotation đơn giản - phải tự viết `if` trong Service và throw `AppException(BAD_REQUEST, "...")`, giống cách `VehicleService` dự kiến check quota.)
3. Nếu `riskLevel != 3`, `blacklistReason` có nên bị xóa về `null` không, hay giữ nguyên giá trị cũ? Nghĩ về ý nghĩa nghiệp vụ: một khách hàng hết bị blacklist thì lý do cũ còn ý nghĩa không?

`deleteCustomer(tenantId, id)`:
1. Tìm customer theo tenant.
2. Gọi `existsBookingForCustomer(id)` - nếu `true` thì throw `AppException(CUSTOMER_IN_USE)`.
3. Nếu không, gọi gì để xóa? (`repository.delete(entity)` hay `deleteById(id)`?)

**Responsibilities:**
- Implement `CustomerService.updateCustomer()`, `changeRiskLevel()`, `deleteCustomer()` per contract Section 4.

**Acceptance Criteria:**
- [ ] `updateCustomer(tenantId, id, {name: "Tên mới"})` -> `CustomerResponseDTO.name == "Tên mới"`, `riskLevel` không đổi
- [ ] `changeRiskLevel(tenantId, id, {riskLevel: 3, blacklistReason: null})` -> throw `AppException(BAD_REQUEST)` (Edge Case #1)
- [ ] `changeRiskLevel(tenantId, id, {riskLevel: 3, blacklistReason: "Bùng xe"})` -> `riskLevel == 3`, `blacklistReason == "Bùng xe"`
- [ ] `changeRiskLevel(tenantId, id, {riskLevel: 1})` (customer đang BLACKLIST) -> `blacklistReason` trả về `null` (Edge Case #2)
- [ ] `deleteCustomer(tenantId, id)` khi khách hàng đã có booking -> throw `AppException(CUSTOMER_IN_USE)` (Edge Case #3)
- [ ] `deleteCustomer(tenantId, id)` khi chưa có booking -> record biến mất khỏi DB
- [ ] Unit test pass cho cả 3 method

---

## TASK-008: `CustomerController`

**Phase/Sprint:** `Phase 2, 3, 4, 5`

**Description:**
6 endpoint REST cho Customer, theo đúng convention `VehicleTypeController` (lấy `tenantId` từ `TenantContext`, `@PreAuthorize` theo role, trả `ApiResponse`).

**Input:** Từ TASK-005, TASK-006, TASK-007 - toàn bộ method `CustomerService`

**Output:** HTTP API - endpoint cuối cùng người dùng gọi

**Files:**
- `backend/src/main/java/com/carrental/car_rental_backend/customer/controller/CustomerController.java` - **create**

**Responsibilities:**
- Implement 6 endpoint, map đúng `CustomerMessage`, đúng `@PreAuthorize` theo Key Decision Section 5.

**Code đầy đủ (tầng Controller):**

```java
package com.carrental.car_rental_backend.customer.controller;

import com.carrental.car_rental_backend.common.dto.ApiResponse;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.customer.constant.CustomerMessage;
import com.carrental.car_rental_backend.customer.dto.ChangeRiskLevelRequestDTO;
import com.carrental.car_rental_backend.customer.dto.CreateCustomerRequestDTO;
import com.carrental.car_rental_backend.customer.dto.CustomerResponseDTO;
import com.carrental.car_rental_backend.customer.dto.UpdateCustomerRequestDTO;
import com.carrental.car_rental_backend.customer.service.CustomerService;
import com.carrental.car_rental_backend.security.context.TenantContext;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF', 'SALE')")
  public ResponseEntity<ApiResponse<CustomerResponseDTO>> createCustomer(
      @Valid @RequestBody CreateCustomerRequestDTO request
  ) {
    UUID tenantId = getTenantIdOrThrow();
    CustomerResponseDTO result = customerService.createCustomer(tenantId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result, CustomerMessage.CREATE_SUCCESS));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF', 'SALE')")
  public ResponseEntity<ApiResponse<Page<CustomerResponseDTO>>> getCustomers(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Integer riskLevel,
      Pageable pageable
  ) {
    UUID tenantId = getTenantIdOrThrow();
    Page<CustomerResponseDTO> result = customerService.getCustomers(tenantId, search, riskLevel, pageable);
    return ResponseEntity.ok(ApiResponse.success(result, CustomerMessage.LIST_SUCCESS));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF', 'SALE')")
  public ResponseEntity<ApiResponse<CustomerResponseDTO>> getCustomerById(@PathVariable UUID id) {
    UUID tenantId = getTenantIdOrThrow();
    CustomerResponseDTO result = customerService.getCustomerById(tenantId, id);
    return ResponseEntity.ok(ApiResponse.success(result, CustomerMessage.DETAIL_SUCCESS));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF', 'SALE')")
  public ResponseEntity<ApiResponse<CustomerResponseDTO>> updateCustomer(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateCustomerRequestDTO request
  ) {
    UUID tenantId = getTenantIdOrThrow();
    CustomerResponseDTO result = customerService.updateCustomer(tenantId, id, request);
    return ResponseEntity.ok(ApiResponse.success(result, CustomerMessage.UPDATE_SUCCESS));
  }

  @PatchMapping("/{id}/risk-level")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<ApiResponse<CustomerResponseDTO>> changeRiskLevel(
      @PathVariable UUID id,
      @Valid @RequestBody ChangeRiskLevelRequestDTO request
  ) {
    UUID tenantId = getTenantIdOrThrow();
    CustomerResponseDTO result = customerService.changeRiskLevel(tenantId, id, request);
    return ResponseEntity.ok(ApiResponse.success(result, CustomerMessage.CHANGE_RISK_LEVEL_SUCCESS));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable UUID id) {
    UUID tenantId = getTenantIdOrThrow();
    customerService.deleteCustomer(tenantId, id);
    return ResponseEntity.ok(ApiResponse.success(null, CustomerMessage.DELETE_SUCCESS));
  }

  private UUID getTenantIdOrThrow() {
    UUID tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Tenant là bắt buộc");
    }
    return tenantId;
  }
}
```

**Acceptance Criteria:**
- [ ] `POST /api/v1/customers` không có JWT -> `401 Unauthorized`
- [ ] `POST /api/v1/customers` với role `SALE` -> `201 Created` thành công
- [ ] `PATCH /{id}/risk-level` với role `STAFF` -> `403 Forbidden`
- [ ] `DELETE /{id}` với role `TENANT_ADMIN` -> `200 OK` (nếu chưa có booking)
- [ ] Toàn bộ endpoint test được qua Swagger UI

---

## TASK-009: `CustomerServiceTest`

**Phase/Sprint:** `Phase 6`

**Description:**
Unit test cho toàn bộ method public của `CustomerService`, dùng Mockito mock `CustomerRepository`.

**Input:** Từ TASK-005, TASK-006, TASK-007 - `CustomerService` hoàn chỉnh

**Output:** Test suite pass - không có consumer khác

**Files:**
- `backend/src/test/java/com/carrental/car_rental_backend/customer/service/CustomerServiceTest.java` - **create**

**Responsibilities:**
- Cover toàn bộ Acceptance Criteria của TASK-005, TASK-006, TASK-007 dưới dạng test case cụ thể (AAA structure, 1 assert/test).

**Acceptance Criteria:**
- [ ] `should_return_response_with_default_risk_level_when_create_customer` - Arrange: request không có riskLevel; Act: `createCustomer()`; Assert: `result.getRiskLevel() == 1`
- [ ] `should_throw_customer_not_found_when_get_customer_by_id_not_exists` - Arrange: mock repository trả `Optional.empty()`; Act + Assert: `assertThrows(AppException.class, ...)`
- [ ] `should_throw_bad_request_when_change_risk_level_to_blacklist_without_reason` - Arrange: request `{riskLevel: 3, blacklistReason: null}`; Act + Assert: throw
- [ ] `should_clear_blacklist_reason_when_risk_level_changed_from_blacklist_to_safe` - Arrange: customer hiện tại `riskLevel=3, blacklistReason="X"`, request `{riskLevel: 1}`; Act; Assert: `result.getBlacklistReason() == null`
- [ ] `should_throw_customer_in_use_when_delete_customer_with_booking_history` - Arrange: mock `existsBookingForCustomer` trả `true`; Act + Assert: throw `AppException(CUSTOMER_IN_USE)`
- [ ] Toàn bộ test pass, `mvn test` không lỗi

---

# 9. Edge Cases

| # | Scenario | Expected Behavior | Handled In |
|---|----------|-------------------|------------|
| 1 | Đổi `riskLevel` sang BLACKLIST (3) nhưng không kèm `blacklistReason` | Throw `400 Bad Request`, không lưu | TASK-007 |
| 2 | Đổi `riskLevel` từ BLACKLIST về SAFE/WARNING | `blacklistReason` tự động về `null` | TASK-007 |
| 3 | Xóa khách hàng đã có ít nhất 1 `booking` | Throw `409 CUSTOMER_IN_USE`, không xóa | TASK-007 |
| 4 | Tạo khách hàng không có `phone`/`email` (đều optional) | Tạo thành công, 2 field lưu `null` | TASK-005 |
| 5 | Tìm kiếm (`search`) không khớp bất kỳ khách hàng nào | Trả `Page` rỗng, không lỗi | TASK-006 |

**Mandatory edge cases:**

| # | Scenario | Expected Behavior | Handled In |
|---|----------|--------------------|------------|
| MT-1 | Request lấy/sửa/xóa khách hàng thuộc tenant khác | Trả `404 CUSTOMER_NOT_FOUND` (không phải 403 - tránh lộ sự tồn tại resource) | TASK-006, TASK-007 |
| MT-2 | Request thiếu `tenantId` trong `TenantContext` | Reject `400 Bad Request`, không fallback all-tenant | TASK-008 (`getTenantIdOrThrow`) |
| MT-4 | (N/A - Customer không nhận `tenantId` từ URL/body) | - | - |

---

# 10. Risks

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | Chưa có `Booking` entity thật, `existsBookingForCustomer` là native query "đoán" tên bảng/cột (`bookings.customer_id`) | Medium | Low | Đã đối chiếu đúng tên bảng/cột theo `Database-Schema.md` mục 2.12; khi module `booking` được build, cần chạy lại integration test này |
| 2 | Rule "bắt buộc `blacklistReason` khi BLACKLIST" chưa được xác nhận chính thức với business, chỉ suy luận hợp lý từ tên field | Low | Medium | Ghi rõ trong PR/commit message là giả định nghiệp vụ, dễ điều chỉnh nếu business yêu cầu khác |

---

# 11. Verification Plan

**Unit Tests:**
- `CustomerServiceTest` - validates: toàn bộ nghiệp vụ Section 8 (TASK-009)

**Manual / Smoke Tests:**
- Tạo khách hàng qua Swagger với role `SALE` -> `201`, `riskLevel = 1`
- Đổi `riskLevel` sang 3 không kèm `blacklistReason` -> `400`
- Xóa khách hàng chưa có booking -> `200`, record biến mất khỏi DB

**Success Criteria:**
- Toàn bộ automated test pass
- 6 endpoint hoạt động đúng qua Swagger, đúng role
- Không có field `id_card`/`driver_license` xuất hiện ở bất kỳ đâu trong code/migration/response

---

# 13. Future Improvements (Optional)

- Thêm `id_card`, `driver_license` (mã hóa AES-256) + `id_card_images`, `driver_license_images` (S3) - làm chung 1 plan riêng, không tách rời field và cơ chế mã hóa.
- OCR tự động điền thông tin từ ảnh giấy tờ khi upload - phụ thuộc 2 mục trên.
- Cân nhắc thêm unique constraint cho `phone` nếu business xác nhận cần dùng để tra cứu khách hàng cũ khi tạo booking mới.
