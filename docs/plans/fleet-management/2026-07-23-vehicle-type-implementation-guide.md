# Hướng Dẫn Chi Tiết Triển Khai Backend API Quản Lý Loại Xe (Step-by-Step Implementation Guide)

**Dự án**: `private-car-rental`  
**Package**: `com.carrental.vehicletype`  
**File Spec Tham Chiếu**: [`docs/plans/fleet-management/2026-07-23-vehicle-type-backend-spec.md`](file:///f:/backend-training/private-car-rental/docs/plans/fleet-management/2026-07-23-vehicle-type-backend-spec.md)

---

## 🛠️ Bước 1: Tạo JPA Entity `VehicleType.java`

**Đường dẫn file**: `backend/src/main/java/com/carrental/vehicletype/entity/VehicleType.java`

```java
package com.carrental.vehicletype.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicle_types")
public class VehicleType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public VehicleType() {
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isActive == null) {
            this.isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
```

---

## 🛠️ Bước 2: Tạo JPA Entity `Vehicle.java` (Phụ thuộc bắt buộc)

> ⚠️ **TẠI SAO CẦN BƯỚC NÀY?**
> Ở Bước 3 bên dưới, `VehicleTypeRepository` có câu truy vấn JPQL:
> ```java
> @Query("SELECT COUNT(v) FROM Vehicle v WHERE v.tenantId = :tenantId AND v.vehicleTypeId = :vehicleTypeId")
> ```
> Câu JPQL này tham chiếu tới **JPA Entity `Vehicle`** — nếu entity này không tồn tại, Spring Boot sẽ **lỗi ngay khi khởi chạy** vì Hibernate không thể resolve được tên entity `Vehicle` trong câu truy vấn.
>
> Entity `Vehicle` mapping với bảng `vehicles` đã được tạo sẵn trong Flyway migration `V3__create_bookings_table.sql`. Ở giai đoạn này, ta chỉ cần tạo Entity tối thiểu đủ để Spring Boot khởi chạy thành công. Khi triển khai module CRUD Xe đầy đủ sau này, entity này sẽ được mở rộng thêm (thêm relationships, custom methods, v.v.).

**Đường dẫn file**: `backend/src/main/java/com/carrental/vehicle/entity/Vehicle.java`

```java
package com.carrental.vehicle.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "vehicle_type_id", nullable = false)
    private UUID vehicleTypeId;

    @Column(name = "license_plate", nullable = false, length = 20)
    private String licensePlate;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "color", length = 30)
    private String color;

    @Column(name = "year")
    private Integer year;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false)
    private Integer status = 1; // 1:AVAILABLE, 2:RENTED, 3:MAINTENANCE, 4:TRANSFERRED

    @Column(name = "current_km")
    private Integer currentKm = 0;

    @Column(name = "fuel_level", length = 20)
    private String fuelLevel = "full";

    @Column(name = "images", columnDefinition = "JSONB")
    private String images = "[]";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Vehicle() {
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isActive == null) {
            this.isActive = true;
        }
        if (this.status == null) {
            this.status = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ==================== Getters & Setters ====================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public void setBranchId(UUID branchId) {
        this.branchId = branchId;
    }

    public UUID getVehicleTypeId() {
        return vehicleTypeId;
    }

    public void setVehicleTypeId(UUID vehicleTypeId) {
        this.vehicleTypeId = vehicleTypeId;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getCurrentKm() {
        return currentKm;
    }

    public void setCurrentKm(Integer currentKm) {
        this.currentKm = currentKm;
    }

    public String getFuelLevel() {
        return fuelLevel;
    }

    public void setFuelLevel(String fuelLevel) {
        this.fuelLevel = fuelLevel;
    }

    public String getImages() {
        return images;
    }

    public void setImages(String images) {
        this.images = images;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
```

---

## 🛠️ Bước 3: Tạo Repository `VehicleTypeRepository.java`

> ℹ️ **Lưu ý quan trọng**: Method `countVehiclesByTenantIdAndVehicleTypeId` sử dụng JPQL cross-entity query tham chiếu tới `Vehicle` entity (đã tạo ở Bước 2). Đây là cách JPA cho phép query liên bảng mà không cần quan hệ `@ManyToOne` trực tiếp — chỉ cần entity `Vehicle` đã được đăng ký với Hibernate là đủ.

**Đường dẫn file**: `backend/src/main/java/com/carrental/vehicletype/repository/VehicleTypeRepository.java`

```java
package com.carrental.vehicletype.repository;

import com.carrental.vehicletype.entity.VehicleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleTypeRepository extends JpaRepository<VehicleType, UUID> {

    Optional<VehicleType> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID id);

    @Query("SELECT vt FROM VehicleType vt WHERE vt.tenantId = :tenantId " +
           "AND (:search IS NULL OR LOWER(vt.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:isActive IS NULL OR vt.isActive = :isActive)")
    Page<VehicleType> findByTenantIdWithFilter(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            @Param("isActive") Boolean isActive,
            Pageable pageable);

    // ⚠️ Cross-entity JPQL: Tham chiếu Entity Vehicle (package com.carrental.vehicle.entity)
    // Entity Vehicle PHẢI tồn tại để Hibernate resolve được câu query này khi Spring Boot khởi chạy.
    @Query("SELECT COUNT(v) FROM Vehicle v WHERE v.tenantId = :tenantId AND v.vehicleTypeId = :vehicleTypeId")
    long countVehiclesByTenantIdAndVehicleTypeId(@Param("tenantId") UUID tenantId, @Param("vehicleTypeId") UUID vehicleTypeId);
}
```

---

## 🛠️ Bước 4: Tạo Các Data Transfer Objects (DTOs)

### 4.1 `CreateVehicleTypeRequestDTO.java`
**Đường dẫn file**: `backend/src/main/java/com/carrental/vehicletype/dto/CreateVehicleTypeRequestDTO.java`

```java
package com.carrental.vehicletype.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CreateVehicleTypeRequestDTO {

    @NotBlank(message = "Tên loại xe không được để trống")
    @Size(max = 50, message = "Tên loại xe tối đa 50 ký tự")
    private String name;

    private String description;

    @NotNull(message = "Giá cơ bản không được để trống")
    @DecimalMin(value = "0.0", message = "Giá cơ bản phải lớn hơn hoặc bằng 0")
    private BigDecimal basePrice;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }
}
```

### 4.2 `UpdateVehicleTypeRequestDTO.java`
**Đường dẫn file**: `backend/src/main/java/com/carrental/vehicletype/dto/UpdateVehicleTypeRequestDTO.java`

```java
package com.carrental.vehicletype.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class UpdateVehicleTypeRequestDTO {

    @NotBlank(message = "Tên loại xe không được để trống")
    @Size(max = 50, message = "Tên loại xe tối đa 50 ký tự")
    private String name;

    private String description;

    @NotNull(message = "Giá cơ bản không được để trống")
    @DecimalMin(value = "0.0", message = "Giá cơ bản phải lớn hơn hoặc bằng 0")
    private BigDecimal basePrice;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }
}
```

### 4.3 `VehicleTypeStatusRequestDTO.java`
**Đường dẫn file**: `backend/src/main/java/com/carrental/vehicletype/dto/VehicleTypeStatusRequestDTO.java`

```java
package com.carrental.vehicletype.dto;

import jakarta.validation.constraints.NotNull;

public class VehicleTypeStatusRequestDTO {

    @NotNull(message = "Trạng thái isActive không được để trống")
    private Boolean status;

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }
}
```

### 4.4 `VehicleTypeResponseDTO.java`
**Đường dẫn file**: `backend/src/main/java/com/carrental/vehicletype/dto/VehicleTypeResponseDTO.java`

```java
package com.carrental.vehicletype.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class VehicleTypeResponseDTO {

    private UUID id;
    private UUID tenantId;
    private String name;
    private String description;
    private BigDecimal basePrice;
    private Boolean isActive;
    private long vehicleCount;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public long getVehicleCount() {
        return vehicleCount;
    }

    public void setVehicleCount(long vehicleCount) {
        this.vehicleCount = vehicleCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
```

---

## 🛠️ Bước 5: Tạo Service Interface & Class Triển Khai

### 5.1 `VehicleTypeService.java`
**Đường dẫn file**: `backend/src/main/java/com/carrental/vehicletype/service/VehicleTypeService.java`

```java
package com.carrental.vehicletype.service;

import com.carrental.vehicletype.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface VehicleTypeService {

    Page<VehicleTypeResponseDTO> getVehicleTypes(UUID tenantId, String search, Boolean isActive, Pageable pageable);

    VehicleTypeResponseDTO getVehicleTypeById(UUID tenantId, UUID id);

    VehicleTypeResponseDTO createVehicleType(UUID tenantId, CreateVehicleTypeRequestDTO request);

    VehicleTypeResponseDTO updateVehicleType(UUID tenantId, UUID id, UpdateVehicleTypeRequestDTO request);

    VehicleTypeResponseDTO changeVehicleTypeStatus(UUID tenantId, UUID id, Boolean status);

    void deleteVehicleType(UUID tenantId, UUID id);
}
```

### 5.2 `VehicleTypeServiceImpl.java`
**Đường dẫn file**: `backend/src/main/java/com/carrental/vehicletype/service/impl/VehicleTypeServiceImpl.java`

```java
package com.carrental.vehicletype.service.impl;

import com.carrental.common.exception.BadRequestException;
import com.carrental.common.exception.ConflictException;
import com.carrental.common.exception.ResourceNotFoundException;
import com.carrental.vehicletype.dto.*;
import com.carrental.vehicletype.entity.VehicleType;
import com.carrental.vehicletype.repository.VehicleTypeRepository;
import com.carrental.vehicletype.service.VehicleTypeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class VehicleTypeServiceImpl implements VehicleTypeService {

    private final VehicleTypeRepository vehicleTypeRepository;

    public VehicleTypeServiceImpl(VehicleTypeRepository vehicleTypeRepository) {
        this.vehicleTypeRepository = vehicleTypeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VehicleTypeResponseDTO> getVehicleTypes(UUID tenantId, String search, Boolean isActive, Pageable pageable) {
        Page<VehicleType> page = vehicleTypeRepository.findByTenantIdWithFilter(tenantId, search, isActive, pageable);
        return page.map(entity -> mapToResponseDTO(tenantId, entity));
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleTypeResponseDTO getVehicleTypeById(UUID tenantId, UUID id) {
        VehicleType entity = vehicleTypeRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle type not found with id: " + id));
        return mapToResponseDTO(tenantId, entity);
    }

    @Override
    @Transactional
    public VehicleTypeResponseDTO createVehicleType(UUID tenantId, CreateVehicleTypeRequestDTO request) {
        String trimmedName = request.getName().trim();
        if (vehicleTypeRepository.existsByTenantIdAndNameIgnoreCase(tenantId, trimmedName)) {
            throw new ConflictException("Tên loại xe đã tồn tại trong hệ thống: " + trimmedName);
        }

        VehicleType entity = new VehicleType();
        entity.setTenantId(tenantId);
        entity.setName(trimmedName);
        entity.setDescription(request.getDescription());
        entity.setBasePrice(request.getBasePrice());
        entity.setIsActive(true);

        VehicleType saved = vehicleTypeRepository.save(entity);
        return mapToResponseDTO(tenantId, saved);
    }

    @Override
    @Transactional
    public VehicleTypeResponseDTO updateVehicleType(UUID tenantId, UUID id, UpdateVehicleTypeRequestDTO request) {
        VehicleType entity = vehicleTypeRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle type not found with id: " + id));

        String trimmedName = request.getName().trim();
        if (vehicleTypeRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, trimmedName, id)) {
            throw new ConflictException("Tên loại xe đã tồn tại trong hệ thống: " + trimmedName);
        }

        entity.setName(trimmedName);
        entity.setDescription(request.getDescription());
        entity.setBasePrice(request.getBasePrice());

        VehicleType updated = vehicleTypeRepository.save(entity);
        return mapToResponseDTO(tenantId, updated);
    }

    @Override
    @Transactional
    public VehicleTypeResponseDTO changeVehicleTypeStatus(UUID tenantId, UUID id, Boolean status) {
        VehicleType entity = vehicleTypeRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle type not found with id: " + id));

        entity.setIsActive(status);
        VehicleType updated = vehicleTypeRepository.save(entity);
        return mapToResponseDTO(tenantId, updated);
    }

    @Override
    @Transactional
    public void deleteVehicleType(UUID tenantId, UUID id) {
        VehicleType entity = vehicleTypeRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle type not found with id: " + id));

        long vehicleCount = vehicleTypeRepository.countVehiclesByTenantIdAndVehicleTypeId(tenantId, id);
        if (vehicleCount > 0) {
            throw new BadRequestException("Không thể xóa loại xe [" + entity.getName() + "] vì đang có " + vehicleCount + " xe thuộc phân khúc này");
        }

        vehicleTypeRepository.delete(entity);
    }

    private VehicleTypeResponseDTO mapToResponseDTO(UUID tenantId, VehicleType entity) {
        VehicleTypeResponseDTO dto = new VehicleTypeResponseDTO();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setBasePrice(entity.getBasePrice());
        dto.setIsActive(entity.getIsActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        long count = vehicleTypeRepository.countVehiclesByTenantIdAndVehicleTypeId(tenantId, entity.getId());
        dto.setVehicleCount(count);

        return dto;
    }
}
```

---

## 🛠️ Bước 6: Tạo REST Controller `VehicleTypeController.java`

**Đường dẫn file**: `backend/src/main/java/com/carrental/vehicletype/controller/VehicleTypeController.java`

```java
package com.carrental.vehicletype.controller;

import com.carrental.common.dto.ApiResponse;
import com.carrental.common.exception.BadRequestException;
import com.carrental.tenant.TenantContext;
import com.carrental.vehicletype.dto.*;
import com.carrental.vehicletype.service.VehicleTypeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicle-types")
public class VehicleTypeController {

    private final VehicleTypeService vehicleTypeService;

    public VehicleTypeController(VehicleTypeService vehicleTypeService) {
        this.vehicleTypeService = vehicleTypeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF', 'SALE')")
    public ResponseEntity<ApiResponse<Page<VehicleTypeResponseDTO>>> getVehicleTypes(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            Pageable pageable) {

        UUID tenantId = getTenantIdOrThrow();
        Page<VehicleTypeResponseDTO> result = vehicleTypeService.getVehicleTypes(tenantId, search, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.success(result, "Lấy danh sách loại xe thành công"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF', 'SALE')")
    public ResponseEntity<ApiResponse<VehicleTypeResponseDTO>> getVehicleTypeById(@PathVariable("id") UUID id) {
        UUID tenantId = getTenantIdOrThrow();
        VehicleTypeResponseDTO result = vehicleTypeService.getVehicleTypeById(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(result, "Lấy chi tiết loại xe thành công"));
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<VehicleTypeResponseDTO>> createVehicleType(
            @Valid @RequestBody CreateVehicleTypeRequestDTO request) {

        UUID tenantId = getTenantIdOrThrow();
        VehicleTypeResponseDTO result = vehicleTypeService.createVehicleType(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result, "Tạo loại xe thành công"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<VehicleTypeResponseDTO>> updateVehicleType(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateVehicleTypeRequestDTO request) {

        UUID tenantId = getTenantIdOrThrow();
        VehicleTypeResponseDTO result = vehicleTypeService.updateVehicleType(tenantId, id, request);
        return ResponseEntity.ok(ApiResponse.success(result, "Cập nhật loại xe thành công"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<VehicleTypeResponseDTO>> changeVehicleTypeStatus(
            @PathVariable("id") UUID id,
            @Valid @RequestBody VehicleTypeStatusRequestDTO request) {

        UUID tenantId = getTenantIdOrThrow();
        VehicleTypeResponseDTO result = vehicleTypeService.changeVehicleTypeStatus(tenantId, id, request.getStatus());
        return ResponseEntity.ok(ApiResponse.success(result, "Cập nhật trạng thái loại xe thành công"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteVehicleType(@PathVariable("id") UUID id) {
        UUID tenantId = getTenantIdOrThrow();
        vehicleTypeService.deleteVehicleType(tenantId, id);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa loại xe thành công"));
    }

    private UUID getTenantIdOrThrow() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BadRequestException("Tenant context is required");
        }
        return tenantId;
    }
}
```

---

## 🛠️ Bước 7: Kiểm Thử Unit Test `VehicleTypeServiceImplTest.java`

**Đường dẫn file**: `backend/src/test/java/com/carrental/vehicletype/service/VehicleTypeServiceImplTest.java`

```java
package com.carrental.vehicletype.service;

import com.carrental.common.exception.BadRequestException;
import com.carrental.common.exception.ConflictException;
import com.carrental.vehicletype.dto.CreateVehicleTypeRequestDTO;
import com.carrental.vehicletype.entity.VehicleType;
import com.carrental.vehicletype.repository.VehicleTypeRepository;
import com.carrental.vehicletype.service.impl.VehicleTypeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleTypeServiceImplTest {

    @Mock
    private VehicleTypeRepository vehicleTypeRepository;

    @InjectMocks
    private VehicleTypeServiceImpl vehicleTypeService;

    private UUID tenantId;
    private UUID typeId;
    private VehicleType vehicleType;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        typeId = UUID.randomUUID();

        vehicleType = new VehicleType();
        vehicleType.setId(typeId);
        vehicleType.setTenantId(tenantId);
        vehicleType.setName("Sedan 4 chỗ");
        vehicleType.setBasePrice(new BigDecimal("1000000.00"));
        vehicleType.setIsActive(true);
    }

    @Test
    void createVehicleType_ShouldSuccess_WhenNameNotExists() {
        CreateVehicleTypeRequestDTO request = new CreateVehicleTypeRequestDTO();
        request.setName("Sedan 4 chỗ");
        request.setBasePrice(new BigDecimal("1000000.00"));

        when(vehicleTypeRepository.existsByTenantIdAndNameIgnoreCase(tenantId, "Sedan 4 chỗ")).thenReturn(false);
        when(vehicleTypeRepository.save(any(VehicleType.class))).thenReturn(vehicleType);

        var result = vehicleTypeService.createVehicleType(tenantId, request);

        assertNotNull(result);
        assertEquals("Sedan 4 chỗ", result.getName());
        verify(vehicleTypeRepository, times(1)).save(any(VehicleType.class));
    }

    @Test
    void createVehicleType_ShouldThrowConflict_WhenNameAlreadyExists() {
        CreateVehicleTypeRequestDTO request = new CreateVehicleTypeRequestDTO();
        request.setName("Sedan 4 chỗ");
        request.setBasePrice(new BigDecimal("1000000.00"));

        when(vehicleTypeRepository.existsByTenantIdAndNameIgnoreCase(tenantId, "Sedan 4 chỗ")).thenReturn(true);

        assertThrows(ConflictException.class, () -> vehicleTypeService.createVehicleType(tenantId, request));
        verify(vehicleTypeRepository, never()).save(any());
    }

    @Test
    void deleteVehicleType_ShouldThrowBadRequest_WhenVehiclesExist() {
        when(vehicleTypeRepository.findByTenantIdAndId(tenantId, typeId)).thenReturn(Optional.of(vehicleType));
        when(vehicleTypeRepository.countVehiclesByTenantIdAndVehicleTypeId(tenantId, typeId)).thenReturn(3L);

        assertThrows(BadRequestException.class, () -> vehicleTypeService.deleteVehicleType(tenantId, typeId));
        verify(vehicleTypeRepository, never()).delete(any());
    }

    @Test
    void deleteVehicleType_ShouldSuccess_WhenNoVehiclesExist() {
        when(vehicleTypeRepository.findByTenantIdAndId(tenantId, typeId)).thenReturn(Optional.of(vehicleType));
        when(vehicleTypeRepository.countVehiclesByTenantIdAndVehicleTypeId(tenantId, typeId)).thenReturn(0L);

        vehicleTypeService.deleteVehicleType(tenantId, typeId);

        verify(vehicleTypeRepository, times(1)).delete(vehicleType);
    }
}
```
