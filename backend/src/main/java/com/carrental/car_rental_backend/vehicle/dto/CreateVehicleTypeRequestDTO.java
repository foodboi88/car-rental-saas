package com.carrental.car_rental_backend.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateVehicleTypeRequestDTO {
  @NotBlank(message = "Tên loại xe không được để trống")
  @Size(max = 50, message = "Tên loại xe tối đa 50 ký tự")
  private String name;

  private String description;
}
