package com.carrental.car_rental_backend.branch.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data 
@NoArgsConstructor
@AllArgsConstructor 

public class BranchSummaryDTO {
    private UUID id;
    private String name;
    private String address;
    private String phone;
}
