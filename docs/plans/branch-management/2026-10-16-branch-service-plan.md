# Implementation Plan: Quản Lý Chi Nhánh Nhà Xe (Branch Service)

Spec Source: `docs/plans/branch-management/2026-10-15-branch-service-spec.md`  
Owner: Dev Team & AI Assistant  
Last Updated: 2026-10-16  
Status: `Draft`  

---

# 1. Context

**Problem:**
Trong hệ thống SaaS Car Rental đa doanh nghiệp (Multi-Tenant & Multi-Branch), mỗi nhà xe (`tenant`) vận hành mạng lưới nhiều chi nhánh để điều phối xe, nhận/trả xe và phân công nhân viên. Hiện tại, module `branch` mới chỉ có khung code sơ khai với URL chưa chuẩn RESTful (`/branchs/...`), hàm xóa còn xóa cứng khỏi DB, thiếu tìm kiếm phân trang và chưa có cơ chế kiểm tra bảo mật cách ly dữ liệu giữa các tenant (nguy cơ IDOR). Kế hoạch này chi tiết hóa từng bước chuẩn hóa và hoàn thiện trọn vẹn nghiệp vụ quản lý chi nhánh.

**Affected Modules:**
- `branch` — Module nghiệp vụ chính: Controller, Service, Repository, Mapper, DTO.
- `security` — Xác thực phân quyền: `TenantContext` (cô lập tenant) và Spring Security RBAC (`@PreAuthorize`).
- `common` — Cấu trúc phản hồi chuẩn `ApiResponse<T>`, mã lỗi `AppException` và `ErrorCode`.

**Non-Goals:**
- Chưa triển khai tính năng điều chuyển xe giữa các chi nhánh (`Vehicle Transfer`) - thuộc Phase 2 theo `Multi-Tenant-Multi-Branch.md`.
- Chưa tích hợp tính toán khoảng cách tọa độ GPS thực tế qua Google Maps API.
- Không hỗ trợ xóa cứng (Hard Delete) bản ghi `branches` khỏi Database.

---

# 2. Constraints

**Language & Framework:** Java 17, Spring Boot 3.4.0 (Spring Framework 7 / Spring Boot Starter Web), Spring Data JPA, PostgreSQL 15, MapStruct 1.5.5.Final, Lombok 1.18.36.  
**Architecture:** Layered Architecture chuẩn (Controller $\rightarrow$ Service $\rightarrow$ Repository $\rightarrow$ Database) kết hợp cơ chế Thread-Local Multi-Tenant Isolation (`TenantContext`).

**Rules:**
- Tuyệt đối tuân thủ Multi-Tenant Isolation: Mọi thao tác truy vấn, kiểm tra trùng lặp, cập nhật đều phải filter theo `TenantContext.getTenantId()`.
- Chống IDOR: Không bao giờ tin tưởng `branchId` client gửi lên mà không kiểm tra quyền sở hữu của tenant.
- Không thay đổi các bảng khác ngoài phạm vi module `branch`.
- Tương thích ngược: Đảm bảo các hàm gọi hiện tại trong `AuthServiceImpl` (như `findByTenantId`, `findAllAssignedBranchesByUserIdTenantId`) không bị gián đoạn.

**Performance Budgets:**
- API tìm kiếm phân trang: Thời gian phản hồi < 150ms (p95) với cơ sở dữ liệu có 10.000 bản ghi chi nhánh.
- API dropdown active branches: Phản hồi < 50ms.

---

# 3. Conventions

**Naming:**
- Classes: `PascalCase` — ví dụ: `BranchController`, `BranchService`, `BranchRepository`, `BranchSummaryDTO`.
- Methods/variables: `camelCase` — ví dụ: `createBranch()`, `searchBranches()`, `activeBranchId`.
- RESTful URLs: Danh từ số nhiều, chữ thường, gạch nối — ví dụ: `/api/v1/branches`, `/api/v1/branches/active`.
- Exception Handling: Ném `AppException(ErrorCode, message)` — không ném `RuntimeException` chung chung.
- Return Types: Luôn bọc trong `ResponseEntity<ApiResponse<T>>`.

