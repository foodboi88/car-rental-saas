package com.carrental.car_rental_backend.tenant.constant;

import java.util.List;

import com.carrental.car_rental_backend.common.constant.RoleConstant;

import lombok.Getter;

@Getter
public enum DefaultRole {
  // permissionCodes rỗng nghĩa là được toàn bộ quyền trong bảng permissions
  TENANT_ADMIN(RoleConstant.TENANT_ADMIN, "Chủ doanh nghiệp", "Toàn quyền quản lý nhà xe", List.of()),
  STAFF("STAFF", "Nhân viên nghiệp vụ", "Nhân viên điều hành và giao nhận xe tại chi nhánh",
      List.of("booking:read", "booking:create", "booking:confirm_deposit",
          "booking:handover", "booking:return", "booking:cancel", "vehicle:read")),
  SALE("SALE", "Cộng tác viên kinh doanh", "Cộng tác viên tạo đơn và nhận hoa hồng",
      List.of("booking:read", "booking:create", "booking:cancel", "vehicle:read"));

  private final String code;
  private final String name;
  private final String description;
  private final List<String> permissionCodes;

  DefaultRole(String code, String name, String description, List<String> permissionCodes) {
    this.code = code;
    this.name = name;
    this.description = description;
    this.permissionCodes = permissionCodes;
  }

  public boolean grantsAllPermissions() {
    return this.permissionCodes.isEmpty();
  }
}
