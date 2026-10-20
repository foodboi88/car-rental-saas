package com.carrental.car_rental_backend.tenant.dto.response;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantAdminResponseDTO {
  private UUID userId;
  private String email;
  private String fullName;
  private Boolean isActive;
  private Instant joinedAt;
}