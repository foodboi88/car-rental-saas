package com.carrental.car_rental_backend.common.enums.plan_tier;

import com.carrental.car_rental_backend.common.enums.converter.AbstractEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PlanTierTenantConverter extends AbstractEnumConverter<PlanTierTenant, Short> {
  public PlanTierTenantConverter() {
    super(PlanTierTenant.class);
  }
}