**Logging:**
- Sử dụng `@Slf4j` của Lombok.
- Ghi log tại điểm bắt đầu các hàm Service quan trọng: `log.info("Creating branch with name={} for tenantId={}", request.getName(), tenantId)`.
- Ghi log cảnh báo khi phát hiện vi phạm quyền tenant: `log.warn("Access denied: branchId={} does not belong to tenantId={}", id, tenantId)`.

**Testing:**
- Framework: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`).
- Naming: `should_<expectedResult>_when_<condition>()`.
- Cấu trúc: Arrange $\rightarrow$ Act $\rightarrow$ Assert (AAA).
- Độ bao phủ: $\ge 80\%$ code logic tại `BranchService`.

---

# 4. Contracts

## Interface: `BranchRepository`
```
existsByTenantIdAndName(tenantId: UUID, name: String): boolean
  - pre: tenantId != null, name != null
  - post: trả về true nếu tên đã tồn tại trong tenant, ngược lại false

existsByTenantIdAndNameAndIdNot(tenantId: UUID, name: String, id: UUID): boolean
  - pre: tenantId != null, name != null, id != null
  - post: trả về true nếu tên trùng với một chi nhánh khác trong tenant

findByIdAndTenantId(id: UUID, tenantId: UUID): Optional<Branch>
  - pre: id != null, tenantId != null
  - post: trả về Optional chứa Branch nếu thuộc đúng tenant, empty nếu không tìm thấy

findByTenantIdAndIsActiveTrueOrderByNameAsc(tenantId: UUID): List<Branch>
  - pre: tenantId != null
  - post: danh sách các chi nhánh đang active sắp xếp A-Z theo tên

searchBranches(tenantId: UUID, keyword: String, isActive: Boolean, pageable: Pageable): Page<Branch>
  - pre: tenantId != null, pageable != null
  - post: Page<Branch> thỏa mãn điều kiện keyword (tìm trên name, address, phone) và trạng thái isActive
```

## Service: `BranchService`
```
createBranch(request: BranchDTORequestToCreate): BranchDTOResponse
  - pre: request hợp lệ, user đã đăng nhập (TenantContext có tenantId)
  - post: tạo mới branch với isActive = true, gắn đúng tenantId, lưu DB và trả về DTO
  - throws: AppException(ErrorCode.BAD_REQUEST) khi tên chi nhánh bị trùng lặp trong tenant

getBranchDetail(id: UUID): BranchDTOResponse
  - pre: id != null
  - post: trả về DTO chi tiết của branch
  - throws: AppException(ErrorCode.RESOURCE_NOT_FOUND) khi không tìm thấy hoặc khác tenant

searchBranches(request: BranchSearchRequest): Page<BranchDTOResponse>
  - pre: request có pageIndex, pageSize hợp lệ
  - post: trả về Page danh sách chi nhánh của tenant hiện tại

getActiveBranchesSummary(): List<BranchSummaryDTO>
  - pre: user đã đăng nhập
  - post: danh sách rút gọn các chi nhánh đang hoạt động của tenant

updateBranch(id: UUID, request: BranchDTORequestToUpdate): BranchDTOResponse
  - pre: id != null, request hợp lệ
  - post: cập nhật các trường không null vào branch, trả về DTO mới
  - throws: AppException(ErrorCode.RESOURCE_NOT_FOUND) khi không tồn tại
  - throws: AppException(ErrorCode.BAD_REQUEST) khi tên mới trùng với chi nhánh khác

updateBranchStatus(id: UUID, isActive: Boolean): BranchDTOResponse
  - pre: id != null, isActive != null
  - post: cập nhật trường isActive của branch, cập nhật updatedAt, trả về DTO
  - throws: AppException(ErrorCode.RESOURCE_NOT_FOUND) khi không tồn tại hoặc khác tenant
