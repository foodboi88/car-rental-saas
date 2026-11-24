package com.carrental.car_rental_backend.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.carrental.car_rental_backend.account.entity.RolePermission;
import com.carrental.car_rental_backend.account.entity.RolePermissionId;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

}
