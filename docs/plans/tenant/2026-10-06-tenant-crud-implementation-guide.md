# 📘 KẾ HOẠCH LÀM MODULE TENANT (THÊM, SỬA, XÓA MỀM NHÀ XE, GÁN CHỦ NHÀ XE)

> **Dự án:** Car Rental SaaS
> **Module:** `tenant`
> **Mục đích tài liệu:** định hướng cách xây dựng module. Đọc từ trên xuống: hiểu bài toán, nắm các quyết định, rồi đi theo từng giai đoạn. Cách trình bày giống [kế hoạch module auth](../auth-login/2026-08-28-auth-jwt-implementation-guide.md).
> **Nguồn thiết kế:** prototype (màn "Thêm nhà xe", "Chi tiết nhà xe"), `onboarding.md` mục 2.1 và 9.2, `Database-Schema.md`, và dữ liệu thật trong DB.

## 🔗 TÀI LIỆU LIÊN QUAN

| Tài liệu | Dùng khi |
| :--- | :--- |
| [Đặc tả API tenant](2026-10-19-tenant-api-spec.md) | Tra **đầu vào, đầu ra, mã lỗi** và xem **sơ đồ tuần tự** luồng xử lý của từng API |
| [Kế hoạch module auth](../auth-login/2026-08-28-auth-jwt-implementation-guide.md) | Tài liệu làm mẫu cho module này, và nơi sinh ra phần đăng nhập, phân quyền mà module này dựa vào |
| [`Database-Schema.md`](../../overviews/Database-Schema.md) | Cấu trúc bảng, nguồn chuẩn về DB |

---

## 1. MỤC TIÊU VÀ PHẠM VI

**Mục tiêu:** Super Admin tạo và quản lý nhà xe (tenant) trên nền tảng.

| Trong phạm vi | Ngoài phạm vi (để module khác hoặc làm sau) |
| :--- | :--- |
| Tạo nhà xe kèm cấu hình mặc định, 3 vai trò mặc định và **một hoặc nhiều chủ nhà xe** | Sửa cấu hình vận hành (`tenant_configs`): do chủ nhà xe tự sửa, thuộc module khác |
| Xem danh sách (tìm, lọc, phân trang) và xem một nhà xe | Gỡ hoặc đổi vai trò chủ nhà xe, khóa riêng một người trong một nhà xe |
| Sửa thông tin nhà xe | Tạo, sửa, khóa nhân viên của nhà xe (module account) |
| Khóa, mở khóa, xóa mềm nhà xe | Mã nhà xe `tenants.code` cho mã đơn `BK_...` (cần migration V7) |
| Thêm và xem chủ nhà xe | Hạn mức theo gói dịch vụ, nhật ký thao tác dạng bảng, Row-Level Security |

**Đăng nhập, chọn nhà xe, chọn chi nhánh không thuộc module này**, do module auth làm.

---

## 2. BỐI CẢNH CẦN BIẾT TRƯỚC KHI LÀM

Sản phẩm là phần mềm cho thuê xe **bán cho các nhà xe**. Mỗi nhà xe là một khách hàng của nền tảng, gọi là **tenant**. Có hai tầng người dùng: **Super Admin** (đội vận hành nền tảng, quyền lấy từ cờ `users.is_super_admin`) và **người của nhà xe** (chủ `TENANT_ADMIN`, nhân viên `STAFF`, Sale `SALE`, quyền lấy từ bảng `roles` và `role_permissions` của từng nhà xe). Module tenant là công cụ của tầng nền tảng, chỉ Super Admin dùng.

