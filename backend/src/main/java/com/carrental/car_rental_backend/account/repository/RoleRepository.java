package com.carrental.car_rental_backend.account.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.carrental.car_rental_backend.account.entity.Role;

public interface RoleRepository extends JpaRepository<Role, UUID> {}
