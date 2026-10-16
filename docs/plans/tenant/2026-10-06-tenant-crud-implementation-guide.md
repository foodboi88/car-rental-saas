# 📘 CẨM NANG TỰ CODE TỪNG BƯỚC: MODULE TENANT (THÊM, SỬA, XÓA MỀM NHÀ XE, GÁN CHỦ NHÀ XE)

> **Dự án:** Car Rental SaaS
> **Module:** `tenant`
> **Phạm vi:** Super Admin tạo nhà xe (kèm cấu hình mặc định, 3 nhóm quyền mặc định và **một hoặc nhiều chủ nhà xe**), xem, sửa, khóa/mở khóa, xóa mềm nhà xe, và thêm/xem chủ nhà xe.
> **Nguồn thiết kế:** prototype (màn "Thêm nhà xe" và "Chi tiết nhà xe", phiên bản 10 trở lên), `onboarding.md` mục 2.1 và 9.2, `Database-Schema.md`, và dữ liệu thật trong DB.
> **Cách dùng:** Tài liệu mô tả luồng logic, tên class, thuộc tính, annotation và các bẫy đã gặp để bạn tự viết mã nguồn. Cùng khuôn với [guide module auth](../auth-login/2026-08-28-auth-jwt-implementation-guide.md).

---

## ⚡ TÓM TẮT TRONG 1 PHÚT

**Module làm gì:** Super Admin tạo nhà xe (kèm cấu hình, 3 vai trò và các chủ nhà xe), xem, sửa, khóa, xóa mềm nhà xe, thêm và xem chủ nhà xe. Chỉ Super Admin gọi được.

**8 API** dưới `/api/v1/tenants`:

| Việc | API |
| :--- | :--- |
| Tạo nhà xe (kèm chủ) | `POST /` |
| Danh sách (tìm, lọc, phân trang) | `GET /` |
| Xem một nhà xe | `GET /{id}` |
| Sửa thông tin | `PUT /{id}` |
| Khóa hoặc mở khóa | `PATCH /{id}/status` |
| Xóa (xóa mềm) | `DELETE /{id}` |
| Xem chủ nhà xe | `GET /{id}/admins` |
| Thêm chủ nhà xe | `POST /{id}/admins` |

**Sáu giai đoạn làm:**

| GĐ | Làm gì | File chính |
| :-: | :--- | :--- |
| 1 | Sửa entity, thêm bảng cấu hình | `Tenant`, `TenantConfig` |
| 2 | DTO vào và ra | `CreateTenantRequestDTO`, `TenantResponseDTO`... |
| 3 | Truy vấn và bản mẫu 3 vai trò | `TenantRepository`, `TenantRoleRepository`, `DefaultRole` |
| 4 | Logic nghiệp vụ | `TenantServiceImpl` |
| 5 | API | `TenantController` |
| 6 | Kiểm thử | 44 test đơn vị, 42 test tích hợp |

**Bốn điều phải nhớ:**
1. **Tạo nhà xe là một giao dịch:** nhà xe, cấu hình, 3 vai trò (22 dòng quyền) và các chủ cùng thành công hoặc cùng hoàn lại. Kiểm tra hết rồi mới ghi.
2. **Xóa là xóa mềm** (`is_active = false`), vì xóa thật kéo theo xóa sạch dữ liệu nhà xe.
3. **Email đã có tài khoản thì chỉ liên kết**, không đổi mật khẩu và họ tên.
4. **Cấu hình vận hành do chủ nhà xe tự sửa**, module này chỉ tạo dòng mặc định.

