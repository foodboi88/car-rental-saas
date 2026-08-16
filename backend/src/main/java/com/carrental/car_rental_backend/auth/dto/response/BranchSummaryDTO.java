package com.carrental.car_rental_backend.auth.dto.response;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchSummaryDTO {
  private UUID branchId;
  private String branchName;
  private String branchCode; 
}
