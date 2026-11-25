> [!NOTE]
> **[COMPLETED / DONE]** Toàn bộ 11 Task sửa lỗi & tối ưu hóa trong kế hoạch này đã được triển khai hoàn tất trên nhánh `feature/refactor-auth`. Tài liệu được lưu lại để phục vụ đối chiếu lịch sử bảo mật & concurrency.

# 📋 KẾ HOẠCH CHI TIẾT SỬA LỖI & TỐI ƯU HÓA TOÀN DIỆN (SYSTEM FIXES SPECIFICATION)
> **Dự án:** Car Rental SaaS  
> **Ngày lập kế hoạch:** 2026-11-19  
> **Nguồn đánh giá:** Dựa trên Báo cáo Review Audit ngày 2026-11-18 (`review-main-2026-11-18.md`)  
> **Phạm vi:** 11 Task sửa code phân bổ trên 3 Nhánh Feature độc lập.

---

## 🎯 TỔNG QUAN CÁC NHÁNH PHÁT TRIỂN

```
┌────────────────────────────────────────────────────────────────────────┐
│ NHÁNH 1: fix/auth-security-core (ƯU TIÊN P1 - BẢO MẬT & CONCURRENCY)   │
│ ├── Task 1: [P1] Login nguyên tử chống ghi đè khóa tài khoản (R04, D04)│
│ ├── Task 2: [P2] Bỏ qua kiểm tra Bearer lỗi tại Public Endpoints (R05) │
│ ├── Task 3: [P2] Ẩn Password khỏi toString() của DTO & Principal (R09) │
│ └── Task 4: [P1] Validate bắt buộc JWT_SECRET khi khởi động (R03)      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ NHÁNH 2: fix/auth-tenant-lifecycle (LUỒNG MULTI-TENANT & LỖI HTTP)     │
│ ├── Task 5: [P2] Cho phép đổi Tenant khi Tenant hiện tại bị khóa (R06) │
│ ├── Task 6: [P2] Lọc Tenant Active trước khi tính toán điều hướng (R18)│
│ ├── Task 7: [P2] Bắt đúng HTTP Status 404, 405, 415 ở ExceptionHandler(R17)
│ ├── Task 8: [P2] Cấu hình CORS cho Angular Localhost:4200 (R10)        │
│ └── Task 9: [P2] Thống nhất mã lỗi 401 khi tài khoản bị khóa/xóa (R20) │
└───────────────────────────────────┬────────────────────────────────────┘
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ NHÁNH 3: fix/entity-guards-defaults (ROBUSTNESS CẤP ENTITY & RBAC)     │
│ ├── Task 10: [Gợi ý] @Builder.Default & @PrePersist cho Entity (D03)   │
│ └── Task 11: [Gợi ý] Chống đặt mã Role tenant trùng SUPER_ADMIN (D02) │
└────────────────────────────────────────────────────────────────────────┘
```

---

# 🔴 NHÓM 1: `fix/auth-security-core` (Ưu Tiên P1 - Bảo Mật & Concurrency)

---

### 📌 TASK 1: [P1] Login nguyên tử chống ghi đè khóa tài khoản & Cập nhật `last_login_at`
* **Mã phát hiện:** R04 (P1) & D04
* **Files liên quan:**
  - `backend/src/main/java/com/carrental/car_rental_backend/account/repository/UserRepository.java`
  - `backend/src/main/java/com/carrental/car_rental_backend/auth/service/impl/AuthServiceImpl.java`

