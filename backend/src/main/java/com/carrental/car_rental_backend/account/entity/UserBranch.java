package com.carrental.car_rental_backend.account.entity;

import java.time.Instant;
import java.util.UUID;

import com.carrental.car_rental_backend.common.enums.user_branch_status.UserBranchStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_branches")
@IdClass(UserBranchId.class)
public class UserBranch {
  @Id
  @Column(name = "tenant_id", nullable = false)
  private UUID tenantId;

  @Id
  @Column(name = "branch_id", nullable = false)
  private UUID branchId;

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "status")
  private UserBranchStatus status;

  @Column(name = "updated_by")
  private UUID updatedBy;
}
