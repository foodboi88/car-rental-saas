package com.carrental.car_rental_backend.account.dto;

import java.util.List;
import java.util.UUID;

import com.carrental.car_rental_backend.common.enums.user.UserRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTORequest {
    public interface CreateGroup {} 
    public interface UpdateGroup {}

    private UUID id;
    private String fullname;
    private String phone;
    private Integer pageIndex = 0;
    private Integer pageSize = 10;
    
    @Data
    public static class UserDTORequestToCreate {
        @NotBlank(groups = CreateGroup.class, message = "Email không được để trống")
        @Email(groups = CreateGroup.class, message = "Email không đúng định dạng")
        private String email;

        @NotBlank(groups = CreateGroup.class, message = "Mật khẩu không được để trống")
        @Size(groups = CreateGroup.class, min = 8, message = "Mật khẩu tối thiểu 8 ký tự")
        private String password;

        @NotBlank(groups = CreateGroup.class, message = "Họ và tên không được để trống")
        private String fullname;

        @NotBlank(groups = CreateGroup.class, message = "Số điện thoại không được để trống")
        @Pattern(groups = CreateGroup.class, regexp = "^0[35789]\\d{8}$", message = "Số điện thoại không đúng định dạng")
        private String phone;

        private String avatarUrl;

        @NotNull(groups = CreateGroup.class, message = "Vai trò không được để trống")
        private UserRole role;

        private List<UUID> branchIds;
    }

    @Data
    public static class UserDTORequestToUpdate {
        @NotBlank(groups = UpdateGroup.class, message = "Email không được để trống")
        @Email(groups = UpdateGroup.class, message = "Email không đúng định dạng")
        private String email;

        @Size(groups = UpdateGroup.class, max = 255, message = "Họ và tên không được vượt quá 255 ký tự")
        private String fullname;

        //giải thích tại sao lại cần groups = UpdateGroup.class
        @Pattern(groups = UpdateGroup.class, regexp = "^0[35789]\\d{8}$", message = "Số điện thoại không đúng định dạng")
        private String phone;

        private String avatarUrl;
        private UserRole role;
        private List<UUID> branchIds;
    }
}

