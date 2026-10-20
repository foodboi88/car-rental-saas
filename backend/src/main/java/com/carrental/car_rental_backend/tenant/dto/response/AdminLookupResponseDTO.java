package com.carrental.car_rental_backend.tenant.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLookupResponseDTO {
  private String email;
  private boolean accountExists;
  private String fullName;
  private boolean canBeAdmin;
  private String reason;
}
