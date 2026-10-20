package com.carrental.car_rental_backend.tenant.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carrental.car_rental_backend.tenant.entity.Tenant;

public interface TenantRepository extends JpaRepository<Tenant, UUID>{
  // Tìm tất cả các tenant được gán cho một userId cụ thể
  @Query(value = """
    SELECT tenants.* FROM tenants
    INNER JOIN user_tenants ON tenants.id = user_tenants.tenant_id
    WHERE user_tenants.user_id = :userId
  """, nativeQuery = true)
  List<Tenant> findAllAssignedTenantsByUserId(@Param("userId") UUID userId);

  // Kiểm tra trùng domain khi tạo mới
  boolean existsByDomain(String domain);

  // Kiểm tra trùng domain khi cập nhật, bỏ qua tenantId hiện tại
  boolean existsByDomainAndIdNot(String domain, UUID tenantId);

  // keywordPattern luôn là chuỗi viết thường dạng "%abc%" (hoặc "%"), KHÔNG được null.
  // Dấu % trong LIKE là ký tự đại diện, thực hiện tìm có chứa từ khoá thay vì không dùng là tìm khớp từ khoá.
  // isActive được phép null (null nghĩa là lấy cả hai trạng thái).
  @Query("""
    SELECT t FROM Tenant t
    WHERE (LOWER(t.name) LIKE :keywordPattern OR LOWER(t.domain) LIKE :keywordPattern)
    AND (:isActive IS NULL OR t.isActive = :isActive)
  """)
  Page<Tenant> search(
    @Param("keywordPattern") String keywordPattern,
    @Param("isActive") Boolean isActive,
    Pageable pageable
  );
}
