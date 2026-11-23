# Kế hoạch Triển khai: Module Quản lý Nhân sự & Người dùng (USER MANAGEMENT)

Spec Source: [2026-11-20-user-management-implementation-guide.md](file:///d:/project ve xe/car-rental-saas/docs/plans/user-management/2026-11-20-user-management-implementation-guide.md)  
Owner: Backend Team / GiangDT  
Last Updated: 2026-11-20  
Status: `Approved`  

---

# 1. Context

**Problem:**  
Hệ thống Car Rental SaaS cần cung cấp bộ tính năng hoàn chỉnh cho phép Chủ nhà xe (`TENANT_ADMIN`) quản lý nhân sự trong nội bộ nhà xe: tuyển dụng nhân viên (`STAFF`, `SALE`), phân công trực tại các bãi xe (`user_branches`), phân trang tìm kiếm, cập nhật chức vụ/chi nhánh, và khóa tài khoản khi nhân viên nghỉ việc. Đồng thời, hệ thống phải bảo đảm an toàn đa khách hàng (Multi-Tenant Isolation) tuyệt đối, chống lỗi IDOR và xử lý linh hoạt trường hợp 1 người dùng đã có tài khoản toàn cầu gia nhập nhà xe mới.

**Affected Modules:**  
- `account` (`entity`, `dto`, `repository`, `service`, `controller`) — Toàn bộ module chịu trách nhiệm về nhân sự và người dùng.  
- `branch` (`BranchRepository`) — Kiểm tra tính hợp lệ của chi nhánh thuộc về Tenant khi phân công nhân viên.  
- `security` (`TenantContext`, `@PreAuthorize`, `SecurityContextHolder`) — Cung cấp định danh tenant và kiểm tra quyền nguyên tử `account:manage`.  

**Non-Goals:**  
- Chưa triển khai giao diện tự đăng ký tài khoản công khai từ phía khách hàng vãng lai (Public Registration).  
- Chưa tích hợp dịch vụ gửi Email thông báo mật khẩu qua SMTP (sẽ làm ở Phase Notification sau khi xong Core).  
- Chưa làm tính năng phân quyền theo ca làm việc chi tiết theo giờ (Shift scheduling).  

---

# 2. Constraints

**Language & Stack:**  
- Java 21, Spring Boot 3, Spring Data JPA / Hibernate, PostgreSQL 15+, Spring Security (JWT HttpOnly).  
- Object Mapping: Sử dụng Lombok `@Builder` thuần túy (không dùng MapStruct theo quy chuẩn đã thống nhất).  

**Rules:**  
- Tuyệt đối không để lộ trường nhạy cảm (`password_hash`) trong DTO Response.  
- Mọi câu truy vấn đọc/ghi đều bắt buộc ràng buộc `tenant_id` lấy từ `TenantContext` (chống IDOR 100%).  
- Không sửa đổi cấu trúc bảng CSDL (schema đã được cố định qua Flyway `V1__init_schema.sql`).  

**Performance Budgets:**  
- API danh sách nhân viên phân trang (`GET /api/v1/users`): Latency < 100ms với dataset 10.000 users.  
- Không bị N+1 Query khi lấy danh sách Role và Chi nhánh của User (dùng JOIN FETCH hoặc query gom nhóm).  

---

# 3. Conventions

**Naming & Packaging:**  
- Package gốc: `com.carrental.car_rental_backend.account`  
- DTO Request: `*RequestDTO` (ví dụ: `CreateUserRequestDTO`, `UpdateUserRequestDTO`)  
- DTO Response: `*ResponseDTO` (ví dụ: `UserDetailResponseDTO`, `UserSummaryResponseDTO`)  
- Service: Tách Interface `UserService` và Implementation `UserServiceImpl`  

**Error Handling:**  
- Sử dụng `AppException(ErrorCode, customMessage)` nhất quán với toàn bộ dự án.  
- Không bao giờ trả về `null` từ Service; trả về `Optional<T>` ở Repository hoặc ném `AppException` nếu không tìm thấy.  

**Logging & Transaction:**  
- Gắn `@Slf4j` cho Service và Controller, ghi log có cấu trúc: `log.info("action={} tenantId={} userId={}", ...)`  
- `@Transactional(readOnly = true)` ở mức class hoặc hàm query; `@Transactional(rollbackFor = Exception.class)` cho các hàm thêm/sửa/xóa.  

---

# 4. Contracts

## Interface: `UserService`

```
Page<UserSummaryResponseDTO> getUsers(keyword: String, roleId: UUID, isActive: Boolean, pageable: Pageable)
  - pre: TenantContext.getTenantId() != null
  - post: Trả về danh sách user phân trang thuộc đúng tenant hiện tại, kèm đếm số chi nhánh phụ trách.
  - throws: AppException(UNAUTHORIZED) nếu tenantId null.

UserDetailResponseDTO getUserById(userId: UUID)
  - pre: userId != null, TenantContext.getTenantId() != null
  - post: Trả về thông tin chi tiết user + Role + danh sách chi nhánh nếu user thuộc tenant.
  - throws: AppException(RESOURCE_NOT_FOUND) nếu user không tồn tại hoặc không thuộc tenant (Chống IDOR).

UserDetailResponseDTO createUser(request: CreateUserRequestDTO)
  - pre: request valid (@Valid), roleId thuộc tenant hiện tại, branchIds thuộc tenant hiện tại.
  - post: Tạo user mới (hoặc tái sử dụng user toàn cầu nếu email đã có), gán user_tenants và user_branches.
  - throws: AppException(BAD_REQUEST) nếu role/branch không hợp lệ hoặc user đã ở trong tenant này.

UserDetailResponseDTO updateUser(userId: UUID, request: UpdateUserRequestDTO)
  - pre: userId thuộc tenant hiện tại, roleId và branchIds hợp lệ.
  - post: Cập nhật thông tin cơ bản, đổi role trong tenant, áp dụng Replace Pattern cho user_branches.
  - throws: AppException(RESOURCE_NOT_FOUND) nếu userId không thuộc tenant.

void changeUserStatus(userId: UUID, isActive: boolean)
  - pre: userId thuộc tenant hiện tại, userId != currentAdminId (không tự khóa chính mình).
  - post: Cập nhật isActive, nếu khóa thì set currentSessionId = null để vô hiệu hóa token ngay lập tức.
  - throws: AppException(BAD_REQUEST) nếu tự khóa chính mình.

void resetPassword(userId: UUID, newPassword: String)
  - pre: userId thuộc tenant hiện tại, newPassword >= 6 ký tự.
  - post: Băm BCrypt mật khẩu mới, lưu vào users, set currentSessionId = null để bắt đăng nhập lại.
```

---

# 5. Target Architecture

**Components & Data Flow:**

```
[ Client / Postman ]
         │ (HTTP Request + Bearer JWT có TenantContext)
         ▼
[ UserController ] ── (@PreAuthorize("hasAuthority('account:manage')") & @Valid)
         │
         ▼
[ UserServiceImpl ] ── (Đọc TenantContext.getTenantId())
         │
         ├──► [ RoleRepository & BranchRepository ] (Validate Role và Chi nhánh thuộc Tenant)
         ├──► [ UserRepository ] (Query User / Kiểm tra Email toàn cầu)
         ├──► [ UserTenantRepository ] (Chốt chặn IDOR: Kiểm tra (userId, tenantId))
         └──► [ UserBranchRepository ] (Xóa cũ & Lưu mới phân công chi nhánh - Replace Pattern)
         │
         ▼
[ PostgreSQL DB ] (users, user_tenants, user_branches)
```

**Key Decisions:**  
1. **Lombok `@Builder` thay vì MapStruct:** Toàn quyền kiểm soát luồng gán dữ liệu giữa Entity và DTO, loại bỏ các file interface mapper dư thừa.  
2. **Replace Pattern cho Chi nhánh:** Xóa toàn bộ bản ghi cũ trong `user_branches` bằng `deleteByTenantIdAndUserId`, sau đó nạp danh sách mới.  
3. **Chống IDOR 2 Lớp:** Kiểm tra cấp Java Service qua `userTenantRepository.findByUserIdAndTenantId()` và Database Composite FK.  

---

# 6. Artifact Registry

| Artifact | Type | Owner Task | Mô tả & Trách nhiệm |
| :--- | :--- | :--- | :--- |
| `account/entity/User.java` | Entity | TASK-001 | Rà soát cấu trúc bảng `users`, `@Builder`, `@Table(name = "users")` |
| `account/entity/UserTenant.java` | Entity | TASK-001 | Rà soát liên kết `(tenant_id, user_id)`, `role_id`, `joined_at` |
| `account/entity/UserBranch.java` | Entity | TASK-001 | Rà soát liên kết `(tenant_id, branch_id, user_id)`, enum `UserBranchStatus` |
| `account/dto/request/CreateUserRequestDTO.java` | DTO | TASK-002 | Phiếu tạo mới nhân sự kèm validation |
| `account/dto/request/UpdateUserRequestDTO.java` | DTO | TASK-002 | Phiếu cập nhật thông tin nhân sự |
| `account/dto/response/UserDetailResponseDTO.java` | DTO | TASK-002 | Trả về thông tin chi tiết nhân viên |
| `account/dto/response/UserSummaryResponseDTO.java` | DTO | TASK-002 | Trả về thông tin tóm tắt khi phân trang |
| `account/dto/response/RoleSummaryDTO.java` | DTO | TASK-002 | Trả về thông tin gọn của Role (`id`, `name`, `code`) |
| `account/repository/UserRepository.java` | Repo | TASK-003 | Query phân trang join `user_tenants`, `findByEmailIgnoreCase` |
| `account/repository/UserTenantRepository.java` | Repo | TASK-003 | Truy vấn kép `(userId, tenantId)`, kiểm tra tồn tại |
| `account/repository/UserBranchRepository.java` | Repo | TASK-003 | `findAllByTenantIdAndUserId`, `deleteByTenantIdAndUserId` |
| `account/service/UserService.java` | Service | TASK-004 | Interface định nghĩa hợp đồng nghiệp vụ nhân sự |
| `account/service/impl/UserServiceImpl.java` | Service | TASK-004 | Triển khai logic nghiệp vụ, bảo mật, Replace Pattern, `@Builder` |
| `account/controller/UserController.java` | Controller | TASK-005 | REST Controller công khai các endpoints `/api/v1/users` |

---

# 7. Task Graph & Trình tự Triển khai (Theo đúng yêu cầu: Entity -> DTO -> Repo -> Service -> Controller)

```
┌──────────────┐
│   TASK-001   │  1. Rà soát & Tối ưu Entity & IdClass (account/entity)
└──────┬───────┘
       ▼
┌──────────────┐
│   TASK-002   │  2. Xây dựng DTO Hợp đồng Giao tiếp (account/dto)
└──────┬───────┘
       ▼
┌──────────────┐
│   TASK-003   │  3. Bổ sung Repository Queries & Scoping (account/repository)
└──────┬───────┘
       ▼
┌──────────────┐
│   TASK-004   │  4. Triển khai Service Layer Interface & Impl (account/service)
└──────┬───────┘
       ▼
┌──────────────┐
│   TASK-005   │  5. Xây dựng REST API Controller & Phân quyền (account/controller)
└──────┬───────┘
       ▼
┌──────────────┐
│   TASK-006   │  6. Biên dịch & Nghiệm thu API Test Suite (Verify & Test)
└──────────────┘
```

---

# 8. Task Specifications

## TASK-001: Rà soát & Tối ưu hóa JPA Entity & IdClass
- **Mục tiêu:** Đảm bảo 3 entity cốt lõi (`User`, `UserTenant`, `UserBranch`) và 2 IdClass (`UserTenantId`, `UserBranchId`) hoàn toàn khớp với schema DB, có đủ `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Getter`, `@Setter`.
- **Files:**
  - `account/entity/User.java` — Kiểm tra các cột `id`, `email`, `password_hash`, `fullname`, `phone`, `is_active`, `current_session_id`.
  - `account/entity/UserTenant.java` — Kiểm tra `@IdClass(UserTenantId.class)`, các cột `tenant_id`, `user_id`, `role_id`, `joined_at`.
  - `account/entity/UserBranch.java` — Kiểm tra `@IdClass(UserBranchId.class)`, enum `UserBranchStatus`, các cột `tenant_id`, `branch_id`, `user_id`.
- **Acceptance Criteria:**
  - [ ] Cả 3 entity đều có `@Builder` và constructor đầy đủ.
  - [ ] `UserTenantId` và `UserBranchId` có `equals()` và `hashCode()` chuẩn chỉnh.
  - [ ] Chạy `mvn compile` không báo lỗi JPA mapping.

---

## TASK-002: Xây dựng Bộ DTO Request & Response
- **Mục tiêu:** Tạo các DTO chuẩn hóa dữ liệu đầu vào và đầu ra, trang bị validation `@NotBlank`, `@Email`, `@Pattern`, `@NotNull`.
- **Files:**
  - `account/dto/request/CreateUserRequestDTO.java` — **create**
  - `account/dto/request/UpdateUserRequestDTO.java` — **create**
  - `account/dto/response/UserDetailResponseDTO.java` — **create**
  - `account/dto/response/UserSummaryResponseDTO.java` — **create**
  - `account/dto/response/RoleSummaryDTO.java` — **create**
- **Acceptance Criteria:**
  - [ ] Request DTO có đầy đủ message validation tiếng Việt rõ ràng.
  - [ ] Response DTO tuyệt đối không chứa trường `passwordHash`.
  - [ ] Các DTO đều có `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`.

---

## TASK-003: Bổ sung Repository Queries Phân trang & Multi-Tenant
- **Mục tiêu:** Viết các phương thức truy vấn cần thiết trên `UserRepository`, `UserTenantRepository`, `UserBranchRepository`.
- **Files:**
  - `account/repository/UserRepository.java` — Thêm `findByEmailIgnoreCase()`, `@Query` phân trang có JOIN `UserTenant` và lọc theo `tenantId`, `keyword`, `roleId`, `isActive`.
  - `account/repository/UserTenantRepository.java` — Thêm `existsByUserIdAndTenantId()`, `deleteByUserIdAndTenantId()`.
  - `account/repository/UserBranchRepository.java` — Thêm `findAllByTenantIdAndUserId()`, `deleteByTenantIdAndUserId()`, `existsByTenantIdAndBranchId()`.
- **Acceptance Criteria:**
  - [ ] Câu query `@Query` trong `UserRepository` xử lý mượt mà tham số `keyword == null` mà không văng lỗi SQL.
  - [ ] Phương thức `deleteByTenantIdAndUserId()` sẵn sàng phục vụ Replace Pattern.

---

## TASK-004: Triển khai Service Layer (Interface & Impl)
- **Mục tiêu:** Viết toàn bộ logic nghiệp vụ, mã hóa mật khẩu, kiểm tra quyền, chống IDOR và Replace Pattern.
- **Files:**
  - `account/service/UserService.java` — **create**: Khai báo 6 method signatures.
  - `account/service/impl/UserServiceImpl.java` — **create**: Cài đặt chi tiết 6 thuật toán.
- **Logic Trọng tâm:**
  - `getUsers`: Phân trang, map sang `UserSummaryResponseDTO` bằng `@Builder`.
  - `getUserById`: Chống IDOR bằng `userTenantRepository.findByUserIdAndTenantId()`.
  - `createUser`: Kiểm tra email tồn tại hay mới, băm BCrypt, gán tenant, validate chi nhánh thuộc tenant.
  - `updateUser`: Chống IDOR, cập nhật hồ sơ, Replace Pattern cho `user_branches`.
  - `changeUserStatus`: Ngăn tự khóa chính mình, hủy `currentSessionId`.
  - `resetPassword`: Băm mật khẩu mới, cập nhật DB.
- **Acceptance Criteria:**
  - [ ] Class gắn `@Service`, `@RequiredArgsConstructor`, `@Slf4j`.
  - [ ] Các hàm ghi DB gắn `@Transactional(rollbackFor = Exception.class)`.
  - [ ] Xử lý đúng ngoại lệ `AppException` với các mã `RESOURCE_NOT_FOUND`, `BAD_REQUEST`.

---

## TASK-005: Xây dựng REST API Controller & Phân quyền
- **Mục tiêu:** Cung cấp các endpoint RESTful, bảo vệ bằng `@PreAuthorize("hasAuthority('account:manage')")`.
- **Files:**
  - `account/controller/UserController.java` — **create**
- **Endpoints:**
  - `GET /api/v1/users` (Phân trang `Pageable`, filter)
  - `GET /api/v1/users/{id}` (Chi tiết)
  - `POST /api/v1/users` (Tạo mới nhân viên)
  - `PUT /api/v1/users/{id}` (Cập nhật)
  - `PATCH /api/v1/users/{id}/status` (Khóa/Mở khóa)
  - `POST /api/v1/users/{id}/reset-password` (Đặt lại mật khẩu)
- **Acceptance Criteria:**
  - [ ] Toàn bộ input body gắn `@Valid`.
  - [ ] Trả về bọc trong `ApiResponse<T>` chuẩn: `ApiResponse.success(data)`.
  - [ ] Trả đúng HTTP Status Code: `201 CREATED` khi tạo mới, `200 OK` khi truy vấn/cập nhật.

---

## TASK-006: Biên dịch & Kiểm thử Nghiệm thu
- **Mục tiêu:** Kiểm tra toàn bộ mã nguồn biên dịch sạch, không lỗi đỏ, chạy thử nghiệm kịch bản phân quyền.
- **Acceptance Criteria:**
  - [ ] `mvn compile` ra kết quả `BUILD SUCCESS`.
  - [ ] Thử request thêm nhân viên với `branch_id` của nhà xe khác $\rightarrow$ Bị chặn ngay lập tức.
  - [ ] Thử sửa thông tin nhân viên với danh sách chi nhánh mới $\rightarrow$ Kiểm tra DB bản ghi cũ bị xóa và bản ghi mới được thêm đúng chuẩn Replace Pattern.

---

# 9. Edge Cases

| # | Tình huống (Scenario) | Hành vi mong muốn (Expected Behavior) | Xử lý tại |
|---|---|---|---|
| 1 | Admin gửi `roleId` không thuộc về `tenant_id` hiện tại | Ném `AppException(BAD_REQUEST, "Nhóm quyền không hợp lệ")` | TASK-004 |
| 2 | Admin gán `branchIds` có chứa ID chi nhánh của nhà xe khác (IDOR) | Ném `AppException(BAD_REQUEST, "Chi nhánh không thuộc nhà xe của bạn")` | TASK-004 |
| 3 | Thêm nhân viên với Email đã tồn tại trong chính nhà xe này | Ném `AppException(BAD_REQUEST, "Nhân viên đã tồn tại trong nhà xe")` | TASK-004 |
| 4 | Thêm nhân viên với Email đã có trên hệ thống nhưng chưa ở nhà xe này | Không tạo mới `User`, tái sử dụng user cũ, tạo mới `user_tenants` | TASK-004 |
| 5 | Admin Nhà xe A cố tình gọi `GET /api/v1/users/{id}` của Nhà xe B | Ném `AppException(RESOURCE_NOT_FOUND)` (Che giấu sự tồn tại của dữ liệu) | TASK-004 |
| 6 | Admin vô tình bấm khóa tài khoản của chính mình (`id == currentAdminId`) | Chặn lại và ném `AppException(BAD_REQUEST, "Không thể tự khóa chính mình")` | TASK-004 |
| 7 | Cập nhật nhân viên nhưng danh sách chi nhánh rỗng (`branchIds = []`) | Xóa sạch phân công cũ trong `user_branches` mà không ném lỗi | TASK-004 |

---

# 10. Risks & Mitigation

| # | Rủi ro kỹ thuật | Mức độ | Giải pháp phòng ngừa (Mitigation) |
|---|---|---|---|
| 1 | **Xung đột Hibernate Write-Behind trong Replace Pattern**: Lệnh `saveAll` mới chạy trước khi lệnh `delete` cũ được flush xuống DB gây lỗi Unique/PK Constraint | Trung bình | Gọi `userBranchRepository.flush()` ngay sau lệnh `deleteByTenantIdAndUserId` trước khi `saveAll`. |
| 2 | **Lỗi N+1 Query khi lấy danh sách User**: Khi duyệt danh sách user, mỗi dòng lại bắn 1 câu query lấy Role và Chi nhánh | Cao | Dùng query gom nhóm hoặc lấy trực tiếp danh sách IDs rồi map trên bộ nhớ RAM. |

