package com.carrental.car_rental_backend.tenant.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carrental.car_rental_backend.account.entity.UserTenant;
import com.carrental.car_rental_backend.account.entity.UserTenantId;

public interface TenantAdminQueryRepository extends JpaRepository<UserTenant, UserTenantId> {

  // Mỗi phần tử là một mảng [User, UserTenant]: tài khoản và liên kết của người đó với nhà xe
  @Query("""
    SELECT u, ut FROM UserTenant ut
    JOIN User u ON u.id = ut.userId
    JOIN Role r ON r.id = ut.roleId
    WHERE ut.tenantId = :tenantId AND r.code = :roleCode
    ORDER BY ut.joinedAt
  """)
  List<Object[]> findMembersByTenantIdAndRoleCode(
    @Param("tenantId") UUID tenantId,
    @Param("roleCode") String roleCode
  );
}