#### 1. Mô tả lỗi hiện tại:
* Trong `AuthServiceImpl.login()`: Sau khi kiểm tra mật khẩu hợp lệ, code đọc entity `User` lên bộ nhớ, set `user.setCurrentSessionId(newSessionId)` rồi gọi hàm `userRepository.save(user)` toàn phần.
* **Tử huyệt Concurrency (Race Condition):**
  - Giả sử lúc 10:00:00.000, Admin gọi API khóa tài khoản: `is_active = false`.
  - Đúng lúc đó, User bấm Đăng nhập. Tiến trình login đã đọc bản ghi `User` từ trước (lúc `is_active = true`).
  - Lệnh `userRepository.save(user)` của Hibernate sinh câu lệnh `UPDATE users SET is_active = true, current_session_id = ...`.
  - **Hậu quả:** Thao tác khóa tài khoản của Admin bị ghi đè và hủy bỏ hoàn toàn. User đăng nhập thành công và tài khoản tự động được active trở lại!
  - Ngoài ra, cột `last_login_at` trong bảng `users` chưa bao giờ được cập nhật thời gian sau khi login thành công.

#### 2. Mong muốn sau khi fix xong:
* **Không dùng `userRepository.save(user)` toàn phần khi đăng nhập.**
* Thay vào đó, thực thi một câu lệnh cập nhật nguyên tử có điều kiện (**Atomic Conditional Update**) tại tầng Repository:
  ```sql
  UPDATE User u 
  SET u.currentSessionId = :sessionId, u.lastLoginAt = CURRENT_TIMESTAMP 
  WHERE u.id = :userId AND u.isActive = true
  ```
* **Kiểm tra kết quả cập nhật:**
  - Nếu số dòng được cập nhật `updatedRows == 1`: Đăng nhập thành công, session mới được cấp phát và `last_login_at` được ghi nhận.
  - Nếu `updatedRows == 0`: Nghĩa là tài khoản đã bị Admin khóa (`isActive = false`) hoặc đã bị xóa ngay trong tích tắc đó $\rightarrow$ Hệ thống lập tức ném ngoại lệ `AppException(ErrorCode.USER_DISABLED)` (HTTP 403) và từ chối cấp Token.
* **Hướng kiểm thử (Verification):** Viết Integration Test với 2 luồng song song: Luồng A update `is_active = false`, Luồng B login. Luồng B phải nhận lỗi 403, DB giữ nguyên `is_active = false`.

---

### 📌 TASK 2: [P2] Bỏ qua kiểm tra Bearer lỗi tại các Public Endpoints
* **Mã phát hiện:** R05 (P2)
* **Files liên quan:**
  - `backend/src/main/java/com/carrental/car_rental_backend/security/jwt/JwtAuthenticationFilter.java`

#### 1. Mô tả lỗi hiện tại:
* `JwtAuthenticationFilter` chặn bắt tất cả các request có Header `Authorization: Bearer <token>`. Nếu token không hợp lệ (hết hạn, sai chữ ký, hỏng format), Filter ném ngay ngoại lệ và dừng request, trả về HTTP 401 Unauthorized trước khi request chạm tới tầng Controller.
* **Tử huyệt UX:** Khi người dùng có token cũ hết hạn lưu trong LocalStorage, Frontend hoặc Swagger vô tình đính kèm Header này vào cả request `POST /api/v1/auth/login` hoặc `POST /api/v1/auth/refresh`. Dù endpoint này được cấu hình `permitAll()`, người dùng vẫn bị Filter chặn 401, không thể đăng nhập lại được nếu không xóa thủ công cache trình duyệt.

#### 2. Mong muốn sau khi fix xong:
* Trong `JwtAuthenticationFilter`, định nghĩa danh sách các Public Endpoints được phép truy cập tự do:
  - `/api/v1/auth/login`
  - `/api/v1/auth/refresh`
  - `/swagger-ui/**`, `/v3/api-docs/**`
* Khi request gửi tới các endpoint thuộc Whitelist này: Nếu Bearer token bị lỗi hoặc hết hạn, Filter **chỉ cần bỏ qua việc xác thực** (không nạp `Authentication` vào `SecurityContextHolder`), sau đó tiếp tục cho request chạy tiếp: `filterChain.doFilter(request, response)`.
* Người dùng gửi kèm Bearer cũ vào trang Login/Refresh vẫn đăng nhập thành công 100%.

