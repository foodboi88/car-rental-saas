package com.carrental.car_rental_backend.tenant.constant;

import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;

import lombok.Getter;

@Getter
public enum AdminRejection {
  TENANT_LOCKED(ErrorCode.BAD_REQUEST, "Nhà xe đang bị khóa, không thể thêm chủ nhà xe !"),
  ACCOUNT_LOCKED(ErrorCode.BAD_REQUEST, "Tài khoản đang bị khóa !"),
  SUPER_ADMIN(ErrorCode.BAD_REQUEST, "Tài khoản Super Admin không thể làm chủ nhà xe"),
  ALREADY_MEMBER(ErrorCode.TENANT_MEMBER_ALREADY_EXISTS, ErrorCode.TENANT_MEMBER_ALREADY_EXISTS.getMessage());

  private final ErrorCode errorCode;
  private final String message;

  AdminRejection(ErrorCode errorCode, String message) {
    this.errorCode = errorCode;
    this.message = message;
  }

  public AppException toException() {
    return new AppException(this.errorCode, this.message);
  }
}