```

## Shared DTOs:
```
BranchDTORequestToCreate {
  name:      String       — required, max 255 chars
  phone:     String       — optional, regex Vietnam phone format
  email:     String       — optional, email format
  address:   String       — optional, max 500 chars
  latitude:  BigDecimal   — optional
  longitude: BigDecimal   — optional
}

BranchDTORequestToUpdate {
  name:      String       — optional, max 255 chars
  phone:     String       — optional, regex Vietnam phone format
  email:     String       — optional, email format
  address:   String       — optional, max 500 chars
  latitude:  BigDecimal   — optional
  longitude: BigDecimal   — optional
}

BranchStatusUpdateRequest {
  isActive:  Boolean      — required
}

BranchSearchRequest {
  keyword:   String       — optional
  isActive:  Boolean      — optional (null: all, true: active, false: inactive)
  page:      Integer      — default 0, min 0
  size:      Integer      — default 10, min 1, max 100
}

BranchDTOResponse {
  id:        UUID         — immutable
  name:      String       — non-blank
  phone:     String       — nullable
  email:     String       — nullable
  address:   String       — nullable
  latitude:  BigDecimal   — nullable
  longitude: BigDecimal   — nullable
  isActive:  Boolean      — non-null
  createdAt: Instant      — creation timestamp
  updatedAt: Instant      — last update timestamp
}

BranchSummaryDTO {
  id:        UUID         — branch ID
  name:      String       — branch name
  address:   String       — detailed address
  phone:     String       — phone number
}
```

---

# 5. Target Architecture

**Components:**
- `BranchController`: Tiếp nhận HTTP requests, validate DTO đầu vào, phân quyền `@PreAuthorize`, gọi Service và đóng gói `ApiResponse<T>`.
- `BranchService`: Chứa toàn bộ nghiệp vụ quản lý chi nhánh, tương tác với `TenantContext`, `BranchRepository` và `BranchMapper`.
- `BranchRepository`: Tầng giao tiếp cơ sở dữ liệu với Spring Data JPA & JPQL queries.
- `BranchMapper`: MapStruct mapper chuyển đổi 2 chiều giữa Entity và DTOs.
- `TenantContext`: Quản lý `tenant_id` an toàn theo từng request thread.

**Interaction Flow:**

```
HTTP Client
  │ (JWT Bearer Token)
  ▼
JwtAuthenticationFilter (Security)
  │ → Trích xuất tenant_id nạp vào TenantContext
  ▼
BranchController
  │ → Validate Request (@Valid) & Check Role (@PreAuthorize)
  ▼
BranchService
  │ → Lấy tenantId = TenantContext.getTenantId()
  │ → Kiểm tra tính hợp lệ & chống trùng tên (BranchRepository)
  │ → Chuyển đổi DTO/Entity (BranchMapper)
  │ → Lưu dữ liệu (BranchRepository)
  ▼
