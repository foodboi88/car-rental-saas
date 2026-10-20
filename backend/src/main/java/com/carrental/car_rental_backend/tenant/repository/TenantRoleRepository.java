package com.carrental.car_rental_backend.tenant.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carrental.car_rental_backend.account.entity.Role;

// @Modifying: Báo cho Spring câu truy vấn này ghi dữ liệu (INSERT, UPDATE, DELETE) không phải chỉ đọc
public interface TenantRoleRepository extends JpaRepository<Role, UUID> {

  @Modifying
  @Query(value = """
    INSERT INTO roles (tenant_id, name, code, description, is_system_default)
    VALUES (:tenantId, :name, :code, :description, TRUE)
  """, nativeQuery = true)
  void insertSystemRole(
    @Param("tenantId") UUID tenantId,
    @Param("name") String name,
    @Param("code") String code,
    @Param("description") String description
  );


  // Cấp mọi quyền cho một role của nhà xe.
  // CROSS JOIN permissions: Ghép role với mọi dòng trong bảng permissions.
  @Modifying
  @Query(value = """
    INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id
    FROM roles r
    CROSS JOIN permissions p
    WHERE r.tenant_id = :tenantId AND r.code = :roleCode
  """, nativeQuery = true)
  void grantAllPermissions(
    @Param("tenantId") UUID tenantId,
    @Param("roleCode") String roleCode
  );

  // Cấp một số quyền cụ thể (chỉ những quyền nằm trong permissionCodes) cho một role của nhà xe.
  @Modifying
  @Query(value = """
    INSERT INTO role_permissions (role_id, permission_id)
    SELECT r.id, p.id
    FROM roles r
    JOIN permissions p ON p.code IN (:permissionCodes)
    WHERE r.tenant_id = :tenantId AND r.code = :roleCode
  """, nativeQuery = true)
  void grantPermissions(
    @Param("tenantId") UUID tenantId,
    @Param("roleCode") String roleCode,
    @Param("permissionCodes") Collection<String> permissionCodes
  );

  Optional<Role> findByTenantIdAndCode(UUID tenantId, String code);
}
