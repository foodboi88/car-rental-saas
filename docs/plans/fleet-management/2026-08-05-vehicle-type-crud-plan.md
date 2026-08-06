# Implementation Plan: Quản lý Loại Xe (Vehicle Type CRUD) — Học từng bước

Spec Source: `docs/plans/fleet-management/2026-07-23-vehicle-type-backend-spec.md`
Reference Guide: `docs/plans/fleet-management/2026-07-23-vehicle-type-implementation-guide.md`
Owner: huynq7 (learning exercise)
Last Updated: 2026-08-05
Status: Draft

> **Lưu ý sai lệch đã biết:** Spec Source và Reference Guide ở trên (viết 23/07) đều còn nhắc tới cột `base_price` trên `vehicle_types` — cột này **đã bị DROP** bởi migration `V4__align_schema_with_docs.sql` (viết sau đó), theo đúng thiết kế mới nhất ở [`Database-Schema.md`](../../overviews/Database-Schema.md) (cập nhật 31/07). Plan này đã tự sửa lại toàn bộ Contract/DTO/Task để **không dùng `base_price`** — khi đọc lại spec/guide gốc, bỏ qua mọi chỗ nhắc tới field này.

---

# 1. Context

**Problem:**
Cần triển khai đầy đủ luồng CRUD (Create, Read, Update, Delete + đổi trạng thái) cho danh mục Loại Xe (`vehicle_types`), để Chủ nhà xe (`TENANT_ADMIN`) quản lý các phân khúc xe (Sedan, SUV, Bán tải...). Lưu ý: sau migration `V4__align_schema_with_docs.sql`, `vehicle_types` **không còn** cột giá (`base_price` đã bị DROP) — giá thuê giờ thuộc về từng chiếc xe cụ thể (`vehicles.price_per_day`/`weekend_price_per_day`), `VehicleType` chỉ còn đóng vai trò phân loại/danh mục thuần túy. Đây cũng là bài tập thực hành đi đúng luồng: Entity → Repository → DTO → Service → Controller cho một module thật trong dự án.

**Affected Modules:**
- `vehicle` (package `com.carrental.car_rental_backend.vehicle`) - chứa toàn bộ entity/repository/dto/service/controller cho Loại Xe (tái sử dụng folder scaffold đã có sẵn, không tạo package `vehicletype` riêng như guide cũ)
- `common.exception` - bổ sung 3 mã lỗi nghiệp vụ mới vào `ErrorCode` enum có sẵn

**Non-Goals:**
- Không xây dựng entity `Vehicle` (xe vật lý) đầy đủ — chỉ dùng 1 native SQL query để đếm số xe phụ thuộc, không tạo entity/module `Vehicle` hoàn chỉnh (nằm ngoài phạm vi plan này).
- Không triển khai Frontend UI.
- Không sửa schema DB — bảng `vehicle_types` đã tồn tại sẵn, đã được cập nhật đúng chuẩn qua `V1__init_schema.sql` → `V4__align_schema_with_docs.sql` (bỏ `base_price`).
- Không triển khai rule "chặn gán xe/booking khi VehicleType inactive" (rule này thuộc module `vehicle`/`booking` sẽ làm ở plan khác, spec mục 6.3 chỉ nêu tham chiếu).

---

# 2. Constraints

**Language:** Java 21, Spring Boot 4.0.7, Spring Data JPA, Lombok
**Architecture:** Layered theo module (`entity` / `repository` / `dto` / `service` / `controller`) — không tách interface/impl cho Service (chỉ có đúng 1 implementation, tránh premature abstraction theo YAGNI)

**Rules:**
- Không sửa module `account`, `branch`, `booking`, `customer`, `auth`, `security`, `tenant` — chỉ thêm mới trong `vehicle` và sửa `ErrorCode.java`
- Không thêm dependency Maven mới — mọi thứ cần đã có sẵn trong `pom.xml` (Spring Data JPA, Validation, Lombok)
- Mọi truy vấn Repository bắt buộc lọc theo `tenantId` — không có ngoại lệ

**Performance Budgets:** Không áp dụng (learning exercise, không có SLA)

---

# 3. Conventions

> Rút ra từ code thật đang có trong `common/`, `security/`, không phải từ guide cũ.

**Naming:**
- Classes: `PascalCase` - vd `VehicleType`, `VehicleTypeService`
- Methods/variables: `camelCase` - vd `getVehicleTypeById`, `tenantId`
- Packages: `com.carrental.car_rental_backend.vehicle.<layer>` - vd `vehicle.entity`, `vehicle.repository`

**Error Handling:**
- Ném `AppException(ErrorCode.XXX)` hoặc `AppException(ErrorCode.XXX, "message tuỳ biến")` — xem `common/exception/AppException.java` hiện có.
- `GlobalExceptionHandler` đã có sẵn, tự bắt `AppException` → trả `ApiResponse.error(code, message)` với đúng `HttpStatus` gắn trong `ErrorCode`.
- Response lỗi validation (`@Valid` fail) đã được `GlobalExceptionHandler` xử lý sẵn, không cần code thêm.

