package com.carrental.car_rental_backend.auth.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectTenantRequestDTO {
  @NotNull(message = "Vui lòng chọn tenant")
  private UUID tenantId;

  @NotNull(message = "Vui lòng chọn chi nhánh")
  private UUID activeBranchId;
}
