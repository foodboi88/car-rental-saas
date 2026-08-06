package com.carrental.car_rental_backend.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public enum ErrorCode {
  // System Errors
  INTERNAL_SERVER_ERROR("ERR_500", "Lỗi hệ thống nội bộ", HttpStatus.INTERNAL_SERVER_ERROR),
  UNAUTHORIZED("AUTH_UNAUTHORIZED", "Chưa xác thực hoặc Token không hợp lệ", HttpStatus.UNAUTHORIZED),
  FORBIDDEN("AUTH_FORBIDDEN", "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
  BAD_REQUEST("ERR_400", "Dữ liệu yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
  RESOURCE_NOT_FOUND("ERR_404", "Không tìm thấy tài nguyên yêu cầu", HttpStatus.NOT_FOUND),
  VALIDATION_ERROR("VALIDATION_ERROR", "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),
  MALFORMED_JSON("MALFORMED_JSON", "Dữ liệu JSON gửi lên không đúng định dạng", HttpStatus.BAD_REQUEST),

  // Business Custom Errors
  TENANT_ACCESS_DENIED("TENANT_ACCESS_DENIED", "Bạn không thuộc Tenant này", HttpStatus.FORBIDDEN),
  VEHICLE_NOT_AVAILABLE("VEHICLE_NOT_AVAILABLE", "Xe đã có lịch đặt hoặc đang bảo dưỡng", HttpStatus.CONFLICT),
  BOOKING_EXPIRED("BOOKING_EXPIRED", "Thời gian giữ chỗ tạm thời đã hết hạn", HttpStatus.BAD_REQUEST),
  VEHICLE_TYPE_NOT_FOUND("VEHICLE_TYPE_NOT_FOUND", "Không tìm thấy loại xe", HttpStatus.NOT_FOUND),
  VEHICLE_TYPE_NAME_EXISTS("VEHICLE_TYPE_NAME_EXISTS", "Tên loại xe đã tồn tại", HttpStatus.CONFLICT),
  VEHICLE_TYPE_IN_USE("VEHICLE_TYPE_IN_USE", "Không thể xóa loại xe đang có xe phụ thuộc", HttpStatus.CONFLICT);

  private final String code;
  private final String message;
  private final HttpStatus httpStatus;

  ErrorCode(String code, String message, HttpStatus httpStatus) {
    this.code = code;
    this.message = message;
    this.httpStatus = httpStatus;
  }
}