**Logging:**
- Dùng Lombok `@Slf4j`, log ở Service layer khi có exception nghiệp vụ (theo pattern `GlobalExceptionHandler` đang làm: `log.warn(...)` cho lỗi nghiệp vụ, `log.error(...)` cho lỗi hệ thống).

**Testing:**
- Framework: JUnit 5 + Mockito (`spring-boot-starter-test` sẵn có).
- Naming: `methodName_ShouldExpectedBehavior_WhenCondition` (theo đúng style trong implementation-guide.md).

**API Response:**
- Mọi Controller method trả `ResponseEntity<ApiResponse<T>>`, dùng `ApiResponse.success(data, message)` hoặc `ApiResponse.success(data)`.
- Field lỗi bên trong `ApiResponse` là `ErrorApi` (nested static class), **không phải** `ApiError` như trong tài liệu cũ.

**Task Size:**
- Max 200 LOC/task (không tính test)

---

# 4. Contracts

## Data Structure: `VehicleType` (Entity)

```text
VehicleType {
  id:          UUID        - PK, @GeneratedValue(strategy = GenerationType.UUID)
  tenantId:    UUID        - not null, FK -> tenants(id)
  name:        String      - not null, max 50 chars
  description: String      - nullable, TEXT
  isActive:    Boolean     - default true
  createdAt:   Instant     - set on @PrePersist, immutable sau đó
  updatedAt:   Instant     - set on @PrePersist, refreshed on @PreUpdate
}
```
> Không có field giá — `base_price` đã bị DROP khỏi `vehicle_types` ở `V4__align_schema_with_docs.sql`. Giá thuê thuộc về `Vehicle` (`price_per_day`/`weekend_price_per_day`), nằm ngoài phạm vi plan này (xem Non-Goals).

## Shared DTOs

```text
CreateVehicleTypeRequestDTO {
  name:        String      - @NotBlank, @Size(max = 50)
  description: String      - optional
}

UpdateVehicleTypeRequestDTO {
  name:        String      - @NotBlank, @Size(max = 50)
  description: String      - optional
}

VehicleTypeStatusRequestDTO {
  status: Boolean - @NotNull  (true = active, false = inactive)
}

VehicleTypeResponseDTO {
  id:           UUID
  tenantId:     UUID
  name:         String
  description:  String       - nullable
  isActive:     Boolean
  vehicleCount: long         - luôn tính lại (không lưu trong DB), số xe hiện thuộc loại này
  createdAt:    Instant
  updatedAt:    Instant
}
```

## Interface: `VehicleTypeRepository` (extends `JpaRepository<VehicleType, UUID>`)

```text
findByTenantIdAndId(tenantId: UUID, id: UUID): Optional<VehicleType>
  - post: rỗng nếu id không tồn tại HOẶC thuộc tenant khác (không phân biệt 2 case này ra ngoài)

existsByTenantIdAndNameIgnoreCase(tenantId: UUID, name: String): boolean
  - post: true nếu đã có 1 VehicleType cùng tenant, tên trùng không phân biệt hoa/thường

existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId: UUID, name: String, id: UUID): boolean
  - post: giống trên nhưng loại trừ chính record đang sửa (dùng khi Update)

findByTenantIdWithFilter(tenantId: UUID, search: String?, isActive: Boolean?, pageable: Pageable): Page<VehicleType>
  - pre: search/isActive có thể null (không filter field đó)
  - post: JPQL LIKE case-insensitive trên `name` khi search != null

countVehiclesByTenantIdAndVehicleTypeId(tenantId: UUID, vehicleTypeId: UUID): long
  - post: native query `SELECT COUNT(*) FROM vehicles WHERE tenant_id = ? AND vehicle_type_id = ?`
  - lý do dùng native query thay vì JPQL: bảng `vehicles` đã tồn tại trong DB nhưng chưa có JPA Entity tương ứng (Non-Goal), nên không thể viết JPQL tham chiếu entity `Vehicle`
```

## Class: `VehicleTypeService` (không tách interface — chỉ 1 implementation, xem Section 5 Key Decisions)

```text
getVehicleTypes(tenantId: UUID, search: String?, isActive: Boolean?, pageable: Pageable): Page<VehicleTypeResponseDTO>
  - post: mỗi item map kèm vehicleCount tính từ countVehiclesByTenantIdAndVehicleTypeId

getVehicleTypeById(tenantId: UUID, id: UUID): VehicleTypeResponseDTO
  - throws: AppException(VEHICLE_TYPE_NOT_FOUND) khi không tìm thấy trong scope tenant

createVehicleType(tenantId: UUID, request: CreateVehicleTypeRequestDTO): VehicleTypeResponseDTO
  - pre: request.name.trim() không trùng tên (case-insensitive) trong cùng tenant
  - throws: AppException(VEHICLE_TYPE_NAME_EXISTS) khi trùng tên
  - post: isActive mặc định = true khi tạo mới

updateVehicleType(tenantId: UUID, id: UUID, request: UpdateVehicleTypeRequestDTO): VehicleTypeResponseDTO
  - throws: AppException(VEHICLE_TYPE_NOT_FOUND) khi id không tồn tại trong tenant
  - throws: AppException(VEHICLE_TYPE_NAME_EXISTS) khi tên trùng với 1 record KHÁC (loại trừ chính nó)

changeVehicleTypeStatus(tenantId: UUID, id: UUID, isActive: Boolean): VehicleTypeResponseDTO
  - throws: AppException(VEHICLE_TYPE_NOT_FOUND) khi id không tồn tại trong tenant
  - post: cho phép set false ngay cả khi vehicleCount > 0 (không chặn)

deleteVehicleType(tenantId: UUID, id: UUID): void
  - throws: AppException(VEHICLE_TYPE_NOT_FOUND) khi id không tồn tại trong tenant
  - throws: AppException(VEHICLE_TYPE_IN_USE) khi vehicleCount > 0 (chặn xóa)
```

