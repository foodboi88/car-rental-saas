package com.carrental.car_rental_backend.tenant.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {
  private int maxBytes;

  @Override
  public void initialize(MaxUtf8Bytes constraint) {
    this.maxBytes = constraint.value();
  }

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    // null để Service quyết định theo tài khoản mới/có sẵn; constraint này chỉ giới hạn số byte.
    return value == null || value.getBytes(StandardCharsets.UTF_8).length <= this.maxBytes;
  }
}
