package com.carrental.car_rental_backend.common.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException{
  private ErrorCode errorCode;

  public AppException(ErrorCode error) {
    super(error.getMessage());
    this.errorCode = error;
  }

  public AppException(ErrorCode error, String customMessage) {
    super(customMessage);
    this.errorCode = error;
  }
}