### Vì sao "tạo tenant" phải làm nhiều việc cùng lúc
Đã khảo sát code và DB:
- DB **không có trigger** sinh dữ liệu phụ, và **không có vai trò dùng chung** (mọi dòng `roles` đều có `tenant_id`). Nhà xe seed có sẵn vai trò là nhờ migration chèn tay.
- `user_tenants.role_id` là `NOT NULL`: muốn gán người vào nhà xe thì nhà xe phải **có sẵn vai trò**.
- API tạo nhân viên (`onboarding.md` mục 9.2) chỉ cho chủ nhà xe gọi và **cấm tạo `TENANT_ADMIN`**. Nhà xe mới chưa có chủ thì không ai gọi được (vòng luẩn quẩn). Nên **chủ đầu tiên phải do Super Admin tạo cùng lúc với nhà xe**, đúng như màn "Thêm nhà xe" của prototype.
- `user_tenants` có khóa chính `(user_id, tenant_id)`: một người **một vai trò mỗi nhà xe**. Không có ràng buộc "mỗi nhà xe một chủ", nên **nhiều chủ** là hợp lệ. Một tài khoản có thể thuộc **nhiều nhà xe**.

```
Super Admin ── POST /api/v1/tenants ──► Controller ──► Service  (MỘT giao dịch)
                                                         │
   ┌───────────────┬───────────────────────┬─────────────┴────────────────────┐
   ▼               ▼                       ▼                                  ▼
tenants        tenant_configs        roles (3) +                   gán từng chủ nhà xe:
(1 dòng)       (1 dòng)              role_permissions (22)         users (tạo mới hoặc dùng lại)
                                                                   + user_tenants (TENANT_ADMIN)
```

---

## 3. CÁC QUYẾT ĐỊNH ĐÃ CHỐT

| Quyết định | Lựa chọn | Lý do |
| :--- | :--- | :--- |
| Xóa nhà xe | **Xóa mềm**: `DELETE` chỉ đặt `is_active = false` | Gần như mọi khóa ngoại tới `tenants` là `ON DELETE CASCADE`: xóa thật mất sạch chi nhánh, xe, khách, đơn, không hoàn tác |
| Tạo nhà xe | Kèm cấu hình mặc định và **3 vai trò mặc định**, tất cả trong **một giao dịch** | Nhà xe dùng được ngay; lỗi giữa chừng không để lại dữ liệu dở |
| Chủ nhà xe | **Một hoặc nhiều**, gán ngay khi tạo | Theo prototype; DB cho phép nhiều chủ |
| Email đã có tài khoản | **Chỉ liên kết**, không đổi mật khẩu, không đổi họ tên | Tránh chiếm quyền tài khoản của nhà xe khác |
| Cấu hình vận hành | **Chủ nhà xe tự sửa**, Super Admin không sửa | Module này chỉ **tạo** dòng mặc định |
| Super Admin xem nhân viên | **Chỉ chủ nhà xe**; nhân viên và Sale chỉ là con số | Dữ liệu người làm thuộc về nhà xe |
| Kiểm tra quyền gọi API tenant | Cờ Super Admin (`@PreAuthorize("hasRole('SUPER_ADMIN')")` cấp lớp), **không dùng bảng `roles`** | Super Admin không thuộc nhà xe nào |

---

## 4. KẾ HOẠCH THEO GIAI ĐOẠN

Mỗi giai đoạn dựa trên giai đoạn trước. **Làm từ trong ra:** entity, DTO, repository, service, rồi controller. Sau mỗi giai đoạn chạy `mvn clean compile`, và chạy ứng dụng khi có thay đổi entity hay truy vấn.

### Giai đoạn 0: Chuẩn bị
- Làm trên nhánh riêng tách từ `main`.
- Chạy PostgreSQL (container `postgres-car-rental`) và backend bằng Maven. Nếu cổng 8080 bị chiếm, chạy với `--server.port=8082`.
- Sau khi đổi nhánh chạy `mvn clean` trước, để tránh lỗi Flyway trùng phiên bản.
- Đọc `AuthController` và `AuthServiceImpl` để nắm cách làm: Controller mỏng, Service xử lý, lỗi ném bằng `AppException`.
- **Lưu ý `ddl-auto: validate`:** Hibernate so entity với DB lúc khởi động, sai tên cột hoặc kiểu là ứng dụng không chạy. DB là nguồn sự thật, entity phải theo DB.

