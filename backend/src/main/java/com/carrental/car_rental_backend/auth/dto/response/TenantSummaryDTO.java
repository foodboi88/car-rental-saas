package com.carrental.car_rental_backend.auth.dto.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSummaryDTO {
  private UUID tenantId;
  private String tenantName;
  private String domain;
  private String role;
  private Boolean isActive;
}
