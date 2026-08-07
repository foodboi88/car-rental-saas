# Technical Specification: Quản lý Loại Xe Backend API (Vehicle Type Management Backend API)

**Date**: 2026-07-23  
**Module**: `com.carrental.vehicletype`  
**Target Scope**: Backend REST API, Data Model, Business Rules, Security & Validation  

---

## 1. Tổng Quan (Overview)

Tính năng **Quản lý Loại Xe (`vehicle_types`)** cung cấp danh mục phân khúc loại xe toàn hệ thống (System-Wide Catalog) do **Super Admin** khởi tạo và quản lý (ví dụ: *Sedan 4 chỗ*, *SUV 7 chỗ*, *Bán tải*, *Xe điện*). Tất cả các Tenant truy vấn danh mục loại xe này để gán thuộc tính cho từng chiếc xe thuộc sở hữu của mình (`vehicles.vehicle_type_id`).

---

## 2. Phân Quyền & Access Control (Security & Dynamic RBAC)

1. **System-wide Catalog**:
   * Danh mục `vehicle_types` không chứa `tenant_id`, dùng chung cho tất cả các nhà xe trong hệ thống SaaS.
2. **Phân quyền truy cập (Dynamic RBAC)**:
   * **`SUPER_ADMIN`**: Toàn quyền CRUD danh mục loại xe hệ thống (`hasAuthority('vehicle_type:manage')`).
   * **Mọi User có quyền `vehicle:read` / `vehicle:create`**: Read-only (`GET /api/v1/vehicle-types`, `GET /api/v1/vehicle-types/{id}`).

---

## 3. Cấu Trúc Code Backend (Package & Architecture Structure)

Nằm tại package: `com.carrental.vehicletype`

```text
backend/src/main/java/com/carrental/vehicletype/
├── controller/
│   └── VehicleTypeController.java
├── dto/
│   ├── CreateVehicleTypeRequestDTO.java
│   ├── UpdateVehicleTypeRequestDTO.java
│   ├── VehicleTypeResponseDTO.java
│   └── VehicleTypeStatusRequestDTO.java
├── entity/
│   └── VehicleType.java
├── repository/
│   └── VehicleTypeRepository.java
└── service/
    ├── VehicleTypeService.java
    └── impl/VehicleTypeServiceImpl.java
```

---

## 4. Chi Tiết Data Model (JPA Entity Mapping)

**Entity**: `com.carrental.vehicletype.entity.VehicleType`  
**Table**: `vehicle_types`

| Column Name | Java Field | Data Type | Constraint | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `id` | `UUID` | Primary Key | Tự động sinh `gen_random_uuid()` |
| `tenant_id` | `tenantId` | `UUID` | Not Null, FK -> `tenants(id)` | Mã định danh Tenant |
| `name` | `name` | `String` | Not Null, Max 50 | Tên phân khúc/loại xe |
| `description` | `description` | `String` | Text | Mô tả chi tiết phân khúc xe |
| `base_price` | `basePrice` | `BigDecimal` | Not Null, `DECIMAL(12, 2)` | Giá thuê sàn cơ bản/ngày |
| `is_active` | `isActive` | `Boolean` | Default `true` | Trạng thái kích hoạt |
| `created_at` | `createdAt` | `Instant` | Current Timestamp | Thời gian tạo record |
| `updated_at` | `updatedAt` | `Instant` | Current Timestamp | Thời gian cập nhật gần nhất |

---

## 5. Danh Sách REST Endpoints & Contracts

Gốc URL: `/api/v1/vehicle-types`

### 5.1 Danh sách Endpoints