---

# 5. Target Architecture

**Components:**
- `VehicleTypeController` - nhận HTTP request, validate `@Valid`, lấy `tenantId` từ `TenantContext`, gọi Service, bọc `ApiResponse`
- `VehicleTypeService` - business rule (trùng tên, delete guard), map Entity ↔ DTO
- `VehicleTypeRepository` - JPA data access, filter theo `tenantId`
- `VehicleType` (Entity) - map bảng `vehicle_types`

**Interaction Flow:**

```text
Client (Swagger/Postman)
  -> VehicleTypeController.createVehicleType(request: CreateVehicleTypeRequestDTO)
    -> TenantContext.getTenantId() -> UUID
    -> VehicleTypeService.createVehicleType(tenantId, request)
      -> VehicleTypeRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name) -> boolean
      -> [nếu true] throw AppException(VEHICLE_TYPE_NAME_EXISTS)
      -> VehicleTypeRepository.save(entity) -> VehicleType
      -> map Entity -> VehicleTypeResponseDTO
  <- ResponseEntity<ApiResponse<VehicleTypeResponseDTO>> (201 Created)

[[Lỗi nghiệp vụ]]
VehicleTypeService ném AppException
  -> GlobalExceptionHandler.handleAppException() (đã có sẵn, không cần code thêm)
  <- ResponseEntity<ApiResponse<Void>> với đúng HttpStatus từ ErrorCode
```

**Key Decisions:**
- Dùng lại package `vehicle` đã scaffold sẵn thay vì tạo `vehicletype` riêng - tránh lệch với cấu trúc module hiện tại của project (mỗi domain 1 package, không tách nhỏ theo entity).
- Đếm `vehicleCount` bằng native SQL thay vì tạo entity `Vehicle` stub - tránh việc phải "extend lại sau" một entity viết tạm bợ, giữ đúng Non-Goal.
- `VehicleTypeService` là 1 class cụ thể, **không tách interface riêng** - chỉ có đúng 1 implementation, tách interface lúc này là premature abstraction (YAGNI); nếu sau này thật sự cần nhiều implementation khác nhau, tách ra khi đó cũng không tốn nhiều công.
- Chia theo Option B (vertical slice từng API) đã được duyệt ở bước trước - mỗi Phase xong test được ngay qua Swagger, đúng nhu cầu học từng bước.

---

# 6. Artifact Registry

| Artifact | Type | Owner Task | Implements |
|----------|------|------------|------------|
| `backend/.../vehicle/entity/VehicleType.java` | entity | TASK-001 | `VehicleType` |
| `backend/.../vehicle/repository/VehicleTypeRepository.java` | interface | TASK-001 | `VehicleTypeRepository` |
| `backend/.../vehicle/dto/CreateVehicleTypeRequestDTO.java` | DTO | TASK-002 | `CreateVehicleTypeRequestDTO` |
| `backend/.../vehicle/dto/UpdateVehicleTypeRequestDTO.java` | DTO | TASK-002 | `UpdateVehicleTypeRequestDTO` |
| `backend/.../vehicle/dto/VehicleTypeStatusRequestDTO.java` | DTO | TASK-002 | `VehicleTypeStatusRequestDTO` |
| `backend/.../vehicle/dto/VehicleTypeResponseDTO.java` | DTO | TASK-002 | `VehicleTypeResponseDTO` |
| `backend/.../common/exception/ErrorCode.java` | enum | TASK-003 | - (modify: thêm 3 mã lỗi) |
| `backend/.../vehicle/service/VehicleTypeService.java` | class | TASK-004 | `createVehicleType()` |
| `backend/.../vehicle/controller/VehicleTypeController.java` | REST controller | TASK-004 | `POST /api/v1/vehicle-types` |
| `backend/.../vehicle/service/VehicleTypeService.java` | class | TASK-005 | - (modify: thêm `getVehicleTypes()`, `getVehicleTypeById()`) |
| `backend/.../vehicle/controller/VehicleTypeController.java` | REST controller | TASK-005 | `GET /api/v1/vehicle-types`, `GET /{id}` |
| `backend/.../vehicle/service/VehicleTypeService.java` | class | TASK-006 | - (modify: thêm `updateVehicleType()`) |
| `backend/.../vehicle/controller/VehicleTypeController.java` | REST controller | TASK-006 | `PUT /api/v1/vehicle-types/{id}` |
| `backend/.../vehicle/service/VehicleTypeService.java` | class | TASK-007 | - (modify: thêm `changeVehicleTypeStatus()`, `deleteVehicleType()`) |
| `backend/.../vehicle/controller/VehicleTypeController.java` | REST controller | TASK-007 | `PATCH /{id}/status`, `DELETE /{id}` |
| `backend/src/test/.../vehicle/service/VehicleTypeServiceTest.java` | test | TASK-008 | - |

