package com.carrental.car_rental_backend.customer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeRiskLevelRequestDTO {

  @NotNull(message = "Mức độ rủi ro không được để trống")
  private Integer riskLevel; // 1:SAFE, 2:WARNING, 3:BLACKLIST

  private String blacklistReason; // Bắt buộc có giá trị khi riskLevel = 3, validate ở Service
}
