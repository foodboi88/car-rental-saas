package com.carrental.car_rental_backend.account.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.carrental.car_rental_backend.account.entity.User;

public interface UserRepository extends JpaRepository<User, UUID>{
  Optional<User> findByEmail(String email);
  Optional<User> findByIdAndIsActiveTrue(UUID id);
  boolean existsByEmail(String email);

  @Modifying(clearAutomatically = true)
  @Query(value = """
    UPDATE User u 
    SET u.currentSessionId = :sessionId,
      u.lastLoginAt = :lastLoginAt
    WHERE u.id = :userId AND
      u.isActive = true
  """)
  int updateNewSessionId(@Param("sessionId") UUID sessionId, @Param("lastLoginAt") Instant lastLoginAt, @Param("userId") UUID userId);
}
