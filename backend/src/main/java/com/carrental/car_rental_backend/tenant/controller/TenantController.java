package com.carrental.car_rental_backend.tenant.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carrental.car_rental_backend.common.dto.ApiResponse;
import com.carrental.car_rental_backend.tenant.dto.request.CreateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantResponseDTO;
import com.carrental.car_rental_backend.tenant.service.TenantService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {
  private final TenantService tenantService;

  @PostMapping
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<ApiResponse<TenantResponseDTO>> createTenant(
      @Valid @RequestBody CreateTenantRequestDTO request) {
    TenantResponseDTO response = this.tenantService.createTenant(request);
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, "Tạo nhà xe thành công"));
  }

}