**Trạng thái:** code và test xong trên nhánh `feature/huynq7-tenant`, chưa gộp vào `main`. Còn lại và việc cần báo người khác: xem [Phụ thuộc người khác](#-phụ-thuộc-người-khác) và [Ngoài phạm vi](#-ngoài-phạm-vi).

> Muốn hiểu **vì sao** thì đọc tiếp Bức tranh toàn cảnh. Muốn **tự code** thì đi theo Giai đoạn 0 đến 6. Phần Service (Giai đoạn 4) đã gộp lời giải thích vào từng hàm.

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

---

## 🗺️ MỤC LỤC

- [Tóm tắt trong 1 phút](#-tóm-tắt-trong-1-phút)
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
- `planTier` trong JSON theo **tên enum** (`"FREE"`, `"BASIC"`, `"PRO"`, `"ENTERPRISE"`). Đã xác nhận bằng test tích hợp (gửi `"BASIC"`, `"PRO"` đều được nhận).
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

### 📖 Giải thích bằng lời: sinh vai trò cho nhà xe mới
**Chuyện cần giải quyết.** Mỗi thành viên của một nhà xe (`user_tenants`) bắt buộc có một vai trò, và vai trò là của **riêng từng nhà xe** (bảng `roles` có `tenant_id`). Nên nhà xe vừa tạo xong mà chưa có vai trò thì không ai gán được cho ai, kể cả chủ nhà xe. Vì vậy lúc tạo nhà xe phải sinh luôn 3 vai trò.

**Cách làm, từng bước:**
1. Với mỗi vai trò trong `DefaultRole` (TENANT_ADMIN, STAFF, SALE), chèn một dòng vào `roles` với `tenant_id` của nhà xe mới (`insertSystemRole`). Các câu này là SQL dạng "chèn từ truy vấn" (`INSERT ... SELECT`): DB tự nối vai trò với quyền, không phải kéo từng quyền về Java rồi lưu lại. Hai lưu ý sống còn (`tenant_id` và `flushAutomatically`) nằm ở danh sách "Điểm khó" ngay phía trên.
2. Rồi nối vai trò đó với các quyền của nó trong `role_permissions`:
   - **TENANT_ADMIN:** nối với **mọi** dòng trong bảng `permissions` (hiện 11 quyền). Danh sách quyền để rỗng trong `DefaultRole` có nghĩa là "tất cả", và có hệ quả hay: sau này thêm quyền mới vào bảng `permissions` thì chủ của các nhà xe **tạo từ đó trở đi** tự có luôn quyền mới.
   - **STAFF:** đúng 7 quyền (đọc, tạo, xác nhận cọc, giao, nhận, hủy đơn; xem xe).
   - **SALE:** đúng 4 quyền (đọc, tạo, hủy đơn; xem xe).
3. Tổng cộng 3 dòng `roles` và 11 + 7 + 4 = **22 dòng `role_permissions`** cho mỗi nhà xe. Đó là dòng nối nhỏ, chỉ sinh một lần lúc tạo nhà xe.

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
- **Dependencies:** `TenantRepository`, `TenantConfigRepository`, `TenantRoleRepository`, `TenantAdminQueryRepository`, `UserRepository`, `UserTenantRepository`, `PasswordEncoder` (bean đã có ở `SecurityConfig`, `AuthServiceImpl` cũng đang tiêm), `ObjectMapper` (kiểm tra `settings` là JSON)
- **Lỗi nghiệp vụ:** ném `AppException(ErrorCode, "...")`. Thêm vào `ErrorCode`: `TENANT_DOMAIN_ALREADY_EXISTS` (`HttpStatus.CONFLICT`) và `TENANT_ADMIN_ALREADY_ASSIGNED` (`HttpStatus.CONFLICT`)

### Hai nguyên tắc chạy xuyên suốt

**1. Service là nơi quyết định.** DTO chỉ kiểm hình dạng, Controller chỉ nhận rồi chuyển. Service trả lời ba câu mà chỉ nó đủ thông tin: việc này có hợp lệ theo luật nghiệp vụ không (cần hỏi DB), phải làm những việc gì theo thứ tự nào, và nếu hỏng giữa chừng thì có hoàn lại không.

**2. Cả lớp chạy trong một giao dịch (`@Transactional`)**, giống một lần chuyển khoản: hoặc mọi thao tác ghi cùng thành công, hoặc không cái nào có hiệu lực. Hàm ném lỗi runtime (`AppException` thuộc loại này) thì mọi thứ đã ghi trong hàm đó bị hoàn lại. Hàm chỉ đọc đánh dấu `readOnly` cho nhẹ.

Có **ba lớp kiểm tra**, mỗi lớp một việc:

| Lớp | Hỏi gì | Ví dụ |
| :--- | :--- | :--- |
| **DTO** (`@Valid`) | Dữ liệu đúng hình dạng không? Không cần DB | Domain đúng mẫu, email đúng dạng, mật khẩu từ 8 ký tự có chữ và số |
| **Service** | Việc này có được phép không? Cần DB hoặc luật riêng | Domain đã dùng chưa, tài khoản có bị khóa không, `settings` có là JSON không |
| **Database** | Chốt chặn cuối khi code sót | `domain` và `email` unique, khóa ngoại |

Service lặp lại vài luật của DTO (danh sách chủ rỗng, email rỗng) vì có thể được gọi từ nơi không qua `@Valid`. Quy tắc: lặp lại thứ làm hỏng dữ liệu nếu sai, không lặp lại thứ chỉ là định dạng.

---

### 🔹 `createTenant(CreateTenantRequestDTO request, UUID actorId)`: như mở một cửa hàng mới

Bạn không giao chìa khóa trước khi có cửa hàng, nhân viên và nội quy. Hàm làm theo đúng thứ tự đó, và **kiểm tra hết rồi mới ghi**. Ví dụ xuyên suốt: tạo "Bình An Car" với hai chủ, `chu1@binhan.vn` (chưa có tài khoản) và `minh.admin@rentcarhanoi.vn` (đã có tài khoản ở nhà xe khác).

**Giai đoạn kiểm tra: chưa ghi gì vào DB**
1. **Domain:** làm gọn (`trim`, `toLowerCase`), `existsByDomain(domain)` đúng thì ném `TENANT_DOMAIN_ALREADY_EXISTS` (**409**).
2. **`settings`:** nếu có thì phải là JSON hợp lệ (`ObjectMapper.readTree`), sai thì **400**. Không có thì lưu `"{}"`.
3. **Cả danh sách chủ** (`prepareAdmins`): có ít nhất 1 người, email không trùng nhau (so sau khi chuẩn hóa), từng người qua `prepareAdmin` (xem hàm bên dưới). Một người sai là dừng cả việc tạo.

*Vì sao kiểm tra hết trước:* nếu chủ thứ hai bị khóa mà vẫn tạo nhà xe rồi mới phát hiện, ta phải hoàn lại. Dừng sớm thì rẻ và không để lại dấu vết.

**Giai đoạn ghi: theo thứ tự, mỗi bước cần bước trước**
4. **Nhà xe:** lưu `Tenant` bằng `saveAndFlush`, `.isActive(true)`, `planTier` mặc định FREE nếu không chọn.
5. **Cấu hình:** lưu `TenantConfig` với giá trị **truyền tường minh**: `holdTimeoutMinutes(30)`, `lateHourlyPrice(new BigDecimal("100000"))`, `defaultCommissionRate(new BigDecimal("5.00"))`.
6. **3 vai trò:** lặp `DefaultRole.values()`, gọi `insertSystemRole`, rồi `grantAllPermissions` (nếu `grantsAllPermissions()`) hoặc `grantPermissions`. Ra 3 dòng `roles` và 22 dòng `role_permissions` (giải thích ở Giai đoạn 3).
7. **Vai trò chủ:** `findByTenantIdAndCode(tenantId, RoleConstant.TENANT_ADMIN)` để lấy vai trò vừa sinh.
8. **Gán từng chủ:** gọi `assignTenantAdmin`.
9. **Log và trả:** `log.info(...)` ghi `actorId`, tên và id nhà xe, số chủ. **Không log mật khẩu.** Trả `CreateTenantResponseDTO`.

**Kết quả trong DB cho ví dụ:** 1 nhà xe, 1 cấu hình, 3 vai trò, 22 dòng quyền, 1 tài khoản mới (`chu1`), 2 thành viên. `minh.admin` giờ thuộc hai nhà xe nên lúc đăng nhập sẽ thấy màn chọn nhà xe.

> **Rollback:** cả 9 bước nằm trong một giao dịch. Bước 1 đến 3 chặn lỗi do người dùng gửi sai; giao dịch lo lỗi bất ngờ ở bước ghi (DB lỗi, mất kết nối). Test hiện chứng minh được "kiểm tra trước thì không ghi gì"; chưa có test gây lỗi giữa bước ghi để chứng minh rollback.

### 🔹 Gán chủ nhà xe: `prepareAdmin` rồi `assignTenantAdmin`

Phần lõi dùng chung cho lúc tạo nhà xe **và** lúc thêm chủ về sau. Tách hai bước để **kiểm tra xong hết rồi mới ghi**. Cả hai ban đầu là hàm `private` trong `TenantServiceImpl`; khi module account cần dùng thì tách ra lớp chung (xem [phụ thuộc](#-phụ-thuộc-người-khác)).

**Kiểm tra, chưa ghi: `prepareAdmin(AdminAssignmentRequestDTO, Set<String> seenEmails)`** trả `PreparedAdmin(email, fullName, password, existingUser)`:
1. Chuẩn hóa email (`trim`, `toLowerCase`). Rỗng thì `BAD_REQUEST`; đã gặp trong danh sách (`seenEmails`) thì `BAD_REQUEST`.
2. `userRepository.findByEmail`. **Email có sẵn:** từ chối nếu tài khoản bị khóa hoặc là Super Admin; bỏ qua `fullName` và `password` gửi lên.
3. **Email mới:** bắt buộc có `fullName` và `password`, thiếu thì `BAD_REQUEST` nêu rõ trường nào.

**Ghi: `assignTenantAdmin(UUID tenantId, UUID adminRoleId, PreparedAdmin admin)`:**
1. **Email mới:** tạo `User` với `passwordHash = passwordEncoder.encode(password)`, **`isActive(true)`**, **`isSuperAdmin(false)`**. **Email có sẵn:** gọi `ensureNotAlreadyMember`.
2. Lưu `UserTenant` (`userId`, `tenantId`, `roleId = adminRoleId`, `joinedAt = Instant.now()`).
3. Trả `AdminResult(email, accountCreated)`.

**Mỗi tình huống ra kết quả gì.** Quy tắc quan trọng nhất: *email đã có tài khoản thì chỉ liên kết, không đổi mật khẩu và họ tên.* Nếu cho ghi đè, ai biết email của người ở nhà xe khác có thể đặt lại mật khẩu của họ khi "tạo nhà xe" và chiếm tài khoản.

| Tình huống | Kết quả | Vì sao |
| :--- | :--- | :--- |
| Email mới, đủ họ tên và mật khẩu | Tạo tài khoản và liên kết (`accountCreated = true`) | Cần tài khoản để đăng nhập |
| Email mới, thiếu họ tên hoặc mật khẩu | **400** | Không tạo được tài khoản dùng được |
| Email có sẵn, đang hoạt động, chưa thuộc nhà xe này | Chỉ liên kết (`accountCreated = false`), bỏ qua họ tên và mật khẩu gửi lên | Tránh chiếm tài khoản |
| Email có sẵn nhưng tài khoản bị khóa | **400** | Không gán quyền cho người đã bị khóa |
| Email có sẵn là Super Admin | **400** | Nhánh đăng nhập Super Admin chạy trước, người này sẽ không vào được nhà xe |
| Đã là chủ của nhà xe này | **409** `TENANT_ADMIN_ALREADY_ASSIGNED` | Gán trùng |
| Đang là nhân viên (STAFF, SALE) của nhà xe này | **400** ("chưa hỗ trợ nâng quyền") | Khóa chính `user_tenants` chỉ cho một vai trò mỗi nhà xe |
| Hai email giống nhau trong cùng danh sách (kể cả khác hoa thường) | **400** | Tránh gán hai lần |

### 🔹 `addTenantAdmin` và `listTenantAdmins`
- **`addTenantAdmin(UUID tenantId, AdminAssignmentRequestDTO admin, UUID actorId)`:** nhà xe phải tồn tại (**404**) và **đang hoạt động** (đang khóa thì **400**). Lấy vai trò `TENANT_ADMIN` **của nhà xe này**, kiểm tra người được thêm bằng `prepareAdmin`, rồi `assignTenantAdmin`. Nếu nhà xe không có vai trò này (dữ liệu hỏng, ví dụ nhà xe tạo tay không qua API) thì báo lỗi hệ thống. Log hành động.
- **`listTenantAdmins(UUID tenantId)`:** kiểm tra nhà xe tồn tại, rồi lấy các thành viên có vai trò TENANT_ADMIN xếp theo ngày tham gia. Chỉ trả id, email, họ tên, trạng thái, ngày tham gia, **không có mật khẩu**. Không có hàm xem nhân viên hay Sale: Super Admin chỉ thấy chủ nhà xe, nhân viên chỉ là con số trong màn chi tiết.

### 🔹 `getTenantById` và `listTenants`
- **`getTenantById(UUID id)`:** không có thì `AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhà xe")`.
- **`listTenants(String keyword, Boolean isActive, Pageable pageable)`:** từ khóa trống thì thành `"%"` (tìm tất cả), có thì `"%" + keyword.trim().toLowerCase() + "%"` và tìm trong **tên hoặc domain**. `isActive` không gửi thì không lọc, gửi `true` hay `false` thì chỉ lấy nhà xe đang hoạt động hoặc đã khóa. Phân trang do Controller dựng sẵn (Giai đoạn 5). Từ khóa luôn là một chuỗi, không bao giờ `null`, vì PostgreSQL báo lỗi hàm `lower` khi gặp `null`.

### 🔹 `updateTenant(UUID id, UpdateTenantRequestDTO, UUID actorId)`: sửa mà không lỡ xóa
Các bước: tìm nhà xe; xử lý `domain`; ghi đè các trường theo bảng dưới; `saveAndFlush` (để `updatedAt`, tự đổi nhờ `@UpdateTimestamp`, đã có giá trị mới khi trả về). **Không đụng `tenant_configs`**, vì cấu hình do chủ nhà xe tự sửa. Ngày tạo giữ nguyên.

**Domain:** làm gọn như lúc tạo. **Không đổi** thì không kiểm tra trùng (nếu không sẽ báo trùng với chính nó). **Đổi** thì `existsByDomainAndIdNot`: không được trùng nhà xe **khác**.

**Trường bắt buộc** (`name`, `domain`, `planTier`) luôn được ghi đè; DTO đã chặn việc thiếu. **Trường tùy chọn** (`logoUrl`, `contactEmail`, `contactPhone`, `settings`) có ba cách gửi:

| Cách gửi | Kết quả | Ví dụ, `contactEmail` đang là `a@x.vn` |
| :--- | :--- | :--- |
| **Không gửi** (hoặc `null`) | **Giữ giá trị cũ** | Vẫn `a@x.vn` |
| **Chuỗi rỗng** hoặc toàn dấu cách | **Xóa** (thành trống; riêng `settings` về `{}`) | Thành trống |
| **Có giá trị** | **Ghi đè** | Thành giá trị mới |

*Vì sao:* client chỉ muốn đổi tên hay gói thì không nên vô tình xóa email, điện thoại chỉ vì quên gửi lại. Muốn xóa thật thì gửi chuỗi rỗng có chủ ý. `settings` không phải JSON hợp lệ thì **400**. Đã kiểm chứng qua HTTP thật 6 trường hợp: không gửi, `null`, giá trị mới, `{}`, chuỗi rỗng, JSON sai.

### 🔹 `changeStatus` và `deleteTenant`
- **`changeStatus`:** đổi cột `is_active`. Nhà xe **đã ở đúng trạng thái yêu cầu** thì trả luôn, không ghi. Nhờ vậy gọi lại nhiều lần không lỗi, không đổi ngày cập nhật, không đầy log (**idempotent**: gọi nhiều lần cho cùng kết quả như gọi một lần).
- **`deleteTenant`:** chính là `changeStatus(false)`, **không gọi `repository.delete`**. Gần như mọi bảng nối tới `tenants` bằng `ON DELETE CASCADE`, xóa thật là xóa sạch chi nhánh, xe, khách, đơn thuê, không hoàn tác.
- **Hậu quả của việc khóa:** người của nhà xe không đăng nhập hay chọn nhà xe được; không thêm được chủ mới; dữ liệu còn nguyên nên mở khóa lại là dùng tiếp. Lưu ý: người đang cầm token còn hạn có thể chưa bị chặn ngay, do lỗi bộ lọc JWT của module auth (xem [phụ thuộc](#-phụ-thuộc-người-khác)).

> **Một ca còn hở:** hai người tạo cùng domain **cùng lúc** đều qua bước kiểm tra, rồi unique của DB chặn một người; người đó có thể nhận lỗi 500 thay vì 409. Khả năng thấp vì chỉ Super Admin tạo nhà xe. Cách sửa: bắt `DataIntegrityViolationException` và đổi sang 409.

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

Khuôn mỗi hàm giống `AuthController`: `ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data, "Tạo nhà xe thành công !"))`. Nhớ `@Valid @RequestBody`, `@PathVariable UUID id`. Controller **không chứa luật nghiệp vụ**: lấy id người gọi từ token (để ghi log ai làm), gọi một hàm Service, bọc kết quả vào `ApiResponse` với mã đúng (201 cho tạo mới, 200 cho còn lại).

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
- **Thứ tự cần biết:** Spring kiểm tra dạng body (`@Valid`) **trước** khi kiểm tra quyền (`@PreAuthorize`), nên người không có quyền gửi body sai sẽ nhận **400** chứ chưa phải 403.

---

## 🧪 GIAI ĐOẠN 6: KIỂM THỬ

### A. Chuẩn bị tài khoản Super Admin (chỉ local, không commit)
DB seed **chưa có** Super Admin (README ghi `superadmin@carrental.vn` nhưng tài khoản này không có trong DB). Cờ này chỉ là một cột trên `users`, bật bằng SQL cho **một tài khoản riêng** dùng làm Super Admin:
```sql
UPDATE users SET is_super_admin = true WHERE email = '<email-super-admin>';
```
Đăng nhập lại để nhận token `SUPER_ADMIN` (không có tenant). Lưu ý:
- **Đừng** để tài khoản này vừa là Super Admin vừa thuộc một nhà xe: nhánh Super Admin chạy trước ở `login`, nên người đó **không vào được nhà xe**. Đã gặp thật khi một tài khoản vừa là Super Admin vừa là chủ nhà xe Sài Gòn Auto.
- Khi cần một chủ nhà xe sẵn có cho kịch bản 2, dùng một tài khoản khác với tài khoản Super Admin.
- Trên máy tác giả có một tài khoản đã được bật cờ Super Admin bằng lệnh trên. Muốn trả lại: `UPDATE users SET is_super_admin = false WHERE email = '<email-super-admin>';`
- Lâu dài cần cách tạo Super Admin chính thức (migration hoặc lệnh khởi tạo, mật khẩu lấy từ biến môi trường), xem mục "Việc cần xác nhận".

### B. Thử tay qua Swagger
Phần lớn kịch bản đã có test tự động (mục C2). Mục này để bạn **nhìn tận mắt** trên Swagger khi cần. Đăng nhập, bấm **Authorize** dán access token. Mẫu body tạo tenant với hai chủ:
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
| 2 | Super Admin `POST` với **2 chủ** (body mẫu) | **201**; chủ có sẵn **không** tạo user mới, `password_hash` **không đổi**; DB đúng như SQL bên dưới; chủ mới đăng nhập được với `role = TENANT_ADMIN` và 11 quyền; chủ có sẵn nay thuộc 2 nhà xe nên thấy màn chọn nhà xe |
| 3 | Một chủ là tài khoản bị khóa hoặc là Super Admin | Từ chối, **không tạo nhà xe** |
| 4 | `POST` cùng `domain` lần nữa | **409** `TENANT_DOMAIN_ALREADY_EXISTS` |
| 5 | `PUT` đổi tên, plan (không gửi `settings`, `logoUrl`, `contactEmail`, `contactPhone`) | `updated_at` đổi, `created_at` giữ nguyên, **bốn trường tùy chọn giữ nguyên**, `tenant_configs` không đổi |
| 6 | `DELETE` hai lần, rồi chủ nhà xe đăng nhập | `is_active = false`, role, user, `user_tenants` **vẫn còn**; lần 2 vẫn thành công; đăng nhập bị từ chối ("tạm ngưng hoạt động") nếu nhà xe chỉ có 1 tenant |
| 7 | `GET /tenants?keyword=...&isActive=false&page=0&size=10` | Đúng danh sách lọc, có tổng số trang |

**SQL kiểm chứng nhà xe vừa tạo** (kịch bản 2, thay `<domain>`):
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
**Hiện thực:** `src/test/java/.../tenant/service/impl/TenantServiceImplTest.java`, **44 test** (Mockito, `@Spy ObjectMapper` thật, không cần DB): tạo nhà xe (21), xem và danh sách (3), sửa (11), khóa và xóa mềm (3), chủ nhà xe (6). Mỗi test một kịch bản, tên mô tả (`should_throw_conflict_when_domain_already_exists`), bố cục Arrange, Act, Assert, dùng hàm phụ để dựng dữ liệu và stub thay vì vòng lặp hay `if` trong test. **Chưa có test gây lỗi giữa bước ghi để chứng minh rollback**; test hiện chứng minh được việc kiểm tra trước khi ghi.

**Cách kiểm tra test có giá trị:** cố ý phá code rồi xem test có báo đỏ không (đã làm với: bỏ `.isActive(true)` khi tạo nhà xe, đổi xóa mềm thành xóa cứng, làm hỏng nhánh gán toàn quyền, đưa `settings` về hành vi luôn ghi đè). Cả bốn lỗi đều bị đúng test bắt.

### C2. Test tích hợp (Testcontainers)
Chạy **cả ứng dụng thật** (Spring, Security, JPA, Flyway) trên một **PostgreSQL thật do Testcontainers dựng** (`postgres:15`), gọi API qua `java.net.http.HttpClient`, rồi đếm dòng trong DB. Khác test đơn vị ở chỗ đi qua đủ các tầng (bộ lọc JWT, controller, service, repository, DB).

- **Cần Docker đang chạy.** Test dựng container riêng với cổng ngẫu nhiên, **không đụng DB dev**, xóa khi xong. Lần đầu tải image `postgres:15` nên chậm.
- **Thêm vào `pom.xml`** (phạm vi `test`): `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`. Đây là file chung của nhóm nên báo trước.
- **Lớp nền:** `src/test/java/.../support/ApiIntegrationTestSupport.java`. Container khởi động **một lần** cho mọi lớp test (mẫu singleton, `@ServiceConnection` tự nối vào ứng dụng). Có sẵn hàm gọi API, đăng nhập, tạo nhà xe, đếm dòng. Mỗi test tự tạo dữ liệu với hậu tố ngẫu nhiên nên không phụ thuộc thứ tự.
- **Super Admin:** DB mới chạy migration **chưa có** Super Admin, nên trước mỗi test lớp nền tự `INSERT` một tài khoản (`ON CONFLICT DO NOTHING`).
- **Chạy riêng:** `mvn test -Dtest='*IntegrationTest'`. `mvn test` thường cũng chạy chúng (chưa tách bằng plugin Failsafe).

| Lớp test | Số test | Phủ gì |
| :--- | :-: | :--- |
| `TenantApiIntegrationTest` | 10 | Tạo nhà xe (đếm 1 tenant, 1 config, 3 role, 22 quyền, 1 thành viên; phản hồi không có mật khẩu), 409 trùng domain, chủ mới đăng nhập đủ quyền, khóa thì login 403, mở khóa đăng nhập lại, xóa mềm hai lần, chủ không hợp lệ thì DB không có gì, 400 kèm lỗi theo trường |
| `TenantUpdateAndListIntegrationTest` | 8 | `PUT` giữ cũ hoặc xóa trường tùy chọn, 409 domain nhà xe khác, `created_at` giữ còn `updated_at` đổi, tìm theo từ khóa, lọc `isActive`, giới hạn `size` 100, 404 |
| `TenantAdminIntegrationTest` | 7 | Thêm chủ (email mới, email có sẵn chỉ liên kết và không đổi mật khẩu hay tên), 409 đã là chủ, 400 nhà xe khóa, tài khoản khóa, Super Admin, danh sách chủ không lộ mật khẩu |
| `TenantAccessControlIntegrationTest` | 17 | Cả 8 API: không token 401, chủ nhà xe 403; chủ nhà xe không khóa được nhà xe khác |

**Kiểm tra test có giá trị:** phá code rồi xem test có đỏ không. Bỏ một quyền của STAFF thì test đếm 22 dòng báo đỏ (21 thay vì 22). Bỏ `@PreAuthorize` ở controller, bỏ điều kiện giữ trường cũ của `PUT`, bỏ kiểm tra "đã là chủ" thì **12 test báo đỏ** ở ba lớp. Đã hoàn lại code sau mỗi lần thử.

**Lưu ý khi viết test phân quyền:** Spring kiểm tra **dạng body trước khi kiểm tra quyền** (`@Valid` chạy trước `@PreAuthorize`), nên body sai trả 400 chứ chưa phải 403. Test phân quyền phải gửi body hợp lệ.

### D. Kiểm tra cuối
- `mvn clean test` xanh: **87 test** (44 test đơn vị của service, 42 test tích hợp, 1 test nạp context), 0 lỗi. Cần Docker chạy cho test tích hợp. `mvn clean` rồi chạy lại ứng dụng không lỗi Flyway.
- Trước khi có test tích hợp, đã chạy thật qua HTTP với JWT thật (33 bước, cộng 6 trường hợp `PUT`), dữ liệu thử được xóa sau đó, số liệu DB trùng khớp trước và sau.
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
2. **Khóa tenant chưa chặn hết người đang dùng.** Đọc lại code auth (chưa chạy thử từng điểm):
   - `AuthServiceImpl.login` (nhánh 1 tenant) và `selectTenant` đã kiểm tra `tenant.isActive`; `refresh-token` cấp token **không có nhà xe**, nên người dùng phải gọi `select-tenant` (nơi có kiểm tra) và sẽ bị chặn.
   - Nhưng `JwtAuthenticationFilter` không kiểm tra `tenants.is_active` mỗi request, **và `switch-branch` chỉ kiểm tra nhà xe có tồn tại, không kiểm tra còn hoạt động**. Người đang cầm access token có nhà xe cứ gọi `switch-branch` trước khi token hết hạn là nhận cặp token mới, nên **khóa nhà xe không cắt được họ** (không chỉ tối đa 15 phút như tưởng ban đầu).
   - Ngược lại, khóa **tài khoản** (`users.is_active=false`) có hiệu lực ngay ở request kế tiếp, vì bộ lọc nạp người dùng từ DB mỗi request.
   - Đây là phần của module auth, nhắn người phụ trách. Module tenant vẫn hoàn chỉnh.
3. **`GlobalExceptionHandler` trả 500 cho tham số đường dẫn sai kiểu** (ví dụ `GET /tenants/abc`). Nên 400. Là phần của người làm module chung, báo họ thêm handler cho `MethodArgumentTypeMismatchException`.
4. **Dùng chung entity và repository của module account** (`User`, `UserTenant`, `UserRepository`, `UserTenantRepository`). Chỉ **dùng**, không sửa. Nếu người làm account đổi tên field hay method thì code của bạn sẽ lỗi biên dịch, nên báo họ biết.
5. **Phần lõi "tìm hoặc tạo tài khoản rồi liên kết"** dùng chung với API tạo nhân viên của module account. Ban đầu viết trong `TenantServiceImpl`; khi module account bắt đầu làm API nhân viên, hai bên thống nhất rút ra một lớp chung để quy tắc không lệch.
6. **`/auth/refresh-token` không kiểm tra loại token** (token không có claim `token_type`): một access token 15 phút đổi được lấy refresh token 7 ngày mới. Ngược lại refresh token dùng như access chỉ vào được các API "chỉ cần đăng nhập" như `select-tenant`.
7. **Khóa bí mật JWT có giá trị mặc định cứng** trong `application.yaml` (`${JWT_SECRET:...}`), đọc theo UTF-8 dù comment ghi Base64. Môi trường chạy quên đặt `JWT_SECRET` thì ai có repo cũng ký được token giả.
8. **`switch-branch` không lọc `user_branches` theo `status` và `ended_at`**: nhân viên đã nghỉ vẫn chọn được chi nhánh.

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
- **Chặn đặt trùng giờ cùng một xe:** DB chưa có ràng buộc loại trừ (`EXCLUDE USING gist` trên `vehicle_id` và khoảng `pickup_time` đến `return_time`, chỉ tính đơn HOLD, CONFIRMED, HANDED_OVER). Không thuộc module tenant, nhưng nên có trước khi viết module booking, cần migration V7.
- **Tạo, sửa, khóa nhân viên của nhà xe:** module account.
