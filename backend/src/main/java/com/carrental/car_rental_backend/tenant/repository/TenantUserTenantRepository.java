package com.carrental.car_rental_backend.tenant.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carrental.car_rental_backend.account.entity.UserTenant;
import com.carrental.car_rental_backend.account.entity.UserTenantId;
import com.carrental.car_rental_backend.tenant.dto.response.TenantAdminResponseDTO;

public interface TenantUserTenantRepository extends JpaRepository<UserTenant, UserTenantId> {
  // Constructor expression: Danh sách chủ (TENANT_ADMIN) của một nhà xe, trả thẳng DTO.
  @Query("""
    SELECT new com.carrental.car_rental_backend.tenant.dto.response.TenantAdminResponseDTO(
      u.id, u.email, u.fullname, u.isActive, ut.joinedAt)
    FROM UserTenant ut
    JOIN User u ON u.id = ut.userId
    JOIN Role r ON r.id = ut.roleId
    WHERE ut.tenantId = :tenantId AND r.code = 'TENANT_ADMIN'
    ORDER BY ut.joinedAt
  """)
  List<TenantAdminResponseDTO> findAdmins(@Param("tenantId") UUID tenantId);
}