---

# 7. Task Graph

**User-Approved Phase Strategy:**

Selected strategy: **Option B — Vertical Slice theo từng API (CRUD)**

Rationale: Người thực hiện đang học Spring Boot lần đầu (chuyển từ JS/TS). Sau Phase 1 (nền tảng), mỗi phase tiếp theo cho ra **1 API gọi thử được ngay qua Swagger UI** — tạo phản hồi tức thì để củng cố hiểu biết, thay vì phải viết xong toàn bộ 5 tầng (entity/repo/dto/service/controller) mới thấy kết quả như Option A.

Rejected alternatives:
- Option A (Horizontal Layers) - bị loại vì phải đợi tới hết Phase 5 mới gọi được API thật, không có phản hồi sớm cho người mới học.
- Option C (MVP rồi mới thêm rule) - bị loại vì phải quay lại sửa code đã viết ở Phase 1 để nhét thêm rule nghiệp vụ, dễ gây rối cho người học.

| Phase | Goal | Testable/Demoable Outcome |
|-------|------|---------------------------|
| Phase 1 | Nền tảng: Entity + Repository + toàn bộ DTO + mã lỗi | Compile thành công, chưa gọi API được (chưa có Controller) |
| Phase 2 | API Tạo mới | `POST /api/v1/vehicle-types` chạy được qua Swagger, trả về record vừa tạo |
| Phase 3 | API Đọc (list + chi tiết) | `GET /api/v1/vehicle-types` và `GET /{id}` trả đúng dữ liệu, có phân trang/tìm kiếm |
| Phase 4 | API Cập nhật | `PUT /{id}` sửa được `name`/`description`, chặn trùng tên |
| Phase 5 | API Đổi trạng thái & Xóa | `PATCH /{id}/status` và `DELETE /{id}` chạy được, có chặn xóa khi còn xe phụ thuộc |
| Phase 6 | Unit Test | `mvn test` pass toàn bộ test case của `VehicleTypeServiceTest` |

| ID | Phase | Name | Depends On | Effort |
|----|-------|------|------------|--------|
| TASK-001 | Phase 1 | Entity + Repository | - | M |
| TASK-002 | Phase 1 | 4 DTOs | - | S |
| TASK-003 | Phase 1 | Thêm mã lỗi vào `ErrorCode` | - | S |
| TASK-004 | Phase 2 | Create API (Service + Controller) | TASK-001, TASK-002, TASK-003 | M |
| TASK-005 | Phase 3 | Read API (list + detail) | TASK-004 | M |
| TASK-006 | Phase 4 | Update API | TASK-005 | S |
| TASK-007 | Phase 5 | Status & Delete API | TASK-006 | M |
| TASK-008 | Phase 6 | Unit Test toàn bộ Service | TASK-007 | M |

**Dependency Graph:**

```text
TASK-001 --.
TASK-002 --+-- TASK-004 -- TASK-005 -- TASK-006 -- TASK-007 -- TASK-008
TASK-003 --'
```

**Execution Rules:**
- TASK-001, TASK-002, TASK-003 độc lập, có thể làm song song (hoặc tuần tự nếu học 1 mình cho dễ theo dõi).
- Từ TASK-004 trở đi bắt buộc tuần tự — mỗi Task sửa tiếp `VehicleTypeService`/`VehicleTypeService`/`VehicleTypeController` đã tạo ở Task trước.

---

# 8. Task Specifications

## TASK-001: Entity + Repository

**Phase:** Phase 1

**Description:**
Tạo `VehicleType` entity map với bảng `vehicle_types` đã có sẵn trong DB, và `VehicleTypeRepository` với các query cần cho toàn bộ luồng CRUD sau này (kể cả các method Phase 2-5 chưa dùng tới, viết luôn 1 lần cho gọn).

**Input:** Không (root task) — dựa trên schema thật trong `V1__init_schema.sql` (bảng `vehicle_types`, `vehicles`)

**Output:** `VehicleType` entity, `VehicleTypeRepository` — dùng bởi TASK-004 đến TASK-007

**Files:**
- `backend/src/main/java/com/carrental/car_rental_backend/vehicle/entity/VehicleType.java` - **create**
- `backend/src/main/java/com/carrental/car_rental_backend/vehicle/repository/VehicleTypeRepository.java` - **create**

