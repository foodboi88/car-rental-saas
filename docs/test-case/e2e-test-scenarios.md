# 🧪 Tài Liệu Kịch Bản Kiểm Thử E2E Backend (E2E Test Scenarios)

> **Mã dự án**: Car Rental SaaS  
> **Modules kiểm thử**: `Auth`, `Account`, `Branch`, `Vehicle-Type`  
> **Ngày cập nhật**: 2026-07-29  

---

# 🚀 LUỒNG TEST 1: Khởi Tạo Nhà Xe, Mở Rộng Chi Nhánh, Cấu Hình Phân Khúc & Phân Quyền Nhân Sự
*(Enterprise Onboarding & Security Boundary Test Flow)*

### 🎯 Kịch bản nghiệp vụ thực tế
Chủ nhà xe **RentCar VN** (`TENANT_ADMIN`) đăng nhập hệ thống, tiến hành tạo Chi nhánh mới tại TP.HCM, cấu hình bảng giá sàn cho loại xe (Sedan 4 chỗ), tuyển dụng Nhân viên (`STAFF`) và Cộng tác viên (`SALE`), đồng thời phân công nhân viên vào đúng chi nhánh. Trong quá trình đó, hệ thống phải liên tục ngăn chặn các hành vi vi phạm giới hạn gói cước (Quota), trùng dữ liệu, và truy cập trái phép chéo Tenant.

---

### 📋 Các bước test chi tiết (Step-by-Step E2E)

#### Bước 1: Đăng nhập & Xác thực Ngữ cảnh Multi-Tenant (Auth Module)

* **1.1 Happy Path - Login Admin**:
  * **API**: `POST /api/auth/login`
  * **Request Body**:
    ```json
    {
      "email": "minh.admin@rentcarhanoi.vn",
      "password": "Hieudvt@123"
    }
    ```
  * **Kỳ vọng**: `200 OK`. Trả về JWT Access Token (hạn 15 phút), Cookie HTTP-Only `refresh_token` (hạn 7 ngày). `requiresTenantSelection = false` (hoặc trả về danh sách `TenantOption` nếu tài khoản thuộc nhiều Tenant).

* **1.2 Case dị #1 - Đăng nhập tài khoản bị vô hiệu hóa (`is_active = false`)**:
  * **Request Body**:
    ```json
    {
      "email": "locked_admin@rentcar.com",
      "password": "Hieudvt@123"
    }
    ```
  * **Kỳ vọng**: `401 Unauthorized` / `DisabledException`.

* **1.3 Case dị #2 - Chọn Tenant không thuộc sở hữu (Cross-Tenant Breach)**:
  * **API**: `POST /api/auth/select-tenant`
  * **Header**: `Cookie: refresh_token=<valid_cookie_user_A>`
  * **Request Body**:
    ```json
    {
      "tenantId": "<UUID_of_Tenant_B>"
    }
    ```
  * **Kỳ vọng**: `403 Forbidden` / `TenantAccessDeniedException` (*"Tenant access denied"*).

* **1.4 Case dị #3 - Gọi `select-tenant` khi không có Cookie Refresh Token**:
  * **Kỳ vọng**: `401 Unauthorized` / `TenantAccessDeniedException` (*"Session expired. Please log in again."*).

---

#### Bước 2: Thiết lập Chi nhánh & Kiểm soát Quota Gói cước (Branch Module)

* **2.1 Happy Path - Tạo Chi Nhánh Mới**:
  * **API**: `POST /api/v1/branches`
  * **Header**: `Authorization: Bearer <Admin_JWT>`
  * **Request Body**:
    ```json
    {
      "code": "CN-Q1",
      "name": "Chi Nhánh Quận 1 - TP.HCM",
      "phone": "0901234567",
      "address": "123 Nguyễn Huệ, Phường Bến Nghé",
      "city": "TP. Hồ Chí Minh",
      "district": "Quận 1",
      "ward": "Phường Bến Nghé",
      "openingHours": "07:00 - 21:00"
    }
    ```
  * **Kỳ vọng**: `201 Created`. Trả về `BranchResponseDTO` chứa `id`, `tenantId`, `status: "ACTIVE"`.

* **2.2 Case dị #4 - Trùng Mã Chi Nhánh (Duplicate Code in Tenant)**:
  * Gửi lại `POST /api/v1/branches` với `code: "CN-Q1"` cho cùng Tenant.
  * **Kỳ vọng**: `400 Bad Request` (`BRANCH_CODE_EXISTS`).

