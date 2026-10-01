package com.carrental.car_rental_backend.common.enums.user_branch_status;

import com.carrental.car_rental_backend.common.enums.BaseEnum;

public enum UserBranchStatus implements BaseEnum<Short> {
  ACTIVE((short) 1, "Đang làm"),
  SUSPENDED((short) 2, "Tạm dừng"),
  RESIGNED((short) 3, "Nghỉ việc");
  
  private final Short value;
  private final String label;

  UserBranchStatus(Short value, String label) {
    this.value = value;
    this.label = label;
  }

  @Override
  public Short getValue() {
    return this.value;
  }
  
}