**Responsibilities:**
- Implement `VehicleType` entity đúng theo Data Structure ở Section 4 (dùng `@PrePersist`/`@PreUpdate` set `createdAt`/`updatedAt`, không dùng Lombok cho entity để thấy rõ getter/setter — theo đúng style `Vehicle.java`/`VehicleType.java` mẫu trong implementation-guide.md).
- Implement toàn bộ 5 method của `VehicleTypeRepository` theo contract Section 4.

**Acceptance Criteria:**
- [ ] Code compile thành công, không có warning về mapping entity/DB (chạy app, log không báo `Schema-validation` error liên quan `vehicle_types`)
- [ ] `findByTenantIdAndId(tenantId, id)` → rỗng khi `id` thuộc tenant khác
- [ ] `existsByTenantIdAndNameIgnoreCase(tenantId, "sedan")` → `true` khi đã có record tên `"Sedan"` (khác hoa/thường) cùng tenant
- [ ] `countVehiclesByTenantIdAndVehicleTypeId(tenantId, id)` → trả `0` khi bảng `vehicles` chưa có record nào tham chiếu `id` đó (native query không lỗi dù bảng `vehicles` rỗng)

---

## TASK-002: 4 DTOs

**Phase:** Phase 1

**Description:**
Viết đủ 4 class DTO theo đúng Shared DTOs ở Section 4, dùng Bean Validation annotation cho request DTO.

**Input:** Không (root task)

**Output:** 4 DTO class — dùng bởi TASK-004 đến TASK-007

**Files:**
- `backend/src/main/java/com/carrental/car_rental_backend/vehicle/dto/CreateVehicleTypeRequestDTO.java` - **create**
- `backend/src/main/java/com/carrental/car_rental_backend/vehicle/dto/UpdateVehicleTypeRequestDTO.java` - **create**
- `backend/src/main/java/com/carrental/car_rental_backend/vehicle/dto/VehicleTypeStatusRequestDTO.java` - **create**
- `backend/src/main/java/com/carrental/car_rental_backend/vehicle/dto/VehicleTypeResponseDTO.java` - **create**

**Responsibilities:**
- Áp đúng annotation validation nêu ở Section 4 (`@NotBlank`, `@Size(max=50)`, `@NotNull`, `@DecimalMin("0.0")`).
- `VehicleTypeResponseDTO` không có annotation validation (là response, không phải request).

**Acceptance Criteria:**
- [ ] `CreateVehicleTypeRequestDTO` với `name = ""` → khi bind qua `@Valid` ở Controller (TASK-004) trả lỗi `VALIDATION_ERROR` (400), field `name` có message tương ứng
- [ ] `CreateVehicleTypeRequestDTO` với `name` dài 51 ký tự → validation lỗi `Size`
- [ ] Code compile thành công

---

## TASK-003: Thêm mã lỗi vào `ErrorCode`

**Phase:** Phase 1

**Description:**
Bổ sung 3 giá trị enum mới vào `ErrorCode.java` hiện có (không tạo file mới, không đổi giá trị cũ), theo đúng bảng mã lỗi ở spec Section 7.

**Input:** Không (root task) — dựa trên file `ErrorCode.java` hiện tại

**Output:** `ErrorCode` enum có đủ 3 mã mới — dùng bởi TASK-004, TASK-005, TASK-007

**Files:**
- `backend/src/main/java/com/carrental/car_rental_backend/common/exception/ErrorCode.java` - **modify**: thêm 3 dòng enum value vào khối `// Business Custom Errors`

**Responsibilities:**
- `VEHICLE_TYPE_NOT_FOUND("VEHICLE_TYPE_NOT_FOUND", "Không tìm thấy loại xe yêu cầu", HttpStatus.NOT_FOUND)`
- `VEHICLE_TYPE_NAME_EXISTS("VEHICLE_TYPE_NAME_EXISTS", "Tên loại xe đã tồn tại trong nhà xe", HttpStatus.CONFLICT)`
- `VEHICLE_TYPE_IN_USE("VEHICLE_TYPE_IN_USE", "Không thể xóa loại xe đang có xe phụ thuộc", HttpStatus.CONFLICT)`

**Acceptance Criteria:**
- [ ] Code compile thành công, 3 mã lỗi cũ (`TENANT_ACCESS_DENIED`, `VEHICLE_NOT_AVAILABLE`, `BOOKING_EXPIRED`) không bị thay đổi
- [ ] `ErrorCode.VEHICLE_TYPE_NOT_FOUND.getHttpStatus()` → `HttpStatus.NOT_FOUND`

---

## TASK-004: Create API (Service + Controller)

**Phase:** Phase 2

**Description:**
Tạo `VehicleTypeService` (class, không tách interface) với method `createVehicleType()`, và `VehicleTypeController` với endpoint `POST /api/v1/vehicle-types`. Đây là API đầu tiên gọi thử được qua Swagger.

**Input:**
- Từ TASK-001: `VehicleType`, `VehicleTypeRepository`
- Từ TASK-002: `CreateVehicleTypeRequestDTO`, `VehicleTypeResponseDTO`
- Từ TASK-003: `ErrorCode.VEHICLE_TYPE_NAME_EXISTS`

