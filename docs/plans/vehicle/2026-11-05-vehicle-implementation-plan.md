# KẾ HOẠCH LÀM MODULE LOẠI XE VÀ XE

> **Ngày:** 05/11/2026 · **Trạng thái:** Bản nháp, chưa triển khai
> **Thiết kế chi tiết đi kèm:** [Thiết kế chi tiết](2026-11-05-vehicle-detail-design.md) (quy tắc, API, mã lỗi, sequence diagram) · [Đặc tả API](2026-11-05-vehicle-api-spec.md)
> **File này trả lời:** làm theo thứ tự nào, mỗi bước suy ra từ đâu, viết những file gì.
> **Khuôn:** theo [kế hoạch module tenant](../tenant/2026-10-06-tenant-implementation-plan.md) và phụ lục "khung dùng lại cho module khác" của file đó.

---

> **Nguồn chuẩn:** hợp đồng API và quy tắc nghiệp vụ theo [Thiết kế chi tiết](2026-11-05-vehicle-detail-design.md); khi các tài liệu khác nhau thì theo file đó.

## 0. CÁCH SUY LUẬN

Giống module tenant: mỗi mục trả lời một câu hỏi, mục sau dựa trên kết quả mục trước.

| Mục | Câu hỏi | Kết quả |
| :-: | :--- | :--- |
| 1 | Làm để làm gì, trong giới hạn nào? | Mục tiêu, phạm vi, ràng buộc |
| 2 | Module phải làm được gì, theo quy tắc nào? | 12 API, quy tắc LX-R, XE-R |
| 3 | Dữ liệu nằm ở bảng nào, ràng buộc nào ảnh hưởng tới code? | Bảng, entity, điểm dễ sai |
| 4 | Mỗi API nhận gì, trả gì? Kiểm tra nào ở DTO, kiểm tra nào ở Service? | DTO, bảng chia việc kiểm tra |
| 5 | Một request đi qua những lớp nào? | Luồng dữ liệu |
| 6 | Service cần hỏi DB những câu gì? | Danh sách hàm Repository |
| 7 | Chia thành những lớp nào, đặt tên ra sao? | Cây file, quy tắc đặt tên |
| 8 | Sai ở đâu thì trả lỗi gì? | Bảng lỗi |
| 9 | Thao tác nào phải cùng thành công hoặc cùng thất bại? | Phạm vi transaction |
| 10 | Viết file nào trước? | Thứ tự code |
| 11 | Làm sao biết đã đúng? | Ca kiểm thử |

---

## 1. MỤC TIÊU, PHẠM VI, RÀNG BUỘC

**Mục tiêu:**
- Super Admin duy trì danh mục loại xe dùng chung.
- Chủ nhà xe khai báo và quản lý xe của mình; nhân viên và Sale xem được xe trong phạm vi của họ, để module booking dùng tiếp.

**Phạm vi:** 6 API loại xe (`/api/v1/vehicle-types`), 6 API xe (`/api/v1/vehicles`).
**Ngoài phạm vi:** quota số xe, upload ảnh, xe trống theo ngày, đặt/gỡ `RENTED`, odo và nhiên liệu, thống kê theo trạng thái. Chi tiết ở [DD, mục 1 và 7](2026-11-05-vehicle-detail-design.md).

**Ràng buộc:**
- Dùng các bảng sẵn có từ V1–V7, bổ sung một migration mới tạo unique index tên loại xe theo `UPPER(BTRIM(name))` trước LX1/LX4. Phối hợp số version còn trống, dự kiến V8; giữ cấu trúc các cột hiện có.
- Không sửa `GlobalExceptionHandler`. Chỉ **thêm** mã lỗi vào `ErrorCode`, không sửa dòng có sẵn (module tenant đã làm như vậy).
- Không thêm hàm vào repository của module khác (`BranchRepository`). Dùng hàm có sẵn; cần truy vấn mới thì đặt repository riêng trong module vehicle.
- Theo quy ước dự án: service gồm interface + Impl, lỗi nghiệp vụ dùng `AppException` + `ErrorCode`, phản hồi bọc `ApiResponse`.

**Phụ thuộc người khác:** không có phần nào chặn việc bắt đầu.