| Method | Endpoint | Description | Permitted Roles | Request Body | Response Format |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/vehicle-types` | Lấy danh sách loại xe (Phân trang, Search tên, Filter `isActive`) | `TENANT_ADMIN`, `STAFF`, `SALE` | - | `ApiResponse<Page<VehicleTypeResponseDTO>>` |
| `GET` | `/api/v1/vehicle-types/{id}` | Lấy chi tiết loại xe theo ID | `TENANT_ADMIN`, `STAFF`, `SALE` | - | `ApiResponse<VehicleTypeResponseDTO>` |
| `POST` | `/api/v1/vehicle-types` | Tạo loại xe mới | `TENANT_ADMIN` | `CreateVehicleTypeRequestDTO` | `ApiResponse<VehicleTypeResponseDTO>` |
| `PUT` | `/api/v1/vehicle-types/{id}` | Cập nhật loại xe | `TENANT_ADMIN` | `UpdateVehicleTypeRequestDTO` | `ApiResponse<VehicleTypeResponseDTO>` |
| `PATCH`| `/api/v1/vehicle-types/{id}/status` | Cập nhật trạng thái `is_active` | `TENANT_ADMIN` | `VehicleTypeStatusRequestDTO` | `ApiResponse<VehicleTypeResponseDTO>` |
| `DELETE`| `/api/v1/vehicle-types/{id}` | Xóa loại xe | `TENANT_ADMIN` | - | `ApiResponse<Void>` |

### 5.2 DTO Specs & Validation

* **`CreateVehicleTypeRequestDTO`**:
  * `name`: `@NotBlank`, `@Size(max = 50)`
  * `basePrice`: `@NotNull`, `@DecimalMin(value = "0.0", inclusive = true)`
  * `description`: `String` (optional)
* **`UpdateVehicleTypeRequestDTO`**: Tương tự `CreateVehicleTypeRequestDTO`.
* **`VehicleTypeStatusRequestDTO`**:
  * `status` / `isActive`: `@NotNull Boolean`
* **`VehicleTypeResponseDTO`**:
  * `id` (`UUID`)
  * `tenantId` (`UUID`)
  * `name` (`String`)
  * `description` (`String`)
  * `basePrice` (`BigDecimal`)
  * `isActive` (`Boolean`)
  * `vehicleCount` (`Long` - Tổng số xe vật lý đang thuộc loại xe này)
  * `createdAt` (`Instant`)
  * `updatedAt` (`Instant`)

---

## 6. Nghiệp Vụ & Ràng Buộc (Business Rules & Validation Logic)

1. **Ràng buộc duy nhất tên loại xe (`name`)**:
   * Kiểm tra trùng tên (Case-insensitive, không phân biệt hoa thường) trong cùng một `tenant_id`.
   * Khi Tạo mới: Nếu `existsByTenantIdAndNameIgnoreCase(tenantId, name.trim()) == true` -> ném lỗi `VEHICLE_TYPE_NAME_EXISTS`.
   * Khi Cập nhật: Nếu `existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, name.trim(), id) == true` -> ném lỗi `VEHICLE_TYPE_NAME_EXISTS`.

2. **Ràng buộc an toàn khi Xóa (Delete Safety Guard)**:
   * Trước khi xóa record `VehicleType`: Đếm số xe thuộc loại này qua `vehicleRepository.countByTenantIdAndVehicleTypeId(tenantId, vehicleTypeId)`.
   * Nếu `vehicleCount > 0`: **Chặn thao tác Xóa**, ném lỗi `VEHICLE_TYPE_IN_USE` (HTTP status `400 Bad Request` hoặc `409 Conflict`) kèm thông điệp: `"Không thể xóa loại xe [tên] vì đang có [X] xe thuộc phân khúc này"`.

3. **Ràng buộc khi Ngừng hoạt động (`is_active = false`)**:
   * Cho phép đổi `is_active` thành `false` kể cả khi có xe thuộc loại xe này.
   * Các xe vật lý đã chọn loại xe này vẫn giữ nguyên thông tin.
   * **Quy tắc chặn gán**: Khi người dùng **Tạo Xe mới**, **Sửa Loại xe của Xe**, hoặc **Tạo Đơn thuê xe (Booking)**, hệ thống sẽ kiểm tra xem `VehicleType.isActive == true`. Nếu `false`, chặn thao tác và thông báo loại xe đã bị ngừng hoạt động (`VEHICLE_TYPE_INACTIVE`).

---

## 7. Mã Lỗi Quy Chuẩn (Error Codes)

| Error Code | HTTP Status | Message Summary |
| :--- | :--- | :--- |
| `VEHICLE_TYPE_NOT_FOUND` | `404 Not Found` | Không tìm thấy loại xe yêu cầu trong tenant |
| `VEHICLE_TYPE_NAME_EXISTS` | `400 Bad Request` / `409 Conflict` | Tên loại xe đã tồn tại trong nhà xe |
| `VEHICLE_TYPE_IN_USE` | `400 Bad Request` / `409 Conflict` | Không thể xóa loại xe đang có xe phụ thuộc |
| `VEHICLE_TYPE_INACTIVE` | `400 Bad Request` | Loại xe đang ở trạng thái ngừng hoạt động |

---

## 8. Chiến Lược Kiểm Thử (Testing Strategy)

1. **Unit Test (`VehicleTypeServiceImplTest`)**:
   * Test CRUD cơ bản: `getBranches`, `create`, `update`, `changeStatus`, `delete`.
   * Test validate trùng tên (`VEHICLE_TYPE_NAME_EXISTS`).
   * Test kiểm tra ràng buộc xóa (`VEHICLE_TYPE_IN_USE`).
2. **Integration Test (`VehicleTypeControllerIntegrationTest`)**:
   * Verify Spring Security (`@PreAuthorize` với `TENANT_ADMIN` vs `STAFF` vs `SALE`).
   * Verify cách ly Multi-tenant (Tenant A không được phép đọc/ghi dữ liệu loại xe của Tenant B).
