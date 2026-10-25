# KẾ HOẠCH LÀM MODULE TENANT

> **Ngày:** 06/10/2026
> **Thiết kế chi tiết đi kèm:** [Thiết kế chi tiết module tenant](2026-10-19-tenant-detail-design.md) (API, trường dữ liệu, mã lỗi, sequence diagram)
> **File này trả lời:** làm theo thứ tự nào, mỗi bước suy ra từ đâu, viết những file gì.

---

## 0. CÁCH SUY LUẬN

Mỗi mục trả lời một câu hỏi. Mục sau dựa trên kết quả mục trước.

| Mục | Câu hỏi | Kết quả |
| :-: | :--- | :--- |
| 1 | Làm để làm gì, trong giới hạn nào? | Mục tiêu, phạm vi, ràng buộc |
| 2 | Module phải làm được gì, theo quy tắc nào? | Danh sách API, quy tắc R1–R9 |
| 3 | Dữ liệu nằm ở bảng nào, ràng buộc nào ảnh hưởng tới code? | Bảng, ràng buộc, entity |
| 4 | Mỗi API nhận gì, trả gì? Kiểm tra nào ở DTO, kiểm tra nào ở Service? | DTO, bảng chia việc kiểm tra |
| 5 | Một request đi qua những lớp nào, mỗi bước truyền dữ liệu gì? | Luồng dữ liệu từng API |
| 6 | Service cần hỏi DB những câu gì? | Danh sách hàm Repository |
| 7 | Chia thành những lớp nào, đặt tên ra sao? | Cây file, quy tắc đặt tên |
| 8 | Sai ở đâu thì trả lỗi gì? | Bảng lỗi |
| 9 | Thao tác nào phải cùng thành công hoặc cùng thất bại? | Phạm vi transaction |
| 10 | Viết file nào trước? | Thứ tự code |
| 11 | Làm sao biết đã đúng? | Ca kiểm thử |

> Nguyên tắc: **đi từ nghiệp vụ và dữ liệu ra code.** Repository không nghĩ ra trước, mà suy ra từ những gì Service cần hỏi DB.

---

## 1. MỤC TIÊU, PHẠM VI, RÀNG BUỘC

**Mục tiêu:** Super Admin tạo và quản lý nhà xe cùng chủ nhà xe. Nhà xe vừa tạo dùng được ngay: có cấu hình, có vai trò, chủ đăng nhập được.

**Phạm vi:** 9 API dưới `/api/v1/tenants`, chỉ Super Admin gọi được.
**Ngoài phạm vi:** sửa cấu hình vận hành (chủ nhà xe tự làm), quản lý nhân viên, đổi vai trò thành viên.

**Ràng buộc:**
- Không đổi schema DB; dùng các bảng sẵn có từ migration V1–V7.
- Không sửa module của người khác (account, auth); chỉ dùng lại entity và repository của họ.
- Theo quy ước dự án: service gồm interface + Impl, lỗi nghiệp vụ dùng `AppException` + `ErrorCode`, phản hồi bọc `ApiResponse`.

---

## 2. YÊU CẦU NGHIỆP VỤ

### 2.1 API

| # | API | Việc |
| :-: | :--- | :--- |
| 1 | `POST /api/v1/tenants` | Tạo nhà xe kèm ít nhất một chủ |
| 2 | `GET /api/v1/tenants` | Danh sách: tìm theo tên/domain, lọc trạng thái, phân trang |
| 3 | `GET /api/v1/tenants/{tenantId}` | Chi tiết |
| 4 | `PUT /api/v1/tenants/{tenantId}` | Sửa thông tin |
| 5 | `PATCH /api/v1/tenants/{tenantId}/status` | Khóa / mở khóa |
| 6 | `DELETE /api/v1/tenants/{tenantId}` | Xóa mềm |
| 7 | `GET /api/v1/tenants/{tenantId}/admins` | Danh sách chủ |
| 8 | `POST /api/v1/tenants/{tenantId}/admins` | Thêm một chủ |
| 9 | `POST /api/v1/tenants/admin-email-check` | Kiểm tra email trước khi thêm chủ |

### 2.2 Quy tắc nghiệp vụ

