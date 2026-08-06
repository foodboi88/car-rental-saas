package com.carrental.car_rental_backend.vehicle.controller;

import com.carrental.car_rental_backend.common.dto.ApiResponse;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.security.context.TenantContext;
import com.carrental.car_rental_backend.vehicle.dto.CreateVehicleTypeRequestDTO;
import com.carrental.car_rental_backend.vehicle.dto.VehicleTypeResponseDTO;
import com.carrental.car_rental_backend.vehicle.service.VehicleTypeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

  private UUID getTenantIdOrThrow() {
    UUID tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Tenant là bắt buộc");
    }
    return tenantId;
  }
}
