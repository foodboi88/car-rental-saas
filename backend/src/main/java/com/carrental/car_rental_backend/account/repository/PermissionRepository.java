package com.carrental.car_rental_backend.account.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carrental.car_rental_backend.account.entity.Permission;

public interface PermissionRepository extends JpaRepository<Permission, UUID>{
  @Query(value = """
    SELECT code FROM permissions
    INNER JOIN role_permissions ON permissions.id = role_permissions.permission_id
    WHERE role_permissions.role_id = :roleId
  """, nativeQuery = true)
  List<String> findAllPermissionCodeByRoleId(@Param("roleId") UUID roleId);
}
