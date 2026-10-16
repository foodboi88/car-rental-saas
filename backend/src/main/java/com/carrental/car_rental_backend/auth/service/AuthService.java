package com.carrental.car_rental_backend.auth.service;

import java.util.UUID;

import com.carrental.car_rental_backend.auth.dto.request.LoginRequestDTO;
import com.carrental.car_rental_backend.auth.dto.request.SelectTenantRequestDTO;
import com.carrental.car_rental_backend.auth.dto.request.SwitchBranchRequestDTO;
import com.carrental.car_rental_backend.auth.dto.response.AuthServiceResult;
import com.carrental.car_rental_backend.auth.dto.response.UserInfoResponseDTO;

public interface AuthService {
  public AuthServiceResult login(LoginRequestDTO request);
  public AuthServiceResult selectTenant(UUID currentUserId, SelectTenantRequestDTO request);
  public AuthServiceResult switchBranch(UUID currentUserId, SwitchBranchRequestDTO request);
  public AuthServiceResult refreshToken(String refreshToken);
  public UserInfoResponseDTO getMe();
  public void logout();
}
