package com.carrental.car_rental_backend.auth.dto.response;

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
public class UserSessionResponseDTO {
  private UUID id;
  private String deviceName;
  private String ipAddress;
  private Instant lastActiveAt;
  private Instant createdAt;
  private Instant expiresAt;
  private Boolean isCurrent;
}