| Mã | Quy tắc |
| :-: | :--- |
| R1 | Domain duy nhất toàn hệ thống |
| R2 | Tạo nhà xe kèm cấu hình mặc định và 3 vai trò (TENANT_ADMIN, STAFF, SALE) có quyền, trong một giao dịch. Thiếu vai trò thì không ai đăng nhập vào nhà xe được |
| R3 | Nhà xe mới có từ 1 đến 10 chủ; một email không xuất hiện hai lần trong cùng một lần gửi |
| R4 | Email mới: bắt buộc họ tên, mật khẩu, tạo tài khoản. Email có sẵn: chỉ liên kết, không đổi họ tên hay mật khẩu |
| R5 | Không làm chủ được nếu: nhà xe bị khóa, tài khoản bị khóa, là Super Admin, đã là chủ (409), đã có vai trò khác trong nhà xe (STAFF, SALE hoặc vai trò tự tạo) |
| R6 | Kiểm tra hết mọi chủ rồi mới ghi |
| R7 | Xóa là xóa mềm (`is_active = false`); đặt trạng thái đang có vẫn thành công |
| R8 | Khi sửa: trường tùy chọn `null` giữ nguyên, chuỗi rỗng xóa |
| R9 | Kiểm tra email trả lý do giống hệt lỗi thật khi thêm chủ |

---

## 3. DỮ LIỆU

### 3.1 Bảng và ràng buộc ảnh hưởng tới code

| Bảng | Ràng buộc | Ảnh hưởng tới code |
| :--- | :--- | :--- |
| `tenants` | `domain` UNIQUE; `plan_tier` 1–4; khóa ngoại từ bảng khác `ON DELETE CASCADE` | R1 có chốt cuối ở DB; xóa cứng sẽ mất toàn bộ dữ liệu nhà xe nên phải xóa mềm (R7) |
| `tenant_configs` | PK `tenant_id`, các cột NOT NULL có DEFAULT | Phải tạo cùng lúc với nhà xe (R2) |
| `roles` | Duy nhất theo `(tenant_id, code)` | Tìm vai trò luôn theo cả nhà xe và mã, **không** theo mã đơn thuần |
| `permissions`, `role_permissions` | Quyền dùng chung | Gán quyền bằng `INSERT ... SELECT` theo mã quyền |
| `users` | `email` UNIQUE | Tra tài khoản theo email (R4) |
| `user_tenants` | PK `(user_id, tenant_id)` | Mỗi người một vai trò trong một nhà xe (R5) |

### 3.2 Entity

| Entity | Thuộc module | Việc phải làm |
| :--- | :--- | :--- |
| `Tenant` | tenant | Viết: `@Setter` để sửa, `@CreationTimestamp` / `@UpdateTimestamp` cho hai cột thời gian |
| `TenantConfig` | tenant | Viết, kèm hàm tạo `withDefaults(tenantId)` |
| `User`, `UserTenant`, `Role` | account | Dùng lại, không sửa |

### 3.3 Hai điểm dễ sai
- **Giá trị mặc định của `tenant_configs` phải đặt trong Java.** Hibernate sinh `INSERT` đủ mọi cột, cột null thì ghi null, nên DEFAULT của DB không được dùng tới và vi phạm NOT NULL. Đặt trong `TenantConfig.withDefaults`: giữ chỗ 30 phút, phí trễ 100.000 đ/giờ, hoa hồng 5,00%.
- **Entity của module account chỉ lưu id**, không có `@ManyToOne`. Truy vấn nối bảng phải viết `JOIN ... ON` tường minh.

---

## 4. DỮ LIỆU VÀO VÀ RA

### 4.1 DTO

| API | Request | Response |
| :-: | :--- | :--- |
| 1 | `CreateTenantRequestDTO` (name, domain, planTier, logoUrl, contactEmail, contactPhone, settings, admins) | `CreateTenantResponseDTO` (tenant, admins) |
| 2 | query: keyword, isActive, page, size | `PagedModel<TenantResponseDTO>` |
| 3 | `tenantId` | `TenantResponseDTO` |
| 4 | `UpdateTenantRequestDTO` | `TenantResponseDTO` |
| 5 | `UpdateTenantStatusRequestDTO` (isActive) | `TenantResponseDTO` |
| 6 | `tenantId` | không có dữ liệu |
| 7 | `tenantId` | `List<TenantAdminResponseDTO>` (userId, email, fullName, isActive, joinedAt) |
| 8 | `AdminAssignmentRequestDTO` (email, fullName, password) | `AdminAssignmentResponseDTO` (email, accountCreated) |
| 9 | `AdminEmailCheckRequestDTO` (email, tenantId) | `AdminEmailCheckResponseDTO` (email, accountExists, fullName, canBeAdmin, reason) |

