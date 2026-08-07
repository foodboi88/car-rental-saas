package com.carrental.car_rental_backend.vehicle.controller;

import com.carrental.car_rental_backend.common.dto.ApiResponse;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.security.context.TenantContext;
import com.carrental.car_rental_backend.vehicle.dto.CreateVehicleTypeRequestDTO;
import com.carrental.car_rental_backend.vehicle.dto.VehicleTypeResponseDTO;
import com.carrental.car_rental_backend.vehicle.service.VehicleTypeService;
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

  @PostMapping
  @PreAuthorize("hasRole('TENANT_ADMIN')")
  public ResponseEntity<ApiResponse<VehicleTypeResponseDTO>> createVehicleType(
      @Valid @RequestBody CreateVehicleTypeRequestDTO request
  ) {
    UUID tenantId = getTenantIdOrThrow();
    VehicleTypeResponseDTO result = vehicleTypeService.createVehicleType(tenantId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result, "Tạo loại xe thành công"));
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF', 'SALE')")
  public ResponseEntity<ApiResponse<Page<VehicleTypeResponseDTO>>>
  getVehicleTypes(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) Boolean isActive,
      Pageable pageable
  ) {
    UUID tenantId = getTenantIdOrThrow();
    Page<VehicleTypeResponseDTO> result = vehicleTypeService.getVehicleTypes(tenantId, search, isActive, pageable);
    return ResponseEntity.ok(ApiResponse.success(result, "Lấy danh sách loại xe thành công"));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'STAFF', 'SALE')")
  public ResponseEntity<ApiResponse<VehicleTypeResponseDTO>> getVehicleTypeById(
      @PathVariable UUID id
  ) {
    UUID tenantId = getTenantIdOrThrow();
    VehicleTypeResponseDTO result = vehicleTypeService.getVehicleTypeById(tenantId, id);
    return ResponseEntity.ok(ApiResponse.success(result, "Lấy chi tiết loại xe thành công"));
  }

  private UUID getTenantIdOrThrow() {
    UUID tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Tenant là bắt buộc");
    }
    return tenantId;
  }
}
