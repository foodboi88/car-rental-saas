package com.carrental.car_rental_backend.tenant.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Giới hạn số byte UTF-8; @Size chỉ đo độ dài chuỗi Java. */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
public @interface MaxUtf8Bytes {
  int value();
  String message() default "Giá trị tối đa {value} byte UTF-8";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};
}
