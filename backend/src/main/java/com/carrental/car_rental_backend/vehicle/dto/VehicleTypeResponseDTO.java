package com.carrental.car_rental_backend.vehicle.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class VehicleTypeResponseDTO {
  private UUID id;
  private UUID tenantId;
  private String name;
  private String description;
  private Boolean isActive;
  private long vehicleCount;
  private Instant createdAt;
  private Instant updatedAt;
}
