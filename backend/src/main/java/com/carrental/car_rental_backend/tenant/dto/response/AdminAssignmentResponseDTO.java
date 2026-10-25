package com.carrental.car_rental_backend.tenant.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAssignmentResponseDTO {
  private String email;
  private boolean accountCreated;
}