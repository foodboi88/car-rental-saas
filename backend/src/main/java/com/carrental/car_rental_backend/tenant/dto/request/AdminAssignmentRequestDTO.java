package com.carrental.car_rental_backend.tenant.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAssignmentRequestDTO {
  @NotBlank(message = "Vui lòng nhập email chủ nhà xe")
  @Email(message = "Email chủ nhà xe không đúng định dạng")
  @Size(max = 255, message = "Email chủ nhà xe tối đa 255 ký tự")
  private String email;

  // Chỉ bắt buộc khi email chưa có tài khoản, Service sẽ kiểm tra điều kiện này
  @Size(max = 255, message = "Họ tên tối đa 255 ký tự")
  private String fullName;

  // Chỉ bắt buộc khi email chưa có tài khoản. Tối đa 72 vì BCrypt chỉ dùng 72 byte đầu
  @Pattern(
    regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$",
    message = "Mật khẩu ít nhất 8 ký tự, gồm chữ và số"
  )
  private String password;
}
