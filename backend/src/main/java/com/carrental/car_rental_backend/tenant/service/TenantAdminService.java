package com.carrental.car_rental_backend.tenant.service;

import java.util.List;
import java.util.UUID;

import com.carrental.car_rental_backend.account.entity.User;
import com.carrental.car_rental_backend.tenant.dto.request.AdminAssignmentRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.response.AdminAssignmentResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.AdminEmailCheckResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantAdminResponseDTO;
import com.carrental.car_rental_backend.tenant.entity.Tenant;

public interface TenantAdminService {
  record ValidatedAdmin(String email, String fullName, String password, User existingUser) {
    public boolean isNewAccount() {
      return this.existingUser == null;
    }

    // Record tự sinh toString() in cả password; chỉ in phần không nhạy cảm.
    @Override
    public String toString() {
      return "ValidatedAdmin[email=" + this.email + ", newAccount=" + this.isNewAccount() + "]";
    }
  }

  public List<ValidatedAdmin> validateAdminsForNewTenant(List<AdminAssignmentRequestDTO> requests);
  public List<ValidatedAdmin> validateAdminRequests(List<AdminAssignmentRequestDTO> requests, Tenant tenant, UUID adminRoleId);
  public List<AdminAssignmentResponseDTO> addAdminsToTenant(UUID tenantId, UUID adminRoleId, List<ValidatedAdmin> validatedAdmins);
  public AdminAssignmentResponseDTO addTenantAdmin(UUID tenantId, AdminAssignmentRequestDTO request, UUID actorId);
  public List<TenantAdminResponseDTO> listTenantAdmins(UUID tenantId);
  public AdminEmailCheckResponseDTO checkAdminEmail(String email, UUID tenantId);
}
