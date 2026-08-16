package com.carrental.car_rental_backend.auth.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDTO {
  private String accessToken;
  private String refreshToken;
  private String tokenType;
  private long expiresIn;
  private UserInfoResponseDTO user;
  private List<TenantSummaryDTO> availableTenants;
}