`AdminAssignmentRequestDTO` dùng chung cho phần tử của `admins` (API 1) và API 8. `AdminAssignmentResponseDTO` dùng chung cho kết quả của API 1 và API 8.

### 4.2 Kiểm tra đặt ở đâu

**DTO kiểm tra hình thức** (không cần DB). **Service kiểm tra nghiệp vụ** (cần DB hoặc cần so nhiều phần tử). Service không kiểm tra lại điều DTO đã chặn.

| Kiểm tra | Đặt ở |
| :--- | :--- |
| Bắt buộc, độ dài, định dạng email | DTO: `@NotBlank`, `@Size`, `@Email` |
| Domain chữ thường, số, `.` và `-` | DTO: `@Pattern` (nên Service nhận domain đã đúng dạng, không cần chuẩn hóa) |
| Danh sách chủ có 1–10 phần tử | DTO: `@NotEmpty` + `@Size(max = 10)` + `@Valid` cho từng phần tử. Giới hạn trên vì mỗi chủ tốn truy vấn và một lần băm mật khẩu. Frontend khóa nút thêm khi đủ 10 dòng |
| Mật khẩu 8–72 ký tự, có chữ và số | DTO: `@Pattern` |
| Domain trùng (R1) | Service |
| Email lặp trong danh sách (R3) | Service |
| Họ tên, mật khẩu bắt buộc khi email mới (R4) | Service, vì phụ thuộc email đã có tài khoản chưa |
| R5 | Service |
| `settings` là JSON hợp lệ | Service |

Service vẫn **chuẩn hóa email** (bỏ khoảng trắng, chữ thường), vì `@Email` chấp nhận chữ hoa.

---

## 5. LUỒNG DỮ LIỆU

### 5.1 Tạo nhà xe (API 1)

| Bước | Lớp / hàm | Nhận | Làm | Truyền tiếp |
| :-: | :--- | :--- | :--- | :--- |
| 1 | `TenantController.createTenant` | JSON, token | `@Valid`; lấy id người gọi từ `UserPrincipal` | `request`, `actorId` |
| 2 | `TenantServiceImpl.createTenant` | `request` | Kiểm tra domain (R1), `settings` | |
| 3 | `TenantAdminService.validateAdminRequests` | danh sách chủ, `tenant = null` | Từng chủ: chuẩn hóa email, R3, R4, R5. **Chưa ghi** (R6) | `List<ValidatedAdmin>` |
| 4 | `saveTenant` | `request` | Lưu nhà xe, gói mặc định FREE | `Tenant` (đã có id) |
| 5 | `TenantConfig.withDefaults` | `tenantId` | Lưu cấu hình (R2) | |
| 6 | `createDefaultRoles` | `tenantId` | Tạo 3 vai trò và quyền (R2) | id vai trò chủ |
| 7 | `TenantAdminService.addAdminsToTenant` | `tenantId`, id vai trò chủ, `List<ValidatedAdmin>` | Email mới thì tạo `User`; lưu `UserTenant` | `List<AdminAssignmentResponseDTO>` |
| 8 | `TenantServiceImpl` | | Dựng `CreateTenantResponseDTO` | Controller trả 201 |

**Kiểu trung gian `ValidatedAdmin`** mang kết quả từ bước 3 sang bước 7: email đã chuẩn hóa, họ tên, mật khẩu, tài khoản có sẵn (hoặc `null` nếu email mới). Nhờ có nó:
- Bước 7 không phải tra lại DB.
- Bước 3 kiểm tra xong cả danh sách mới sang bước 4 (R6).

