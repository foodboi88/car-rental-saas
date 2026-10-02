package com.carrental.car_rental_backend.account.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carrental.car_rental_backend.account.entity.Role;

public interface RoleRepository extends JpaRepository<Role, UUID> {
  Optional<Role> findByCode(String roleCode);

  @Query(value = """
    SELECT * FROM roles
    INNER JOIN user_tenants ON roles.id = user_tenants.role_id
    WHERE user_tenants.user_id = :userId and user_tenants.tenant_id = :tenantId
  """, nativeQuery = true)
  Optional<Role> findRoleByUserIdAndTenantId(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
}
