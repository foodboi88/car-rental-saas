package com.carrental.car_rental_backend.tenant.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTenantStatusRequestDTO {
  @NotNull(message = "Vui lòng chọn trạng thái hoạt động")
  private Boolean isActive;
}