```java
// Khai báo lồng trong interface TenantAdminService: chỉ phục vụ hai bước kiểm tra và ghi của service này
// Một chủ nhà xe đã qua kiểm tra, sẵn sàng để ghi
record ValidatedAdmin(String email, String fullName, String password, User existingUser) {
  public boolean isNewAccount() { return existingUser == null; }
}
```

### 5.2 Thêm chủ (API 8)

| Bước | Làm |
| :-: | :--- |
| 1 | Controller `@Valid`, gọi `TenantAdminService.addTenantAdmin(tenantId, request, actorId)` |
| 2 | Tìm nhà xe (404 nếu không có), tìm vai trò chủ của nhà xe |
| 3 | `validateAdminRequests(một request, tenant, adminRoleId)`: R4, R5 (có nhà xe nên xét thêm nhà xe bị khóa, đã là thành viên) |
| 4 | `addAdminsToTenant`, như bước 7 của API 1 |

**Mọi kiểm tra nằm ở bước kiểm tra, bước ghi chỉ ghi.**

### 5.3 Kiểm tra email (API 9)

| Bước | Làm |
| :-: | :--- |
| 1 | Có `tenantId` thì tìm nhà xe và vai trò chủ |
| 2 | Chuẩn hóa email, tìm tài khoản |
| 3 | Gọi `findRejection(tenant, user, adminRoleId)`: **cùng hàm** với API 1, 8 |
| 4 | Có lý do thì `canBeAdmin = false`, `reason` là thông báo của lý do đó |

Dùng chung một hàm nên lý do luôn khớp lỗi thật (R9).

### 5.4 Các API còn lại
- **2:** Controller dựng `PageRequest` (page ≥ 0, size 1–100, `createdAt` giảm dần); Service đổi từ khóa thành `%từ khóa%` chữ thường; Repository nhận `Pageable` nên Spring Data tự sinh `LIMIT/OFFSET` và câu `COUNT`.
- **3:** tìm theo id, không có thì 404.
- **4:** tìm nhà xe; domain đổi thì kiểm tra trùng loại trừ chính nó; áp R8; `saveAndFlush` để `updatedAt` trả về là giờ mới.
- **5, 6:** đã đúng trạng thái thì trả luôn (R7); API 6 gọi API 5 với `false`.
- **7:** tìm nhà xe (404), truy vấn trả thẳng `TenantAdminResponseDTO`.

---

## 6. SUY RA REPOSITORY TỪ NHU CẦU CỦA SERVICE

Đi lại các luồng ở mục 5. Mỗi lần Service cần **hỏi** hoặc **ghi** DB là một dòng.

| Service cần | Câu hỏi với DB | Hàm Repository | Viết mới / có sẵn |
| :--- | :--- | :--- | :--- |
| R1 khi tạo | Domain này đã có nhà xe nào dùng chưa? | `TenantRepository.existsByDomain` | Viết mới (Spring Data tự sinh từ tên hàm) |
| R1 khi sửa | Domain này có nhà xe **khác** dùng chưa? | `TenantRepository.existsByDomainAndIdNot` | Viết mới (tự sinh) |
| Tìm nhà xe | Có nhà xe id này không? | `findById` | Có sẵn trong `JpaRepository` |
| API 2 | Nhà xe khớp từ khóa, trạng thái, trang thứ n | `TenantRepository.search(keywordPattern, isActive, Pageable)` | Viết mới (`@Query` JPQL) |
| Lưu nhà xe, cấu hình | | `save`, `saveAndFlush` | Có sẵn |
| R2 | Tạo vai trò; gán mọi quyền; gán một số quyền | `TenantRoleRepository.insertSystemRole`, `grantAllPermissions`, `grantPermissions` | Viết mới (native `INSERT ... SELECT`) |
| Vai trò chủ | Vai trò TENANT_ADMIN của nhà xe này là dòng nào? | `TenantRoleRepository.findByTenantIdAndCode` | Viết mới (tự sinh) |
| R4, R5 | Email này đã có tài khoản chưa? | `UserRepository.findByEmail` | Có sẵn (module account) |
| R5 | Người này đã thuộc nhà xe này chưa, vai trò gì? | `UserTenantRepository.findByUserIdAndTenantId` | Có sẵn (module account) |
| Ghi chủ | Lưu tài khoản mới, lưu liên kết | `UserRepository.save`, `UserTenantRepository.save` | Có sẵn |
| API 7 | Những ai là chủ của nhà xe này? | `TenantUserTenantRepository.findAdmins` | Viết mới (`@Query` JPQL, trả DTO) |

