package com.carrental.car_rental_backend.common.enums.user_branch_status;

import com.carrental.car_rental_backend.common.enums.converter.AbstractEnumConverter;

import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserBranchStatusConverter extends AbstractEnumConverter<UserBranchStatus, Short>{
  public UserBranchStatusConverter() {
    super(UserBranchStatus.class);
  }
}
