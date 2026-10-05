package com.carrental.car_rental_backend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDTO {
  @NotBlank(message = "Vui lòng nhập email")
  @Email(message = "Email không đúng định dạng")
  private String email;

  @NotBlank(message = "Vui lòng nhập mật khẩu")
  private String password;
}
