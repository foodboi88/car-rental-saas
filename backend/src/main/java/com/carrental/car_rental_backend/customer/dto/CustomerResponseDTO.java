package com.carrental.car_rental_backend.customer.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class CustomerResponseDTO {

  private UUID id;
  private UUID tenantId;
  private String name;
  private String phone;
  private String email;
  private String address;
  private Integer riskLevel;
  private String blacklistReason;
  private String notes;
  private Instant createdAt;
  private Instant updatedAt;
}
