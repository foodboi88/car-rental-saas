package com.carrental.car_rental_backend.common.constant;

public final class RoleConstant {
  // Private constructor để ngăn chặn việc khởi tạo đối tượng `new RoleConstant()`
  private RoleConstant() {
  }
  // Mã Role logic
  public static final String SUPER_ADMIN = "SUPER_ADMIN";
  public static final String TENANT_ADMIN = "TENANT_ADMIN";
  // Tiền tố chuẩn của Spring Security nếu cần dùng
  public static final String PREFIX_ROLE = "ROLE_";
  public static final String ROLE_SUPER_ADMIN = PREFIX_ROLE + SUPER_ADMIN;
}
