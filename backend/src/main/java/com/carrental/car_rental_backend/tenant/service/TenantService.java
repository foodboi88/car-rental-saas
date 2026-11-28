package com.carrental.car_rental_backend.tenant.service;

import org.springframework.data.domain.Page;

import com.carrental.car_rental_backend.tenant.dto.request.CreateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantResponseDTO;

public interface TenantService {
  public TenantResponseDTO createTenant(CreateTenantRequestDTO request);
  public Page<TenantResponseDTO> listTenants(int page, int size);
}