---

### 📌 TASK 3: [P2] Ẩn Password khỏi `toString()` trong Log Debug
* **Mã phát hiện:** R09 (P2)
* **Files liên quan:**
  - `backend/src/main/java/com/carrental/car_rental_backend/auth/dto/request/LoginRequestDTO.java`
  - `backend/src/main/java/com/carrental/car_rental_backend/security/principal/UserPrincipal.java`

#### 1. Mô tả lỗi hiện tại:
* Class `LoginRequestDTO` đang sử dụng Lombok `@Data` hoặc `@ToString`. Khi ứng dụng chạy ở chế độ debug log hoặc có framework log request body, hàm `toString()` tự sinh in toàn bộ chuỗi: `LoginRequestDTO(email=..., password=123456)` ra console và file log hệ thống.
* Điều này vi phạm nghiêm trọng tiêu chuẩn bảo mật dữ liệu nhạy cảm (OWASP / TT09).

#### 2. Mong muốn sau khi fix xong:
* Thêm annotation `@ToString(exclude = "password")` trên class `LoginRequestDTO` và `UserPrincipal`.
* Khi gọi `toString()`, mật khẩu bị loại trừ hoàn toàn khỏi chuỗi log: `LoginRequestDTO(email=admin@rentcar.vn)`.

---

### 📌 TASK 4: [P1] Validate bắt buộc `JWT_SECRET` khi khởi động ứng dụng
* **Mã phát hiện:** R03 (P1)
* **Files liên quan:**
  - `backend/src/main/resources/application.yaml`
  - `backend/src/main/java/com/carrental/car_rental_backend/security/jwt/JwtProvider.java`

#### 1. Mô tả lỗi hiện tại:
* Trong `application.yaml` và `JwtProvider.java`, `jwt.secret` có giá trị fallback mặc định hardcode sẵn trong source code.
* Nếu môi trường deploy ngoài production quên cấu hình biến môi trường `JWT_SECRET`, ứng dụng vẫn khởi động bình thường với khóa ký biết trước này. Kẻ xấu có thể dùng khóa mặc định này để tự sinh token giả mạo hợp lệ.

#### 2. Mong muốn sau khi fix xong:
* Trong `JwtProvider.java`, thêm phương thức `@PostConstruct public void validateSecret()`:
  - Kiểm tra nếu `jwtSecret` bị null, rỗng hoặc độ dài khóa yếu (< 256 bits cho thuật toán HS256) $\rightarrow$ Ném `IllegalStateException("CRITICAL: JWT_SECRET environment variable is missing or insecure!")` làm sập quá trình start app ngay lập tức (Fail-Fast).
  - Thống nhất cơ chế giải mã: Sử dụng `Decoders.BASE64.decode(jwtSecret)` hoặc `jwtSecret.getBytes(StandardCharsets.UTF_8)` đồng bộ giữa cấu hình và code.

---

# 🟡 NHÓM 2: `fix/auth-tenant-lifecycle` (Multi-Tenant & Lỗi HTTP)

---

### 📌 TASK 5: [P2] Cho phép đổi Tenant khi Tenant hiện tại bị khóa
* **Mã phát hiện:** R06 (P2)
* **Files liên quan:**
  - `backend/src/main/java/com/carrental/car_rental_backend/security/jwt/JwtAuthenticationFilter.java`
  - `backend/src/main/java/com/carrental/car_rental_backend/auth/service/impl/AuthServiceImpl.java`

