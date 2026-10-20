package com.carrental.car_rental_backend.tenant.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.carrental.car_rental_backend.tenant.dto.request.AdminAssignmentRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.request.CreateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.request.UpdateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.response.AdminLookupResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.CreateTenantResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantAdminResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantResponseDTO;

public interface TenantService {
  public CreateTenantResponseDTO createTenant(CreateTenantRequestDTO request, UUID actorId);
  public TenantResponseDTO getTenantById(UUID tenantId);
  public Page<TenantResponseDTO> listTenants(String keyword, Boolean isActive, Pageable pageable);
  public TenantResponseDTO updateTenant(UUID tenantId, UpdateTenantRequestDTO request, UUID actorId);
  public TenantResponseDTO changeStatus(UUID tenantId, boolean isActive, UUID actorId);
  public void deleteTenant(UUID tenantId, UUID actorId);
  public CreateTenantResponseDTO.AdminResult addTenantAdmin(UUID tenantId, AdminAssignmentRequestDTO request, UUID actorId);
  public List<TenantAdminResponseDTO> listTenantAdmins(UUID tenantId);
  public AdminLookupResponseDTO lookupAdminByEmail(String email, UUID tenantId);
}