**Output:** `VehicleTypeService.createVehicleType()` — chạy được qua HTTP; `VehicleTypeController` skeleton dùng tiếp ở TASK-005 đến TASK-007

**Files:**
- `backend/src/main/java/com/carrental/car_rental_backend/vehicle/service/VehicleTypeService.java` - **create**: class `@Service` (không tách interface), implement `createVehicleType()`, có helper method `private VehicleTypeResponseDTO mapToResponseDTO(...)` dùng lại ở các Task sau
- `backend/src/main/java/com/carrental/car_rental_backend/vehicle/controller/VehicleTypeController.java` - **create**: khai báo `@RestController @RequestMapping("/api/v1/vehicle-types")`, chỉ có method `POST` ở bước này, có helper `private UUID getTenantIdOrThrow()` đọc từ `TenantContext.getTenantId()`, ném `AppException(ErrorCode.BAD_REQUEST, "Tenant context is required")` nếu null

**Responsibilities:**
- Implement `createVehicleType()` theo contract Section 4 (check trùng tên trước khi save).
- Controller method có `@PreAuthorize("hasRole('TENANT_ADMIN')")`, `@Valid @RequestBody`, trả `ResponseEntity.status(HttpStatus.CREATED)`.

**Acceptance Criteria:**
- [ ] `createVehicleType(tenantId, {name: "Sedan 4 chỗ", description: "Xe 4 chỗ phổ thông"})` → trả `VehicleTypeResponseDTO` với `isActive = true`, `vehicleCount = 0`
- [ ] `createVehicleType(tenantId, {name: "sedan 4 chỗ", ...})` khi đã tồn tại `"Sedan 4 chỗ"` cùng tenant → `AppException(VEHICLE_TYPE_NAME_EXISTS)`
- [ ] Gọi `POST /api/v1/vehicle-types` qua Swagger UI với JWT role `STAFF` → `403 Forbidden`
- [ ] Gọi qua Swagger UI với JWT role `TENANT_ADMIN`, body hợp lệ → `201 Created`, body `ApiResponse.success(...)` chứa đúng data
- [ ] Edge case #6, #7 (Section 9) xử lý đúng

---

## TASK-005: Read API (list + detail)

**Phase:** Phase 3

**Description:**
Thêm 2 method đọc dữ liệu vào `VehicleTypeService` đã có, thêm 2 endpoint `GET` vào Controller đã có.

**Input:**
- Từ TASK-004: `VehicleTypeService`, `VehicleTypeService`, `VehicleTypeController`, helper `mapToResponseDTO()`, `getTenantIdOrThrow()`

**Output:** `getVehicleTypes()`, `getVehicleTypeById()` — dùng để verify dữ liệu tạo ở TASK-004, và dùng lại ở TASK-006/007 sau khi update/status để lấy lại state

**Files:**
- `backend/.../vehicle/service/VehicleTypeService.java` - **modify**: thêm 2 method signature
- `backend/.../vehicle/service/VehicleTypeService.java` - **modify**: implement 2 method
- `backend/.../vehicle/controller/VehicleTypeController.java` - **modify**: thêm `GET` (list, có `@RequestParam search, isActive, Pageable`) và `GET /{id}`

**Responsibilities:**
- `getVehicleTypes()` cho phép `search`/`isActive` null (không filter field đó).
- `getVehicleTypeById()` ném `VEHICLE_TYPE_NOT_FOUND` nếu không tìm thấy trong scope tenant.
- Cả 2 endpoint `@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF', 'SALE')")`.

**Acceptance Criteria:**
- [ ] `getVehicleTypes(tenantId, null, null, pageable)` → trả về record đã tạo ở TASK-004
- [ ] `getVehicleTypes(tenantId, "sedan", null, pageable)` → chỉ trả record có tên chứa "sedan" (không phân biệt hoa/thường)
- [ ] `getVehicleTypeById(tenantId, idKhôngTồnTại)` → `AppException(VEHICLE_TYPE_NOT_FOUND)`
- [ ] `getVehicleTypeById(tenantIdKhác, idHợpLệCủaTenantA)` → `AppException(VEHICLE_TYPE_NOT_FOUND)` (MT-1, không lộ dữ liệu tenant khác)
- [ ] Gọi `GET /api/v1/vehicle-types` qua Swagger với JWT role `SALE` → `200 OK` (không bị chặn quyền)

---

## TASK-006: Update API

**Phase:** Phase 4

**Description:**
Thêm method `updateVehicleType()` và endpoint `PUT /{id}`.

**Input:**
- Từ TASK-005: Controller/Service đã có `getVehicleTypeById` dùng để kiểm tra tồn tại trước khi update

**Output:** `updateVehicleType()` — không có task nào sau phụ thuộc trực tiếp, nhưng dùng chung Service/Controller với TASK-007

**Files:**
- `backend/.../vehicle/service/VehicleTypeService.java` - **modify**: thêm method signature
- `backend/.../vehicle/service/VehicleTypeService.java` - **modify**: implement `updateVehicleType()`
- `backend/.../vehicle/controller/VehicleTypeController.java` - **modify**: thêm `PUT /{id}`, `@PreAuthorize("hasRole('TENANT_ADMIN')")`

