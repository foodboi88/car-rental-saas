package com.carrental.car_rental_backend.common.enums.plan_tier;

import com.carrental.car_rental_backend.common.enums.BaseEnum;

import lombok.Getter;

@Getter
public enum PlanTierTenant implements BaseEnum<Short> {
  // Các level gói dịch vụ Car rental
  FREE((short) 1, "Gói miễn phí"),
  BASIC((short) 2, "Gói cơ bản"),
  PRO((short) 3, "Gói nâng cao"),
  ENTERPRISE((short) 4, "Gói doanh nghiệp");

  private final Short value;
  private final String label;

  PlanTierTenant(Short value, String label) {
    this.value = value;
    this.label = label;
  }

  @Override
  public Short getValue() {
    return this.value;
  }
}
