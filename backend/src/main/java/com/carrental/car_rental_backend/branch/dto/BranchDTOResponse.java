package com.carrental.car_rental_backend.branch.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor 

//sử dụng để truyền dữ liệu giữa Controller và Client
//định dạng dữ liệu sẽ trả về cho client
public class BranchDTOResponse {
    // private UUID id;
    private String name;
    // private String phone;
    private String email;
    // private String address;
    // private String city;
    // private String district;
    // private String ward;
    // private String opening_hours;
    // private BigDecimal latitude;
    // private BigDecimal longtitude;
    // private Boolean isCentral;
    // private Boolean isActive;
    // private OffsetDateTime creatAt;
    // private OffsetDateTime updateAt;
}