PostgreSQL Database (bảng branches)
```

---

# 6. Artifact Registry

| Artifact | Type | Owner Task | Implements / Responsibility |
| :--- | :---: | :---: | :--- |
| `branch/dto/BranchDTORequest.java` | class | TASK-001 | Chứa các Request DTOs (Create, Update, StatusUpdate, Search) |
| `branch/dto/BranchDTOResponse.java` | class | TASK-001 | Chi tiết thông tin chi nhánh trả về cho Client |
| `branch/dto/BranchSummaryDTO.java` | class | TASK-001 | DTO rút gọn cho Dropdown chọn chi nhánh |
| `branch/mapper/BranchMapper.java` | interface | TASK-001 | MapStruct mapping giữa Entity và các DTOs |
| `branch/repository/BranchRepository.java` | interface | TASK-002 | Truy vấn dữ liệu chi nhánh theo Tenant & tìm kiếm phân trang |
| `branch/service/BranchService.java` | class | TASK-003 | Toàn bộ nghiệp vụ CRUD, validate trùng tên, soft delete |
| `branch/controller/BranchController.java` | class | TASK-004 | RESTful APIs, routing chuẩn, kiểm tra quyền Spring Security |
| `branch/service/BranchServiceTest.java` | test | TASK-005 | Unit tests kiểm thử toàn bộ các nhánh logic của BranchService |

---

# 7. Task Graph

| ID | Tên Nhiệm Vụ | Phụ Thuộc | Khối Lượng |
| :--- | :--- | :---: | :---: |
| **TASK-001** | Chuẩn hóa DTOs và MapStruct Mapper cho Branch | — | S |
| **TASK-002** | Bổ sung các phương thức truy vấn trong `BranchRepository` | TASK-001 | S |
| **TASK-003** | Tái cấu trúc và hoàn thiện toàn bộ nghiệp vụ tại `BranchService` | TASK-002 | M |
| **TASK-004** | Chuẩn hóa RESTful Endpoints & Phân quyền tại `BranchController` | TASK-003 | S |
| **TASK-005** | Viết bộ kiểm thử Unit Test cho `BranchService` | TASK-003 | M |

```
TASK-001 (DTOs & Mapper)
    │
    ▼
TASK-002 (Repository Queries)
    │
    ▼
TASK-003 (Service Business Logic)
    ├── TASK-004 (RESTful Controller & Security)
    └── TASK-005 (Unit Tests)
