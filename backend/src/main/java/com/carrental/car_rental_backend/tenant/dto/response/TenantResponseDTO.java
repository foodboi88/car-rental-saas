package com.carrental.car_rental_backend.tenant.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.carrental.car_rental_backend.common.enums.plan_tier.PlanTierTenant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantResponseDTO {
  private UUID id;
  private String name;
  private String domain;
  private PlanTierTenant planTier;
  private String logoUrl;
  private String contactEmail;
  private String contactPhone;
  private String settings;
  private Boolean isActive;
  private Instant createdAt;
  private Instant updatedAt;
}