### Giai đoạn 1: Entity
**Việc cần làm:**
- `Tenant`: thêm `@Setter`; gắn `@CreationTimestamp` cho `createdAt` và `@UpdateTimestamp` cho `updatedAt` (không có thì Hibernate lưu `null` đè giá trị mặc định của DB).
- Tạo `TenantConfig` cho bảng `tenant_configs` (khóa chính `tenant_id`, quan hệ 1-1).

| Trường Java | Kiểu | Cột DB | Mặc định |
| :--- | :--- | :--- | :--- |
| `tenantId` | `UUID` | `tenant_id` | `@Id`, không `@GeneratedValue` |
| `holdTimeoutMinutes` | `Integer` | `hold_timeout_minutes` | 30 |
| `lateHourlyPrice` | `BigDecimal` | `late_hourly_price` | 100000 |
| `defaultCommissionRate` | `BigDecimal` | `default_commission_rate` | 5.00 |
| `createdAt`, `updatedAt` | `Instant` | `created_at`, `updated_at` | tự gán |

**Chú ý:** tiền và tỉ lệ dùng `BigDecimal`, không dùng `Double`; gõ đúng tên cột; không dùng `@Data` trên entity.
**Xong khi:** ứng dụng khởi động không lỗi `validate`.

### Giai đoạn 2: DTO
**Việc cần làm:** tách `dto/request` và `dto/response`.

| DTO | Vai trò |
| :--- | :--- |
| `CreateTenantRequestDTO` | Tạo nhà xe: thông tin nhà xe và danh sách `admins` (không rỗng, có `@Valid`) |
| `AdminAssignmentRequestDTO` | Một chủ nhà xe: `email`, `fullName`, `password`. Dùng lại cho API thêm chủ |
| `UpdateTenantRequestDTO` | Sửa nhà xe: `name`, `domain`, `planTier` bắt buộc; bốn trường còn lại tùy chọn |
| `UpdateTenantStatusRequestDTO` | `isActive` (`Boolean`, `@NotNull`) |
| `TenantResponseDTO` | Thông tin một nhà xe |
| `CreateTenantResponseDTO` | Nhà xe vừa tạo và kết quả từng chủ (`email`, `accountCreated`) |
| `TenantAdminResponseDTO` | Một chủ nhà xe khi xem danh sách |

**Chú ý:**
- DTO chỉ kiểm tra **hình dạng** (không hỏi DB). Giới hạn `@Size` khớp độ rộng cột DB. Chi tiết từng trường xem [đặc tả](2026-10-19-tenant-api-spec.md).
- `fullName` và `password` **không** đánh `@NotBlank`: chỉ bắt buộc khi email chưa có tài khoản, Service kiểm tra điều kiện này.
- Mật khẩu tối đa 72 ký tự vì BCrypt chỉ dùng 72 byte đầu.
- **Thiếu `@Valid` trên danh sách `admins`** thì Spring không kiểm tra từng phần tử.
- `@NotBlank` chỉ cho `String`; enum, `Boolean`, `UUID` dùng `@NotNull`.
- **Không trả mật khẩu**, kể cả bản băm, trong bất kỳ phản hồi nào.

**Xong khi:** `mvn clean compile` qua.

### Giai đoạn 3: Repository và DefaultRole
**Việc cần làm:**
- `TenantRepository`: thêm `existsByDomain`, `existsByDomainAndIdNot`, và truy vấn `search(keywordPattern, isActive, pageable)` (tìm theo tên hoặc domain, `isActive` rỗng thì không lọc).
- `TenantConfigRepository` (mới).
- `DefaultRole` (enum, mới): định nghĩa 3 default role, mỗi default role mang `code`, `name`, `description`, danh sách mã quyền.
- `TenantRoleRepository` (mới, **không sửa** `RoleRepository` của module account): `insertSystemRole`, `grantAllPermissions`, `grantPermissions` (SQL thuần dạng `INSERT ... SELECT`) và `findByTenantIdAndCode`.
- `TenantAdminQueryRepository` (mới): truy vấn nối `users`, `user_tenants`, `roles` để xem chủ nhà xe.

