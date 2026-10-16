package com.carrental.car_rental_backend.account.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.carrental.car_rental_backend.account.entity.UserTenant;

@Mapper(componentModel = "spring")
public interface UserTenantMapper {

    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "roleId", source = "roleId")
    @Mapping(target = "joinedAt", expression = "java(java.time.Instant.now())")
    UserTenant toUserTenant(UUID tenantId, UUID userId, UUID roleId);
}