```

---

# 8. Task Specifications

## TASK-001: Chuẩn hóa DTOs và MapStruct Mapper cho Branch

**Description:**
Lược bỏ các trường cũ không còn trong database (`code`), tạo mới `BranchSummaryDTO` cho dropdown, bổ sung validation định dạng SĐT Việt Nam/Email, và cấu hình `BranchMapper` để chuyển đổi mượt mà giữa Entity và các DTOs.

**Input:**
- Đặc tả tại Section 4.

**Output:**
- `BranchDTORequest.java`, `BranchDTOResponse.java`, `BranchSummaryDTO.java`, `BranchMapper.java`.

**Files:**
- `backend/src/main/java/com/carrental/car_rental_backend/branch/dto/BranchDTORequest.java` — **modify**
- `backend/src/main/java/com/carrental/car_rental_backend/branch/dto/BranchDTOResponse.java` — **modify**
- `backend/src/main/java/com/carrental/car_rental_backend/branch/dto/BranchSummaryDTO.java` — **create**
- `backend/src/main/java/com/carrental/car_rental_backend/branch/mapper/BranchMapper.java` — **modify**

**Responsibilities:**
- Định nghĩa `BranchDTORequestToCreate`, `BranchDTORequestToUpdate`, `BranchStatusUpdateRequest`, `BranchSearchRequest`.
- Thêm `createdAt`, `updatedAt`, `latitude`, `longitude` vào `BranchDTOResponse`.
- Tạo `BranchSummaryDTO(id, name, address, phone)`.
- Cấu hình `BranchMapper`: `toResponse`, `toEntity`, `updateBranch`, `toSummaryList`.

**Acceptance Criteria:**
- [ ] DTOs biên dịch thành công, không còn trường thừa `code`.
- [ ] MapStruct sinh file `BranchMapperImpl.java` đầy đủ, mapping chính xác `isActive`, `createdAt`, `updatedAt`.
- [ ] Mapper chuyển đổi danh sách `List<Branch>` sang `List<BranchSummaryDTO>` chuẩn xác.

---

## TASK-002: Bổ sung các phương thức truy vấn trong `BranchRepository`

**Description:**
Khai báo các phương thức Spring Data JPA và câu lệnh JPQL trong `BranchRepository` để phục vụ kiểm tra trùng tên khi update, tìm kiếm phân trang có điều kiện, và lấy danh sách chi nhánh đang active.

**Input:**
- `BranchRepository.java` hiện có từ TASK-001.

**Output:**
- `BranchRepository.java` đã được bổ sung đầy đủ 5 phương thức theo hợp đồng Section 4.

**Files:**
- `backend/src/main/java/com/carrental/car_rental_backend/branch/repository/BranchRepository.java` — **modify**

**Responsibilities:**
- Bổ sung `existsByTenantIdAndNameAndIdNot(tenantId, name, id)`.
- Bổ sung `findByIdAndTenantId(id, tenantId)`.
- Bổ sung `findByTenantIdAndIsActiveTrueOrderByNameAsc(tenantId)`.
- Thêm câu truy vấn JPQL `@Query` cho `searchBranches` hỗ trợ tìm kiếm không phân biệt hoa thường theo `name`, `address`, `phone` kèm lọc theo `isActive`.

**Acceptance Criteria:**
- [ ] Repository biên dịch thành công, không gặp lỗi cú pháp JPQL khi Spring Boot nạp context.
- [ ] Phương thức `existsByTenantIdAndNameAndIdNot` loại trừ chính `id` đang cập nhật khi kiểm tra tên.
- [ ] Các phương thức cũ `findByTenantId` và `findAllAssignedBranchesByUserIdTenantId` vẫn được giữ nguyên vẹn để không làm vỡ `AuthServiceImpl`.

---

## TASK-003: Tái cấu trúc và hoàn thiện toàn bộ nghiệp vụ tại `BranchService`

**Description:**
Viết lại toàn diện `BranchService` với 6 phương thức nghiệp vụ hoàn chỉnh, loại bỏ hoàn toàn hard delete, thay thế logic hardcode, bắt buộc kiểm tra tenant qua `TenantContext.getTenantId()`.

**Input:**
- DTOs và Mapper từ TASK-001, Repository từ TASK-002.

**Output:**
- `BranchService.java` hoàn chỉnh, phục vụ cho Controller và Tests.

**Files:**
- `backend/src/main/java/com/carrental/car_rental_backend/branch/service/BranchService.java` — **modify**

**Responsibilities:**
- `createBranch`: Lấy `tenantId`, kiểm tra trùng tên, map entity, set `tenantId`, gán `isActive = true`, `save()` và trả về `toResponse(savedBranch)`.
- `getBranchDetail`: Tìm theo `findByIdAndTenantId`, ném `AppException(RESOURCE_NOT_FOUND)` nếu không thấy.
- `searchBranches`: Phân trang bằng `PageRequest.of(page, size)`, gọi `branchRepository.searchBranches()`, map sang `Page<BranchDTOResponse>`.
- `getActiveBranchesSummary`: Gọi `findByTenantIdAndIsActiveTrueOrderByNameAsc`, map sang `List<BranchSummaryDTO>`.
- `updateBranch`: Kiểm tra tồn tại qua `findByIdAndTenantId`, kiểm tra trùng tên qua `existsByTenantIdAndNameAndIdNot`, map các trường thay đổi, lưu DB.
- `updateBranchStatus`: Tìm chi nhánh, cập nhật `isActive`, cập nhật `updatedAt = Instant.now()`, lưu DB.

**Acceptance Criteria:**
- [ ] Tạo branch thành công, `isActive` luôn là `true`, `tenantId` luôn khớp với `TenantContext`.
- [ ] Cố tình tạo hoặc đổi tên chi nhánh trùng lặp trong cùng Tenant $\rightarrow$ Ném lỗi `AppException(BAD_REQUEST)`.
- [ ] Truy vấn chi nhánh bằng ID của Tenant khác $\rightarrow$ Ném lỗi `AppException(RESOURCE_NOT_FOUND)`.
- [ ] Tắt trạng thái chi nhánh (`isActive = false`) $\rightarrow$ Database cập nhật thành công, không có lệnh `delete()` nào được gọi.

---

## TASK-004: Chuẩn hóa RESTful Endpoints & Phân quyền tại `BranchController`

**Description:**
Chuẩn hóa toàn bộ routing URL theo chuẩn RESTful (`/api/v1/branches`), hỗ trợ phân trang qua query parameters, và áp dụng phân quyền nghiêm ngặt với `@PreAuthorize`.

**Input:**
- `BranchService.java` từ TASK-003.

**Output:**
- `BranchController.java` hoàn chỉnh.

**Files:**
- `backend/src/main/java/com/carrental/car_rental_backend/branch/controller/BranchController.java` — **modify**

**Responsibilities:**
- Cấu hình base path `@RequestMapping("/api/v1/branches")`.
- `POST /`: `@PreAuthorize("hasRole('TENANT_ADMIN')")`, `@Valid @RequestBody BranchDTORequestToCreate`.
- `GET /`: `@PreAuthorize("hasRole('TENANT_ADMIN')")`, tiếp nhận `keyword`, `isActive`, `page`, `size`.
- `GET /active`: `@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF', 'SALE')")`, trả về danh sách tóm tắt dropdown.
- `GET /{id}`: `@PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF')")`.
- `PUT /{id}`: `@PreAuthorize("hasRole('TENANT_ADMIN')")`, `@Valid @RequestBody BranchDTORequestToUpdate`.
- `PATCH /{id}/status`: `@PreAuthorize("hasRole('TENANT_ADMIN')")`, `@Valid @RequestBody BranchStatusUpdateRequest`.