**`DefaultRole`: 3 default role** (phải khớp migration V5):

| Vai trò | Quyền | Cách cấp |
| :--- | :-: | :--- |
| `TENANT_ADMIN` | 11 (tất cả) | `grantAllPermissions`. Danh sách quyền để rỗng trong `DefaultRole` nghĩa là "tất cả" |
| `STAFF` | 7: `booking:read, create, confirm_deposit, handover, return, cancel` và `vehicle:read` | `grantPermissions` |
| `SALE` | 4: `booking:read, create, cancel` và `vehicle:read` | `grantPermissions` |

Mỗi nhà xe mới sinh **3 dòng `roles`** và **11 + 7 + 4 = 22 dòng `role_permissions`**.

**Điểm khó:**
- **Mọi câu SQL thuần phải có `r.tenant_id = :tenantId`.** Thiếu thì `r.code = 'STAFF'` khớp STAFF của mọi nhà xe và gán quyền nhầm sang nhà xe khác.
- `@Modifying` bắt buộc cho `INSERT/UPDATE/DELETE` dùng `@Query`.
- **Nhà xe vừa lưu phải nằm trong DB trước khi câu SQL thuần chạy** (khóa ngoại `roles.tenant_id`). Hibernate thường tự đẩy dữ liệu chờ xuống DB trước câu SQL thuần; nếu gặp lỗi khóa ngoại thì dùng `saveAndFlush` hoặc `@Modifying(flushAutomatically = true)`.
- `IN (:permissionCodes)` với **danh sách rỗng gây lỗi SQL**, nên `TENANT_ADMIN` phải đi theo `grantAllPermissions`.
- `keywordPattern` luôn là một chuỗi (`"%"` khi không tìm), **không bao giờ `null`**, vì PostgreSQL báo lỗi hàm `lower` khi gặp `null`.
- **Không dùng `RoleRepository.findByCode`** (nhiều nhà xe cùng `code`, nổ `IncorrectResultSize`). Dùng `findByTenantIdAndCode`.
- Các entity module account chỉ lưu id, không có `@ManyToOne`, nên truy vấn nối dùng `JOIN ... ON` giữa các entity không liên kết; trả `Object[]` để `joinedAt` giữ kiểu `Instant`.

**Xong khi:** ứng dụng khởi động (Spring phân tích được truy vấn `search`).

### Giai đoạn 4: Service
`TenantService` (interface) và `TenantServiceImpl`. Cả lớp chạy trong **một giao dịch** (`@Transactional`), hàm chỉ đọc đánh dấu `readOnly`. Lỗi nghiệp vụ ném `AppException(ErrorCode, ...)`. Thêm vào `ErrorCode`: `TENANT_DOMAIN_ALREADY_EXISTS` và `TENANT_ADMIN_ALREADY_ASSIGNED` (đều 409).

**Phân vai kiểm tra:** DTO kiểm hình dạng; Service kiểm luật cần DB hoặc luật riêng; DB là chốt chặn cuối (unique, khóa ngoại).

| Hàm | Việc làm |
| :--- | :--- |
| `createTenant` | Xem thứ tự bên dưới |
| `getTenantById` | Không thấy thì 404 |
| `listTenants` | Từ khóa trống thành `"%"`, có thì chữ thường và bọc `%`; gọi `search`; map sang DTO |
| `updateTenant` | Xem quy tắc bên dưới. Không đụng `tenant_configs` |
| `changeStatus` | Đã đúng trạng thái thì trả luôn, không ghi (idempotent) |
| `deleteTenant` | Gọi `changeStatus(false)`, **không gọi `repository.delete`** |
| `addTenantAdmin` | Nhà xe phải tồn tại (404) và đang hoạt động (400); lấy vai trò `TENANT_ADMIN` của nhà xe đó; kiểm tra và gán chủ |
| `listTenantAdmins` | Nhà xe phải tồn tại; trả các thành viên có vai trò `TENANT_ADMIN`, xếp theo ngày tham gia |

