package com.carrental.car_rental_backend.tenant.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.carrental.car_rental_backend.tenant.entity.TenantConfig;

public interface TenantConfigRepository extends JpaRepository<TenantConfig, UUID> {

}