#### 1. Mô tả lỗi hiện tại:
* User thuộc 2 nhà xe: Nhà xe A và Nhà xe B. User đang tác nghiệp tại Nhà xe A thì Nhà xe A bị Admin tổng khóa (`is_active = false`).
* User muốn chuyển sang Nhà xe B bằng cách gọi API `POST /api/v1/auth/select-tenant { "tenantId": "ID_NHA_XE_B" }`.
* Nhưng vì Access Token hiện tại của User chứa claim `tenant_id` của Nhà xe A, `JwtAuthenticationFilter` kiểm tra thấy Nhà xe A bị khóa liền ném lỗi 403 Forbidden trước khi request đến được Service chọn nhà xe. User bị kẹt vĩnh viễn, không thể chuyển sang nhà xe B còn hoạt động.

#### 2. Mong muốn sau khi fix xong:
* Endpoint `POST /api/v1/auth/select-tenant` được cấp quyền đặc biệt: Filter chỉ cần xác thực danh tính Người dùng (`user_id`) và Phiên làm việc (`currentSessionId`) còn hợp lệ, **bỏ qua việc kiểm tra trạng thái active của Tenant cũ trong token**.
* Tại `AuthServiceImpl.selectTenant()`: Tiến hành kiểm tra và cấp Token mới nếu Tenant đích (Nhà xe B) đang hoạt động bình thường và User có quyền truy cập hợp lệ.

---

### 📌 TASK 6: [P2] Lọc Tenant Active trước khi tính toán điều hướng login
* **Mã phát hiện:** R18 (P2)
* **Files liên quan:**
  - `backend/src/main/java/com/carrental/car_rental_backend/auth/service/impl/AuthServiceImpl.java`

#### 1. Mô tả lỗi hiện tại:
* Trong hàm `login()`: Code đếm tổng số bản ghi trong bảng `user_tenants` để quyết định luồng trả về:
  - Nếu số membership = 1: Tự động chọn nhà xe đó và cấp token có `tenantId`.
  - Nếu số membership > 1: Trả về `tenantId = null` để Frontend chuyển hướng sang màn hình chọn nhà xe.
* **Xung đột logic:** Nếu user thuộc 2 nhà xe nhưng 1 nhà xe đã bị khóa: Code đếm = 2 $\rightarrow$ Trả về `tenantId = null`, nhưng danh sách `availableTenants` gửi về chỉ có đúng 1 nhà xe. Frontend không thể xử lý trạng thái dở dang này (không tự vào dashboard được, mà màn chọn nhà xe chỉ có 1 nút).
* Tương tự, nếu cả 2 nhà xe đều bị khóa, login vẫn trả về 200 với danh sách rỗng thay vì báo lỗi.

#### 2. Mong muốn sau khi fix xong:
* Tại hàm `login()`, truy vấn và chỉ đếm các Tenant **đang hoạt động** (`is_active = true`):
  - **Nếu số tenant active = 1:** Tự động đăng nhập thẳng vào nhà xe đó (`tenantId` có giá trị, cấp token đầy đủ).
  - **Nếu số tenant active > 1:** Trả về `tenantId = null` kèm danh sách các nhà xe active để user chọn.
  - **Nếu số tenant active = 0:** Ném lỗi `AppException(ErrorCode.NO_ACTIVE_TENANTS)` (HTTP 403) từ chối đăng nhập.

---

### 📌 TASK 7: [P2] Bắt đúng HTTP Status Framework 404, 405, 415 trong `GlobalExceptionHandler`
* **Mã phát hiện:** R17 (P2)
* **Files liên quan:**
  - `backend/src/main/java/com/carrental/car_rental_backend/common/exception/GlobalExceptionHandler.java`

#### 1. Mô tả lỗi hiện tại:
* `@ExceptionHandler(Exception.class)` bắt mọi ngoại lệ chưa khai báo và ép trả về HTTP 500 (`INTERNAL_SERVER_ERROR`).
* Khi Client gọi sai method (gửi `GET` vào `/api/v1/auth/login`), gửi sai định dạng body (`Content-Type: text/plain` hoặc XML), hoặc gọi vào URL không tồn tại, Server đều trả về HTTP 500.
* Điều này làm sai lệch nghiêm trọng chỉ số giám sát hệ thống (tăng vọt lỗi 5xx giả) và khiến Frontend không biết lỗi do đâu.

