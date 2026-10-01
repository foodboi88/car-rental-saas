package com.carrental.car_rental_backend.auth.service;

import java.util.UUID;

import com.carrental.car_rental_backend.auth.dto.request.LoginRequestDTO;
import com.carrental.car_rental_backend.auth.dto.request.RefreshTokenRequestDTO;
import com.carrental.car_rental_backend.auth.dto.request.SelectTenantRequestDTO;
import com.carrental.car_rental_backend.auth.dto.response.AuthResponseDTO;
import com.carrental.car_rental_backend.auth.dto.response.UserInfoResponseDTO;

public interface AuthService {
  public AuthResponseDTO login(LoginRequestDTO request);
  public AuthResponseDTO selectTenant(UUID currentUserId, SelectTenantRequestDTO request);
  public AuthResponseDTO refreshToken(RefreshTokenRequestDTO request);
  public UserInfoResponseDTO getMe();
}