* **2.3 Case dị #5 - Vượt Giới Hạn Quota Chi Nhánh (Quota Enforcement)**:
  * Nếu Tenant đang dùng gói **Starter** (giới hạn `max_branches = 1`).
  * Thực hiện `POST /api/v1/branches` để tạo chi nhánh thứ 2 (`CN-Q3`).
  * **Kỳ vọng**: `400 Bad Request` / `403 Forbidden` (`BRANCH_QUOTA_EXCEEDED`).

* **2.4 Case dị #6 - Sai Định Dạng Số Điện Thoại**:
  * Request Body chứa `phone: "12345"` hoặc `phone: "abc"`.
  * **Kỳ vọng**: `400 Bad Request` (MethodArgumentNotValidException: *"Phone number must start with 0 and contain 10-11 digits"*).

* **2.5 Case dị #7 - Isolation Check (Đọc dữ liệu chi nhánh Tenant khác)**:
  * **API**: `GET /api/v1/branches/<UUID_Branch_Thuoc_Tenant_B>`
  * **Kỳ vọng**: `404 Not Found` (`BRANCH_NOT_FOUND`), không bao giờ rò rỉ dữ liệu của Tenant khác.

---

#### Bước 3: Cấu hình Phân khúc & Bảng giá Sàn Loại xe (Vehicle Type Module)

* **3.1 Happy Path - Tạo Loại Xe Mới**:
  * **API**: `POST /api/v1/vehicle-types`
  * **Request Body**:
    ```json
    {
      "name": "Sedan 4 Chỗ Phổ Thông",
      "description": "Dòng xe Toyota Vios, Hyundai Accent",
      "basePrice": 800000.00
    }
    ```
  * **Kỳ vọng**: `201 Created`. Trả về `VehicleTypeResponseDTO` với `isActive: true`, `vehicleCount: 0`.

* **3.2 Case dị #8 - Trùng Tên Loại Xe (Case-Insensitive Uniqueness)**:
  * Gửi `POST /api/v1/vehicle-types` với `name: "sedan 4 chỗ phổ thông"` (chữ thường).
  * **Kỳ vọng**: `400 Bad Request` / `409 Conflict` (`VEHICLE_TYPE_NAME_EXISTS`).

* **3.3 Case dị #9 - Giá Sàn Âm (Negative Price Constraint)**:
  * **Request Body**: `{"name": "SUV 7 Chỗ", "basePrice": -500000.00}`
  * **Kỳ vọng**: `400 Bad Request` (*"Giá cơ bản phải lớn hơn hoặc bằng 0"*).

---

#### Bước 4: Tạo Tài Khoản Nhân Sự & Validate Ràng Buộc Vai Trò (Account Module)

* **4.1 Happy Path - Tạo Account Nhân Viên (STAFF - Role 2)**:
  * **API**: `POST /api/v1/accounts`
  * **Request Body**:
    ```json
    {
      "email": "staff_q1@rentcar.com",
      "password": "StaffPassword123!",
      "fullName": "Nguyễn Văn Staff",
      "phone": "0987654321",
      "role": 2,
      "branchIds": ["<UUID_CN_Q1>"]
    }
    ```
  * **Kỳ vọng**: `200 OK` / `201 Created`. Tạo `User`, gán `UserTenant` (role=STAFF), tạo `UserBranch`.

* **4.2 Case dị #10 - Tạo STAFF nhưng KHÔNG phân công Chi nhánh**:
  * **Request Body**: `{"role": 2, "branchIds": []}` hoặc `null`.
  * **Kỳ vọng**: `400 Bad Request` (*"Staff account must be assigned to at least one branch"*).

* **4.3 Case dị #11 - Tạo SALE (Role 3) nhưng LẠI gán Chi nhánh**:
  * **Request Body**: `{"role": 3, "branchIds": ["<UUID_CN_Q1>"]}`.
  * **Kỳ vọng**: `400 Bad Request` (*"Sale account must not be assigned to any branch"*).

* **4.4 Case dị #12 - Cố tình tạo tài khoản TENANT_ADMIN (Role 1)**:
  * **Request Body**: `{"role": 1, "branchIds": []}`.
  * **Kỳ vọng**: `400 Bad Request` (Validation error `@Min(2), @Max(3)`: *"Role must be 2 (STAFF) or 3 (SALE)"*).

* **4.5 Case dị #13 - Gán Chi Nhánh thuộc Tenant khác (Cross-Tenant Branch Assignment)**:
  * **Request Body**: `{"role": 2, "branchIds": ["<UUID_Branch_Tenant_B>"]}`.
  * **Kỳ vọng**: `403 Forbidden` / `TenantAccessDeniedException` (*"One or more branches do not belong to this tenant"*).

