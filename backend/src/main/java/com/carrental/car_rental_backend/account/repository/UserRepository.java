package com.carrental.car_rental_backend.account.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carrental.car_rental_backend.account.entity.User;

public interface UserRepository extends JpaRepository<User, UUID>{


  @Query(value = """
          SELECT u.* FROM users u JOIN user_tenants ut ON u.id = ut.user_id
          WHERE ut.tenant_id = :tenantId
          AND (:keyword IS NULL OR :keyword = ''
            OR u.full_name ILIKE CONCAT( '%', :keyword, '%' )
            OR u.email ILIKE CONCAT('%', :keyword, '%')
            OR u.phone ILIKE CONCAT('%', :keyword, '%'))
          AND (:roleId IS NULL OR ut.role_id = :roleId)
          AND (:isActive IS NULL OR ut.is_active = :isActive)
          """,
        countQuery = """
          SELECT COUNT (u.id) FROM users u JOIN user_tenants ut ON u.id = ut.user_id
          WHERE ut.tenant_id = :tenantId
          AND (:keyword IS NULL OR :keyword = ''
            OR u.full_name ILIKE CONCAT( '%', :keyword, '%' )
            OR u.email ILIKE CONCAT('%', :keyword, '%')
            OR u.phone ILIKE CONCAT('%', :keyword, '%'))
          AND (:roleId IS NULL OR ut.role_id = :roleId)
          AND (:isActive IS NULL OR ut.is_active = :isActive)
          """,
      nativeQuery = true)
  Page<User> getUserWithFilters(
    @Param("tenantId") UUID tenantId,
    @Param("keyword") String keyword,
    @Param("roleId") UUID roleId,
    @Param("isActive") Boolean isActive,
    Pageable pageable
  );

  Optional<User> findByEmail(String email);
  Optional<User> findById(UUID id);

  Optional<User> findByEmailIgnoreCase(String email);
  Optional<User> findByIdAndIsActiveTrue(UUID id);
  boolean existsByEmail(String email);
}