**Acceptance Criteria:**
- [ ] Toàn bộ 6 API hoạt động thông suốt với HTTP method chuẩn (`POST`, `GET`, `PUT`, `PATCH`).
- [ ] Swagger UI hiển thị đúng tên nhóm controller, schema DTO và các mô tả API.
- [ ] Request không có quyền phù hợp bị chặn với mã `403 Forbidden`.

---

## TASK-005: Viết bộ kiểm thử Unit Test cho `BranchService`

**Description:**
Xây dựng lớp kiểm thử đơn vị `BranchServiceTest` với Mockito để bảo vệ toàn bộ các ca kiểm thử nghiệp vụ, biên và bảo mật của `BranchService`.

**Input:**
- `BranchService.java` từ TASK-003.

**Output:**
- File kiểm thử `BranchServiceTest.java`.

**Files:**
- `backend/src/test/java/com/carrental/car_rental_backend/branch/service/BranchServiceTest.java` — **create**

**Responsibilities:**
- Mock `BranchRepository`, `BranchMapper`, và mock static `TenantContext`.
- Viết các test cases cho:
  - `should_createBranch_successfully_when_validData()`
  - `should_throwException_when_createBranchWithDuplicateName()`
  - `should_returnBranchDetail_when_branchExistsAndBelongsToTenant()`
  - `should_throwException_when_branchNotFoundOrBelongsToAnotherTenant()`
  - `should_updateBranch_successfully_when_validUpdate()`
  - `should_throwException_when_updateBranchWithDuplicateName()`
  - `should_toggleBranchStatus_successfully()`
  - `should_returnActiveBranchesSummary_successfully()`

**Acceptance Criteria:**
- [ ] Toàn bộ các test cases chạy thành công (Pass 100%).
- [ ] Độ bao phủ dòng code logic đạt $\ge 80\%$.

---

# 9. Edge Cases

