package com.carrental.car_rental_backend.account.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor 
public class UserDTOResponse {
    private UUID id;
    private String email;
    private String fullname;
    private String phone;
    private String avatarUrl;
    private Boolean isActive;
    private Boolean isSuperAdmin;
    private Instant lastLoginAt;
    private Instant createdAt;
}