**`createTenant`: kiểm tra hết rồi mới ghi.**
1. *Kiểm tra, chưa ghi gì:* domain (làm gọn, đã có người dùng thì 409); `settings` phải là JSON hợp lệ; **cả danh sách chủ** (ít nhất 1 người, email không trùng nhau, từng người qua `prepareAdmin`).
2. *Ghi, theo thứ tự:* lưu `Tenant` (`.isActive(true)`, gói mặc định `FREE`) → lưu `TenantConfig` (30, 100000, 5.00 truyền tường minh) → sinh 3 vai trò và 22 quyền → lấy lại vai trò `TENANT_ADMIN` → gán từng chủ.
3. Ghi log (người gọi, nhà xe, số chủ, **không log mật khẩu**) và trả `CreateTenantResponseDTO`.

**Gán chủ nhà xe** (`prepareAdmin` để kiểm tra, `assignTenantAdmin` để ghi, dùng chung cho tạo nhà xe và thêm chủ):

| Tình huống | Kết quả |
| :--- | :--- |
| Email mới, đủ họ tên và mật khẩu | Tạo tài khoản (BCrypt, `isActive(true)`, `isSuperAdmin(false)`) rồi liên kết |
| Email mới, thiếu họ tên hoặc mật khẩu | 400 |
| Email có sẵn, đang hoạt động, chưa thuộc nhà xe | **Chỉ liên kết**, bỏ qua họ tên và mật khẩu gửi lên |
| Email có sẵn nhưng bị khóa, hoặc là Super Admin | 400 |
| Đã là chủ nhà xe này | 409 |
| Đang là nhân viên của nhà xe này | 400 (chưa hỗ trợ nâng quyền) |
| Hai email trùng nhau trong danh sách (không phân biệt hoa thường) | 400 |

**`updateTenant`:** `name`, `domain`, `planTier` luôn ghi đè. Bốn trường tùy chọn (`logoUrl`, `contactEmail`, `contactPhone`, `settings`): **không gửi hoặc `null` thì giữ giá trị cũ**; chuỗi rỗng thì xóa (`settings` về `{}`); có giá trị thì ghi đè. Domain: không đổi thì không kiểm tra trùng; đổi thì không được trùng nhà xe **khác**.

**Chú ý:**
- Quên `.isActive(true)` khi dựng `Tenant` bằng builder thì nhà xe mới bị khóa (`boolean` mặc định `false`).
- Quên `isActive(true)` và `isSuperAdmin(false)` khi dựng `User` thì lưu `null`, về sau đăng nhập gặp `NullPointerException`.
- Quên `joinedAt = Instant.now()` khi dựng `UserTenant` thì lưu `null`.
- Mã hóa mật khẩu bằng `PasswordEncoder`, không lưu thô.

**Xong khi:** chạy được từng hàm qua test đơn vị (xem Giai đoạn 6).

### Giai đoạn 5: Controller
`TenantController`, `@RequestMapping("/api/v1/tenants")`, **`@PreAuthorize("hasRole('SUPER_ADMIN')")` ở cấp lớp**. Lấy người gọi bằng `@AuthenticationPrincipal UserPrincipal`. Trả kết quả bằng `ApiResponse.success(data, "...")`, làm giống `AuthController`; nhớ `@Valid @RequestBody` và `@PathVariable UUID`.

