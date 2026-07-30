# Implementation Plan: Quản lý Loại Xe Backend API (Vehicle Type Management Backend API)

**Date**: 2026-07-23  
**Spec Document**: [`docs/plans/fleet-management/2026-07-23-vehicle-type-backend-spec.md`](file:///f:/backend-training/private-car-rental/docs/plans/fleet-management/2026-07-23-vehicle-type-backend-spec.md)  
**Implementation Guide**: [`docs/plans/fleet-management/2026-07-23-vehicle-type-implementation-guide.md`](file:///f:/backend-training/private-car-rental/docs/plans/fleet-management/2026-07-23-vehicle-type-implementation-guide.md)  

---

## 1. Context & Scope

Triển khai trọn bộ Backend module `com.carrental.vehicletype` bao gồm Entity, Repository, DTOs, Service, Controller và Unit Test cho tính năng Quản lý Loại Xe.

### Non-Goals
* Không triển khai phần Giao diện Frontend UI (sẽ thực hiện ở Phase sau).
* Không sửa đổi schema DB (bảng `vehicle_types` đã được tạo từ migration `V3__create_bookings_table.sql`).

---

## 2. Constraints & Security Rules

* **Framework**: Spring Boot 3.x, Spring Data JPA, Spring Security (`@PreAuthorize`).
* **Multi-tenant**: Tất cả thao tác dữ liệu đều lọc theo `tenant_id` từ `TenantContext.getTenantId()`.
* **Security Rules**:
  * `TENANT_ADMIN`: Toàn quyền `POST`, `PUT`, `PATCH`, `DELETE`.
  * `STAFF` & `SALE`: Chỉ được quyền xem `GET`.

---

## 3. Target Artifacts (Registry)

| # | Action | File Path | Description |
| :--- | :--- | :--- | :--- |
| 1 | `[NEW]` | `backend/src/main/java/com/carrental/vehicletype/entity/VehicleType.java` | JPA Entity đại diện bảng `vehicle_types` |
| 2 | `[NEW]` | `backend/src/main/java/com/carrental/vehicletype/repository/VehicleTypeRepository.java` | Data Access Interface |
| 3 | `[NEW]` | `backend/src/main/java/com/carrental/vehicletype/dto/CreateVehicleTypeRequestDTO.java` | DTO payload tạo loại xe mới |
| 4 | `[NEW]` | `backend/src/main/java/com/carrental/vehicletype/dto/UpdateVehicleTypeRequestDTO.java` | DTO payload cập nhật loại xe |
| 5 | `[NEW]` | `backend/src/main/java/com/carrental/vehicletype/dto/VehicleTypeStatusRequestDTO.java` | DTO payload cập nhật trạng thái |
| 6 | `[NEW]` | `backend/src/main/java/com/carrental/vehicletype/dto/VehicleTypeResponseDTO.java` | DTO response trả về FE |
| 7 | `[NEW]` | `backend/src/main/java/com/carrental/vehicletype/service/VehicleTypeService.java` | Service Interface |
| 8 | `[NEW]` | `backend/src/main/java/com/carrental/vehicletype/service/impl/VehicleTypeServiceImpl.java` | Service Implementation |
| 9 | `[NEW]` | `backend/src/main/java/com/carrental/vehicletype/controller/VehicleTypeController.java` | REST Controller API |
| 10 | `[NEW]` | `backend/src/test/java/com/carrental/vehicletype/service/VehicleTypeServiceImplTest.java` | Unit tests cho Service layer |

---

## 4. Sequence of Implementation Tasks

1. **Task 1: Model & Repository Layer**  
   Tạo `VehicleType.java` và `VehicleTypeRepository.java` với các query lọc `tenantId`, tìm trùng tên `name` và đếm số lượng xe phụ thuộc (`countVehiclesByTenantIdAndVehicleTypeId`).

2. **Task 2: DTOs Layer**  
   Tạo `CreateVehicleTypeRequestDTO`, `UpdateVehicleTypeRequestDTO`, `VehicleTypeStatusRequestDTO`, và `VehicleTypeResponseDTO`.

3. **Task 3: Service Layer & Business Logic**  
   Tạo `VehicleTypeService` & `VehicleTypeServiceImpl` xử lý các quy tắc nghiệp vụ:
   * Unique `name` theo Tenant (Case-insensitive).
   * Delete safety guard (chặn xóa khi `vehicleCount > 0`).
   * Mapping DTOs kèm `vehicleCount`.

4. **Task 4: REST Controller Layer & Security Annotations**  
   Tạo `VehicleTypeController` gắn `@PreAuthorize` tương ứng với từng vai trò người dùng (`TENANT_ADMIN`, `STAFF`, `SALE`).

5. **Task 5: Verification & Unit Tests**  
   Viết `VehicleTypeServiceImplTest` kiểm thử tất cả các trường hợp thành công, trùng tên, và ném lỗi khi xóa loại xe có phụ thuộc. Chạy `mvn test`.
