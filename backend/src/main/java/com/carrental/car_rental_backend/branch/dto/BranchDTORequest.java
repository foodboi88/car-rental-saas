package com.carrental.car_rental_backend.branch.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
//định dạng dữ liệu client khi client gửi yêu cầu
public class BranchDTORequest {
    @NotBlank(message = "Tên chi nhánh không được để trống")
    private String name;

    private String phone;
    private String email;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longtitude;
    private Boolean isCentral;
}
