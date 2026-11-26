package com.carrental.car_rental_backend.account.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 
public class UpdateUserRequestDTO {

    @NotBlank (message = "Họ tên không được để trống")
    private String fullname;

    @Pattern (regexp = "^(0|\\+84)[0-9]{9}$", message = "Số điện thoại không hợp lệ")
    private String phone;
    private String avatarUrl;

    @NotNull (message = "Vai trò không được để trống")
    private UUID roleId;

    private List<UUID> branchIds;

    @NotNull (message = "Trạng thái hoạt động không được để trống")
    private Boolean isActive;

    @NotNull(message = "Phiên bản dữ liệu không được để trống")
    private Long version;
}
