package com.carrental.car_rental_backend.account.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "role_permissions")
@IdClass(RolePermissionId.class)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermission {
  @Id
  @Column(name = "role_id", nullable = false)
  private UUID roleId;

  @Id
  @Column(name = "permission_id", nullable = false)
  private UUID permissionId;
}
