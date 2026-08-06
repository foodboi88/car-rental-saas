package com.carrental.car_rental_backend.vehicle.repository;

import com.carrental.car_rental_backend.vehicle.entity.VehicleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VehicleTypeRepository extends JpaRepository<VehicleType, UUID> {
  // Optional: Tránh việc trả về null
  Optional<VehicleType> findByTenantIdAndId(UUID tenantId, UUID id);

  // Ignore case để tránh việc phân biệt chữ hoa chữ thường
  boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);

  // IdNot để tránh việc kiểm tra chính bản thân mình khi cập nhật
  boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(UUID tenantId, String name, UUID id);

  // Tìm kiếm với filter và phân trang
  @Query("SELECT vt FROM VehicleType vt WHERE vt.tenantId = :tenantId " +
      "AND (:search IS NULL OR LOWER(vt.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
      "AND (:isActive IS NULL OR vt.isActive = :isActive)")
  Page<VehicleType> findByTenantIdWithFilter(
      @Param("tenantId") UUID tenantId,
      @Param("search") String search,
      @Param("isActive") Boolean isActive,
      Pageable pageable
  );

  // Đếm số lượng xe theo tenantId và vehicleTypeId
  @Query(value = "SELECT COUNT(*) FROM vehicles WHERE tenant_id = :tenantId AND vehicle_type_id = :vehicleTypeId",
      nativeQuery = true)
  long countVehiclesByTenantIdAndVehicleTypeId(
      @Param("tenantId") UUID tenantId,
      @Param("vehicleTypeId") UUID vehicleTypeId
  );
}
