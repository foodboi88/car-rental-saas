package com.carrental.car_rental_backend.account.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.carrental.car_rental_backend.account.entity.UserBranch;
import com.carrental.car_rental_backend.account.entity.UserBranchId;

public interface UserBranchRepository extends JpaRepository<UserBranch, UserBranchId> {
  Optional<UserBranch> findByUserIdAndBranchId(UUID userId, UUID branchId);
}
