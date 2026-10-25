package com.carrental.car_rental_backend.tenant.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantResponseDTO {
  private TenantResponseDTO tenant;
  private List<AdminAssignmentResponseDTO> admins;
}
