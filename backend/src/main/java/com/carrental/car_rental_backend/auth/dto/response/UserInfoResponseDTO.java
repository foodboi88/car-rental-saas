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
  private String role;
  private List<String> permissions;
  private UUID activeBranchId;
  private List<BranchSummaryDTO> assignedBranches;
}
