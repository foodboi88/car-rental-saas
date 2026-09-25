package com.carrental.car_rental_backend.auth.service;

import java.util.UUID;

import com.carrental.car_rental_backend.security.principal.UserPrincipal;

public interface CustomUserDetailsService {
  public UserPrincipal loadUserByIdAndTenantId(UUID userId, UUID tenantId);
  public UserPrincipal loadSuperAdminById(UUID userId);
}
