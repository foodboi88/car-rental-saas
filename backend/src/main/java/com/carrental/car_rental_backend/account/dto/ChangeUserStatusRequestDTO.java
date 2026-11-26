package com.carrental.car_rental_backend.account.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@NoArgsConstructor 
@AllArgsConstructor 

public class ChangeUserStatusRequestDTO {
    @NotNull (message = "Trạng thái hoạt động không được để trống")
    private Boolean isActive;
}
