package com.carrental.car_rental_backend.account.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.carrental.car_rental_backend.account.entity.UserTenant;
import com.carrental.car_rental_backend.account.entity.UserTenantId;

public interface UserTenantRepository extends JpaRepository<UserTenant, UserTenantId>{
  Optional<UserTenant> findByUserIdAndTenantId(UUID userId, UUID tenantId);
  List<UserTenant> findByUserId(UUID userId);
}
