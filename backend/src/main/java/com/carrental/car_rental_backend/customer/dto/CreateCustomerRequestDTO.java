package com.carrental.car_rental_backend.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCustomerRequestDTO {

  @NotBlank(message = "Tên khách hàng không được để trống")
  @Size(max = 255, message = "Tên khách hàng tối đa 255 ký tự")
  private String name;

  @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
  private String phone;

  @Email(message = "Email không đúng định dạng")
  @Size(max = 255, message = "Email tối đa 255 ký tự")
  private String email;

  private String address;

  private String notes;
}
