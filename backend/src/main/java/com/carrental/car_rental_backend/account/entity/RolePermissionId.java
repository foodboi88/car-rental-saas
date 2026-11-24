package com.carrental.car_rental_backend.account.entity;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionId {
  private UUID roleId;
  private UUID permissionId;
}