**Vì sao có repository riêng cho bảng của module account (`roles`, `user_tenants`):**
- Module account đang được phát triển song song, thêm hàm vào `RoleRepository` / `UserTenantRepository` dễ xung đột khi merge. Các truy vấn này cũng chỉ module tenant dùng.
- Quy tắc đặt tên: entity của module mình là `<Entity>Repository`; entity của module khác là `Tenant<Entity>Repository`. Không đặt trùng tên `RoleRepository` vì Spring đặt tên bean theo tên interface, hai interface cùng tên sẽ trùng bean và ứng dụng không khởi động được.
- `RoleRepository.findByCode` không dùng được, vì nhiều nhà xe có cùng mã vai trò.

```java
// Trả thẳng DTO bằng constructor expression: không phải ép kiểu, không kéo password_hash lên bộ nhớ
@Query("""
  SELECT new com.carrental.car_rental_backend.tenant.dto.response.TenantAdminResponseDTO(
    u.id, u.email, u.fullname, u.isActive, ut.joinedAt)
  FROM UserTenant ut
  JOIN User u ON u.id = ut.userId
  JOIN Role r ON r.id = ut.roleId
  WHERE ut.tenantId = :tenantId AND r.code = 'TENANT_ADMIN'
  ORDER BY ut.joinedAt
""")
List<TenantAdminResponseDTO> findAdmins(@Param("tenantId") UUID tenantId);
```

---

## 7. CHIA LỚP VÀ ĐẶT TÊN

### 7.1 Các file cần viết

```
tenant/
├── constant/
│   ├── DefaultRole.java              3 vai trò mặc định và mã quyền của từng vai trò
│   └── AdminRejection.java           các lý do không thể làm chủ, kèm mã lỗi và thông báo
├── controller/
│   └── TenantController.java
├── dto/
│   ├── request/   CreateTenantRequestDTO, UpdateTenantRequestDTO, UpdateTenantStatusRequestDTO,
│   │              AdminAssignmentRequestDTO, AdminEmailCheckRequestDTO
│   └── response/  TenantResponseDTO, CreateTenantResponseDTO, AdminAssignmentResponseDTO,
│                  TenantAdminResponseDTO, AdminEmailCheckResponseDTO
├── entity/
│   ├── Tenant.java
│   └── TenantConfig.java
├── repository/
│   ├── TenantRepository.java
│   ├── TenantConfigRepository.java
│   ├── TenantRoleRepository.java
│   └── TenantUserTenantRepository.java
└── service/
    ├── TenantService.java            API 1–6: nhà xe
    ├── TenantAdminService.java       API 7–9: chủ nhà xe, và hai bước kiểm tra / ghi chủ dùng cho API 1
    └── impl/
        ├── TenantServiceImpl.java
        └── TenantAdminServiceImpl.java
```

**Vì sao tách hai service:** nhà xe và chủ nhà xe là hai nhóm việc, thay đổi vì lý do khác nhau. `TenantServiceImpl.createTenant` gọi sang `TenantAdminService` để kiểm tra và ghi chủ, vẫn trong cùng giao dịch.

### 7.2 Quy tắc đặt tên
- Tên nói **nghiệp vụ**, không nói kỹ thuật. Tránh `prepare`, `process`, `handle`, `data`, `info`.
- `check...`: kiểm tra, sai thì ném lỗi.
- `find...`: tìm, có thể không thấy (`Optional`). `find...OrThrow`: không thấy thì 404.
- `validate...`: kiểm tra một đầu vào và trả dạng đã kiểm tra.
- `boolean`: `is...`, `has...`. Danh sách: danh từ số nhiều.

