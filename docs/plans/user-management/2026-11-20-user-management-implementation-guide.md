# 📘 CẨM NANG HƯỚNG DẪN TỰ CODE TỪNG BƯỚC: MODULE QUẢN LÝ NHÂN SỰ & NGƯỜI DÙNG (USER MANAGEMENT)

> **Dự án:** Car Rental SaaS (Hệ thống Quản lý Chuỗi Cho Thuê Xe Tự Lái)  
> **Module:** `account` / `users` (Quản lý Tài khoản, Phân quyền Thành viên Nhà xe & Gán Chi nhánh)  
> **Tài liệu tham khảo:** [Database-Schema.md](file:///d:/project ve xe/car-rental-saas/docs/overviews/Database-Schema.md), [Multi-Tenant-Multi-Branch.md](file:///d:/project ve xe/car-rental-saas/docs/overviews/Multi-Tenant-Multi-Branch.md)  
> **Phương pháp tiếp cận:** Tech Lead / Socratic Mentor — Giải thích bản chất Java Core, CSDL và bảo mật Multi-Tenant + Hướng dẫn luồng logic, tên Class/DTO, tham số và thuật toán để bạn tự tay gõ 100% code vào IDE.

---

## 🏛️ BỨC TRANH TOÀN CẢNH (HỆ THỐNG PHÒNG NHÂN SỰ CHUỖI NHÀ XE)

Để hiểu sâu kiến trúc của module này, hãy tưởng tượng hệ thống phần mềm của chúng ta như **Bộ phận Tổ chức Cán bộ của một Tập đoàn Xe**:

```
[ Khách hàng / Ứng viên ] ── (Nộp hồ sơ) ──► [ users: Sổ Hộ Tịch Toàn Cầu ]
                                                   │
         ┌─────────────────────────────────────────┴─────────────────────────────────────────┐
         ▼                                                                                   ▼
[ Nhà xe RentCar Hà Nội ]                                                           [ Nhà xe Xe Điện SG ]
         │ (user_tenants: Quyết định tiếp nhận)                                              │
         ▼                                                                                   ▼
[ Gán Chức vụ: STAFF / SALE ]                                                       [ Gán Chức vụ: ADMIN ]
         │ (user_branches: Phân công địa bàn công tác)
         ▼
[ Trực tại: Bãi Cầu Giấy & Bãi Đống Đa ]
```

1. **`users` (Sổ hộ tịch công dân):** Là nơi lưu trữ thông tin con người thực tế (Email, Họ tên, SĐT, Mật khẩu mã hóa BCrypt). Một người chỉ có duy nhất 1 trang sổ hộ tịch trên toàn cầu.
2. **`user_tenants` (Hợp đồng tuyển dụng & Quyết định bổ nhiệm):** Người đó gia nhập vào Nhà xe nào và giữ Chức danh (`role_id`: `TENANT_ADMIN`, `STAFF`, hoặc `SALE`) gì tại nhà xe đó.
3. **`user_branches` (Lệnh điều động công tác):** Nếu là nhân viên bãi (`STAFF`), họ được phân công làm việc tại những bãi xe / chi nhánh nào (`branch_id`).
4. **Chiến lược Phân quyền:**
   - **Chủ nhà xe (`TENANT_ADMIN`):** Toàn quyền xem, thêm, sửa, phân công bãi đỗ hoặc cho thôi việc nhân viên trong phạm vi nhà xe mình.
   - **Bảo mật Multi-Tenant (Chống IDOR):** Chủ nhà xe A tuyệt đối không được nhìn thấy, sửa đổi hoặc phân công nhân viên của Nhà xe B.

---

## 🗺️ MỤC LỤC LỘ TRÌNH TRIỂN KHAI

- [Giai đoạn 1: Chuẩn hóa DTO Hợp đồng Giao tiếp (account/dto/)](#giai-đoạn-1-chuẩn-hóa-dto-hợp-đồng-giao-tiếp-accountdto)
- [Giai đoạn 2: Bổ sung Repository Truy vấn Nâng cao (account/repository/)](#giai-đoạn-2-bổ-sung-repository-truy-vấn-nâng-cao-accountrepository)
- [Giai đoạn 3: Triển khai Logic Nghiệp vụ Service (account/service/)](#giai-đoạn-3-triển-khai-logic-nghiệp-vụ-service-accountservice)
- [Giai đoạn 4: Xây dựng REST API Controller & Phân quyền (account/controller/)](#giai-đoạn-4-xây-dựng-rest-api-controller--phân-quyền-accountcontroller)
- [Bản chất Kỹ thuật Deep Dive (Java Core & CSDL)](#bản-chất-kỹ-thuật-deep-dive-java-core--csdl)
- [Checklist Nghiệm thu & Kiểm thử](#checklist-nghiệm-thu--kiểm-thử)

---

## 📦 GIAI ĐOẠN 1: CHUẨN HÓA DTO HỢP ĐỒNG GIAO TIẾP (`account/dto/`)

> [!NOTE]
> **Quy tắc DTO:** Tuyệt đối không trả thực thể JPA (`User`, `UserTenant`, `UserBranch`) trực tiếp ra Controller để tránh làm lộ mật khẩu (`password_hash`), rò rỉ cấu trúc DB hoặc văng ngoại lệ `LazyInitializationException` khi serialize JSON.

---

### 1. File `CreateUserRequestDTO.java` (Phiếu tuyển dụng nhân sự mới)
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/account/dto/request/CreateUserRequestDTO.java`
- **Mục đích:** Nhận dữ liệu khi Admin nhà xe bấm "Thêm nhân viên mới".
- **Lombok Annotations:** `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- **Các thuộc tính & Validation:**
  - `email` (`String`): Email đăng nhập. Gắn `@NotBlank(message = "Email không được để trống")`, `@Email(message = "Email không đúng định dạng")`, `@Size(max = 255)`.
  - `password` (`String`): Mật khẩu khởi tạo ban đầu. Gắn `@NotBlank(message = "Mật khẩu không được để trống")`, `@Size(min = 6, max = 50, message = "Mật khẩu phải từ 6 đến 50 ký tự")`.
  - `fullName` (`String`): Họ và tên nhân viên. Gắn `@NotBlank(message = "Họ tên không được để trống")`, `@Size(max = 255)`.
  - `phone` (`String`): Số điện thoại liên hệ. Gắn `@Pattern(regexp = "^(0|\\+84)[0-9]{9}$", message = "Số điện thoại không hợp lệ")`.
  - `roleId` (`UUID`): Mã nhóm quyền muốn gán (`STAFF`, `SALE`...). Gắn `@NotNull(message = "Vai trò không được để trống")`.
  - `branchIds` (`List<UUID>`): Danh sách các chi nhánh phân công cho nhân viên trực (tùy chọn, áp dụng cho role `STAFF`).
  - `avatarUrl` (`String`): URL ảnh đại diện (tùy chọn).

---

### 2. File `UpdateUserRequestDTO.java` (Phiếu điều chỉnh thông tin nhân sự)
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/account/dto/request/UpdateUserRequestDTO.java`
- **Mục đích:** Nhận dữ liệu khi Admin sửa hồ sơ, chức vụ hoặc phân công lại bãi trực của nhân viên.
- **Lombok Annotations:** `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- **Các thuộc tính & Validation:**
  - `fullName` (`String`): Gắn `@NotBlank(message = "Họ tên không được để trống")`.
  - `phone` (`String`): Gắn `@Pattern(regexp = "^(0|\\+84)[0-9]{9}$", message = "Số điện thoại không hợp lệ")`.
  - `avatarUrl` (`String`): Tùy chọn.
  - `roleId` (`UUID`): Gắn `@NotNull(message = "Vai trò không được để trống")`.
  - `branchIds` (`List<UUID>`): Danh sách chi nhánh mới (áp dụng **Replace Pattern** ở Service).
  - `isActive` (`Boolean`): Cờ kích hoạt / khóa tài khoản (`@NotNull`).

---

### 3. File `UserDetailResponseDTO.java` (Hồ sơ nhân sự chi tiết)
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/account/dto/response/UserDetailResponseDTO.java`
- **Mục đích:** Trả về đầy đủ thông tin khi xem chi tiết 1 nhân viên (`GET /api/v1/users/{id}`).
- **Lombok Annotations:** `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- **Các thuộc tính:**
  - `id` (`UUID`): Mã định danh người dùng.
  - `email` (`String`): Email đăng nhập.
  - `fullName` (`String`): Họ tên.
  - `phone` (`String`): Số điện thoại.
  - `avatarUrl` (`String`): Ảnh đại diện.
  - `isActive` (`Boolean`): Trạng thái tài khoản.
  - `role` (`RoleSummaryDTO`): Thông tin nhóm quyền hiện tại (`id`, `name`, `code`).
  - `assignedBranches` (`List<BranchSummaryDTO>`): Danh sách chi nhánh nhân viên được phân công làm việc.
  - `joinedAt` (`Instant`): Ngày gia nhập nhà xe.
  - `lastLoginAt` (`Instant`): Lần đăng nhập gần nhất.
  - `createdAt` (`Instant`): Ngày tạo hồ sơ.

---

### 4. File `UserSummaryResponseDTO.java` (Dòng tóm tắt trên bảng danh sách)
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/account/dto/response/UserSummaryResponseDTO.java`
- **Mục đích:** Trả về danh sách phân trang gọn nhẹ trên bảng hiển thị (`GET /api/v1/users`).
- **Thuộc tính:** `id`, `email`, `fullName`, `phone`, `roleName`, `roleCode`, `isActive`, `assignedBranchCount`, `lastLoginAt`, `joinedAt`.

---

## 🗄️ GIAI ĐOẠN 2: BỔ SUNG REPOSITORY TRUY VẤN NÂNG CAO (`account/repository/`)

Module cần thực hiện truy vấn đa bảng giữa `users`, `user_tenants`, `user_branches` và `branches`.

---

### 1. Cập nhật `UserRepository.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/account/repository/UserRepository.java`
- **Các phương thức cần bổ sung:**
  - `findByEmailIgnoreCase(String email)`: Trả về `Optional<User>` — Tìm kiếm không phân biệt hoa thường khi đăng ký hoặc kiểm tra trùng lặp email.
  - Truy vấn phân trang nhân viên theo Nhà xe:
    - Viết câu query `@Query` kết hợp `Pageable` để lấy toàn bộ User thuộc về một `tenant_id`:
      ```
      SELECT u FROM User u 
      JOIN UserTenant ut ON u.id = ut.userId 
      WHERE ut.tenantId = :tenantId
      AND (:keyword IS NULL OR LOWER(u.fullname) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR u.phone LIKE CONCAT('%', :keyword, '%'))
      AND (:roleId IS NULL OR ut.roleId = :roleId)
      AND (:isActive IS NULL OR u.isActive = :isActive)
      ```
    - Trả về: `Page<User>`.
  - **Từ khóa Google:** `"Spring Data JPA @Query join pagination Pageable"`, `"Spring Data JPA search filter null check"`.

---

### 2. Cập nhật `UserTenantRepository.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/account/repository/UserTenantRepository.java`
- **Phương thức cần bổ sung:**
  - `findByUserIdAndTenantId(UUID userId, UUID tenantId)`: Trả về `Optional<UserTenant>` (Đã có — chốt chặn IDOR quan trọng nhất!).
  - `existsByUserIdAndTenantId(UUID userId, UUID tenantId)`: Kiểm tra nhanh người dùng đã là thành viên của nhà xe chưa.
  - `deleteByUserIdAndTenantId(UUID userId, UUID tenantId)`: Xóa tư cách thành viên khỏi nhà xe.

---

### 3. Cập nhật `UserBranchRepository.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/account/repository/UserBranchRepository.java`
- **Phương thức cần bổ sung:**
  - `findAllByTenantIdAndUserId(UUID tenantId, UUID userId)`: Trả về `List<UserBranch>` — Lấy toàn bộ chi nhánh mà nhân viên đang được gán.
  - `deleteByTenantIdAndUserId(UUID tenantId, UUID userId)`: **Vũ khí cốt lõi của Replace Pattern** — Xóa sạch toàn bộ phân công chi nhánh cũ của nhân viên trong nhà xe để chuẩn bị lưu danh sách mới.
  - `existsByTenantIdAndBranchId(UUID tenantId, UUID branchId)`: Kiểm tra chi nhánh còn nhân viên trực không trước khi xóa chi nhánh.
  - **Từ khóa Google:** `"Spring Data JPA derived delete query @Modifying @Transactional"`.

---

## 🧠 GIAI ĐOẠN 3: TRIỂN KHAI LOGIC NGHIỆP VỤ SERVICE (`account/service/`)

Tách bạch rõ ràng giữa Interface và Implementation theo chuẩn Clean Code.

---

### 1. Interface `UserService.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/account/service/UserService.java`
- **Danh sách chữ ký hàm (Method Signatures):**
  - `Page<UserSummaryResponseDTO> getUsers(String keyword, UUID roleId, Boolean isActive, Pageable pageable);`
  - `UserDetailResponseDTO getUserById(UUID userId);`
  - `UserDetailResponseDTO createUser(CreateUserRequestDTO request);`
  - `UserDetailResponseDTO updateUser(UUID userId, UpdateUserRequestDTO request);`
  - `void changeUserStatus(UUID userId, boolean isActive);`
  - `void resetPassword(UUID userId, String newPassword);`

---

### 2. Class `UserServiceImpl.java` (Thuật toán chi tiết bằng văn bản)
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/account/service/impl/UserServiceImpl.java`
- **Annotations cho Class:**
  - `@Service`
  - `@RequiredArgsConstructor` (Tự động inject `UserRepository`, `UserTenantRepository`, `UserBranchRepository`, `RoleRepository`, `BranchRepository`, `PasswordEncoder` qua constructor).
  - `@Slf4j` (Ghi nhật ký kiểm toán hành động nhân sự).

#### 📍 Thuật toán 1: Lấy danh sách nhân viên (`getUsers`)
- **Bước 1:** Lấy `currentTenantId` từ `TenantContext.getTenantId()`. Nếu rỗng ném `AppException(ErrorCode.UNAUTHORIZED)`.
- **Bước 2:** Gọi `userRepository` thực hiện query phân trang lọc theo `currentTenantId`, `keyword`, `roleId`, `isActive`, `pageable`.
- **Bước 3:** Chuyển đổi (`.map()`) từng `User` thành `UserSummaryResponseDTO`:
  - Dùng Lombok `@Builder` để gán các trường.
  - Lấy thông tin Role từ `user_tenants` và đếm số lượng chi nhánh phụ trách từ `user_branches`.
- **Bước 4:** Trả về `Page<UserSummaryResponseDTO>`.

---

#### 📍 Thuật toán 2: Xem chi tiết 1 nhân viên (`getUserById`)
- **Bước 1:** Lấy `currentTenantId` từ `TenantContext`.
- **Bước 2 (Chống IDOR):** Gọi `userTenantRepository.findByUserIdAndTenantId(userId, currentTenantId)`. Nếu không tồn tại $\rightarrow$ Ném ngay `AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhân viên trong nhà xe của bạn")`.
- **Bước 3:** Lấy thực thể `User` từ `userRepository.findById(userId)`. Nếu không có ném `RESOURCE_NOT_FOUND`.
- **Bước 4:** Lấy thông tin `Role` theo `userTenant.getRoleId()`.
- **Bước 5:** Lấy danh sách chi nhánh được gán từ `userBranchRepository.findAllByTenantIdAndUserId(currentTenantId, userId)`, sau đó join/map sang `BranchSummaryDTO`.
- **Bước 6:** Đóng gói toàn bộ vào `UserDetailResponseDTO` bằng `@Builder` và trả về.

---

#### 📍 Thuật toán 3: Thêm nhân viên mới (`createUser`) — Gắn `@Transactional`
- **Bước 1:** Lấy `currentTenantId` từ `TenantContext`.
- **Bước 2 (Kiểm tra Role):** Kiểm tra `request.getRoleId()` có tồn tại và thuộc về chính `currentTenantId` này không qua `roleRepository.findByIdAndTenantId()`. Nếu không hợp lệ $\rightarrow$ Ném `AppException(ErrorCode.BAD_REQUEST, "Nhóm quyền không hợp lệ")`.
- **Bước 3 (Kiểm tra Email):**
  - Tìm `userRepository.findByEmailIgnoreCase(request.getEmail().trim())`.
  - **Nhánh A (User đã tồn tại trên hệ thống):**
    - Kiểm tra xem người này đã là thành viên của nhà xe chưa: `userTenantRepository.existsByUserIdAndTenantId(existUser.getId(), currentTenantId)`.
    - Nếu đã là thành viên: Ném `AppException(ErrorCode.BAD_REQUEST, "Nhân viên với email này đã tồn tại trong nhà xe")`.
    - Nếu chưa là thành viên (ví dụ: từng làm ở nhà xe khác): Dùng lại `existUser` này để gán vào `user_tenants`.
  - **Nhánh B (User hoàn toàn mới):**
    - Tạo `User` mới: Băm mật khẩu bằng `passwordEncoder.encode(request.getPassword())`, gán `email`, `fullName`, `phone`, `isActive = true`, `isSuperAdmin = false`.
    - Lưu vào DB: `userRepository.save(newUser)`.
- **Bước 4 (Tạo quyết định gia nhập Nhà xe):**
  - Tạo thực thể `UserTenant` với `userId`, `currentTenantId`, `roleId`, `joinedAt = Instant.now()`.
  - Lưu vào DB: `userTenantRepository.save(userTenant)`.
- **Bước 5 (Gán chi nhánh nếu có):**
  - Nếu `request.getBranchIds()` không rỗng:
    - Validate toàn bộ các `branchId` gửi lên có thuộc về `currentTenantId` không qua `branchRepository.findAllByIdInAndTenantId()`. Nếu số lượng tìm thấy khác số lượng gửi lên $\rightarrow$ Ném lỗi ngay để chống gán chi nhánh của nhà xe khác!
    - Duyệt danh sách ID chi nhánh, tạo các thực thể `UserBranch` với `status = UserBranchStatus.ACTIVE`, `startedAt = Instant.now()`.
    - Lưu hàng loạt: `userBranchRepository.saveAll(userBranches)`.
- **Bước 6:** Trả về `UserDetailResponseDTO`.

---

#### 📍 Thuật toán 4: Cập nhật nhân viên (`updateUser`) — Gắn `@Transactional`
- **Bước 1:** Lấy `currentTenantId` từ `TenantContext`.
- **Bước 2 (Chống IDOR):** Tìm `userTenantRepository.findByUserIdAndTenantId(userId, currentTenantId)`. Nếu rỗng $\rightarrow$ Ném `RESOURCE_NOT_FOUND`.
- **Bước 3:** Lấy `User` từ `userRepository.findById(userId)`. Cập nhật các trường `fullName`, `phone`, `avatarUrl`, `isActive`.
- **Bước 4 (Cập nhật Quyền):** Nếu `request.getRoleId()` khác role cũ, kiểm tra tính hợp lệ của Role mới trong Tenant rồi cập nhật: `userTenant.setRoleId(newRoleId)`.
- **Bước 5 (Áp dụng REPLACE PATTERN cho Chi nhánh):**
  - Khi Admin chọn lại danh sách chi nhánh (ví dụ: nhân viên chuyển từ bãi Cầu Giấy sang bãi Đống Đa):
    1. Gọi `userBranchRepository.deleteByTenantIdAndUserId(currentTenantId, userId);` $\rightarrow$ Xóa sạch phân công cũ.
    2. Nếu `request.getBranchIds()` có phần tử mới:
       - Validate tính hợp lệ của các chi nhánh thuộc Tenant.
       - Tạo các bản ghi `UserBranch` mới và gọi `userBranchRepository.saveAll(...)`.
- **Bước 6:** Lưu `userRepository.save(user)` và trả về DTO hoàn chỉnh.

---

#### 📍 Thuật toán 5: Khóa / Mở khóa tài khoản (`changeUserStatus`)
- **Bước 1:** Kiểm tra nhân viên có thuộc Tenant không qua `user_tenants`.
- **Bước 2 (Chống tự khóa chính mình):** Nếu `userId` trùng với ID của Admin đang thực hiện request $\rightarrow$ Ném `AppException(ErrorCode.BAD_REQUEST, "Bạn không thể tự khóa tài khoản của chính mình")`.
- **Bước 3:** Cập nhật `user.setIsActive(isActive)`.
- **Bước 4 (Đá phiên đăng nhập):** Nếu chuyển sang khóa (`isActive = false`), gán `user.setCurrentSessionId(null)` để vô hiệu hóa ngay lập tức mọi phiên làm việc hiện tại của nhân viên đó.

---

## 🚪 GIAI ĐOẠN 4: XÂY DỰNG REST API CONTROLLER & PHÂN QUYỀN (`account/controller/`)

- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/account/controller/UserController.java`
- **Annotations cho Class:**
  - `@RestController`
  - `@RequestMapping("/api/v1/users")`
  - `@RequiredArgsConstructor`
  - `@Tag(name = "User Management", description = "Quản lý Nhân sự & Phân quyền Chi nhánh")`
  - `@PreAuthorize("hasAuthority('account:manage')")` (Bảo vệ toàn bộ Controller: chỉ User có quyền quản lý nhân sự mới được truy cập).

### Danh sách Endpoint:

| Phương thức | Đường dẫn URL | Mô tả nghiệp vụ | Tham số / Body | Mã HTTP |
| :---: | :--- | :--- | :--- | :---: |
| **GET** | `/api/v1/users` | Danh sách nhân viên phân trang | `page`, `size`, `sort`, `keyword`, `roleId`, `isActive` | `200 OK` |
| **GET** | `/api/v1/users/{id}` | Xem chi tiết 1 nhân viên | `@PathVariable UUID id` | `200 OK` |
| **POST** | `/api/v1/users` | Thêm nhân viên mới | `@Valid @RequestBody CreateUserRequestDTO` | `201 CREATED` |
| **PUT** | `/api/v1/users/{id}` | Cập nhật hồ sơ & chi nhánh | `@PathVariable UUID id`, `@Valid @RequestBody UpdateUserRequestDTO` | `200 OK` |
| **PATCH** | `/api/v1/users/{id}/status` | Khóa / Mở khóa nhân viên | `@PathVariable UUID id`, `@RequestParam boolean isActive` | `200 OK` |
| **POST** | `/api/v1/users/{id}/reset-password` | Đặt lại mật khẩu nhân viên | `@PathVariable UUID id`, `@RequestParam String newPassword` | `200 OK` |

---

## 🔬 BẢN CHẤT KỸ THUẬT DEEP DIVE (JAVA CORE & CSDL)

Dành cho bạn để hiểu rõ "dưới gầm bàn" của máy ảo Java và PostgreSQL:

### 1. Tại sao dùng Chiến lược Replace Pattern cho Chi nhánh?
- **Nếu không dùng Replace:** Bạn phải viết thuật toán so sánh 2 tập hợp (Diff Algorithm): *"Chi nhánh nào giữ nguyên? Chi nhánh nào mới tích vào? Chi nhánh nào vừa bị bỏ tích?"* $\rightarrow$ Tốn hàng chục dòng code lồng nhau, rất dễ phát sinh bug bất đồng bộ.
- **Dùng Replace:** `DELETE` sạch các dòng cũ của User đó $\rightarrow$ `INSERT` danh sách mới. Đúng 3 dòng code, sạch sẽ, triệt tiêu 100% dữ liệu rác.
- **Bản chất Hibernate Write-Behind:** Nhớ gọi `userBranchRepository.flush()` giữa lệnh xóa và lệnh thêm nếu bạn dùng chung IdClass hoặc có nguy cơ xung đột khóa chính trước khi transaction commit!

---

### 2. Sự khác biệt giữa `hasRole()` và `hasAuthority()`
- `hasRole('ADMIN')`: Spring Security sẽ tự động tìm kiếm Authority có tiền tố `ROLE_ADMIN`. Bị gò bó theo tên Role cố định.
- `hasAuthority('account:manage')`: Kiểm tra trực tiếp chuỗi quyền nguyên tử (Atomic Permission) do DB cấp phát. Linh hoạt hơn rất nhiều: dù nhà xe đặt tên Role là "Chủ bãi", "Giám đốc" hay "Admin", chỉ cần Role đó được gán quyền `account:manage` trong bảng `role_permissions` là đều truy cập được API!

---

### 3. Tại sao kiểm tra Email phải dùng `equalsIgnoreCase`?
- Hệ thống cơ sở dữ liệu hoặc người dùng khi gõ `Staff@rentcar.vn` và `staff@rentcar.vn` có thể bị coi là khác nhau nếu collation DB là phân biệt hoa thường.
- Luôn luôn chuẩn hóa: `request.getEmail().trim().toLowerCase()` trước khi kiểm tra hoặc lưu vào DB để đảm bảo tính nhất quán và chống tạo tài khoản trùng lặp.

---

## 🔎 TỪ KHÓA TÌM KIẾM (GOOGLE KEYWORDS)

Khi gõ code gặp vướng mắc, bạn hãy copy chính xác các cụm từ khóa tiếng Anh này để tra cứu tài liệu chuẩn:
- `"Spring Data JPA @Query pagination with join"`
- `"Spring Data JPA deleteBy and saveAll in same transaction"`
- `"Lombok @Builder inheritance with DTO"`
- `"Spring Security @PreAuthorize hasAuthority vs hasRole"`
- `"BCryptPasswordEncoder password hash verification"`

---

## ✅ CHECKLIST NGHIỆM THU

Sau khi bạn tự tay code xong, hãy tự kiểm tra lại theo danh sách này:
- [ ] Mọi câu query tìm kiếm User đều có ràng buộc `tenant_id` từ `TenantContext`.
- [ ] Không có API nào để lộ trường `password_hash` ra DTO response.
- [ ] Thử dùng Token của Nhà xe A để sửa thông tin User của Nhà xe B $\rightarrow$ Bắt buộc phải văng lỗi `404 Not Found` hoặc `403 Forbidden` (Chống IDOR thành công).
- [ ] Test trường hợp khóa tài khoản: User bị khóa thì `current_session_id` bị xóa, không thể dùng token cũ để thao tác tiếp.
- [ ] Chạy `mvn compile` thành công không có lỗi cú pháp.

