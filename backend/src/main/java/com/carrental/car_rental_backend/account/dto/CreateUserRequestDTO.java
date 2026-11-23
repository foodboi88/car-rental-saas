package com.carrental.car_rental_backend.account.dto;

import java.util.List;
import java.util.UUID;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@AllArgsConstructor 
@NoArgsConstructor
@Builder 
public class CreateUserRequestDTO {

    @NotBlank (message = "Email không được để trống")
    @Email (message = "Email không đúng định dạng")
    private String email;

    @NotBlank (message = "Mật khẩu không được để trống")
    @Size (min = 6, max = 50, message = "Mật khẩu phải từ 6 tới 50 ký tự")
    private String password;

    @NotBlank (message = "Họ tên không được để trống")
    private String fullName;

    @Pattern (regexp = "^(0|\\+84)[0-9]{9}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    private String avatarUrl;

    @NotNull (message = "Vai trò không được để trống")
    private UUID roleId;

    private List<UUID> branchIds;
}
