package com.carrental.car_rental_backend.tenant.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.carrental.car_rental_backend.common.enums.plan_tier.PlanTierTenant;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantResponseDTO {
  private UUID id;
  private String name;
  private String domain;
  private String logoUrl;
  private String contactEmail;
  private String contactPhone;
  private PlanTierTenant planTier;
  private boolean isActive;
  private Instant createdAt;
  private Instant updatedAt;

  @JsonProperty("isActive")
  public boolean isActive() {
    return this.isActive;
  }
}
