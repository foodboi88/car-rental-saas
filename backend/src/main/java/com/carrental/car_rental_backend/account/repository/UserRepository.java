package com.carrental.car_rental_backend.account.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.carrental.car_rental_backend.account.entity.User;

public interface UserRepository extends JpaRepository<User, UUID>{
  Optional<User> findByEmail(String email);
  Optional<User> findByIdAndIsActiveTrue(UUID id);
  boolean existsByEmail(String email);
}
