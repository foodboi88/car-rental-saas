package com.carrental.car_rental_backend.account.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.carrental.car_rental_backend.auth.dto.response.BranchSummaryDTO;

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
public class UserDetailResponseDTO {

    private UUID id;
    private String email;
    private String fullname;
    private String phone;
    private String avatarURL;
    private Boolean isActive;
    private RoleSummaryDTO role;
    private List<BranchSummaryDTO> assignedBranches;
    private Instant joinedAt;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Long version;

}