#### 2. Mong muốn sau khi fix xong:
* Khai báo các `@ExceptionHandler` chuyên biệt trong `GlobalExceptionHandler`:
  - `HttpRequestMethodNotSupportedException` $\rightarrow$ Trả về HTTP 405 Method Not Allowed.
  - `HttpMediaTypeNotSupportedException` $\rightarrow$ Trả về HTTP 415 Unsupported Media Type.
  - `NoResourceFoundException` (Spring Boot 3/4) $\rightarrow$ Trả về HTTP 404 Not Found.
* Toàn bộ lỗi vẫn được bọc trong cấu trúc chuẩn:
  ```json
  {
    "success": false,
    "errorCode": "METHOD_NOT_ALLOWED",
    "message": "Phương thức HTTP không được hỗ trợ cho tài nguyên này.",
    "data": null
  }
  ```

---

### 📌 TASK 8: [P2] Cấu hình CORS đầy đủ cho Frontend `localhost:4200`
* **Mã phát hiện:** R10 (P2)
* **Files liên quan:**
  - `backend/src/main/java/com/carrental/car_rental_backend/security/config/SecurityConfig.java`

#### 1. Mô tả lỗi hiện tại:
* `SecurityConfig` chưa khai báo Bean `CorsConfigurationSource`.
* Khi Frontend Angular/React chạy ở `http://localhost:4200` gọi API backend ở `http://localhost:8080` kèm `withCredentials: true` (để gửi nhận HttpOnly Cookie), trình duyệt gửi request preflight `OPTIONS` và bị chặn lại do thiếu các Header `Access-Control-Allow-Origin` và `Access-Control-Allow-Credentials`.

#### 2. Mong muốn sau khi fix xong:
* Định nghĩa Bean `CorsConfigurationSource`:
  - Cho phép Origin: `http://localhost:4200`, `http://localhost:3000`.
  - Cho phép Methods: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `PATCH`.
  - Cho phép Headers: `Authorization`, `Content-Type`, `X-Active-Branch-Id`, `X-Requested-With`...
  - Bật cấu hình `setAllowCredentials(true)` để hỗ trợ truyền Cookie an toàn.

---

### 📌 TASK 9: [P2] Thống nhất mã lỗi 401 khi tài khoản bị khóa hoặc phiên thu hồi
* **Mã phát hiện:** R20 (P2)
* **Files liên quan:**
  - `backend/src/main/java/com/carrental/car_rental_backend/auth/service/impl/CustomUserDetailsServiceImpl.java`
  - `backend/src/main/java/com/carrental/car_rental_backend/auth/service/impl/AuthServiceImpl.java`

#### 1. Mô tả lỗi hiện tại:
* Khi tài khoản bị khóa hoặc xóa, các nhánh xác thực trả về mã lỗi không đồng nhất:
  - Dùng Access Token gọi API khi user bị khóa $\rightarrow$ Trả về 404 (`RESOURCE_NOT_FOUND`).
  - Dùng Refresh Token khi user bị khóa $\rightarrow$ Trả về 401 (`AUTH_UNAUTHORIZED`).
  - Dùng Refresh Token khi user bị xóa $\rightarrow$ Trả về 400 (`BAD_REQUEST`).
* Sự bất nhất này khiến Interceptor ở Frontend không thể bắt đúng sự kiện để dọn dẹp storage và điều hướng về trang Login.

#### 2. Mong muốn sau khi fix xong:
* Thống nhất: Mọi request có phiên không hợp lệ, user không tồn tại, hoặc user bị khóa (`is_active = false`) đều được quy chuẩn trả về **HTTP 401 Unauthorized** kèm mã lỗi rõ ràng: `USER_DISABLED` hoặc `SESSION_EXPIRED`.

---

