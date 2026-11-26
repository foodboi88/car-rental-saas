package com.carrental.car_rental_backend.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter 
@Getter 
@NoArgsConstructor 
@AllArgsConstructor 

public class ResetPasswordRequestDTO {
    @NotBlank @Size(min = 6, max = 50)
    private String newPassword;
}