| Hàm / kiểu chính | Nghĩa |
| :--- | :--- |
| `validateAdminRequests` / `validateAdminRequest` | Kiểm tra danh sách / một yêu cầu thêm chủ |
| `ValidatedAdmin` | Một chủ đã qua kiểm tra |
| `emailsInRequest` | Các email đã gặp trong lần gửi này (phát hiện R3) |
| `addAdminsToTenant` | Ghi chủ vào nhà xe |
| `findRejection` | Lý do người này không làm chủ được (nếu có) |
| `checkAdminEmail` | Kiểm tra email (API 9) |
| `checkDomainNotTaken` | Domain chưa ai dùng, ngược lại ném 409 |
| `findTenantOrThrow`, `findAdminRoleOrThrow` | Tìm nhà xe, vai trò chủ; không có thì báo lỗi |

### 7.3 Một hàm trả lý do, dùng chung cho thêm chủ và kiểm tra

```java
// Lý do một người không thể làm chủ nhà xe
public enum AdminRejection {
  TENANT_LOCKED(ErrorCode.BAD_REQUEST, "Nhà xe đang bị khóa, không thể thêm chủ nhà xe !"),
  ACCOUNT_LOCKED(ErrorCode.BAD_REQUEST, "Tài khoản đang bị khóa !"),
  SUPER_ADMIN(ErrorCode.BAD_REQUEST, "Tài khoản Super Admin không thể làm chủ nhà xe"),
  ALREADY_ADMIN(ErrorCode.TENANT_ADMIN_ALREADY_ASSIGNED, "Người này đã là chủ nhà xe của nhà xe này"),
  // Đã thuộc nhà xe với vai trò khác TENANT_ADMIN (STAFF, SALE hoặc vai trò tự tạo)
  HAS_OTHER_ROLE(ErrorCode.BAD_REQUEST, "Người này đang là nhân viên của nhà xe, chưa hỗ trợ nâng quyền");
  // errorCode, message, toException()
}
```

```java
// tenant = null khi đang tạo nhà xe mới; user = null khi email chưa có tài khoản
private Optional<AdminRejection> findRejection(Tenant tenant, User user, UUID adminRoleId) {
  if (tenant != null && !tenant.isActive()) return Optional.of(AdminRejection.TENANT_LOCKED);
  if (user == null) return Optional.empty();
  if (!Boolean.TRUE.equals(user.getIsActive())) return Optional.of(AdminRejection.ACCOUNT_LOCKED);
  if (Boolean.TRUE.equals(user.getIsSuperAdmin())) return Optional.of(AdminRejection.SUPER_ADMIN);
  if (tenant == null) return Optional.empty();
  return userTenantRepository.findByUserIdAndTenantId(user.getId(), tenant.getId())
    .map(membership -> adminRoleId.equals(membership.getRoleId())
      ? AdminRejection.ALREADY_ADMIN
      : AdminRejection.HAS_OTHER_ROLE);
}
```

- Thêm chủ: `findRejection(...).ifPresent(rejection -> { throw rejection.toException(); });`
- Kiểm tra: `reason = findRejection(...).map(AdminRejection::getMessage).orElse(null);`

### 7.4 `createTenant` đọc như các bước nghiệp vụ

```java
public CreateTenantResponseDTO createTenant(CreateTenantRequestDTO request, UUID actorId) {
  this.checkDomainNotTaken(request.getDomain());                                                             // R1
  List<ValidatedAdmin> admins = this.tenantAdminService.validateAdminRequests(request.getAdmins(), null, null); // R3–R6

  Tenant tenant = this.saveTenant(request);
  this.tenantConfigRepository.save(TenantConfig.withDefaults(tenant.getId()));                               // R2
  UUID adminRoleId = this.createDefaultRoles(tenant.getId());                                                // R2
  List<AdminAssignmentResponseDTO> results = this.tenantAdminService.addAdminsToTenant(tenant.getId(), adminRoleId, admins);

  return CreateTenantResponseDTO.builder().tenant(this.toTenantResponse(tenant)).admins(results).build();
}
```

---

## 8. LỖI

Chi tiết mã lỗi và thông báo ở [thiết kế chi tiết, mục 3.1 và 4](2026-10-19-tenant-detail-design.md). Tóm tắt nơi phát hiện:

