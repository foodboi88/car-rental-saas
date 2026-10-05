package com.carrental.car_rental_backend.auth.dto.response;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponseDTO {
  private UUID id;
  private String email;
  private String fullname;
  private String phone;
  private UUID tenantId;
  private String tenantName;
  private String roleCode; // Cần trường này để xác định hiển thị giao diện cho nhân viên/quản lý
  private List<String> permissions; // Trả về cho client để ẩn hiện chức năng
  private UUID activeBranchId;
  private List<BranchSummaryDTO> assignedBranches;
}
