package com.carrental.car_rental_backend.account.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.carrental.car_rental_backend.account.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);
  Optional<User> findByIdAndIsActiveTrue(UUID id);
  boolean existsByEmail(String email);
  boolean existsByEmailAndIdNot(String email, UUID id);

// Lấy thông tin user từ trong bảng User
// join giữa User và UserTenant để xem id của user phải chính xác với userId trong bảng UserTenant
// ON: tìm kiếm những bản ghi trong bảng uset_tenant có userId chính xác với userId trong bảng user
//kết hợp điều kiện WHERE ut.tenantId = :tenantId lọc giữ liệu theo chính xác tenant hiện tại
// countQuery đếm số lượng bản ghi tìm được do khi sử dụng join việc đếm tổng sổ bản ghi có thể sai
// kết qả tìm kiếm sắp xếp theo ngày từ mới nhất tới cũ nhất: ORDER BY u.createdAt DESC
  @Query(value = "SELECT u FROM User u " +
          "JOIN UserTenant ut ON ut.userId = u.id " +
          "WHERE ut.tenantId = :tenantId " +
          "  AND (CAST(:fullname AS String) IS NULL OR LOWER(u.fullname) LIKE LOWER(CONCAT('%', CAST(:fullname AS String), '%'))) "
          +
          "  AND (CAST(:phone AS String) IS NULL OR LOWER(u.phone) LIKE LOWER(CONCAT('%', CAST(:phone AS String), '%'))) "
          +
          "ORDER BY u.createdAt DESC", countQuery = "SELECT count(u) FROM User u " +
                  "JOIN UserTenant ut ON ut.userId = u.id " +
                  "WHERE ut.tenantId = :tenantId " +
                  "  AND (CAST(:fullname AS String) IS NULL OR LOWER(u.fullname) LIKE LOWER(CONCAT('%', CAST(:fullname AS String), '%'))) "
                  +
                  "  AND (CAST(:phone AS String) IS NULL OR LOWER(u.phone) LIKE LOWER(CONCAT('%', CAST(:phone AS String), '%')))")
  Page<User> searchUser(
        @Param ("tenantId") UUID id,
          @Param("fullname") String fullname,
          @Param("phone") String phone,
          Pageable pageable);
}