| Lỗi | Phát hiện ở | HTTP |
| :--- | :--- | :-: |
| Thiếu trường, sai định dạng | DTO `@Valid` | 400 |
| Không phải Super Admin | `@PreAuthorize("hasRole('SUPER_ADMIN')")` cấp lớp Controller | 403 |
| Không có nhà xe | `findTenantOrThrow` | 404 |
| Domain trùng (R1) | `checkDomainNotTaken` | 409 |
| Email lặp, thiếu họ tên/mật khẩu cho email mới | `validateAdminRequest` | 400 |
| R5 | `findRejection` → `AdminRejection` | 400 / 409 |
| Nhà xe thiếu vai trò chủ (dữ liệu hỏng) | `findAdminRoleOrThrow` | 500 |

---

## 9. TRANSACTION

- Hai service gắn `@Transactional` cấp lớp; hàm chỉ đọc gắn `@Transactional(readOnly = true)`.
- `createTenant` gọi sang `TenantAdminService` với kiểu mặc định `REQUIRED`, nên **dùng chung** giao dịch. Lỗi ở bất kỳ bước nào thì rollback cả nhà xe, cấu hình, vai trò và tài khoản vừa tạo (R2).
- Không gọi dịch vụ bên ngoài trong giao dịch. Mã hóa mật khẩu chỉ dùng CPU.

---

## 10. THỨ TỰ VIẾT CODE

Đi từ chỗ **ít phụ thuộc** tới chỗ **phụ thuộc nhiều**. Mỗi bước xong phải biên dịch được và chạy được test của bước đó.

| Bước | Viết | Vì sao ở vị trí này | Kiểm tra |
| :-: | :--- | :--- | :--- |
| 0 | Tạo nhánh theo quy ước (ví dụ `feature/tenant-management`); đọc lại module auth để theo khuôn | | |
| 1 | Entity `Tenant`, `TenantConfig` | Mọi lớp khác dựa vào entity | Ứng dụng khởi động, Hibernate không báo lỗi ánh xạ |
| 2 | DTO request, response | Chốt hợp đồng API trước, khớp thiết kế chi tiết | |
| 3 | `DefaultRole`, `AdminRejection` | Hằng số nghiệp vụ, không phụ thuộc gì | |
| 4 | Repository theo bảng mục 6 | Service cần chúng | Test repository với DB thật: `search`, `findAdmins`, ba câu INSERT vai trò |
| 5 | `TenantAdminService` + Impl | `TenantService` phụ thuộc vào nó | Unit test (Mockito): `findRejection`, `validateAdminRequests`, `addAdminsToTenant`, API 7–9 |
| 6 | `TenantService` + Impl | | Unit test: API 1–6 |
| 7 | `TenantController` | Lớp ngoài cùng | Test tích hợp gọi API thật (MockMvc + DB) |
| 8 | Chạy thử bằng Swagger với tài khoản Super Admin và tài khoản thường | | Đi qua 9 API, kiểm tra dữ liệu trong DB |
| 9 | Cập nhật README nếu có thay đổi cách chạy | | |

---

## 11. KIỂM THỬ

### 11.1 `findRejection` (mỗi dòng một test)

| Tình huống | Kết quả |
| :--- | :--- |
| Email mới, không có nhà xe | không có lý do |
| Tài khoản bình thường, không có nhà xe | không có lý do |
| Tài khoản bị khóa | `ACCOUNT_LOCKED` |
| Tài khoản Super Admin | `SUPER_ADMIN` |
| Nhà xe bị khóa (kể cả email mới) | `TENANT_LOCKED` |
| Nhà xe khóa **và** tài khoản khóa | `TENANT_LOCKED` (xét nhà xe trước) |
| Đã là chủ của nhà xe này | `ALREADY_ADMIN` |
| Đang là STAFF của nhà xe này | `HAS_OTHER_ROLE` |
| Đang là SALE của nhà xe này | `HAS_OTHER_ROLE` |
| Là chủ của nhà xe **khác** | không có lý do |

### 11.2 Theo API

