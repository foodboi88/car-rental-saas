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

import com.carrental.car_rental_backend.branch.dto.BranchDTOResponse;
import com.carrental.car_rental_backend.branch.entity.Branch;

@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {

    //Lấy ra branch từ trong danh sách
    //các điều kiện tìm kiếm theo tên, mã và số điện thoại chi nhánh
    //sử dụng toán tử AND để có thể tìm kiếm kết hợp các điều kiện
    //(CAST(:[<tên biến> AS <kiểu dữ liệu>) ép kiểu
    @Query("SELECT branch FROM Branch branch WHERE " + 
        "(CAST(:name AS String) IS NULL OR LOWER(branch.name) LIKE LOWER (CONCAT('%', CAST(:name AS String), '%'))) AND " +
        "(CAST(:code AS String) IS NULL OR LOWER(branch.code) LIKE LOWER (CONCAT('%', CAST(:code AS String), '%'))) AND " +
        "(CAST(:phone AS String) IS NULL OR LOWER(branch.phone) LIKE LOWER (CONCAT('%', CAST(:phone AS String), '%')))"
    )
    Page<Branch> searchBranch(@Param("name") String name,
        @Param("code") String code,
        @Param("phone") String phone,
        Pageable pageable
    );

    Optional<Branch> findById(UUID id);
}
