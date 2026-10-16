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

  @Query("SELECT u FROM User u WHERE " + 
      "(CAST(:fullname AS String) IS NULL OR LOWER(u.fullname) LIKE LOWER(CONCAT('%', CAST(:fullname AS String), '%'))) AND " +
      "(CAST(:phone AS String) IS NULL OR LOWER(u.phone) LIKE LOWER(CONCAT('%', CAST(:phone AS String), '%')))"
  )
  Page<User> searchUser(
      @Param("fullname") String fullname,
      @Param("phone") String phone,
      Pageable pageable
  );
}