**Responsibilities:**
- Check tồn tại (`VEHICLE_TYPE_NOT_FOUND`) trước, rồi check trùng tên loại trừ chính nó (`existsByTenantIdAndNameIgnoreCaseAndIdNot`).

**Acceptance Criteria:**
- [ ] `updateVehicleType(tenantId, id, {name: "Sedan 4 chỗ (2026)", description: "Cập nhật mô tả"})` → trả DTO với giá trị mới, `updatedAt` thay đổi
- [ ] `updateVehicleType(tenantId, id, {name: <tên của 1 record khác>, ...})` → `AppException(VEHICLE_TYPE_NAME_EXISTS)`
- [ ] `updateVehicleType(tenantId, id, {name: <tên hiện tại của chính nó>, ...})` → **không** ném lỗi (loại trừ chính nó khỏi check trùng)
- [ ] `updateVehicleType(tenantId, idKhôngTồnTại, ...)` → `AppException(VEHICLE_TYPE_NOT_FOUND)`

---

## TASK-007: Status & Delete API

**Phase:** Phase 5

**Description:**
Thêm 2 method cuối cùng: đổi trạng thái và xóa (có delete-guard), cùng 2 endpoint `PATCH`/`DELETE`.

**Input:**
- Từ TASK-001: `countVehiclesByTenantIdAndVehicleTypeId()` — dùng cho delete guard
- Từ TASK-003: `ErrorCode.VEHICLE_TYPE_IN_USE`

**Output:** Toàn bộ `VehicleTypeService` hoàn chỉnh — dùng cho TASK-008 (unit test)

**Files:**
- `backend/.../vehicle/service/VehicleTypeService.java` - **modify**: thêm 2 method signature cuối
- `backend/.../vehicle/service/VehicleTypeService.java` - **modify**: implement `changeVehicleTypeStatus()`, `deleteVehicleType()`
- `backend/.../vehicle/controller/VehicleTypeController.java` - **modify**: thêm `PATCH /{id}/status`, `DELETE /{id}`, cả 2 đều `@PreAuthorize("hasRole('TENANT_ADMIN')")`

**Responsibilities:**
- `deleteVehicleType()`: gọi `countVehiclesByTenantIdAndVehicleTypeId` trước, nếu `> 0` ném `VEHICLE_TYPE_IN_USE` kèm message có tên loại xe và số lượng xe (theo đúng format spec Section 6.2).
- `changeVehicleTypeStatus()`: **không** check `vehicleCount`, luôn cho phép đổi (kể cả set `false` khi còn xe).

