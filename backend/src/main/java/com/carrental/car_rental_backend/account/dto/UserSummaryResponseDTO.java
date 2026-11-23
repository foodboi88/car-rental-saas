package com.carrental.car_rental_backend.account.dto;

import java.time.Instant;
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
public class UserSummaryResponseDTO {

    private UUID id;
    private String fullname;
    private String phone;
    private String roleName;
    private String roleCode;
    private Boolean isActive;
    private Integer assignedBranchCount;
    private Instant lastLoginAt;
    private Instant joinedAt;

}