# 🟢 NHÓM 3: `fix/entity-guards-defaults` (Tối Ưu Entity & RBAC)

---

### 📌 TASK 10: [Gợi ý] Tránh bẫy `@Builder` làm mất `DEFAULT` của CSDL
* **Mã phát hiện:** D03
* **Files liên quan:**
  - `backend/src/main/java/com/carrental/car_rental_backend/tenant/entity/Tenant.java`
  - `backend/src/main/java/com/carrental/car_rental_backend/branch/entity/Branch.java`
  - `backend/src/main/java/com/carrental/car_rental_backend/account/entity/User.java`

#### 1. Mô tả lỗi hiện tại:
* Các Entity sử dụng Lombok `@Builder`. Khi khởi tạo object bằng builder mà không truyền trường `isActive`, primitive `boolean` mặc định là `false` hoặc Wrapper `Boolean` mặc định là `null`.
* Khi Hibernate thực hiện câu lệnh `INSERT`, nó gửi thẳng giá trị `false` hoặc `null` xuống CSDL, làm vô hiệu hóa hoàn toàn khai báo `DEFAULT TRUE` của PostgreSQL.
* Tương tự, `createdAt` và `updatedAt` bị gửi `null` thay vì nhận `DEFAULT CURRENT_TIMESTAMP` của Database.

#### 2. Mong muốn sau khi fix xong:
* Thêm annotation `@Builder.Default` cho các trường có giá trị mặc định:
  ```java
  @Builder.Default
  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;
  ```
* Bổ sung Entity Lifecycle Callbacks `@PrePersist` và `@PreUpdate` để tự động gán `Instant.now()` cho `createdAt` và `updatedAt` nếu chúng đang bị null trước khi lưu xuống CSDL.

---

### 📌 TASK 11: [Gợi ý] Chống leo thang đặc quyền đặt mã Role `SUPER_ADMIN` trong Tenant
* **Mã phát hiện:** D02
* **Files liên quan:**
  - `backend/src/main/java/com/carrental/car_rental_backend/security/principal/UserPrincipal.java`

#### 1. Mô tả lỗi hiện tại:
* `UserPrincipal.create()` ánh xạ trực tiếp `role.getCode()` từ bảng `roles` thành `GrantedAuthority`.
* Nếu một Tenant Admin tạo nhóm quyền trong nhà xe của mình và đặt mã `code = 'SUPER_ADMIN'`, hệ thống sẽ cấp quyền `ROLE_SUPER_ADMIN` cho user đó $\rightarrow$ User này vô tình nhận đặc quyền Super Admin toàn hệ thống, có thể bypass RLS và can thiệp vào các nhà xe khác!

#### 2. Mong muốn sau khi fix xong:
* Quyền `ROLE_SUPER_ADMIN` **chỉ được cấp duy nhất** khi cờ `user.isSuperAdmin == true`.
* Các Role lấy từ `user_tenants` được chuẩn hóa tiền tố an toàn (ví dụ: `ROLE_TENANT_` + `role.getCode()`), hoặc chặn cứng không cho phép gán quyền `SUPER_ADMIN` từ role động của tenant.

---

## 📅 LỘ TRÌNH THỰC THI ĐỀ XUẤT (EXECUTION TIMELINE)

1. **Sprint 1 (Ngay bây giờ):** Tạo nhánh `fix/auth-security-core` $\rightarrow$ Triển khai Task 1 đến Task 4 (giải quyết triệt để 2 lỗi P1).
2. **Sprint 2:** Tạo nhánh `fix/auth-tenant-lifecycle` $\rightarrow$ Triển khai Task 5 đến Task 9 (hoàn thiện luồng Multi-Tenant và xử lý lỗi HTTP).
3. **Sprint 3:** Tạo nhánh `fix/entity-guards-defaults` $\rightarrow$ Triển khai Task 10 và Task 11 (củng cố an toàn cấp Entity và RBAC).

