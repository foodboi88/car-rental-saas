# 📘 CẨM NANG TỰ CODE TỪNG BƯỚC: MODULE TENANT (THÊM, SỬA, XÓA MỀM NHÀ XE, GÁN CHỦ NHÀ XE)

> **Dự án:** Car Rental SaaS
> **Module:** `tenant`
> **Phạm vi:** Super Admin tạo nhà xe (kèm cấu hình mặc định, 3 nhóm quyền mặc định và **một hoặc nhiều chủ nhà xe**), xem, sửa, khóa/mở khóa, xóa mềm nhà xe, và thêm/xem chủ nhà xe.
> **Nguồn thiết kế:** prototype (màn "Thêm nhà xe" và "Chi tiết nhà xe", phiên bản 10 trở lên), `onboarding.md` mục 2.1 và 9.2, `Database-Schema.md`, và dữ liệu thật trong DB.
> **Cách dùng:** Tài liệu mô tả luồng logic, tên class, thuộc tính, annotation và các bẫy đã gặp để bạn tự viết mã nguồn. Cùng khuôn với [guide module auth](../auth-login/2026-08-28-auth-jwt-implementation-guide.md).

---

## 🏛️ BỨC TRANH TOÀN CẢNH

Sản phẩm là phần mềm cho thuê xe **bán cho các nhà xe**. Mỗi nhà xe là một khách hàng của nền tảng, gọi là **tenant**. Có hai tầng người dùng:

| Tầng | Ai | Làm gì | Quyền đến từ đâu |
| :--- | :--- | :--- | :--- |
| **Nền tảng** (đội vận hành SaaS) | **Super Admin** | Tạo, sửa, khóa nhà xe; gán chủ nhà xe; quản lý loại xe chung | Cờ `users.is_super_admin = true` |
| **Nhà xe** (khách hàng) | Chủ nhà xe (`TENANT_ADMIN`), nhân viên (`STAFF`), Sale (`SALE`) | Quản lý chi nhánh, xe, khách, đơn, **cấu hình vận hành** của riêng nhà xe mình | Bảng `roles` và `role_permissions` của từng tenant |

Module tenant là **công cụ của tầng nền tảng**, chỉ Super Admin dùng.

### Vì sao "tạo tenant" phải làm nhiều việc cùng lúc
Đã kiểm chứng trên code và DB:
- DB **không có trigger** tự sinh dữ liệu phụ, và **không có role dùng chung** (`roles` có `tenant_id` rỗng: 0 dòng). Hai nhà xe seed có sẵn role và cấu hình là nhờ migration V5 chèn tay.
- `user_tenants.role_id` là `NOT NULL`, khóa ngoại tới `roles`: muốn gán người vào tenant thì tenant phải **có sẵn role**.
- API tạo nhân viên (`onboarding.md` mục 9.2) chỉ cho `TENANT_ADMIN` của tenant gọi và **cấm tạo `TENANT_ADMIN`**. Tenant mới chưa có chủ thì không ai gọi được API đó (vòng luẩn quẩn). Vì vậy **chủ nhà xe đầu tiên phải do Super Admin tạo cùng lúc với tenant**, đúng như màn "Thêm nhà xe" của prototype.
- `user_tenants` có khóa chính `(user_id, tenant_id)`: một người chỉ **một role mỗi nhà xe**. Không có ràng buộc "mỗi tenant một TENANT_ADMIN", nên **một nhà xe có nhiều chủ** là hợp lệ. Ngược lại một tài khoản có thể thuộc **nhiều nhà xe** với role khác nhau.

```
[ Super Admin ] ── POST /api/v1/tenants ──► TenantController ──► TenantService  (MỘT transaction)
                                                                    │
     ┌───────────────────┬────────────────────────┬─────────────────┴────────────────────┐
     ▼                   ▼                        ▼                                      ▼
tenants (1 dòng)   tenant_configs (1 dòng)  roles (3) + role_permissions (22)   assignTenantAdmin × số chủ
"hồ sơ nhà xe"     "luật vận hành mặc định" "3 chức vụ mặc định"                 users (tạo mới/dùng lại) + user_tenants (TENANT_ADMIN)
```

### Các quyết định đã chốt
| Quyết định | Lựa chọn | Lý do |
| :--- | :--- | :--- |
| Xóa tenant | **Xóa mềm**: `DELETE` chỉ đặt `is_active = false` | Hầu hết khóa ngoại tới `tenants` là `ON DELETE CASCADE`: xóa cứng mất toàn bộ chi nhánh, xe, khách, đơn thuê, không hoàn tác |
| Tạo tenant | Kèm cấu hình mặc định và **3 role mặc định** | Tenant dùng được ngay, `user_tenants.role_id` bắt buộc có role |
| Chủ nhà xe | **Một hoặc nhiều** (không giới hạn số lượng), gán ngay khi tạo | Theo prototype; DB cho phép nhiều admin |
| Email đã có tài khoản | **Chỉ liên kết**, không đổi mật khẩu, không đổi họ tên | Khớp quy tắc `onboarding.md` mục 9.2; tránh chiếm quyền tài khoản của nhà xe khác |
| Cấu hình vận hành (`tenant_configs`) | **Chủ nhà xe tự sửa**, Super Admin không sửa | Quyết định của bạn. Module này chỉ **tạo** dòng mặc định, không có API sửa cấu hình |
| Super Admin xem nhân viên | **Chỉ chủ nhà xe**; nhân viên và Sale chỉ là con số | Dữ liệu người làm thuộc nhà xe; API chỉ có xem chủ nhà xe |

### Role có liên quan gì ở đây
- **Kiểm tra quyền gọi API tenant: không dùng bảng `roles`.** Quyền Super Admin lấy từ cờ `is_super_admin` (xem Giai đoạn 5).
- Bảng `roles` liên quan tới **nhân viên bên trong nhà xe** và tới việc gán chủ nhà xe (`user_tenants.role_id`).

---

## 🗺️ MỤC LỤC