| API | Ca cần có |
| :-: | :--- |
| 1 | Thành công với email mới và email có sẵn; DB có đủ 1 nhà xe, 1 cấu hình, 3 vai trò, 22 quyền; trùng domain 409; email lặp; email mới thiếu họ tên hoặc mật khẩu; một chủ không hợp lệ thì **không ghi gì** (R6) |
| 2 | Tìm theo tên, theo domain, không phân biệt hoa thường; lọc trạng thái; `size` ngoài 1–100 bị kẹp |
| 3 | Có; không có (404) |
| 4 | Trường tùy chọn null giữ nguyên, rỗng thì xóa; đổi domain trùng nhà xe khác 409; giữ domain cũ không báo trùng |
| 5, 6 | Đổi trạng thái; gọi lặp vẫn thành công; xóa hai lần vẫn thành công |
| 7 | Chỉ có chủ, không có STAFF hay SALE; sắp theo ngày tham gia |
| 8 | Các lý do của 11.1 qua API; nhà xe không tồn tại 404 |
| 9 | Email mới; email có sẵn; mỗi lý do trả đúng câu thông báo như API 8 |
| Mọi API | Tài khoản không phải Super Admin nhận 403; không có token nhận 401 |

---

## 12. VIỆC CẦN NHÓM QUYẾT ĐỊNH

| Việc | Ghi chú |
| :--- | :--- |
| Hai yêu cầu đồng thời cùng domain hoặc email trả 500 | Đề xuất bắt `DataIntegrityViolationException` trả 409 trong `GlobalExceptionHandler` (dùng chung cả dự án) |
| Chống ghi đè khi hai người cùng sửa | Thêm `@Version`, cần migration |
| Giữ trường `settings` không | Giao diện chưa dùng |
| Giữ cả API 5 và API 6 không | Cùng một việc |
| MapStruct thay gán tay | Thêm dependency cho cả dự án |
| Mã lỗi riêng cho các lý do từ chối | Hiện 4/5 lý do dùng chung `ERR_400`, client không phân biệt được nếu không so câu chữ. Đề xuất: `TENANT_LOCKED`, `ACCOUNT_LOCKED`, `INVALID_OPERATION` (Super Admin, có vai trò khác; theo mục 8.1 onboarding). Frontend và test hiện không phụ thuộc `ERR_400` |
| Câu thông báo của `HAS_OTHER_ROLE` | Câu hiện tại gọi cả SALE là "nhân viên". Đề xuất: "Người này đã có vai trò khác trong nhà xe, chưa hỗ trợ đổi vai trò". Đổi câu là đổi API |

---

## 13. BA CÂU HỎI TRƯỚC KHI COMMIT

Ví dụ với API tạo nhà xe:
1. **API làm gì?** Super Admin tạo một nhà xe dùng được ngay: cấu hình mặc định, 3 vai trò có quyền, ít nhất một chủ đăng nhập được.
2. **Chạy khi nào, ảnh hưởng dữ liệu nào?** Khi Super Admin tạo nhà xe. Ghi `tenants`, `tenant_configs`, `roles`, `role_permissions`, `users` (email mới), `user_tenants`, trong một giao dịch.
3. **Lỗi trả mã gì?** 400 dữ liệu sai hoặc chủ không hợp lệ, 403 không phải Super Admin, 409 trùng domain hoặc đã là chủ.

---

## PHỤ LỤC: KHUNG DÙNG LẠI CHO MODULE KHÁC

1. Module làm được gì? Ai được làm? → danh sách API
2. Quy tắc nào không được vi phạm? → R1, R2...
3. Dữ liệu ở bảng nào? Ràng buộc nào (UNIQUE, khóa ngoại, CASCADE, NOT NULL) ảnh hưởng tới code?
4. Mỗi API nhận gì, trả gì? Kiểm tra nào ở DTO, kiểm tra nào ở Service?
5. Đi một request qua từng lớp: mỗi bước nhận gì, làm gì, truyền gì cho bước sau? Có cần kiểu dữ liệu trung gian không?
6. Từ mục 5, liệt kê mọi câu Service cần hỏi DB → hàm Repository. Hàm nào có sẵn, hàm nào phải viết?
7. Chia lớp, đặt tên theo nghiệp vụ.
8. Sai ở đâu, trả mã HTTP nào?
9. Thao tác nào phải cùng thành công hoặc cùng thất bại → transaction.
10. Viết từ chỗ ít phụ thuộc tới chỗ phụ thuộc nhiều, mỗi bước chạy test.
11. Ca test: thành công, dữ liệu sai, trùng, không tìm thấy, không có quyền, biên.