* **4.6 Case dị #14 - Email đã tồn tại trong cùng Nhà xe**:
  * Gửi lại request tạo account với email `staff_q1@rentcar.com`.
  * **Kỳ vọng**: `400 Bad Request` (*"Account with this email already exists in your organization"*).

---

# 🔄 LUỒNG TEST 2: Vận Hành Hằng Ngày, Kiểm Soát Phân Quyền, Khóa Tài Khoản & An Toàn Ràng Buộc Dữ Liệu
*(Operational Lifecycle, Status Lockdown & Safety Guards Test Flow)*

### 🎯 Kịch bản nghiệp vụ thực tế
Nhân viên `STAFF` đăng nhập vào làm việc, thực hiện tra cứu danh sách chi nhánh và phân khúc xe. Chủ nhà xe thực hiện tạm ngưng hoạt động của chi nhánh/loại xe bảo trì, thực hiện điều chuyển vai trò nhân sự (Staff ➔ Sale), khóa tài khoản vi phạm, và cố gắng xóa các dữ liệu đang có ràng buộc xe/hợp đồng.

---

### 📋 Các bước test chi tiết (Step-by-Step E2E)

#### Bước 1: Kiểm Tra Quyền Hạn Nhân Viên (STAFF)

* **1.1 Happy Path - Staff Đăng nhập & Đọc Dữ liệu (Read-Only Access)**:
  * `POST /api/auth/login` với `lan.staff@rentcarhanoi.vn` / `Hieudvt@123`.
  * Lấy JWT Token của Staff.
  * Call `GET /api/v1/branches` và `GET /api/v1/vehicle-types`.
  * **Kỳ vọng**: `200 OK`. Staff xem được danh sách chi nhánh và loại xe.

* **1.2 Case dị #15 - Leo Quyền (Privilege Escalation Attack)**:
  * Dùng JWT Token của `STAFF` gọi API của Admin: `POST /api/v1/branches` hoặc `POST /api/v1/accounts`.
  * **Kỳ vọng**: `403 Forbidden` do vi phạm `@PreAuthorize("hasRole('TENANT_ADMIN')")`.

---

#### Bước 2: Thay Đổi Trạng Thái Hoạt Động (Maintenance & Active Toggle)

* **2.1 Happy Path - Tạm ngưng Chi nhánh (INACTIVE)**:
  * `PATCH /api/v1/branches/{CN-Q1-id}/status` bởi `TENANT_ADMIN`
  * **Request Body**: `{"status": "INACTIVE"}`
  * **Kỳ vọng**: `200 OK`. Chi nhánh chuyển sang `INACTIVE`.

* **2.2 Happy Path - Tạm ngưng Loại xe (`isActive = false`)**:
  * `PATCH /api/v1/vehicle-types/{type-id}/status` bởi `TENANT_ADMIN`
  * **Request Body**: `{"status": false}`
  * **Kỳ vọng**: `200 OK`. Loại xe bị ngưng kích hoạt (`isActive: false`).

* **2.3 Case dị #16 - Truy vấn Filter theo trạng thái (`isActive`)**:
  * Call `GET /api/v1/vehicle-types?isActive=true` ➔ Không xuất hiện loại xe vừa ngưng.
  * Call `GET /api/v1/vehicle-types?isActive=false` ➔ Trả về loại xe vừa ngưng.

---

#### Bước 3: Kiểm Soát Ràng Buộc An Toàn Khi Xóa (Delete Safety Guard)

* **3.1 Case dị #17 - Xóa Loại Xe Đang Được Sử Dụng (Vehicle Type In Use)**:
  * Giả định trong DB loại xe "Sedan 4 Chỗ" đang có xe vật lý phụ thuộc (`vehicleCount > 0`).
  * Admin gọi `DELETE /api/v1/vehicle-types/{type-id}`.
  * **Kỳ vọng**: `400 Bad Request` / `409 Conflict` (`VEHICLE_TYPE_IN_USE` - *"Không thể xóa loại xe [tên] vì đang có [X] xe thuộc phân khúc này"*).

* **3.2 Case dị #18 - Xóa Chi Nhánh Đang Có Xe Hoặc Hợp Đồng (Branch Has Active Data)**:
  * Admin gọi `DELETE /api/v1/branches/{CN-Q1-id}` khi chi nhánh đang chứa xe hoặc nhân viên.
  * **Kỳ vọng**: `400 Bad Request` (`BRANCH_HAS_ASSOCIATED_DATA` / `BRANCH_HAS_ACTIVE_VEHICLES`).

* **3.3 Happy Path - Xóa Rống (Clean Soft Delete)**:
  * Tạo 1 Chi nhánh rác (không có xe, không có staff), sau đó gọi `DELETE /api/v1/branches/{dummy-id}`.
  * **Kỳ vọng**: `200 OK`. Hệ thống thực hiện Soft Delete (`is_deleted = true`).