- [Giai đoạn 0: Chuẩn bị](#giai-đoạn-0-chuẩn-bị)
- [Giai đoạn 1: Entity](#giai-đoạn-1-sửa-entity-thêm-tenantconfig)
- [Giai đoạn 2: DTO](#giai-đoạn-2-dto)
- [Giai đoạn 3: Repository và bản mẫu role](#giai-đoạn-3-repository-và-bản-mẫu-role)
- [Giai đoạn 4: Service](#giai-đoạn-4-service)
- [Giai đoạn 5: Controller](#giai-đoạn-5-controller)
- [Giai đoạn 6: Kiểm thử](#giai-đoạn-6-kiểm-thử)
- [Việc cần xác nhận với nhóm](#-việc-cần-xác-nhận-với-nhóm)
- [Phụ thuộc người khác](#-phụ-thuộc-người-khác)
- [Checklist lưu ý sống còn](#️-checklist-lưu-ý-sống-còn)
- [Ngoài phạm vi](#-ngoài-phạm-vi)

---

## 🧰 GIAI ĐOẠN 0: CHUẨN BỊ

1. Làm trên nhánh riêng (tách từ `main`), ví dụ `feature/huynq7-tenant`.
2. Chạy Postgres (container Docker `postgres-car-rental`) và backend.
   - Backend chạy bằng Maven trên máy, không chạy trong Docker.
   - Cổng mặc định 8080. Nếu cổng đang bị chiếm: `mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8082"`.
   - Sau khi đổi nhánh, chạy `mvn clean` trước để tránh lỗi Flyway "Found more than one migration with version 6".
   - Sau khi khởi động lại máy: bật Docker Desktop, `docker start postgres-car-rental`, rồi chạy backend. Dữ liệu còn nguyên (nằm trong volume của container).
3. Đọc lại `AuthController` và `AuthServiceImpl.selectTenant`, `login` để quen khuôn: Controller mỏng, Service xử lý, lỗi ném bằng `AppException`.

**Hiện trạng module ban đầu:** chỉ có `entity/Tenant.java` và `repository/TenantRepository.java`. Các thư mục `controller`, `service`, `dto` trống.

> **Về `ddl-auto: validate`:** lúc khởi động Hibernate so entity với DB. Sai tên cột hoặc sai kiểu là app **không chạy** (`missing column`, `wrong column type`). Đây là cách kiểm tra nhanh nhất mỗi giai đoạn. DB là nguồn sự thật (Flyway quản lý), entity phải theo DB, không làm ngược lại. Bảng chưa có entity thì Hibernate bỏ qua, không lỗi.

---

## 🗄️ GIAI ĐOẠN 1: SỬA ENTITY, THÊM TENANTCONFIG

### 1. Sửa `Tenant.java` (`tenant/entity/Tenant.java`)
- Thêm `@Setter`.
- Gắn `@CreationTimestamp` (kèm `updatable = false`) cho `createdAt` và `@UpdateTimestamp` cho `updatedAt`. Thiếu thì Hibernate insert `null` đè lên default của DB.
- **Bẫy `@Builder`:** `isActive` là `boolean` nguyên thủy, mặc định `false`. Quên `.isActive(true)` thì tenant mới bị khóa ngay.
- Tên Lombok: field `isActive` kiểu `boolean` sinh `isActive()` và `setActive(...)`; builder là `.isActive(...)`.
- Cột `plan_tier` là `NOT NULL`, mặc định `PlanTierTenant.FREE`.

### 2. Tạo `TenantConfig.java` (`tenant/entity/TenantConfig.java`)
- **Bảng:** `tenant_configs`, quan hệ 1-1 với `tenants`. Luật vận hành riêng của từng nhà xe: thời gian giữ xe, phí trễ giờ, hoa hồng mặc định.
- **Vai trò trong module này:** chỉ để **tạo** dòng mặc định khi tạo tenant. Việc **sửa** cấu hình là của chủ nhà xe (module khác).
- **Annotations:** `@Entity`, `@Table(name = "tenant_configs")`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`. Không dùng `@Data` trên entity.

| Field Java | Kiểu | Cột DB | Ghi chú |
| :--- | :--- | :--- | :--- |
| `tenantId` | `UUID` | `tenant_id` | `@Id`, **không** `@GeneratedValue` |
| `holdTimeoutMinutes` | `Integer` | `hold_timeout_minutes` | mặc định DB 30 |
| `lateHourlyPrice` | `BigDecimal` | `late_hourly_price` | `numeric(12,2)`, mặc định 100000 |
| `defaultCommissionRate` | `BigDecimal` | `default_commission_rate` | `numeric(5,2)`, mặc định 5.00 |
| `createdAt` | `Instant` | `created_at` | `@CreationTimestamp` |
| `updatedAt` | `Instant` | `updated_at` | `@UpdateTimestamp` |

### Lỗi đã gặp ở giai đoạn này
| Triệu chứng | Nguyên nhân |
| :--- | :--- |
| `Duplicate method TenantConfig()` | Class chưa có field: `@NoArgsConstructor` và `@AllArgsConstructor` cùng sinh constructor rỗng |
| `missing column [hold_timeout_miniutes]` | Gõ sai tên cột |
| `wrong column type ... found [numeric], expecting [float(53)]` | Dùng `Double`. Tiền và tỉ lệ phải dùng `BigDecimal` |
| `Port 8080 was already in use` | Cổng đang bị chiếm, dùng `--server.port=8082` |

**Kiểm tra:** chạy app, thấy `Started CarRentalBackendApplication`, không có `Schema validation`.

---

## 📦 GIAI ĐOẠN 2: DTO

> Tách `request/` và `response/` như module auth. Annotations chung: `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`.

### Request (`tenant/dto/request/`)

| File | Thuộc tính và validate |
| :--- | :--- |
| `AdminAssignmentRequestDTO` (một chủ nhà xe) | `email` (`@NotBlank`, `@Email`, `@Size(max = 255)`); `fullName` (`@Size(max = 255)`); `password` (`@Pattern` `^(?=.*[A-Za-z])(?=.*\d).{8,72}$`, báo "ít nhất 8 ký tự, gồm chữ và số") |
| `CreateTenantRequestDTO` | **Nhà xe:** `name` (`@NotBlank`, `@Size(max = 255)`); `domain` (`@NotBlank`, `@Size(max = 255)`, `@Pattern` `^[a-z0-9]+([.-][a-z0-9]+)*$`); `planTier` (tùy chọn, thiếu thì FREE); `logoUrl` (`@Size(max = 500)`); `contactEmail` (`@Email`); `contactPhone` (`@Size(max = 20)`); `settings` (chuỗi JSON, tùy chọn). **Chủ nhà xe:** `admins` (`List<AdminAssignmentRequestDTO>`, `@NotEmpty`, `@Valid`; không giới hạn số lượng) |
| `UpdateTenantRequestDTO` | Các trường nhà xe như trên, `planTier` là `@NotNull`. Không có phần chủ nhà xe |
| `UpdateTenantStatusRequestDTO` | `isActive` kiểu `Boolean`, `@NotNull` |

Thêm chủ nhà xe về sau (`POST /tenants/{id}/admins`) dùng lại chính `AdminAssignmentRequestDTO`.

**`fullName` và `password` không đánh `@NotBlank`**, vì chỉ bắt buộc khi email chưa có tài khoản. `@Size` và `@Pattern` bỏ qua giá trị `null`, nên khi không gửi thì không lỗi; Service kiểm tra điều kiện "bắt buộc nếu là email mới". Giới hạn 72 vì BCrypt chỉ dùng tối đa 72 byte của mật khẩu. **Đừng quên `@Valid` trên danh sách** để Spring kiểm tra từng phần tử.

### Response (`tenant/dto/response/`)
- `TenantResponseDTO`: `id`, `name`, `domain`, `planTier`, `logoUrl`, `contactEmail`, `contactPhone`, `settings`, `isActive`, `createdAt`, `updatedAt`.
- `CreateTenantResponseDTO` (cho `POST /tenants`): `tenant` (`TenantResponseDTO`), `admins` (danh sách `{email, accountCreated}`; `accountCreated = true` nếu tạo tài khoản mới, `false` nếu chỉ liên kết). **Không bao giờ trả mật khẩu**, kể cả dạng băm.
- `TenantAdminResponseDTO` (cho xem chủ nhà xe): `userId`, `email`, `fullName`, `isActive`, `joinedAt`.
- Danh sách nhà xe: trả `Page<TenantResponseDTO>`.

### Lưu ý
- `@NotBlank` chỉ dùng cho `String`. Enum, `Boolean`, `UUID` dùng `@NotNull`.
- DTO dùng `Boolean` (hoa) để phân biệt "không gửi" với `false`; entity `Tenant` là `boolean` nguyên thủy, khi map phải gọi đúng tên getter/setter.
- `planTier` trong JSON theo **tên enum** (`"FREE"`, `"BASIC"`, `"PRO"`, `"ENTERPRISE"`). Suy luận từ mặc định của Jackson, xác nhận khi test Swagger.
- Giới hạn `@Size` khớp cột DB: `name`, `domain` `varchar(255)` NOT NULL; `logo_url` `varchar(500)`; `contact_phone` `varchar(20)`; `contact_email` `varchar(255)`.

**Kiểm tra:** `mvn clean compile`. Nhớ dòng `package` khớp thư mục.

---

## 🗃️ GIAI ĐOẠN 3: REPOSITORY VÀ BẢN MẪU ROLE

### 1. `TenantRepository` (thêm 3 method, giữ nguyên query cũ)
```java
boolean existsByDomain(String domain);

boolean existsByDomainAndIdNot(String domain, UUID id);

@Query("""
  SELECT t FROM Tenant t
  WHERE (LOWER(t.name) LIKE :keywordPattern OR LOWER(t.domain) LIKE :keywordPattern)
    AND (:isActive IS NULL OR t.isActive = :isActive)
""")
Page<Tenant> search(
  @Param("keywordPattern") String keywordPattern,
  @Param("isActive") Boolean isActive,
  Pageable pageable
);
```
- `keywordPattern` luôn là chuỗi viết thường dạng `"%abc%"`, và `"%"` khi không tìm. **Không bao giờ `null`** (PostgreSQL lỗi `function lower(bytea) does not exist`). `isActive` được phép `null`.
- Tên thuộc tính trong HQL là **tên field Java** (`t.isActive`), không phải tên cột.
- `:isActive IS NULL OR ...` chạy đúng với Hibernate 7 và PostgreSQL khi `isActive = null` (đã kiểm chứng qua HTTP thật: liệt kê không lọc, lọc `true`, lọc `false`). Không cần `CAST`.

### 2. `TenantConfigRepository` (mới)
```java
public interface TenantConfigRepository extends JpaRepository<TenantConfig, UUID> {
}
```

### 3. Bản mẫu 3 role: `tenant/constant/DefaultRole.java` (mới)
```java
package com.carrental.car_rental_backend.tenant.constant;

import java.util.List;

import com.carrental.car_rental_backend.common.constant.RoleConstant;

import lombok.Getter;

@Getter
public enum DefaultRole {
  // permissionCodes rỗng nghĩa là được toàn bộ quyền trong bảng permissions
  TENANT_ADMIN(RoleConstant.TENANT_ADMIN, "Chủ doanh nghiệp", "Toàn quyền quản lý nhà xe", List.of()),
  STAFF("STAFF", "Nhân viên nghiệp vụ", "Nhân viên điều hành và giao nhận xe tại chi nhánh",
    List.of("booking:read", "booking:create", "booking:confirm_deposit",
            "booking:handover", "booking:return", "booking:cancel", "vehicle:read")),
  SALE("SALE", "Cộng tác viên kinh doanh", "Cộng tác viên tạo đơn và nhận hoa hồng",
    List.of("booking:read", "booking:create", "booking:cancel", "vehicle:read"));

  private final String code;
  private final String name;
  private final String description;
  private final List<String> permissionCodes;

  DefaultRole(String code, String name, String description, List<String> permissionCodes) {
    this.code = code;
    this.name = name;
    this.description = description;
    this.permissionCodes = permissionCodes;
  }

  public boolean grantsAllPermissions() {
    return this.permissionCodes.isEmpty();
  }
}
```
Dữ liệu này **phải khớp migration V5** (11, 7 và 4 quyền). Nếu sau này nhóm đổi quyền mặc định trong một migration mới thì sửa cả file này, nếu không nhà xe tạo bằng API sẽ khác nhà xe seed. Quyền hiện có trong DB: `account:manage`, `booking:cancel`, `booking:confirm_deposit`, `booking:create`, `booking:handover`, `booking:read`, `booking:return`, `branch:manage`, `report:view_revenue`, `vehicle:manage`, `vehicle:read`.

### 4. `tenant/repository/TenantRoleRepository.java` (mới)
Tách riêng ở module tenant, **không sửa `RoleRepository`** của module account để tránh xung đột code.

```java
public interface TenantRoleRepository extends JpaRepository<Role, UUID> {

  @Modifying(flushAutomatically = true)
  @Query(value = """
    INSERT INTO roles (tenant_id, name, code, description, is_system_default)
    VALUES (:tenantId, :name, :code, :description, TRUE)
  """, nativeQuery = true)
  void insertSystemRole(@Param("tenantId") UUID tenantId, @Param("name") String name,
                        @Param("code") String code, @Param("description") String description);

  @Modifying(flushAutomatically = true)
  @Query(value = """
    INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
    WHERE r.tenant_id = :tenantId AND r.code = :roleCode
  """, nativeQuery = true)
  void grantAllPermissions(@Param("tenantId") UUID tenantId, @Param("roleCode") String roleCode);

  @Modifying(flushAutomatically = true)
  @Query(value = """
    INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id FROM roles r
    JOIN permissions p ON p.code IN (:permissionCodes)
    WHERE r.tenant_id = :tenantId AND r.code = :roleCode
  """, nativeQuery = true)
  void grantPermissions(@Param("tenantId") UUID tenantId, @Param("roleCode") String roleCode,
                        @Param("permissionCodes") Collection<String> permissionCodes);

  Optional<Role> findByTenantIdAndCode(UUID tenantId, String code); // lấy role vừa tạo để gán chủ nhà xe
}
```
Các câu SQL này đã được chạy thử trong một transaction rồi hoàn tác: mỗi tenant mới ra 3 role với 4, 7 và 11 quyền (tổng 22), không ảnh hưởng tenant khác.

Điểm khó:
- **Mọi câu có `r.tenant_id = :tenantId`.** Thiếu điều kiện này thì `r.code = 'STAFF'` khớp STAFF của **mọi** nhà xe và gán quyền cho cả tenant khác.
- **`@Modifying`** bắt buộc cho INSERT/UPDATE/DELETE dùng `@Query`. **`flushAutomatically = true`** đẩy `tenants` vừa lưu xuống DB trước khi native query chạy, nếu thiếu có thể vi phạm khóa ngoại.
- **`IN (:permissionCodes)`** nhận `Collection<String>`. **Danh sách rỗng gây lỗi SQL**, nên `TENANT_ADMIN` (danh sách rỗng) phải dùng `grantAllPermissions`.
- **Đừng dùng `RoleRepository.findByCode`** (nhiều tenant cùng `code`, nổ `IncorrectResultSize`). Dùng `findByTenantIdAndCode`.

### 5. Hai repository của module account (chỉ **dùng**, không sửa) và truy vấn xem chủ nhà xe
`UserRepository` (có `findByEmail`) và `UserTenantRepository` (có `findByUserIdAndTenantId`, `findByUserId`).

Xem danh sách chủ nhà xe cần nối ba bảng, đặt trong repository của module tenant `TenantAdminQueryRepository` (không sửa file của module account):
```java
public interface TenantAdminQueryRepository extends JpaRepository<UserTenant, UserTenantId> {

  // Mỗi phần tử là một mảng [User, UserTenant]: tài khoản và liên kết của người đó với nhà xe
  @Query("""
    SELECT u, ut FROM UserTenant ut
    JOIN User u ON u.id = ut.userId
    JOIN Role r ON r.id = ut.roleId
    WHERE ut.tenantId = :tenantId AND r.code = :roleCode
    ORDER BY ut.joinedAt
  """)
  List<Object[]> findMembersByTenantIdAndRoleCode(@Param("tenantId") UUID tenantId, @Param("roleCode") String roleCode);
}
```
Các entity không có quan hệ `@ManyToOne` (chỉ lưu id), nên dùng `JOIN ... ON` giữa các entity không liên kết (Hibernate hỗ trợ). Trả `Object[]` thay vì projection để `joinedAt` giữ kiểu `Instant`, tránh lỗi ép kiểu của truy vấn thuần.

**Kiểm tra:** chạy app, không lỗi khởi động (Spring parse HQL của `search` lúc này).

---

## 💼 GIAI ĐOẠN 4: SERVICE

- **Interface:** `tenant/service/TenantService`; **Implementation:** `tenant/service/impl/TenantServiceImpl`
- **Annotations:** `@Service`, `@RequiredArgsConstructor`, `@Slf4j`, `@Transactional` ở lớp
- **Dependencies:** `TenantRepository`, `TenantConfigRepository`, `TenantRoleRepository`, `UserRepository`, `UserTenantRepository`, `PasswordEncoder` (bean đã có ở `SecurityConfig`, `AuthServiceImpl` cũng đang tiêm)
- **Lỗi nghiệp vụ:** ném `AppException(ErrorCode, "...")`. Thêm vào `ErrorCode`: `TENANT_DOMAIN_ALREADY_EXISTS` (`HttpStatus.CONFLICT`) và `TENANT_ADMIN_ALREADY_ASSIGNED` (`HttpStatus.CONFLICT`)

### 🔹 `assignTenantAdmin(UUID tenantId, UUID adminRoleId, AdminAssignmentRequestDTO admin)`
Hàm dùng chung cho lúc tạo tenant **và** lúc thêm chủ về sau. Ban đầu viết là hàm `private` trong `TenantServiceImpl`; khi module account cần dùng thì tách ra lớp chung (xem [phụ thuộc](#-phụ-thuộc-người-khác)). Quy tắc:
1. Chuẩn hóa email (`trim`, `toLowerCase`), tra `userRepository.findByEmail`.
2. **Email mới:** bắt buộc `fullName` không rỗng và `password` hợp lệ, thiếu thì `BAD_REQUEST` nêu rõ trường nào. Tạo `User` với `passwordHash = passwordEncoder.encode(password)`, **`isActive(true)`**, **`isSuperAdmin(false)`**.
3. **Email có sẵn:** bỏ qua `fullName` và `password` gửi lên. Từ chối nếu tài khoản bị khóa hoặc là Super Admin.
4. Nếu người đó **đã là thành viên của tenant này** (`findByUserIdAndTenantId`): đã là `TENANT_ADMIN` thì ném `TENANT_ADMIN_ALREADY_ASSIGNED`; đang là STAFF/SALE thì từ chối ("chưa hỗ trợ nâng quyền"), vì khóa chính chỉ cho một role mỗi nhà xe.
5. Lưu `UserTenant` (`userId`, `tenantId`, `roleId = adminRoleId`, `joinedAt = Instant.now()`).
6. Trả về `accountCreated` (đã tạo tài khoản mới hay chỉ liên kết).

### 🔹 `createTenant(CreateTenantRequestDTO request, UUID actorId)`
1. Chuẩn hóa `domain` (`trim`, `toLowerCase`). `existsByDomain(domain)` đúng thì ném `TENANT_DOMAIN_ALREADY_EXISTS`.
2. Kiểm tra danh sách `admins`: có ít nhất 1 người, **email không trùng nhau** (so sánh sau khi chuẩn hóa).
3. **Kiểm tra từng chủ nhà xe trước khi ghi bất cứ thứ gì** (email mới thiếu họ tên hoặc mật khẩu; tài khoản có sẵn bị khóa hoặc là Super Admin). Lỗi người dùng thì dừng ngay, chưa tốn thao tác ghi.
4. Nếu có `settings`, kiểm tra là JSON hợp lệ (`ObjectMapper.readTree`), sai thì `BAD_REQUEST`. Không có thì `"{}"`.
5. Lưu `Tenant` (`.isActive(true)`, `planTier` mặc định FREE) bằng `saveAndFlush`.
6. Lưu `TenantConfig` với `tenantId` vừa có và giá trị **truyền tường minh**: `holdTimeoutMinutes(30)`, `lateHourlyPrice(new BigDecimal("100000"))`, `defaultCommissionRate(new BigDecimal("5.00"))`.
7. Tạo 3 role: lặp `DefaultRole.values()`, gọi `insertSystemRole`, rồi `grantAllPermissions` (nếu `grantsAllPermissions()`) hoặc `grantPermissions`.
8. Lấy role chủ nhà xe: `findByTenantIdAndCode(tenantId, RoleConstant.TENANT_ADMIN)`.
9. Với mỗi chủ nhà xe: gọi `assignTenantAdmin`.
10. `log.info(...)` ghi `actorId`, `tenantId`, số chủ nhà xe, mỗi người đã tạo mới hay chỉ liên kết. **Không log mật khẩu.**
11. Trả `CreateTenantResponseDTO`.

> Cả 11 bước nằm trong **một transaction**. Lỗi giữa chừng thì toàn bộ rollback: không để lại nhà xe thiếu role, hay tài khoản chủ mồ côi.

### 🔹 `addTenantAdmin(UUID tenantId, AdminAssignmentRequestDTO admin, UUID actorId)` và `listTenantAdmins(UUID tenantId)`
- `addTenantAdmin`: tenant phải tồn tại (`RESOURCE_NOT_FOUND`) và **đang hoạt động**; lấy role `TENANT_ADMIN` của tenant, rồi gọi `assignTenantAdmin`. Log hành động.
- `listTenantAdmins`: trả các chủ nhà xe của tenant (tên, email, trạng thái, ngày tham gia). **Không có hàm xem nhân viên hay Sale**: Super Admin chỉ thấy chủ nhà xe, nhân viên chỉ là con số trong màn chi tiết.

### 🔹 `getTenantById`, `listTenants`, `updateTenant`, `changeStatus`, `deleteTenant`
- `getTenantById(UUID id)`: không có thì `AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhà xe")`.
- `listTenants(String keyword, Boolean isActive, Pageable pageable)`: `keyword` thành `"%" + keyword.trim().toLowerCase() + "%"` (hoặc `"%"` nếu rỗng), gọi `search`, map sang DTO.
- `updateTenant(UUID id, UpdateTenantRequestDTO, UUID actorId)`: tìm tenant; nếu đổi `domain` thì chuẩn hóa, khác giá trị cũ thì `existsByDomainAndIdNot`; `updatedAt` tự đổi nhờ `@UpdateTimestamp` (dùng `saveAndFlush` để phản hồi có giá trị mới). **Không đụng `tenant_configs`.**
  - **Trường bắt buộc** (`name`, `domain`, `planTier`): luôn ghi đè.
  - **Trường tùy chọn** (`settings`, `logoUrl`, `contactEmail`, `contactPhone`): **không gửi hoặc `null` thì giữ giá trị cũ**; gửi chuỗi rỗng hoặc toàn dấu cách thì xóa (`logoUrl`, `contactEmail`, `contactPhone` thành `null`; `settings` thành `{}`); gửi giá trị thì ghi đè. `settings` không phải JSON hợp lệ thì `400`. Lý do: tránh lỡ xóa dữ liệu khi client chỉ muốn đổi tên hay gói.
  - Đã kiểm chứng qua HTTP thật 6 trường hợp: không gửi, gửi `null`, gửi giá trị mới, gửi `{}`, gửi chuỗi rỗng, gửi JSON sai.
- `changeStatus` / `deleteTenant` (xóa mềm): đặt `isActive`. **Không gọi `repository.delete`.** Làm **idempotent**: tenant đã khóa mà xóa tiếp vẫn thành công.

---

## 🌐 GIAI ĐOẠN 5: CONTROLLER

- **File:** `tenant/controller/TenantController.java`
- **Annotations:** `@RestController`, `@RequestMapping("/api/v1/tenants")`, `@RequiredArgsConstructor`, và **`@PreAuthorize("hasRole('SUPER_ADMIN')")` ở cấp lớp**.
- **Người thực hiện:** `@AuthenticationPrincipal UserPrincipal` để ghi log (`getId()`).
- **Phân trang tự dựng, không nhận tham số `sort` từ client:** controller chỉ nhận `page` (âm thì thành 0) và `size` (giới hạn 1 đến 100), luôn sắp theo `createdAt` giảm dần. Lý do: tham số `sort` đi thẳng vào câu truy vấn, sort theo trường không tồn tại sẽ gây lỗi. Trả `PagedModel<TenantResponseDTO>` (JSON ổn định `{content, page: {size, number, totalElements, totalPages}}`) thay vì `Page`, để tránh cảnh báo tuần tự hóa `PageImpl` của Spring Data.

| Endpoint | Method | Body | Trả về |
| :--- | :---: | :--- | :--- |
| `/api/v1/tenants` | `POST` | `CreateTenantRequestDTO` | `201 Created` + `CreateTenantResponseDTO` |
| `/api/v1/tenants` | `GET` | query: `keyword`, `isActive`, `page`, `size` | `200` + trang |
| `/api/v1/tenants/{id}` | `GET` | | `200` |
| `/api/v1/tenants/{id}` | `PUT` | `UpdateTenantRequestDTO` | `200` |
| `/api/v1/tenants/{id}/status` | `PATCH` | `UpdateTenantStatusRequestDTO` | `200` |
| `/api/v1/tenants/{id}` | `DELETE` | | `200` (xóa mềm) |
| `/api/v1/tenants/{id}/admins` | `GET` | | `200` + danh sách chủ nhà xe |
| `/api/v1/tenants/{id}/admins` | `POST` | `AdminAssignmentRequestDTO` | `201 Created` |

Khuôn mỗi hàm giống `AuthController`: `ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, "Tạo nhà xe thành công !"))`. Nhớ `@Valid @RequestBody`, `@PathVariable UUID id`.

### Hai API gán người vào nhà xe: tách riêng, dùng chung phần lõi
| | API gán chủ nhà xe (module này) | API tạo nhân viên (`POST /api/v1/accounts`, module account, người khác làm) |
| :--- | :--- | :--- |
| Ai gọi | Super Admin | Chủ nhà xe (`TENANT_ADMIN`) |
| Nhà xe lấy từ đâu | URL hoặc body (token Super Admin không có nhà xe) | Token, **không nhận từ client** (chống IDOR) |
| Role được gán | Chỉ `TENANT_ADMIN` | Chỉ `STAFF` hoặc `SALE`, **cấm** `TENANT_ADMIN` |
| Chi nhánh | Không | Có, gán `user_branches` và kiểm tra chi nhánh thuộc nhà xe |

**Không gộp thành một endpoint:** quyền khác nhau và cấm chéo, gộp sẽ phải rẽ nhánh theo vai trò, rất dễ leo quyền. **Phần lõi giống nhau** ("tìm hoặc tạo tài khoản, rồi liên kết": không đổi mật khẩu người đã có, kiểm tra tài khoản hoạt động và không phải Super Admin, chuẩn hóa email) nên viết ở một chỗ để không lệch.

### Kiểm tra quyền diễn ra ở đâu (không dùng bảng `roles`)
```
Request + Bearer token
  ▼ JwtAuthenticationFilter (đã có sẵn)
  │   token role = SUPER_ADMIN, không có tenant_id
  │   → loadSuperAdminById(userId): đọc DB, bắt buộc user active và is_super_admin = true
  │   → UserPrincipal.create(user, null, "SUPER_ADMIN", null): thêm authority "ROLE_SUPER_ADMIN"
  ▼ @PreAuthorize("hasRole('SUPER_ADMIN')") trên TenantController   ← phần bạn thêm
  │   có ROLE_SUPER_ADMIN → qua; không có → 403
  ▼ TenantService (không cần check role nữa)
```
- Chủ nhà xe, nhân viên, Sale mang `ROLE_TENANT_ADMIN`, `ROLE_STAFF`, `ROLE_SALE` nên bị **403**. Chưa đăng nhập hoặc token sai thì **401**.
- `@EnableMethodSecurity` đã bật ở `SecurityConfig`; trước module này chưa có `@PreAuthorize` nào. **Đã kiểm chứng ở Giai đoạn 6:** không token thì 401, token chủ nhà xe thì 403 (mã `AUTH_FORBIDDEN`) cho cả `GET`, `POST`, `DELETE`, token Super Admin thì qua.
- `GlobalExceptionHandler` đã có handler `AccessDeniedException` nên 403 trả đúng khuôn `ApiResponse`.

---

## 🧪 GIAI ĐOẠN 6: KIỂM THỬ

### A. Chuẩn bị tài khoản Super Admin (chỉ local, không commit)
DB seed **chưa có** Super Admin (README ghi `superadmin@carrental.vn` nhưng tài khoản này không có trong DB). Cờ này chỉ là một cột trên `users`, bật bằng SQL cho **một tài khoản riêng** dùng làm Super Admin:
```sql
UPDATE users SET is_super_admin = true WHERE email = '<email-super-admin>';
```
Đăng nhập lại để nhận token `SUPER_ADMIN` (không có tenant). Lưu ý:
- **Đừng** để tài khoản này vừa là Super Admin vừa thuộc một nhà xe: nhánh Super Admin chạy trước ở `login`, nên người đó **không vào được nhà xe**. Đã gặp thật khi một tài khoản vừa là Super Admin vừa là chủ nhà xe Sài Gòn Auto.
- Khi cần một chủ nhà xe sẵn có cho kịch bản 5, dùng một tài khoản khác với tài khoản Super Admin.
- Trên máy tác giả, tài khoản `huynq7@vnpay.vn` đã là Super Admin. Muốn trả lại: `UPDATE users SET is_super_admin = false WHERE email = '<email-super-admin>';`
- Lâu dài cần cách tạo Super Admin chính thức (migration hoặc lệnh khởi tạo, mật khẩu lấy từ biến môi trường), xem mục "Việc cần xác nhận".

### B. Kiểm thử qua Swagger
Đăng nhập, bấm **Authorize** dán access token. Mẫu body tạo tenant với hai chủ:
```json
{
  "name": "Bình An Car",
  "domain": "binhan-car",
  "planTier": "FREE",
  "contactEmail": "lienhe@binhan.vn",
  "contactPhone": "0900000001",
  "admins": [
    { "email": "chu1@binhan.vn", "fullName": "Nguyễn Văn Bình", "password": "Matkhau123" },
    { "email": "minh.admin@rentcarhanoi.vn" }
  ]
}
```

| # | Kịch bản | Kết quả đúng |
| :-: | :--- | :--- |
| 1 | Token chủ nhà xe (`minh.admin`) gọi `POST /tenants` | **403** |
| 2 | Super Admin `POST` với 1 chủ, email mới | **201**, `accountCreated = true` |
| 3 | Kiểm tra DB (SQL bên dưới) | 1 tenant, 1 config (30 / 100000 / 5.00), 3 role, 22 dòng quyền, đúng số chủ nhà xe |
| 4 | **Đăng nhập bằng tài khoản chủ vừa tạo** | Token `role = TENANT_ADMIN`, vào thẳng tenant mới, 11 quyền, danh sách chi nhánh rỗng |
| 5 | `POST` với **2 chủ** (một mới, một đã có tài khoản như body mẫu) | **201**; chủ có sẵn **không** tạo user mới, `password_hash` **không đổi**; user đó giờ có 2 nhà xe, đăng nhập thấy màn chọn nhà xe |
| 6 | `POST` với `adminEmail` trùng nhau trong danh sách, hoặc danh sách rỗng | **400**, không tạo gì |
| 7 | Email mới thiếu `fullName`, hoặc mật khẩu yếu (`"abc"`) | **400**, không tạo gì (kiểm tra trước khi ghi) |
| 8 | Một chủ là tài khoản bị khóa hoặc là Super Admin | Từ chối, **không tạo tenant** (kiểm tra rollback) |
| 9 | `POST` cùng `domain` lần nữa | **409** `TENANT_DOMAIN_ALREADY_EXISTS` |
| 10 | `POST /tenants/{id}/admins` thêm chủ thứ 3 (email mới, email có sẵn) | **201**, `GET /tenants/{id}/admins` thấy đủ |
| 11 | Thêm lại chính người đã là chủ (khác hoa thường) | **409** `TENANT_ADMIN_ALREADY_ASSIGNED` |
| 12 | Thêm người đang là STAFF/SALE của nhà xe đó | Từ chối ("chưa hỗ trợ nâng quyền") |
| 13 | Thêm chủ cho nhà xe đã khóa | Từ chối |
| 14 | `PUT` đổi tên, plan (không gửi `settings`, `logoUrl`, `contactEmail`, `contactPhone`) | `updated_at` đổi, `created_at` giữ nguyên, **bốn trường tùy chọn giữ nguyên**, `tenant_configs` không đổi |
| 15 | `PUT` đổi `domain` sang domain đã dùng | **409** |
| 16 | `DELETE` tenant | `is_active = false`; role, user, `user_tenants` **vẫn còn** |
| 17 | `DELETE` lần 2 | vẫn thành công (idempotent) |
| 18 | Chủ nhà xe của tenant đã khóa đăng nhập | Bị từ chối ("tạm ngưng hoạt động") nếu nhà xe chỉ có 1 tenant |
| 19 | `GET /tenants/{id}` id không tồn tại | **404** |
| 20 | `GET /tenants?keyword=...&isActive=false&page=0&size=10` | đúng danh sách lọc, có tổng số trang |

**SQL kiểm chứng kịch bản 3** (thay `<domain>`):
```sql
SELECT
  (SELECT count(*) FROM tenants t WHERE t.domain = '<domain>' AND t.is_active)                       AS tenants,
  (SELECT count(*) FROM tenant_configs c JOIN tenants t ON t.id = c.tenant_id WHERE t.domain = '<domain>') AS configs,
  (SELECT count(*) FROM roles r JOIN tenants t ON t.id = r.tenant_id WHERE t.domain = '<domain>')   AS roles,
  (SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id
     JOIN tenants t ON t.id = r.tenant_id WHERE t.domain = '<domain>')                              AS role_permissions,
  (SELECT count(*) FROM user_tenants ut JOIN roles r ON r.id = ut.role_id
     JOIN tenants t ON t.id = ut.tenant_id WHERE t.domain = '<domain>' AND r.code = 'TENANT_ADMIN') AS so_chu_nha_xe;
-- Kỳ vọng: tenants = 1, configs = 1, roles = 3, role_permissions = 22, so_chu_nha_xe = số người trong admins
SELECT email, is_active, is_super_admin FROM users WHERE email = 'chu1@binhan.vn';
-- Kỳ vọng: is_active = true, is_super_admin = false (không phải null)
```

### C. Unit test service (Mockito)
Viết `TenantServiceImplTest`, mỗi kịch bản một test, tên mô tả (`should_throw_conflict_when_domain_already_exists`), cấu trúc Arrange, Act, Assert, không dùng vòng lặp hay if trong test:
- tạo thành công với 1 chủ email mới (lưu config, 3 role, user với `isActive = true`, `user_tenants` role TENANT_ADMIN, mật khẩu đã băm),
- tạo với 2 chủ (một mới, một có sẵn: không gọi `encode` cho người có sẵn),
- email trùng trong danh sách, tài khoản có sẵn bị khóa, tài khoản Super Admin,
- email mới thiếu họ tên hoặc mật khẩu,
- trùng domain,
- lỗi ở bước tạo role hoặc gán chủ thì không lưu thêm gì (kiểm tra hành vi rollback bằng mock ném lỗi),
- thêm chủ trùng, thêm người đang là STAFF, thêm chủ cho nhà xe đã khóa,
- sửa trùng domain, xóa hai lần, tenant không tồn tại,
- sửa nhà xe: trường tùy chọn (`settings`, `logoUrl`, `contactEmail`, `contactPhone`) không gửi thì giữ giá trị cũ, gửi giá trị thì ghi đè, gửi chuỗi rỗng thì xóa, `settings` sai JSON thì 400.

**Hiện thực:** `src/test/java/.../tenant/service/impl/TenantServiceImplTest.java`, **44 test** (Mockito, `@Spy ObjectMapper` thật, không cần DB). Mỗi test một kịch bản, bố cục Arrange, Act, Assert, dùng hàm phụ để dựng dữ liệu và stub thay vì vòng lặp hay `if` trong test.

**Cách kiểm tra test có giá trị:** cố ý phá code rồi xem test có báo đỏ không (đã làm với: bỏ `.isActive(true)` khi tạo nhà xe, đổi xóa mềm thành xóa cứng, làm hỏng nhánh gán toàn quyền, đưa `settings` về hành vi luôn ghi đè). Cả bốn lỗi đều bị đúng test bắt.

### D. Kiểm tra cuối
- `mvn test` xanh: **45 test** (44 test của service và 1 test nạp context), `mvn clean` rồi chạy lại ứng dụng không lỗi Flyway.
- Đã chạy thật qua HTTP với JWT thật (33 bước trong các kịch bản ở mục B, cộng 6 trường hợp `PUT`), dữ liệu thử được xóa sau đó, số liệu DB trùng khớp trước và sau.
- Lỗi có sẵn không thuộc module: `GET /tenants/{id}` với id không phải UUID trả **500** (nên là 400) vì `GlobalExceptionHandler` chưa xử lý lỗi sai kiểu tham số đường dẫn.

---

## ❓ VIỆC CẦN XÁC NHẬN VỚI NHÓM

Các điểm docs không quy định. Prototype đã chọn hướng làm, nên hỏi để nhóm biết và chốt:

1. **Module nào sinh 3 role mặc định cho tenant mới?** Guide này để module tenant sinh (cùng transaction), module account chỉ gán. Đây là cách duy nhất khớp DB (không role chung, `role_id` bắt buộc) và prototype. `Database-Schema.md` mục 2.4 còn gợi ý kiểu role chung (`tenant_id` rỗng) và entity `Role` lại khai báo `tenantId` không null, nên nên xác nhận để không lệch.
2. **Mật khẩu chủ nhà xe:** Super Admin đặt mật khẩu tạm (như prototype), hay gửi email mời? `users` **không có cột "bắt đổi mật khẩu lần đầu"**. Rủi ro: người tạo biết mật khẩu, và nếu tài khoản đó sau này được thêm sang nhà xe khác thì người ở nhà xe cũ vẫn vào được nhà xe mới.
3. **Email đã có tài khoản là Super Admin** có được làm chủ nhà xe không? Nhánh Super Admin ở `login` chạy trước, tài khoản đó sẽ không vào được tenant. Khuyên từ chối.
4. **`BRANCH_MANAGER`** còn dùng không? Docs nhắc ở vài chỗ (`Multi-Tenant-Multi-Branch.md`, biên bản 03/07) nhưng DB và `onboarding.md` không có.
5. **Gỡ hoặc đổi vai trò chủ nhà xe** (chưa làm trong module này). Xóa dòng `user_tenants` làm các đơn cũ mất `created_by`, `handover_by`, `returned_by` (khóa ngoại `ON DELETE SET NULL`) và xóa phân công chi nhánh (`ON DELETE CASCADE`). Cần chốt cách xử lý, và nên cân nhắc thêm cột `user_tenants.status` để khóa riêng một người trong một nhà xe mà giữ lịch sử. Luôn phải còn ít nhất một chủ.
6. **Chi nhánh của SALE:** `onboarding.md` mục 9.2 ghi SALE không được gán chi nhánh, nhưng mục 2.1 và dữ liệu thật (mỗi Sale có 1 chi nhánh) cho thấy phải gán. Cần cho API tạo nhân viên (module account).

---

## 🔗 PHỤ THUỘC NGƯỜI KHÁC

1. **Tài khoản Super Admin để test:** xem Giai đoạn 6A. Hỏi người làm auth xem họ định seed Super Admin ở migration nào.
2. **Khóa tenant chỉ chặn ở thời điểm đăng nhập và chọn tenant.**
   - `AuthServiceImpl.login` (nhánh 1 tenant) và `selectTenant` đã kiểm tra `tenant.isActive`; `refresh-token` cấp token **không có nhà xe**, nên người dùng phải gọi `select-tenant` (nơi có kiểm tra) và sẽ bị chặn.
   - Nhưng `JwtAuthenticationFilter` không kiểm tra `tenants.is_active` mỗi request, nên người đang cầm access token còn hạn vẫn làm việc tiếp cho đến hết token, **tối đa 15 phút**.
   - Đây là phần của module auth, nhắn người phụ trách. Module tenant vẫn hoàn chỉnh.
3. **`GlobalExceptionHandler` trả 500 cho tham số đường dẫn sai kiểu** (ví dụ `GET /tenants/abc`). Nên 400. Là phần của người làm module chung, báo họ thêm handler cho `MethodArgumentTypeMismatchException`.
4. **Dùng chung entity và repository của module account** (`User`, `UserTenant`, `UserRepository`, `UserTenantRepository`). Chỉ **dùng**, không sửa. Nếu người làm account đổi tên field hay method thì code của bạn sẽ lỗi biên dịch, nên báo họ biết.
5. **Phần lõi "tìm hoặc tạo tài khoản rồi liên kết"** dùng chung với API tạo nhân viên của module account. Ban đầu viết trong `TenantServiceImpl`; khi module account bắt đầu làm API nhân viên, hai bên thống nhất rút ra một lớp chung để quy tắc không lệch.

---

## ⚠️ CHECKLIST LƯU Ý SỐNG CÒN

| Điểm cần nhớ | Lý do kỹ thuật |
| :--- | :--- |
| **Gán `.isActive(true)` khi tạo `Tenant` bằng builder** | `boolean` nguyên thủy mặc định `false`, tenant mới bị khóa |
| **Gán `isActive(true)` và `isSuperAdmin(false)` khi tạo `User`** | `Boolean` không có default phía Java, lưu `null`; về sau `login` chạy `!user.getIsActive()` sẽ ném `NullPointerException` |
| **Gán `joinedAt = Instant.now()` khi tạo `UserTenant`** | Không có `@CreationTimestamp`, nếu không sẽ lưu `null` |
| **Mã hóa mật khẩu bằng `PasswordEncoder` (BCrypt), không lưu thô** | `users.password_hash` là bản băm; không log, không trả về mật khẩu |
| **Email có sẵn thì không đổi mật khẩu, không đổi họ tên** | Tránh chiếm quyền tài khoản của nhà xe khác |
| **`@Valid` trên danh sách `admins`** | Thiếu thì Spring không kiểm tra từng phần tử |
| **Kiểm tra hết danh sách chủ nhà xe trước khi ghi** | Lỗi người dùng không cần rollback tốn kém |
| **Luôn có `planTier`** | Cột `plan_tier` là `NOT NULL`, mặc định FREE |
| **`@CreationTimestamp` và `@UpdateTimestamp`** | Không có thì Hibernate insert `null` đè default của DB |
| **Tiền và tỉ lệ dùng `BigDecimal`** | `Double` bị `validate` chặn và sai số làm tròn |
| **Gõ đúng tên cột trong `@Column`** | Sai là app không khởi động |
| **Truyền giá trị mặc định tường minh khi tạo `TenantConfig`** | Builder không biết default của DB, `null` vào cột `NOT NULL` sẽ lỗi |
| **Mọi native query có `WHERE ... tenant_id = :tenantId`** | Thiếu điều kiện sẽ ghi dữ liệu vào tenant khác |
| **Không dùng `RoleRepository.findByCode`** | Nhiều tenant cùng `code`, nổ `IncorrectResultSize`. Dùng `findByTenantIdAndCode` |
| **Danh sách quyền rỗng dùng `grantAllPermissions`, không dùng `IN ()`** | `IN` với danh sách rỗng gây lỗi SQL |
| **Tạo tenant, config, role, user, `user_tenants` trong một `@Transactional`** | Lỗi giữa chừng phải rollback |
| **Không `delete` tenant** | `ON DELETE CASCADE` xóa sạch dữ liệu nghiệp vụ |
| **Không dùng `@NotBlank` cho UUID, enum, Boolean** | Dùng `@NotNull` |
| **Không dùng `@Data` trên entity** | Tránh `StackOverflowError` do `hashCode/equals` |
| **Không truyền `null` làm `keywordPattern`** | PostgreSQL lỗi `function lower(bytea)` |
| **`@PreAuthorize("hasRole('SUPER_ADMIN')")` ở cấp class** | Super Admin lấy từ cờ `is_super_admin`, không từ bảng `roles` |
| **Chuẩn hóa email về chữ thường trước khi tra và lưu** | `users.email` unique phân biệt hoa thường; seed hiện toàn chữ thường |
| **Không trả danh sách nhân viên cho Super Admin** | Dữ liệu thuộc nhà xe; chỉ chủ nhà xe và số lượng |

---

## 🚧 NGOÀI PHẠM VI

- **Sửa cấu hình vận hành (`tenant_configs`):** do chủ nhà xe tự sửa, thuộc module khác. Module này chỉ tạo dòng mặc định.
- **Gỡ hoặc đổi vai trò chủ nhà xe, khóa riêng một người trong một nhà xe:** chờ nhóm chốt (mục xác nhận số 5).
- **API khóa tài khoản người dùng** (`users.is_active`): hiện chỉ làm bằng SQL. Nên đề xuất nhóm làm sớm khi chuẩn bị chạy thật, vì đây là thao tác khẩn cấp có hiệu lực ngay ở request kế tiếp (filter đọc lại DB mỗi request), kể cả refresh token. Câu lệnh tạm thời: `UPDATE users SET is_active = false WHERE email = '...'`. Sau khi khóa nên rà các tài khoản mới tạo gần đây của nhà xe (`users.created_at`, `user_tenants.joined_at`).
- **Đặt lại mật khẩu, bắt đổi mật khẩu lần đầu, email mời:** chưa có cơ chế (xem mục xác nhận số 2).
- **Chọn chủ nhà xe từ danh sách người dùng có sẵn:** cần API tìm user của module account, hiện chỉ nhập email.
- **Cột `tenants.code`:** chưa có trong DB, cần cho mã đơn `BK_[nhà xe]_[ngày]_[ngẫu nhiên]`. Prototype tự sinh mã 3 chữ cái từ domain nhưng DB chưa có chỗ lưu. Cần migration V7, phải thống nhất với cả nhóm.
- **Hạn mức theo gói dịch vụ** (số chi nhánh, xe, nhân sự): chưa có chỗ lưu.
- **Nhật ký thao tác (audit log) dạng bảng:** hiện chỉ log ra file.
- **Row-Level Security:** tài liệu mô tả nhưng DB chưa có policy nào.
- **Tạo, sửa, khóa nhân viên của nhà xe:** module account.
