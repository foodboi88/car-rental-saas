package com.carrental.car_rental_backend.tenant.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carrental.car_rental_backend.tenant.entity.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, UUID>{
  @Query(value = """
    SELECT * FROM tenants
    INNER JOIN user_tenants ON tenants.id = user_tenants.tenant_id
    WHERE user_tenants.user_id = :userId
  """, nativeQuery = true)
  List<Tenant> findAllAssignedTenantsByUserId(@Param("userId") UUID userId);
}
