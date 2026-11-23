package com.carrental.car_rental_backend.account.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.carrental.car_rental_backend.account.entity.UserBranch;
import com.carrental.car_rental_backend.account.entity.UserBranchId;

public interface UserBranchRepository extends JpaRepository<UserBranch, UserBranchId> {
  Optional<UserBranch> findByUserIdAndBranchId(UUID userId, UUID branchId);
  Optional<UserBranch> findByTenantIdAndUserIdAndBranchId(UUID tenantId, UUID userId, UUID branchId);

  List<UserBranch> findAllByTenantIdAndUserId(UUID tenantId, UUID userId);

  void deleteByTenantIdAndUserId(UUID tenantId, UUID userId);
}