| API | Method | Mã thành công |
| :--- | :---: | :---: |
| `/api/v1/tenants` (tạo) | `POST` | 201 |
| `/api/v1/tenants` (danh sách: `keyword`, `isActive`, `page`, `size`) | `GET` | 200 |
| `/api/v1/tenants/{id}` | `GET` | 200 |
| `/api/v1/tenants/{id}` | `PUT` | 200 |
| `/api/v1/tenants/{id}/status` | `PATCH` | 200 |
| `/api/v1/tenants/{id}` (xóa mềm) | `DELETE` | 200 |
| `/api/v1/tenants/{id}/admins` | `GET` | 200 |
| `/api/v1/tenants/{id}/admins` | `POST` | 201 |

**Chú ý:**
- **Phân trang tự dựng, không nhận `sort` từ client:** `page` âm thành 0, `size` kẹp từ 1 đến 100, luôn sắp theo `createdAt` giảm dần (tham số `sort` đi thẳng vào câu truy vấn, sai tên trường gây lỗi). Trả `PagedModel`, không trả `Page`.
- **Hai API gán người vào nhà xe tách riêng:** API gán chủ (module này, Super Admin gọi, nhà xe lấy từ URL, chỉ gán `TENANT_ADMIN`) khác API tạo nhân viên (module account, chủ nhà xe gọi, nhà xe lấy từ token chống IDOR, chỉ gán `STAFF` hoặc `SALE`). Không gộp một endpoint để khỏi rẽ nhánh theo vai trò và leo quyền. **Phần lõi** "tìm hoặc tạo tài khoản rồi liên kết" viết một chỗ để quy tắc không lệch.
- Spring kiểm tra **hình dạng body trước quyền**: người không có quyền gửi body sai nhận 400 chứ chưa phải 403.

**Xong khi:** gọi được cả 8 API, người không phải Super Admin nhận 403.

### Giai đoạn 6: Kiểm thử
**Cần chuẩn bị:** DB mới chạy migration **chưa có Super Admin** (README ghi tài khoản nhưng DB không có). Test thủ công thì bật cờ `is_super_admin` bằng SQL cho **một tài khoản riêng** (không để tài khoản đó vừa là Super Admin vừa thuộc nhà xe, vì nhánh Super Admin ở `login` chạy trước). Test tự động thì tự thêm một tài khoản trong lúc chuẩn bị.

**Hai loại test bổ trợ nhau:**
- **Test đơn vị** cho `TenantServiceImpl` (Mockito, repository giả, không cần DB): mỗi kịch bản một test, tên mô tả, bố cục Arrange, Act, Assert, không vòng lặp hay `if` trong test.
- **Test tích hợp** (Testcontainers): chạy ứng dụng thật trên PostgreSQL thật do container dựng, gọi API qua HTTP rồi đếm dòng trong DB. Cần Docker; không đụng DB dev.

**Kịch bản cần phủ:**

| Nhóm | Kịch bản |
| :--- | :--- |
| Tạo nhà xe | Thành công (đủ 1 nhà xe, 1 cấu hình, 3 vai trò, 22 quyền, đúng số chủ); trùng domain; danh sách chủ rỗng, email trùng, tài khoản khóa hoặc Super Admin, thiếu họ tên hoặc mật khẩu, `settings` sai JSON; **một chủ sai thì không ghi gì** |
| Chủ nhà xe | Email mới tạo tài khoản; email có sẵn chỉ liên kết và **không đổi mật khẩu, họ tên**; chủ mới đăng nhập được với đủ quyền; thêm trùng, thêm khi nhà xe khóa, thêm người đang là nhân viên |
| Sửa | Giữ cũ khi không gửi, xóa khi gửi chuỗi rỗng, ghi đè khi có giá trị; domain trùng nhà xe khác; `created_at` giữ, `updated_at` đổi |
| Khóa, xóa | Khóa thì đăng nhập bị từ chối; mở khóa dùng lại được; xóa hai lần vẫn thành công và dữ liệu liên quan còn |
| Danh sách | Tìm theo từ khóa, lọc `isActive`, giới hạn `size` ở 100, 404 khi không có nhà xe |
| Phân quyền | **Cả 8 API:** không token 401, không phải Super Admin 403; chủ nhà xe này không khóa được nhà xe khác |

