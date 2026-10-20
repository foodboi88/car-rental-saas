package com.carrental.car_rental_backend.tenant.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantResponseDTO {
  private TenantResponseDTO tenant;
  private List<AdminResult> admins;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AdminResult {
    private String email;
    private boolean accountCreated; // true: tạo tài khoản mới, false: chỉ liên kết tài khoản có sẵn
  }
}