| Cần | Có sẵn chưa | Ghi chú |
| :--- | :--- | :--- |
| Tenant, chi nhánh, vai trò của người gọi | Có: `UserPrincipal` (`tenantId`, `roleCode`, mã quyền) và `TenantContext.getBranchId()` | Do module auth đặt trong `JwtAuthenticationFilter` |
| Kiểm tra chi nhánh thuộc nhà xe | Có: `BranchRepository.findByIdAndTenantId` | Chỉ đọc |
| Bảo đảm tên loại xe duy nhất không phân biệt hoa thường khi ghi đồng thời | Cần migration mới trước LX1/LX4 | Unique index `idx_vehicle_types_normalized_name` trên `UPPER(BTRIM(name))`, quy trình theo DD mục 2 |
| Đếm đơn chưa xong của xe | Bảng `bookings` có, entity chưa có | Native query trong `VehicleRepository` đến khi có module booking |
| Lỗi UNIQUE khi request đồng thời trả 409 | Chưa | Người phụ trách module chung, như module tenant |
| Quyền cho STAFF đổi trạng thái xe | Chưa | Cần migration và sửa `DefaultRole`; chờ nhóm quyết ([DD, mục 7](2026-11-05-vehicle-detail-design.md#7-giới-hạn-đã-biết-và-việc-cần-quyết-định) dòng 3) |

---

## 2. YÊU CẦU NGHIỆP VỤ

Danh sách 12 API và quy tắc ở [DD, mục 1](2026-11-05-vehicle-detail-design.md#1-tổng-quan). Tóm tắt những quy tắc ảnh hưởng nhiều nhất tới code:

| Mã | Tóm tắt | Vì sao quan trọng |
| :-: | :--- | :--- |
| XE-R1 | Mọi truy vấn xe lọc theo `tenant_id` của token | DB **không có RLS**; quên lọc là lộ xe nhà xe khác (IDOR) |
| XE-R5 | Phạm vi xem theo vai trò (chủ, STAFF, vai trò khác) | Quyết định tham số của câu tìm kiếm |
| XE-R11 | STAFF xem xe phải kiểm tra lại chi nhánh và phân công ACTIVE trên DB ở mỗi request | Access token còn hạn có thể giữ chi nhánh đã bị khóa hoặc phân công đã bị thu hồi |
| XE-R7 | Bảng chuyển trạng thái; `RENTED` thuộc module booking | Một hàm kiểm tra dùng cho cả đổi trạng thái và xóa mềm |
| XE-R8 | Xóa mềm bằng `INACTIVE`, chặn khi còn đơn | Đọc bảng `bookings` của module khác |
| LX-R4 | Chỉ xóa loại xe chưa có xe dùng | Module loại xe hỏi bảng `vehicles` |

---

## 3. DỮ LIỆU

### 3.1 Bảng và ràng buộc ảnh hưởng tới code

| Bảng | Ràng buộc | Ảnh hưởng tới code |
| :--- | :--- | :--- |
| `vehicle_types` | V1–V7 có `name` UNIQUE phân biệt hoa thường; cần unique index mới trên `UPPER(BTRIM(name))`; không có `tenant_id` | LX-R1 dùng cùng biểu thức chuẩn hóa ở Service và DB, kể cả ghi đồng thời |
| `vehicles` | UNIQUE `(tenant_id, license_plate)` | XE-R2: chuẩn hóa chữ in hoa trước khi kiểm tra và lưu |
| `vehicles` | FK `(branch_id, tenant_id) → branches` | DB đã chặn chi nhánh của nhà xe khác; Service vẫn kiểm tra để trả 400 thay vì 500 |
| `vehicles` | FK `vehicle_type_id → vehicle_types`, không có `ON DELETE` | DB chặn xóa loại xe đang dùng; Service kiểm tra trước để trả 409 |
| `vehicles` | `status` INTEGER, CHECK 1–5 | Enum `BaseEnum<Integer>` |
| `bookings` | `vehicle_id` FK `ON DELETE SET NULL` | Xóa thật xe làm đơn cũ mất xe, nên chỉ xóa mềm |

### 3.2 Entity

| Entity | Thuộc module | Việc phải làm |
| :--- | :--- | :--- |
| `VehicleType` | vehicle_type | Viết mới: `@Getter @Setter @Builder`, `@CreationTimestamp` / `@UpdateTimestamp` như `Tenant` |
| `Vehicle` | vehicle | Viết mới: lưu `tenantId`, `branchId`, `vehicleTypeId` dạng UUID, không `@ManyToOne`; `images` là `List<String>` với `@JdbcTypeCode(SqlTypes.JSON)` |
| `VehicleStatus` + `VehicleStatusConverter` | common/enums/vehicle_status | Viết mới theo khuôn `PlanTierTenant` / `PlanTierTenantConverter`, nhưng kiểu `Integer` |
| `Branch` | branch | Dùng lại, không sửa |
| `UserBranch` và `UserBranchStatus` | account, common/enums/user_branch_status | Dùng lại để kiểm tra trạng thái phân công hiện tại của STAFF |

### 3.3 Những điểm dễ sai
- **`vehicles.status` là INTEGER, không phải SMALLINT.** V1 tạo cột `INTEGER`, V5 chỉ đổi CHECK. Tài liệu `Database-Schema.md` ghi SMALLINT là chưa đúng. Ứng dụng chạy `ddl-auto: validate`, nên enum dùng `Short` có thể làm ứng dụng không khởi động (suy ra, chưa chạy thử). Dùng `BaseEnum<Integer>`.
- **Giá trị mặc định đặt trong Java.** Hibernate ghi đủ mọi cột: `VehicleType.isActive = true`, `Vehicle.status = AVAILABLE`, `Vehicle.images = List.of()` khi tạo. Bài học từ `TenantConfig.withDefaults` của module tenant.
- **Không sắp danh sách xe theo `created_at`.** 60 xe seed có cùng `created_at`. Sắp theo `licensePlate` (duy nhất trong nhà xe), loại xe theo `name`.
- **Xe seed có `price_per_day = 0`.** Đọc vẫn được; test sửa xe seed phải gửi giá > 0.
- **Câu tìm kiếm trả thẳng DTO kèm phân trang cần `countQuery` riêng.** Spring Data không tự sinh câu `COUNT` đúng cho câu `SELECT new ...` có `JOIN`.
- **`keywordPattern` không được `null`** (bài học module tenant: PostgreSQL báo lỗi `lower(bytea)`). Từ khóa trống thì truyền `"%"`.

---

## 4. DỮ LIỆU VÀO VÀ RA

### 4.1 DTO

| API | Request | Response |
| :-: | :--- | :--- |
| LX1 | `CreateVehicleTypeRequestDTO` (name, description) | `VehicleTypeResponseDTO` |
| LX2 | query: keyword, isActive, page, size | `PagedModel<VehicleTypeResponseDTO>` |
| LX3 | `vehicleTypeId` | `VehicleTypeResponseDTO` |
| LX4 | `UpdateVehicleTypeRequestDTO` (name, description) | `VehicleTypeResponseDTO` |
| LX5 | `UpdateVehicleTypeStatusRequestDTO` (isActive) | `VehicleTypeResponseDTO` |
| LX6 | `vehicleTypeId` | không có dữ liệu |
| XE1 | `VehicleRequestDTO` (13 trường, [đặc tả XE1](2026-11-05-vehicle-api-spec.md#xe1-thêm-xe-post-apiv1vehicles)) | `VehicleResponseDTO` |
| XE2 | query: keyword, status, branchId, vehicleTypeId, page, size | `PagedModel<VehicleResponseDTO>` |
| XE3 | `vehicleId` | `VehicleResponseDTO` |
| XE4 | `VehicleRequestDTO` (dùng chung với XE1) | `VehicleResponseDTO` |
| XE5 | `UpdateVehicleStatusRequestDTO` (status) | `VehicleResponseDTO` |
| XE6 | `vehicleId` | không có dữ liệu |

`VehicleRequestDTO` dùng chung cho thêm và sửa, vì XE4 ghi đè cả form với cùng ràng buộc. Loại xe tách hai DTO tạo/sửa vì quy tắc `description` khi sửa khác (giữ nguyên khi `null`), giống module tenant tách `CreateTenantRequestDTO` / `UpdateTenantRequestDTO`.

### 4.2 Kiểm tra đặt ở đâu

**DTO kiểm tra hình thức** (không cần DB). **Service kiểm tra nghiệp vụ** (cần DB, cần ngày hiện tại hoặc so với dữ liệu cũ).

| Kiểm tra | Đặt ở |
| :--- | :--- |
| Bắt buộc, độ dài chuỗi | DTO: `@NotBlank`, `@NotNull` (UUID, enum, số), `@Size` |
| Ký tự biển số | DTO: `@Pattern` |
| Giá > 0, tối đa 10 chữ số phần nguyên, 2 chữ số thập phân | DTO: `@Positive` + `@Digits(integer = 10, fraction = 2)` |
| Tối đa 5 ảnh, mỗi ảnh là URL ≤ 500 | DTO: `@Size(max = 5)` trên danh sách; `@NotBlank @Size(max = 500) @Pattern` trên phần tử (`List<@NotBlank ... String>`) |
| Năm 1990 đến năm nay + 1 | Service: phụ thuộc ngày hiện tại, annotation không làm được |
| Biển số chữ in hoa | Service chuẩn hóa trước khi kiểm tra trùng |
| Trùng tên loại xe (LX-R1), trùng biển số (XE-R2) | Service |
| Chi nhánh thuộc nhà xe và hoạt động (XE-R3), loại xe đang dùng (XE-R4) | Service |
| Chuyển trạng thái (XE-R7), còn đơn chưa xong (XE-R8) | Service |
| Phạm vi xem (XE-R5) | Controller dựng `VehicleScope` gồm userId đã xác thực; Repository lọc theo scope |
| Quyền chi nhánh hiện tại của STAFF (XE-R11) | Service gọi `checkStaffBranchAccess(scope)` trước mỗi truy vấn danh sách và chi tiết xe, đọc `branches` và `user_branches` |

---

## 5. LUỒNG DỮ LIỆU

### 5.1 Thêm xe (XE1)

| Bước | Lớp / hàm | Nhận | Làm | Truyền tiếp |
| :-: | :--- | :--- | :--- | :--- |
| 1 | `VehicleController.createVehicle` | JSON, token | `@Valid`; lấy `tenantId` từ `UserPrincipal` | `request`, `tenantId` |
| 2 | `VehicleServiceImpl.createVehicle` | `request`, `tenantId` | Chuẩn hóa biển số, kiểm tra năm | biển số đã chuẩn hóa |
| 3 | `checkLicensePlateNotTaken` | `tenantId`, biển số | XE-R2, trùng thì 409 | |
| 4 | `findActiveBranchOrThrow` | `branchId`, `tenantId` | XE-R3 | `Branch` (lấy tên cho phản hồi) |
| 5 | `findSelectableVehicleTypeOrThrow` | `vehicleTypeId` | XE-R4 | `VehicleType` (lấy tên cho phản hồi) |
| 6 | `VehicleRepository.saveAndFlush` | `Vehicle` `AVAILABLE` | Lưu | `Vehicle` có id, thời gian |
| 7 | `toVehicleResponse(vehicle, branch, vehicleType)` | | Dựng DTO từ dữ liệu đã có, **không** truy vấn lại | Controller trả 201 |

### 5.2 Danh sách và chi tiết xe (XE2, XE3)

| Bước | Làm |
| :-: | :--- |
| 1 | Controller dựng `VehicleScope` từ `UserPrincipal` (id, tenantId, roleCode) và `TenantContext.getBranchId()` theo [DD, mục 3.1](2026-11-05-vehicle-detail-design.md#31-vehiclescope-phạm-vi-xem-xe-r5). STAFF thiếu chi nhánh thì 400 |
| 2 | Controller dựng `PageRequest` (page ≥ 0, size 1–100, `licensePlate` tăng dần) |
| 3 | Ở đầu `listVehicles()` và `getVehicleById()`, Service gọi `checkStaffBranchAccess(scope)`. Với STAFF, kiểm tra chi nhánh còn thuộc tenant, đang hoạt động và phân công của user còn ACTIVE. Sai điều kiện thì 403 trước khi đọc xe |
| 4 | Service chọn chi nhánh lọc: `scope.branchId()` nếu có, ngược lại `branchId` của query; đổi từ khóa thành `%từ khóa%` chữ thường |
| 5 | `VehicleRepository.search(...)` trả thẳng `VehicleResponseDTO` (JPQL `JOIN VehicleType`, `LEFT JOIN Branch`) |
| 6 | XE3 sau guard dùng cùng điều kiện phạm vi qua `findDetail`; không thấy thì 404 |

```java
// Phạm vi xem xe của người gọi. branchId = null nghĩa là cả nhà xe.
public record VehicleScope(UUID userId, UUID tenantId, UUID branchId, boolean includeInactive) {}
```

Đặt việc dựng scope ở một hàm riêng (ví dụ `VehicleScopeResolver.resolve(principal, activeBranchId)`), để test được từng vai trò mà không cần gọi HTTP.

`VehicleScopeResolver` chỉ dựng dữ liệu phạm vi từ người đã xác thực. `VehicleServiceImpl.checkStaffBranchAccess(scope)` kiểm tra quyền hiện tại bằng `BranchRepository.findByIdAndTenantId` và `UserBranchRepository.findByTenantIdAndUserIdAndBranchId` trước khi dùng scope để truy vấn xe. Dùng enum `UserBranchStatus.ACTIVE`. Các trạng thái khác, gồm null, bị từ chối với 403. Thông báo theo XE-R11 trong DD, dùng cùng chính sách với luồng refresh token của auth. Hai repository này đã có hàm cần dùng, chỉ bổ sung dependency vào Service.

### 5.3 Đổi trạng thái và xóa mềm (XE5, XE6)

| Bước | Làm |
| :-: | :--- |
| 1 | Tìm xe theo `vehicleId` và `tenantId` (404) |
| 2 | `checkManualStatusChange(current, target)` cho phép giữ nguyên. Khi thực sự đổi, hiện tại `RENTED` thì 409, đích `RENTED` thì 400 |
| 3 | Đã đúng trạng thái thì trả luôn, không ghi |
| 4 | Đích `INACTIVE`: đếm đơn chưa xong, còn thì 409 |
| 5 | `saveAndFlush` với trạng thái mới |

XE6 gọi lại hàm của XE5 với `INACTIVE`, giống `deleteTenant` gọi `updateTenantStatus(false)`.

### 5.4 Loại xe (LX1–LX6)
- **LX1, LX4:** bỏ khoảng trắng tên; kiểm tra trùng không phân biệt hoa thường (LX4 loại trừ chính nó); LX4 áp quy tắc `description` như R8 của tenant.
- **LX2, LX3:** Controller ép `isActive = true` khi người gọi không phải Super Admin.
- **LX5:** như API 5 của tenant.
- **LX6:** đếm xe theo loại (mọi nhà xe), có thì 409 kèm số; không có thì xóa.

---

## 6. SUY RA REPOSITORY TỪ NHU CẦU CỦA SERVICE

| Service cần | Câu hỏi với DB | Hàm Repository | Viết mới / có sẵn |
| :--- | :--- | :--- | :--- |
| LX-R1 khi tạo | Có tên cùng `UPPER(BTRIM(name))` chưa? | `VehicleTypeRepository.existsByNormalizedName` | Viết mới, `@Query` native SQL dùng cùng biểu thức với index |
| LX-R1 khi sửa | Có loại **khác** dùng cùng khóa tên chuẩn hóa chưa? | `VehicleTypeRepository.existsByNormalizedNameAndIdNot` | Viết mới, `@Query` native SQL thêm điều kiện loại trừ ID hiện tại |
| LX2 | Loại xe khớp từ khóa, trạng thái, trang thứ n | `VehicleTypeRepository.search(keywordPattern, isActive, Pageable)` | Viết mới (`@Query` JPQL, như `TenantRepository.search`) |
| Tìm loại xe | | `findById`, `save`, `saveAndFlush`, `delete` | Có sẵn |
| LX-R4 | Có bao nhiêu xe dùng loại này (mọi nhà xe)? | `VehicleRepository.countByVehicleTypeId` | Viết mới (tự sinh) |
| XE-R1 | Xe id này có trong nhà xe này không? | `VehicleRepository.findByIdAndTenantId` | Viết mới (tự sinh) |
| XE-R2 khi thêm | Biển số này đã có trong nhà xe chưa? | `VehicleRepository.existsByTenantIdAndLicensePlate` | Viết mới (tự sinh) |
| XE-R2 khi sửa | Có xe **khác** trong nhà xe dùng biển số này chưa? | `VehicleRepository.existsByTenantIdAndLicensePlateAndIdNot` | Viết mới (tự sinh) |
| XE-R3 | Chi nhánh id này có thuộc nhà xe này không? | `BranchRepository.findByIdAndTenantId` | **Có sẵn** (module branch/auth) |
| XE-R11 | Chi nhánh đang làm việc của STAFF có còn thuộc tenant và đang hoạt động? | `BranchRepository.findByIdAndTenantId` | **Có sẵn**, dùng cho guard đọc xe |
| XE-R11 | Người gọi có phân công ACTIVE trong đúng chi nhánh và tenant? | `UserBranchRepository.findByTenantIdAndUserIdAndBranchId` | **Có sẵn** (module account/auth), kiểm tra status sau khi đọc |
| XE2 | Xe trong phạm vi, khớp bộ lọc, trang thứ n, kèm tên loại và chi nhánh | `VehicleRepository.search(...)` + `countQuery` | Viết mới (`@Query` JPQL trả DTO) |
| XE3 | Một xe trong phạm vi, kèm tên loại và chi nhánh | `VehicleRepository.findDetail(vehicleId, tenantId, branchId, includeInactive)` | Viết mới (`@Query` JPQL trả DTO) |
| XE-R8 | Xe này còn bao nhiêu đơn HOLD, CONFIRMED, HANDED_OVER? | `VehicleRepository.countUnfinishedBookings(vehicleId, tenantId)` | Viết mới (native query trên `bookings`) |
| Cung cấp cho module chi nhánh | Chi nhánh này còn bao nhiêu xe chưa xóa mềm? | `VehicleRepository.countByBranchIdAndStatusNot` | Viết mới khi module chi nhánh cần ([DD, mục 7](2026-11-05-vehicle-detail-design.md#7-giới-hạn-đã-biết-và-việc-cần-quyết-định) dòng 11) |

```java
// Phác thảo: tham số null nghĩa là không lọc. keywordPattern luôn khác null ("%" khi trống).
@Query(value = """
  SELECT new com.carrental.car_rental_backend.vehicle.dto.response.VehicleResponseDTO(
    v.id, v.licensePlate, v.status, t.id, t.name, b.id, b.name, ...)
  FROM Vehicle v
  JOIN VehicleType t ON t.id = v.vehicleTypeId
  LEFT JOIN Branch b ON b.id = v.branchId
  WHERE v.tenantId = :tenantId
    AND (:branchId IS NULL OR v.branchId = :branchId)
    AND (:status IS NULL OR v.status = :status)
    AND (:includeInactive = true OR v.status <> :inactiveStatus)
    AND (:vehicleTypeId IS NULL OR v.vehicleTypeId = :vehicleTypeId)
    AND (LOWER(v.licensePlate) LIKE :keywordPattern OR LOWER(v.model) LIKE :keywordPattern)
  """,
  countQuery = "SELECT COUNT(v) FROM Vehicle v WHERE ... cùng điều kiện, không JOIN ...")
Page<VehicleResponseDTO> search(...);
```

Lưu ý: `LOWER(v.model) LIKE` với `model` null cho kết quả null, nên xe không có model vẫn được tìm qua biển số. Cần test.

---

## 7. CHIA LỚP VÀ ĐẶT TÊN

### 7.1 Các file cần viết

```
common/enums/vehicle_status/
├── VehicleStatus.java                 5 trạng thái, BaseEnum<Integer>
└── VehicleStatusConverter.java        @Converter(autoApply = true)

common/exception/ErrorCode.java        THÊM 4 mã (không sửa mã cũ)

vehicle_type/
├── controller/VehicleTypeController.java
├── dto/
│   ├── request/   CreateVehicleTypeRequestDTO, UpdateVehicleTypeRequestDTO, UpdateVehicleTypeStatusRequestDTO
│   └── response/  VehicleTypeResponseDTO
├── entity/VehicleType.java
├── repository/VehicleTypeRepository.java
└── service/
    ├── VehicleTypeService.java        LX1–LX6
    └── impl/VehicleTypeServiceImpl.java

vehicle/
├── controller/VehicleController.java
├── dto/
│   ├── request/   VehicleRequestDTO, UpdateVehicleStatusRequestDTO
│   └── response/  VehicleResponseDTO
├── entity/Vehicle.java
├── repository/VehicleRepository.java
├── security/      VehicleScope (record), VehicleScopeResolver
└── service/
    ├── VehicleService.java            XE1–XE6
    └── impl/VehicleServiceImpl.java
```

Tên package `vehicle_type` theo kiểu `plan_tier`, `user_branch_status` đang có. Chờ trưởng nhóm chốt ([DD, mục 7](2026-11-05-vehicle-detail-design.md#7-giới-hạn-đã-biết-và-việc-cần-quyết-định) dòng 4).

**Vì sao hai module:** loại xe là danh mục toàn hệ thống do Super Admin quản lý; xe là dữ liệu của từng nhà xe. Hai nhóm việc có người gọi, quyền và lý do thay đổi khác nhau.

### 7.2 Quy tắc đặt tên

Theo mục 7.2 của kế hoạch tenant (`check...` là guard ném lỗi, `find...OrThrow`, `validate...`, `is...`/`has...`).

| Hàm / kiểu chính | Nghĩa |
| :--- | :--- |
| `checkVehicleTypeNameNotTaken` | Tên loại xe chưa ai dùng, ngược lại 409 |
| `checkLicensePlateNotTaken` | Biển số chưa có trong nhà xe, ngược lại 409 |
| `checkManualStatusChange` | Chuyển trạng thái bằng tay có hợp lệ không (bảng 3.2 của DD) |
| `checkNoUnfinishedBookings` | Xe không còn đơn chưa xong, ngược lại 409 |
| `findVehicleTypeOrThrow` | Tìm loại xe, không có thì 404 (dùng ở API loại xe) |
| `findSelectableVehicleTypeOrThrow` | Loại xe tồn tại và đang dùng, ngược lại 400 (dùng khi chọn loại cho xe) |
| `findActiveBranchOrThrow` | Chi nhánh thuộc nhà xe và đang hoạt động, ngược lại 400 |
| `checkStaffBranchAccess` | Trước khi đọc xe, kiểm tra chi nhánh và phân công hiện tại theo scope của STAFF, sai điều kiện thì 403 |
| `findVehicleOrThrow` | Tìm xe trong nhà xe, không có thì 404 |
| `normalizeLicensePlate` | Bỏ khoảng trắng hai đầu, chữ in hoa |
| `VehicleScope`, `VehicleScopeResolver.resolve` | Phạm vi xem; dựng phạm vi từ người gọi |

Hai hàm tìm loại xe trả mã khác nhau có chủ ý: loại xe trên **đường dẫn** không có là 404; loại xe trong **body** không dùng được là 400.

### 7.3 `createVehicle` đọc như các bước nghiệp vụ

```java
public VehicleResponseDTO createVehicle(VehicleRequestDTO request, UUID tenantId) {
  String licensePlate = this.normalizeLicensePlate(request.getLicensePlate());
  this.checkManufactureYear(request.getYear());
  this.checkLicensePlateNotTaken(tenantId, licensePlate);
  Branch branch = this.findActiveBranchOrThrow(request.getBranchId(), tenantId);
  VehicleType vehicleType = this.findSelectableVehicleTypeOrThrow(request.getVehicleTypeId());

  Vehicle vehicle = this.vehicleRepository.saveAndFlush(this.newVehicle(request, tenantId, licensePlate));
  return this.toVehicleResponse(vehicle, branch, vehicleType);
}
```

### 7.4 Một hàm kiểm tra chuyển trạng thái

```java
// Giữ nguyên trạng thái được phép; RENTED chỉ do module booking đặt và gỡ
private void checkManualStatusChange(VehicleStatus current, VehicleStatus target) {
  if (current == target) {
    return;
  }
  if (current == VehicleStatus.RENTED) {
    throw new AppException(ErrorCode.VEHICLE_IN_USE);
  }
  if (target == VehicleStatus.RENTED) {
    throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái Đang thuê do đơn thuê cập nhật !");
  }
}
```

Bảng 3.2 của DD được áp dụng theo thứ tự giữ nguyên, chặn gỡ RENTED bằng tay, chặn đặt RENTED bằng tay. Service gọi guard, sau đó trả response ngay nếu trạng thái giữ nguyên. Chỉ khi trạng thái thực sự đổi mới đếm booking nếu cần và gọi `saveAndFlush`. Vì vậy `RENTED → RENTED` trả 200, giữ nguyên `updatedAt` và dữ liệu.

---

## 8. LỖI

Chi tiết mã lỗi và thông báo ở [DD, mục 4](2026-11-05-vehicle-detail-design.md#4-quy-ước-chung) và [đặc tả API, Phụ lục A](2026-11-05-vehicle-api-spec.md#a-mã-lỗi). Tóm tắt nơi phát hiện:

| Lỗi | Phát hiện ở | HTTP |
| :--- | :--- | :-: |
| Thiếu trường, sai định dạng | DTO `@Valid` | 400 |
| Không đủ quyền | `@PreAuthorize` theo từng hàm Controller | 403 |
| STAFF chưa chọn chi nhánh | `VehicleScopeResolver.resolve` | 400 |
| Chi nhánh của STAFF đã mất, sai tenant hoặc ngừng hoạt động; phân công đã bị gỡ hoặc không ACTIVE | `VehicleServiceImpl.checkStaffBranchAccess` trước XE2, XE3 | 403 |
| Không có loại xe / xe (đường dẫn) | `findVehicleTypeOrThrow`, `findVehicleOrThrow`, `findDetail` | 404 |
| Chi nhánh, loại xe trong body không dùng được; năm sai | `findActiveBranchOrThrow`, `findSelectableVehicleTypeOrThrow`, `checkManufactureYear` | 400 |
| Trùng tên loại, trùng biển số | `checkVehicleTypeNameNotTaken`, `checkLicensePlateNotTaken` | 409 |
| Loại xe đang dùng | `deleteVehicleType` | 409 |
| Xe đang thuê, còn đơn | `checkManualStatusChange`, `checkNoUnfinishedBookings`, đổi chi nhánh ở XE4 | 409 |
| Đích `RENTED`, hiện tại khác `RENTED` | `checkManualStatusChange` | 400 |

**`@PreAuthorize` theo từng hàm:** khác `TenantController` (gắn cấp lớp), vì đọc và ghi cần quyền khác nhau.

| Controller | Hàm đọc | Hàm ghi |
| :--- | :--- | :--- |
| `VehicleTypeController` | `hasRole('SUPER_ADMIN') or hasAuthority('vehicle:read')` | `hasRole('SUPER_ADMIN')` |
| `VehicleController` | `hasAuthority('vehicle:read')` | `hasAuthority('vehicle:manage')` |

**Xung đột ghi đồng thời:** sau migration tên loại xe, unique index `UPPER(BTRIM(name))` chặn cả `SUV` và `suv`. Khi hai request đã cùng qua kiểm tra, lỗi UNIQUE ở bước ghi hiện trả 500, như module tenant; chờ người phụ trách module chung ánh xạ 409. Trùng được Service phát hiện trước khi ghi vẫn trả 409.

---

## 9. TRANSACTION

- Hai service gắn `@Transactional` cấp lớp; hàm chỉ đọc gắn `@Transactional(readOnly = true)`.
- Mỗi API chỉ ghi một bảng. Kiểm tra (đếm đơn, kiểm tra trùng) và ghi nằm trong cùng giao dịch, nhưng không khóa dòng, nên hai request đồng thời vẫn có thể cùng qua kiểm tra. Với XE-R8: một đơn mới tạo đúng lúc xe đang bị xóa mềm có thể lọt. Module booking khóa dòng xe (`SELECT FOR UPDATE`, theo onboarding §4.2 Hold Engine) sẽ xử lý phía đặt xe; khi đó xóa mềm cũng nên khóa cùng dòng xe để hai bên không lọt nhau.

---

## 10. THỨ TỰ VIẾT CODE

Đi từ chỗ **ít phụ thuộc** tới chỗ **phụ thuộc nhiều**. Làm hết module loại xe trước, vì xe phụ thuộc loại xe còn loại xe gần như không phụ thuộc gì. Mỗi bước xong phải biên dịch được và chạy được test của bước đó.

| Bước | Viết | Vì sao ở vị trí này | Kiểm tra |
| :-: | :--- | :--- | :--- |
| 0 | Tạo nhánh (ví dụ `feature/vehicle-management`); đọc lại module tenant để theo khuôn | | |
| 0.1 | Migration unique index `idx_vehicle_types_normalized_name`, theo DD mục 2 | LX-R1 cần được DB bảo vệ khi ghi đồng thời | Kiểm tra trước các nhóm tên trùng theo `UPPER(BTRIM(name))`. Nếu có thì dừng và báo để xử lý dữ liệu. Test trên DB mới và DB có dữ liệu cũ, xác nhận index chặn tên khác hoa thường/khoảng trắng hai đầu |
| 1 | `VehicleType` entity | Mọi lớp của module loại xe dựa vào nó | Ứng dụng khởi động, Hibernate `validate` không báo lỗi |
| 2 | DTO loại xe, 2 mã lỗi loại xe trong `ErrorCode` | Chốt hợp đồng API | Test DTO: tên rỗng, 50 và 51 ký tự |
| 3 | `VehicleTypeRepository` | | Test với DB thật: `search`, `existsByNormalizedName`, `existsByNormalizedNameAndIdNot` |
| 4 | `VehicleTypeService` + Impl (LX6 tạm chưa đếm xe) | | Unit test LX1–LX5 |
| 5 | `VehicleTypeController` | | Test tích hợp LX1–LX5, gồm 403 cho người của nhà xe khi ghi |
| 6 | `VehicleStatus` + converter, `Vehicle` entity | Module xe bắt đầu từ đây | Ứng dụng khởi động; đọc được 60 xe seed |
| 7 | DTO xe, 2 mã lỗi xe | | Test DTO: giá 0, 6 ảnh, URL sai, biển số có ký tự lạ |
| 8 | `VehicleRepository` (gồm `countByVehicleTypeId`); hoàn thiện LX6 | LX6 cần hàm đếm | Test `search` theo từng bộ lọc, `countUnfinishedBookings` |
| 9 | `VehicleScope`, `VehicleScopeResolver` | Controller xe cần | Unit test từng vai trò |
| 10 | `VehicleService` + Impl, inject `UserBranchRepository` có sẵn | | Unit test XE1–XE6, `checkManualStatusChange`, `checkStaffBranchAccess`; xác nhận guard chạy trước truy vấn xe |
| 11 | `VehicleController` | Lớp ngoài cùng | Test tích hợp HTTP thật + PostgreSQL Testcontainers |
| 12 | Chạy thử bằng Swagger với Super Admin, chủ, STAFF, SALE | | Đi qua 12 API, kiểm tra dữ liệu trong DB |

---

## 11. KIỂM THỬ

Test tích hợp dùng `ApiIntegrationTestSupport` (PostgreSQL Testcontainers) như module tenant. Bảng `bookings` không có entity: chèn đơn mẫu bằng `JdbcTemplate` (cần cả `customers` vì `bookings.customer_id` NOT NULL).

### 11.1 Phạm vi xem (mỗi dòng một test)

| Người gọi | Tình huống | Kết quả |
| :--- | :--- | :--- |
| Chủ nhà xe Hà Nội | Xem xe của nhà xe Sài Gòn bằng id | 404 (XE-R1, chống IDOR) |
| Chủ nhà xe Hà Nội | Danh sách | Chỉ xe Hà Nội, gồm cả xe `INACTIVE` |
| STAFF chi nhánh A | Danh sách, gửi `branchId` của chi nhánh B | Chỉ xe chi nhánh A |
| STAFF chi nhánh A | Xem xe chi nhánh B bằng id | 404 |
| STAFF chưa `switch-branch` | Danh sách | 400 |
| STAFF có token hợp lệ của chi nhánh A | XE2 và XE3 khi chi nhánh hoạt động, phân công ACTIVE | 200 trong phạm vi A |
| STAFF giữ nguyên token đã chọn A | Xóa phân công A khỏi `user_branches` rồi gọi XE2 và XE3 | 403, cả hai API đều bị chặn trước truy vấn xe |
| STAFF giữ nguyên token đã chọn A | Đổi phân công sang SUSPENDED hoặc RESIGNED rồi gọi XE2 và XE3, mỗi trạng thái một test | 403 |
| STAFF giữ nguyên token đã chọn A | Khóa chi nhánh A rồi gọi XE2 và XE3 | 403 |
| STAFF giữ nguyên token đã chọn A | Xóa cứng chi nhánh A rồi gọi XE2 và XE3 | 403 |
| STAFF giữ nguyên token đã chọn A, phân công A đã bị gỡ | XE2 gửi query branchId=B hoặc XE3 gọi xe của B | 403 từ guard của A, query và ID xe không thay thế được quyền chi nhánh hiện tại |
| STAFF chuyển phân công từ A sang B | Token cũ A gọi XE2/XE3, sau đó switch sang B và gọi bằng token mới | Token A nhận 403, token B nhận 200 trong phạm vi B |
| Chủ hoặc SALE | Đọc xe theo phạm vi khi không có phân công trong từng chi nhánh | Áp XE-R5, guard STAFF không thu hẹp phạm vi của hai vai trò này |
| SALE | Danh sách | Mọi xe của nhà xe, không có `INACTIVE` |
| SALE | Lọc `status=INACTIVE` | Trang rỗng |
| Super Admin | Gọi bất kỳ API xe | 403 |
| Người thuộc nhiều nhà xe, chưa `select-tenant` | Gọi bất kỳ API xe | 403 |

Các test token cũ lấy access token trước, thay đổi trạng thái trong DB và commit, rồi gửi lại đúng token đó cho cả XE2 và XE3. Unit test guard có thêm trường hợp chi nhánh sai tenant và trạng thái phân công null. Với các nhánh bị từ chối, kiểm tra `VehicleRepository.search` / `findDetail` chưa được gọi. Các kiểm tra này được bổ sung khi triển khai module Vehicle.

### 11.2 Chuyển trạng thái (`checkManualStatusChange` + XE5)

| Tình huống | Kết quả |
| :--- | :--- |
| `AVAILABLE` → `MAINTENANCE`, `TRANSFERRED`, `INACTIVE` | Thành công |
| `INACTIVE` → `AVAILABLE` | Thành công |
| Trạng thái khác `RENTED` → `RENTED` | 400 |
| Hiện tại `RENTED` → trạng thái khác | 409 `VEHICLE_IN_USE` |
| Gửi lại trạng thái đang có, đủ cả 5 trạng thái | 200, `updatedAt` không đổi, không gọi đếm booking hoặc `saveAndFlush` |
| `RENTED` → `RENTED` | Guard chấp nhận, API trả 200, không ghi. Có test riêng để tránh kiểm tra RENTED trước bước giữ nguyên |
| → `INACTIVE` khi còn đơn HOLD / CONFIRMED / HANDED_OVER | 409, mỗi trạng thái đơn một test |
| → `INACTIVE` khi chỉ còn đơn RETURNED, CANCELLED | Thành công |
| → `INACTIVE` khi xe **khác** có đơn chưa xong | Thành công |

### 11.3 Theo API

| API | Ca cần có |
| :-: | :--- |
| LX1 | Thành công; trùng tên khác hoa thường 409; tên có khoảng trắng hai đầu được cắt; sau migration, DB chặn chèn trực tiếp tên trùng khóa chuẩn hóa |
| LX2 | Người của nhà xe gửi `isActive=false` vẫn chỉ nhận loại đang dùng; sắp theo tên; `size` bị kẹp |
| LX3 | Người của nhà xe xem loại đã tắt 404; Super Admin xem được |
| LX4 | Đổi hoa thường tên của chính nó không báo trùng; trùng loại khác 409; `description` null giữ nguyên, rỗng thì xóa |
| LX5 | Tắt rồi thêm xe với loại đó 400; xe cũ vẫn giữ loại; gọi lặp thành công |
| LX6 | Loại chưa dùng xóa được, xóa lần hai 404; loại có xe (kể cả xe `INACTIVE` hoặc xe nhà xe khác) 409 kèm số xe |
| XE1 | Thành công, biển số thành chữ in hoa, `status = AVAILABLE`; trùng biển số trong nhà xe 409; **cùng biển số ở nhà xe khác thành công**; chi nhánh nhà xe khác 400; chi nhánh tạm ngưng 400; loại xe đã tắt 400; năm 1989 và năm nay + 2 bị chặn |
| XE2 | Mỗi bộ lọc; tìm theo biển số và model, không phân biệt hoa thường; xe không có model vẫn tìm được qua biển số; xe mất chi nhánh (`branch_id` null) vẫn hiện với chủ, `branchName` null |
| XE3 | Có; không có 404 |
| XE4 | Ghi đè: không gửi `color` thì `color` thành null; giữ biển số không báo trùng; đổi sang biển số xe khác 409; giữ loại đã tắt vẫn sửa được; đổi sang loại đã tắt 400; đổi chi nhánh khi `RENTED` 409; sửa xe seed với giá > 0 thành công |
| XE6 | Xóa mềm: dòng còn trong DB, `status = 5`; xóa lần hai thành công |
| Mọi API ghi | Người chỉ có `vehicle:read` (STAFF, SALE) nhận 403 |

Kiểm thử đồng thời LX1/LX4 sau migration dùng hai transaction riêng cho các cặp tạo/tạo, tạo/sửa và sửa/sửa, với tên cùng `UPPER(BTRIM(name))`. Xác nhận tối đa một bản ghi mang khóa tên đích, thao tác thua rollback và dữ liệu cũ của nó được giữ. Nếu chủ động cho cả hai vượt qua kiểm tra Service trước khi ghi, thao tác thua hiện nhận 500 theo handler chung; nếu Service thấy trùng trước bước ghi thì nhận 409.

---

## 12. VIỆC CẦN NHÓM QUYẾT ĐỊNH

Đầy đủ ở [DD, mục 7](2026-11-05-vehicle-detail-design.md#7-giới-hạn-đã-biết-và-việc-cần-quyết-định). Nên hỏi trước khi code:

| Việc | Ảnh hưởng nếu đổi sau |
| :--- | :--- |
| Ai quản lý loại xe | Đổi `@PreAuthorize` và test của LX1, LX4–LX6; nếu mỗi nhà xe có loại riêng thì cần migration và viết lại module loại xe |
| Tên package | Đổi tên thư mục, không đổi logic |
| Quyền cho STAFF đổi trạng thái | Thêm migration, sửa `DefaultRole`, đổi `@PreAuthorize` của XE5 |

Quota, upload ảnh, định dạng biển số có thể quyết sau, không làm lại phần đã viết.

---

## 13. BA CÂU HỎI TRƯỚC KHI COMMIT

Ví dụ với API thêm xe:
1. **API làm gì?** Chủ nhà xe khai báo một xe mới ở một chi nhánh của mình, với loại xe đang dùng; xe sẵn sàng cho thuê ngay.
2. **Chạy khi nào, ảnh hưởng dữ liệu nào?** Khi chủ nhà xe thêm xe. Đọc `vehicles` (trùng biển số), `branches`, `vehicle_types`; ghi 1 dòng `vehicles`.
3. **Lỗi trả mã gì?** 400 dữ liệu sai, chi nhánh hoặc loại xe không dùng được; 403 không có `vehicle:manage`; 409 trùng biển số trong nhà xe.