| # | Tình Huống Biên (Edge Case) | Hành Vi Kỳ Vọng | Xử Lý Tại |
| :---: | :--- | :--- | :---: |
| **MT-1** | User thuộc Tenant A cố tình truyền UUID chi nhánh của Tenant B vào URL `/api/v1/branches/{id}` (Lỗ hổng IDOR) | Hệ thống tìm theo `findByIdAndTenantId`, không tìm thấy $\rightarrow$ Trả về `404 Not Found` (tuyệt đối không để lộ dữ liệu) | TASK-003 |
| **MT-2** | Hai Tenant khác nhau tạo chi nhánh có cùng tên "Chi nhánh Cầu Giấy" | Cho phép tạo thành công (tên chi nhánh chỉ duy nhất trong phạm vi từng Tenant) | TASK-002, TASK-003 |
| **EC-1** | Cập nhật chi nhánh: Người dùng giữ nguyên tên cũ và chỉ sửa số điện thoại | Kiểm tra trùng tên loại trừ chính `id` đang sửa $\rightarrow$ Cho phép lưu thành công, không báo trùng | TASK-002, TASK-003 |
| **EC-2** | Tìm kiếm phân trang với từ khóa có khoảng trắng thừa hoặc lẫn chữ hoa/thường (ví dụ `"  cẦu GiẤy  "`) | Hệ thống tự động `trim()` và chuyển thành chữ thường khi so khớp trong SQL LIKE | TASK-002, TASK-003 |
| **EC-3** | Chi nhánh có trường `phone`, `email`, hoặc tọa độ gửi lên là `null` hoặc chuỗi rỗng | Cho phép lưu vào DB bình thường (các trường này là optional) | TASK-001, TASK-003 |
| **EC-4** | Tắt chi nhánh (`isActive = false`) trong khi chi nhánh đó đang có xe | Cập nhật trạng thái thành công; chi nhánh bị loại khỏi dropdown tạo mới nhưng các booking lịch sử vẫn giữ nguyên vẹn | TASK-003 |

---

# 10. Risks

| # | Rủi Ro Kỹ Thuật | Tác Động | Khả Năng | Giải Pháp Giảm Thiểu |
| :---: | :--- | :---: | :---: | :--- |
| 1 | Xung đột `TenantContext` giữa các luồng tái sử dụng của Tomcat | Cao | Thấp | `JwtAuthenticationFilter` đã đảm bảo khối `finally { TenantContext.clear(); }` dọn dẹp sạch sẽ sau mỗi request |
| 2 | Lỗi xung đột kiểu dữ liệu `isActive` giữa MapStruct và Entity | Trung bình | Thấp | Đã chuẩn hóa toàn diện sang kiểu đối tượng `Boolean` (chữ B hoa) có getter `getIsActive()` đồng bộ |
| 3 | Đổi URL API từ `/branchs/...` sang `/branches/...` có thể làm ảnh hưởng tới Frontend (nếu có) | Trung bình | Trung bình | Cập nhật tài liệu Swagger rõ ràng, thông báo chuyển đổi URL sang số nhiều tiếng Anh chuẩn |

---

# 11. Verification Plan

**Automated Tests:**
- Chạy toàn bộ Unit Tests của `BranchService`:
  ```bash
  mvn test -Dtest=BranchServiceTest
  ```
- Kiểm tra toàn bộ dự án biên dịch sạch sẽ không cảnh báo:
  ```bash
  mvn clean test-compile
  ```

**Manual / Swagger Verification:**
1. Đăng nhập bằng tài khoản `TENANT_ADMIN` $\rightarrow$ Lấy Access Token $\rightarrow$ Bấm **Authorize 🔓** trên Swagger.
2. Gọi `POST /api/v1/branches`: Tạo mới "Chi nhánh Cầu Giấy" $\rightarrow$ Kiểm tra `isActive = true`, `tenantId` khớp token.
3. Gọi lại `POST /api/v1/branches` với cùng tên "Chi nhánh Cầu Giấy" $\rightarrow$ Mong đợi lỗi `400 Bad Request` ("Tên chi nhánh đã tồn tại").
4. Gọi `GET /api/v1/branches`: Kiểm tra kết quả phân trang trả về đúng chi nhánh vừa tạo.
5. Gọi `GET /api/v1/branches/active`: Xác nhận chi nhánh xuất hiện trong danh sách dropdown.
6. Gọi `PATCH /api/v1/branches/{id}/status` với `isActive = false`:
   - Gọi lại `GET /api/v1/branches/active` $\rightarrow$ Xác nhận chi nhánh đã bị ẩn khỏi dropdown.
   - Gọi `GET /api/v1/branches?isActive=false` $\rightarrow$ Xác nhận chi nhánh xuất hiện trong danh sách tạm ngưng.

