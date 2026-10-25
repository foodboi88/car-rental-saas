package com.carrental.car_rental_backend.tenant.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminEmailCheckRequestDTO {
  @NotBlank(message = "Vui lòng nhập email cần kiểm tra")
  @Size(max = 255, message = "Email tối đa 255 ký tự")
  private String email;
  private UUID tenantId;
}
