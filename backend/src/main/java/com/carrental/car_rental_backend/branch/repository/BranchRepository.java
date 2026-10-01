package com.carrental.car_rental_backend.branch.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carrental.car_rental_backend.branch.entity.Branch;
public interface BranchRepository extends JpaRepository<Branch, UUID> {
  @Query(value = """
    SELECT * FROM branches
    INNER JOIN user_branches ON branches.id = user_branches.branch_id
    WHERE user_branches.user_id = :userId and user_branches.tenant_id = :tenantId
  """, nativeQuery = true)
  List<Branch> findAllAssignedBranchesByUserIdTenantId(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
}
