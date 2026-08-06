package com.carrental.car_rental_backend.vehicle.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class VehicleTypeStatusRequestDTO {
  @NotNull(message = "Trạng thái hoạt động không được để trống")
  private Boolean isActive;
}