**Acceptance Criteria:**
- [ ] `changeVehicleTypeStatus(tenantId, id, false)` khi `vehicleCount > 0` → **thành công**, không ném lỗi (đúng edge case #5)
- [ ] `deleteVehicleType(tenantId, id)` khi `countVehiclesByTenantIdAndVehicleTypeId > 0` → `AppException(VEHICLE_TYPE_IN_USE)`, message chứa tên loại xe + số lượng
- [ ] `deleteVehicleType(tenantId, id)` khi `count == 0` → xóa thành công, `findByTenantIdAndId` sau đó trả rỗng
- [ ] `deleteVehicleType(tenantId, idKhôngTồnTại)` → `AppException(VEHICLE_TYPE_NOT_FOUND)`

---

## TASK-008: Unit Test toàn bộ Service

**Phase:** Phase 6

**Description:**
Viết `VehicleTypeServiceTest` dùng JUnit 5 + Mockito, mock `VehicleTypeRepository`, cover toàn bộ nhánh nghiệp vụ đã liệt kê ở Section 9.

**Input:** Từ TASK-004 đến TASK-007 — toàn bộ `VehicleTypeService` đã hoàn chỉnh

**Output:** Test suite — không có task nào phụ thuộc, là điểm kết thúc plan

**Files:**
- `backend/src/test/java/com/carrental/car_rental_backend/vehicle/service/VehicleTypeServiceTest.java` - **create**

**Responsibilities:**
- Mock `VehicleTypeRepository` bằng `@Mock`, inject vào `@InjectMocks VehicleTypeService`.
- Test tên method theo convention Section 3 (`methodName_ShouldExpectedBehavior_WhenCondition`).

**Acceptance Criteria:**
- [ ] `createVehicleType_ShouldSuccess_WhenNameNotExists` — pass
- [ ] `createVehicleType_ShouldThrowNameExists_WhenNameDuplicated` — pass
- [ ] `updateVehicleType_ShouldThrowNotFound_WhenIdNotExists` — pass
- [ ] `updateVehicleType_ShouldAllowSameName_WhenUpdatingItself` — pass (edge case dễ bị bug nếu quên `AndIdNot`)
- [ ] `deleteVehicleType_ShouldThrowInUse_WhenVehicleCountGreaterThanZero` — pass
- [ ] `deleteVehicleType_ShouldSuccess_WhenNoVehicleDependency` — pass
- [ ] `changeVehicleTypeStatus_ShouldAllowDeactivate_WhenVehicleCountGreaterThanZero` — pass
- [ ] `mvn test -Dtest=VehicleTypeServiceTest` → toàn bộ pass, 0 failures

---

# 9. Edge Cases

| # | Scenario | Expected Behavior | Handled In |
|---|----------|-------------------|------------|
| 1 | Tạo mới với tên trùng (không phân biệt hoa/thường) trong cùng tenant | `409` `VEHICLE_TYPE_NAME_EXISTS` | TASK-004 |
| 2 | Update với tên trùng 1 record KHÁC trong cùng tenant | `409` `VEHICLE_TYPE_NAME_EXISTS` | TASK-006 |
| 3 | Update với tên trùng CHÍNH NÓ (không đổi tên, chỉ đổi mô tả) | Cho phép, không lỗi | TASK-006 |
| 4 | Xóa loại xe đang có `vehicleCount > 0` | Chặn, `409` `VEHICLE_TYPE_IN_USE`, message có tên + số lượng | TASK-007 |
| 5 | Đổi `isActive = false` khi vẫn còn xe thuộc loại này | Cho phép, không chặn | TASK-007 |
| 6 | `name` rỗng hoặc dài hơn 50 ký tự | `400` validation error | TASK-002, TASK-004 |
| 7 | Role `STAFF`/`SALE` gọi `POST`/`PUT`/`PATCH`/`DELETE` | `403 Forbidden` | TASK-004, TASK-006, TASK-007 |
| 8 | `GET` list với `search`/`isActive` không khớp record nào | Trả `Page` rỗng, không lỗi | TASK-005 |
| MT-1 | Tenant A request `id` của Tenant B (GET/PUT/PATCH/DELETE) | `404 VEHICLE_TYPE_NOT_FOUND` (không phải 403 — không tiết lộ record tồn tại) | TASK-005, TASK-006, TASK-007 |
| MT-2 | Request thiếu Tenant context (JWT không hợp lệ/thiếu `tenant_id`) | `400 Bad Request`, không fallback về "tất cả tenant" | TASK-004 (`getTenantIdOrThrow()`) |

---

# 10. Risks

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | Native SQL query đếm `vehicles` (TASK-001) không có compile-time check — nếu ai đó đổi tên cột `vehicle_type_id` trong migration sau này, query lỗi âm thầm lúc runtime | Med | Low | Viết Acceptance Criteria xác nhận query chạy được ngay ở TASK-001 (test với bảng `vehicles` rỗng); khi có entity `Vehicle` thật ở plan sau, thay native query bằng JPQL cross-entity như implementation-guide.md đã mô tả |
| 2 | Người thực hiện mới học Spring Data JPA, dễ quên hậu tố `AndIdNot` khi viết method Update → tự vô tình chặn nhầm cả chính nó khi trùng tên | Med | Med | Task-008 có sẵn test case `updateVehicleType_ShouldAllowSameName_WhenUpdatingItself` để bắt lỗi này sớm |
| 3 | Nhầm giữa `ErrorApi` (tên thật trong code) và `ApiError` (tên trong guide/spec cũ) khi code theo tài liệu cũ | Low | Med | Đã ghi rõ trong Section 3 Conventions |

---

# 11. Verification Plan

**Unit Tests:**
- `VehicleTypeServiceTest` - validates: toàn bộ business rule ở Section 9 (trùng tên, delete guard, not-found)

**Manual / Smoke Tests (qua Swagger UI `/swagger-ui.html`):**
- Scenario A - Tạo loại xe mới với JWT `TENANT_ADMIN` → `201`, thấy record trong `GET` list ngay sau đó
- Scenario B - Gọi bất kỳ endpoint ghi (`POST`/`PUT`/`PATCH`/`DELETE`) với JWT `STAFF` → `403`
- Scenario C - Tạo loại xe, gán 1 dòng test vào `vehicles` table trực tiếp qua DBeaver (`INSERT INTO vehicles (tenant_id, vehicle_type_id, ...) VALUES (...)`), sau đó gọi `DELETE` → phải bị chặn `VEHICLE_TYPE_IN_USE`

**Success Criteria:**
- Toàn bộ Acceptance Criteria ở Section 8 đạt
- `mvn test` pass 0 failures
- Hành vi khớp spec: `docs/plans/fleet-management/2026-07-23-vehicle-type-backend-spec.md`

---

# 13. Future Improvements (Optional)

- Tạo entity `Vehicle` đầy đủ + thay native query đếm bằng JPQL cross-entity thật (theo đúng implementation-guide.md mục Bước 2-3) - rationale: hiện tại native query đủ dùng cho phạm vi plan này, việc dựng cả module `Vehicle` xứng đáng có plan riêng.
- Bổ sung rule "chặn gán VehicleType inactive khi tạo Vehicle/Booking mới" (spec mục 6.3) - rationale: phụ thuộc vào module `Vehicle`/`Booking` chưa tồn tại, để plan riêng khi tới lượt.
