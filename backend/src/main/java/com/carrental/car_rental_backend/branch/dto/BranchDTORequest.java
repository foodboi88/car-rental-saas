package com.carrental.car_rental_backend.branch.dto;

import java.util.UUID;

import com.carrental.car_rental_backend.branch.dto.BranchDTORequest.BranchDTORequestToCreate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
// định dạng dữ liệu client khi client gửi yêu cầu
// Nested Class (class lồng)
public class BranchDTORequest {

    //sử dụng để áp dụng validate
    public interface CreateGroup {}
    public interface UpdateGroup {}

    private UUID id;
    private String name;
    private String phone;
    private String keyword;
    private Boolean isActive;
    private Integer pageIndex = 0;
    private Integer pageSize = 10;

    //sử dụng mô hình namespace
    // interface nội bộ
    @Data
    //class con hoạt động độc lập, có thể khởi tạo thông qua class cha
    public static class BranchDTORequestToCreate {
        private UUID tenantId;

        @NotBlank(groups = CreateGroup.class, message = "Tên chi nhánh không được để trống")
        @Size(groups = CreateGroup.class, max = 255, message = "Tên chi nhánh không được vượt quá 255 ký tự")
        private String name;

        @Pattern(groups = CreateGroup.class, regexp = "^0[35789]\\d{8}$", message = "Số điện thoại không đúng định dạng")
        private String phone;

        @Email
        private String email;
        private String address;
    }

    @Data 
    public static class  BranchDTORequestToUpdate {
        @Size(groups = UpdateGroup.class, max = 255, message = "Tên chi nhánh không được vượt quá 255 ký tự")
        private String name;

        @Pattern(groups = UpdateGroup.class, regexp = "^0[35789]\\d{8}$", message = "Số điện thoại không đúng định dạng")
        private String phone;

        @Email
        private String email;
        private String address;
    }

    @Data 
    public static class BranchStatusUpdateRequest {
        private Boolean isActive;
    }
}