---

#### Bước 4: Điều Chuyển Vai Trò & Khóa Tài Khoản Nhân Sự (HR Management)

* **4.1 Happy Path - Chuyển Vai Trò (Role Swap from STAFF to SALE)**:
  * `PUT /api/v1/accounts/{staff_q1_id}/role` bởi Admin
  * **Request Body**: `{"role": 3, "branchIds": []}`
  * **Kỳ vọng**: `200 OK`. Chuyển role thành `SALE`, tự động xóa tất cả bản ghi phân công chi nhánh trong `user_branches`.

* **4.2 Case dị #19 - Chuyển sang STAFF nhưng quên truyền Chi nhánh**:
  * `PUT /api/v1/accounts/{staff_q1_id}/role`
  * **Request Body**: `{"role": 2, "branchIds": []}`
  * **Kỳ vọng**: `400 Bad Request` (*"Staff account must be assigned to at least one branch"*).

* **4.3 Happy Path - Khóa Tài Khoản Nhân Viên (Lock Account)**:
  * `PATCH /api/v1/accounts/{staff_q1_id}/status` bởi Admin
  * **Request Body**: `{"isActive": false}`
  * **Kỳ vọng**: `200 OK`. Tài khoản chuyển sang trạng thái bị khóa.

* **4.4 Case dị #20 - Tài khoản bị khóa cố tình đăng nhập lại**:
  * `staff_q1@rentcar.com` gọi `POST /api/auth/login`.
  * **Kỳ vọng**: `401 Unauthorized` / `DisabledException` (*"Account is disabled"*).

---

#### Bước 5: Đăng Xuất & Thu Hồi Phiên (Logout & Session Revocation)

* **5.1 Happy Path - Logout**:
  * `POST /api/auth/logout`
  * **Header**: `Cookie: refresh_token=<valid_token>`
  * **Kỳ vọng**: `200 OK`. Trả về Header `Set-Cookie: refresh_token=; Max-Age=0; HttpOnly; Secure`. JTI của Refresh Token được lưu vào danh sách thu hồi.

* **5.2 Case dị #21 - Tái sử dụng Refresh Token đã bị thu hồi (Token Replay Attack)**:
  * Dùng lại chuỗi `refresh_token` cũ để gọi `POST /api/auth/select-tenant`.
  * **Kỳ vọng**: `401 Unauthorized` / `TenantAccessDeniedException` (*"Session expired. Please log in again."*).

---

# 📊 BẢNG TỔNG HỢP MÃ LỖI & KẾT QUẢ KỲ VỌNG (QUY CHUẨN BACKEND)

| Module | Scenario / Test Case | Expected HTTP Status | Expected Error Code / Message |
| :--- | :--- | :--- | :--- |
| **Auth** | Đăng nhập sai mật khẩu | `401 Unauthorized` | `BadCredentialsException` |
| **Auth** | Tài khoản bị vô hiệu hóa | `401 Unauthorized` | `DisabledException` |
| **Auth** | Chọn Tenant không thuộc quyền truy cập | `403 Forbidden` | `TenantAccessDeniedException` |
| **Branch** | Tạo chi nhánh vượt Quota gói cước | `400 Bad Request` / `403` | `BRANCH_QUOTA_EXCEEDED` |
| **Branch** | Trùng mã chi nhánh trong cùng Tenant | `400 Bad Request` | `BRANCH_CODE_EXISTS` |
| **Branch** | Xóa chi nhánh đang có xe/hợp đồng | `400 Bad Request` | `BRANCH_HAS_ACTIVE_VEHICLES` / `BRANCH_HAS_ASSOCIATED_DATA` |
| **VehicleType** | Trùng tên loại xe (không phân biệt hoa/thường) | `400 Bad Request` / `409` | `VEHICLE_TYPE_NAME_EXISTS` |
| **VehicleType** | Xóa loại xe đang có xe vật lý phụ thuộc | `400 Bad Request` / `409` | `VEHICLE_TYPE_IN_USE` |
| **Account** | STAFF không được phân công chi nhánh | `400 Bad Request` | *"Staff account must be assigned to at least one branch"* |
| **Account** | SALE nhưng lại gán chi nhánh | `400 Bad Request` | *"Sale account must not be assigned to any branch"* |
| **Account** | Gán chi nhánh thuộc Tenant khác | `403 Forbidden` | *"One or more branches do not belong to this tenant"* |
| **RBAC** | `STAFF` / `SALE` gọi API chỉnh sửa dữ liệu | `403 Forbidden` | Spring Security Access Denied |
