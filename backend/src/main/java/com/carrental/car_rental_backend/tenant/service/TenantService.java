package com.carrental.car_rental_backend.tenant.service;

import com.carrental.car_rental_backend.tenant.dto.request.CreateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantResponseDTO;

public interface TenantService {
  public TenantResponseDTO createTenant(CreateTenantRequestDTO request);
}
