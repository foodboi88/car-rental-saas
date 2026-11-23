package com.carrental.car_rental_backend.account.dto;

import java.util.UUID;

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
public class RoleSummaryDTO {
    private UUID id;
    private String name;
    private String code;
}
