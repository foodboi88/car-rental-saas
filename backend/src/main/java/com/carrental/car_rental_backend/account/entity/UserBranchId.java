package com.carrental.car_rental_backend.account.entity;

import java.io.Serializable;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBranchId implements Serializable {
  private UUID tenantId;
  private UUID branchId;
  private UUID userId;
}
