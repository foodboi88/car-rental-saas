package com.carrental.car_rental_backend.branch.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.carrental.car_rental_backend.branch.entity.Branch;
@Repository 
public interface BranchRepository extends JpaRepository<Branch, UUID> {
  @Query(value = """
    SELECT branches.* FROM branches
    INNER JOIN user_branches ON branches.id = user_branches.branch_id
    WHERE user_branches.user_id = :userId and user_branches.tenant_id = :tenantId
  """, nativeQuery = true)
  List<Branch> findAllAssignedBranchesByUserIdTenantId(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
  List<Branch> findByTenantId(UUID tenantId);

  // kiểm tra trùng lặp tên chi nhánh
  boolean existsByTenantIdAndName(UUID tenantId, String name);

  //kiểm tra trùng tên khi update
  boolean existsByTenantIdAndNameAndIdNot(UUID tenantId, String name, UUID id);

  //tìm kiếm tránh lỗi bảo mật
  Optional<Branch> findByIdAndTenantId(UUID id, UUID tenantId);

  //lấy danh sách active
  List<Branch> findByTenantIdAndIsActiveTrueOrderByNameAsc(UUID tenantId);

  // tìm kiếm branch
  @Query("""
    SELECT b FROM Branch b 
    WHERE b.tenantId = :tenantId
      AND (:isActive IS NULL OR b.isActive = :isActive)
      AND (
          :keyword IS NULL OR :keyword = '' 
          OR LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(b.address) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR b.phone LIKE CONCAT('%', :keyword, '%')
      )
    ORDER BY b.createdAt DESC""")
  Page<Branch> searchBranch(
    @Param ("tenantId") UUID tenantId,
    @Param ("keyword") String keyword,
    @Param ("isActive") Boolean isActive,
    Pageable pageable
  );
}
