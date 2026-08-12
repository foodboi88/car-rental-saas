package com.carrental.car_rental_backend.customer.repository;

import com.carrental.car_rental_backend.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
  Optional<Customer> findByTenantIdAndId(UUID tenantId, UUID id);

  // Xem danh sách khách hàng (tìm kiếm theo tên/sđt/email + lọc riskLevel + phân trang)
  @Query("SELECT c FROM Customer c WHERE c.tenantId = :tenantId " +
      "AND (:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
      "     OR LOWER(c.phone) LIKE LOWER(CONCAT('%', :search, '%')) " +
      "     OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
      "AND (:riskLevel IS NULL OR c.riskLevel = :riskLevel)")
  Page<Customer> findByTenantIdWithFilter(
      @Param("tenantId") UUID tenantId,
      @Param("search") String search,
      @Param("riskLevel") Integer riskLevel,
      Pageable pageable
  );

  // Kiểm tra khách hàng đã có lịch sử đơn thuê chưa, dùng khi xóa khách hàng
  // (chưa build Booking entity, dùng native query tối thiểu như VehicleRepository đã làm)
  @Query(value = "SELECT EXISTS(SELECT 1 FROM bookings WHERE customer_id = :customerId)",
      nativeQuery = true)
  boolean existsBookingForCustomer(@Param("customerId") UUID customerId);
}