**Kiểm tra test có giá trị:** cố ý phá code (bỏ `@PreAuthorize`, bỏ một quyền của `STAFF`, bỏ điều kiện giữ trường cũ...) rồi xem test có báo đỏ không.
**Xong khi:** `mvn clean test` xanh và ứng dụng khởi động không lỗi Flyway.

---

## 5. PHỤ THUỘC NGƯỜI KHÁC

1. **Tài khoản Super Admin:** DB chưa có. Hỏi người làm auth xem seed ở migration nào. Lâu dài cần cách tạo chính thức (migration hoặc lệnh khởi tạo, mật khẩu từ biến môi trường).
2. **Khóa nhà xe có thể chưa có tác dụng ngay với người đang đăng nhập:** bộ lọc JWT và `switch-branch` (module auth) cần kiểm tra nhà xe còn hoạt động. Khóa **tài khoản** (`users.is_active`) thì có hiệu lực ngay. Báo người làm auth.
3. **`GlobalExceptionHandler` có thể trả 500 cho tham số sai kiểu** (UUID sai dạng, boolean sai kiểu), lẽ ra 400. Cần xác nhận và báo người làm module chung.
4. **Dùng chung entity và repository của module account** (`User`, `UserTenant`, `UserRepository`, `UserTenantRepository`): chỉ **dùng**, không sửa; báo họ nếu đổi tên trường hay hàm.
5. **Phần lõi "tìm hoặc tạo tài khoản rồi liên kết"** dùng chung với API tạo nhân viên: khi module account làm, hai bên thống nhất rút ra một lớp chung.
6. **Cần có ràng buộc DB chặn đặt trùng giờ cùng một xe** (ràng buộc loại trừ trên `vehicle_id` và khoảng `pickup_time` đến `return_time`) trước khi làm module booking; cần migration V7 thống nhất với nhóm.
7. **Hai request tạo cùng một domain cùng lúc:** cả hai cùng qua bước kiểm tra, ràng buộc unique của DB chặn một bên. Cân nhắc bắt lỗi trùng ràng buộc và trả 409 thay vì 500 (khả năng thấp vì chỉ Super Admin tạo nhà xe).

## 6. VIỆC CẦN XÁC NHẬN VỚI NHÓM

1. **Module nào sinh 3 vai trò mặc định?** Kế hoạch này để module tenant sinh (cùng giao dịch), module account chỉ gán. `Database-Schema.md` mục 2.4 còn gợi ý vai trò dùng chung, trong khi entity `Role` khai báo `tenantId` không null, nên cần xác nhận để không lệch.
2. **Mật khẩu chủ nhà xe:** Super Admin đặt mật khẩu tạm (như prototype) hay gửi email mời? `users` không có cột "bắt đổi mật khẩu lần đầu".
3. **Super Admin có được làm chủ nhà xe không?** Nhánh Super Admin ở `login` chạy trước nên tài khoản đó không vào được nhà xe. Khuyên từ chối.
4. **`BRANCH_MANAGER`** còn dùng không? Docs nhắc nhưng DB và `onboarding.md` không có.
5. **Gỡ hoặc đổi vai trò chủ nhà xe:** xóa dòng `user_tenants` làm đơn cũ mất `created_by`, `handover_by`, `returned_by` (`ON DELETE SET NULL`) và xóa phân công chi nhánh (`ON DELETE CASCADE`). Cần chốt cách xử lý, cân nhắc thêm cột `user_tenants.status`. Luôn phải còn ít nhất một chủ.
6. **Chi nhánh của SALE:** `onboarding.md` mục 9.2 ghi SALE không gán chi nhánh, nhưng mục 2.1 và dữ liệu thật cho thấy phải gán. Cần cho API tạo nhân viên.
