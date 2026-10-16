package com.carrental.car_rental_backend.account.mapper;

import java.util.List;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.carrental.car_rental_backend.account.entity.UserBranch;

@Mapper(componentModel = "spring")
public interface UserBranchMapper {

    @Mapping(target = "tenantId", source = "tenantId")
    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "branchId", source = "branchId")
    @Mapping(target = "startedAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "status", expression = "java(com.carrental.car_rental_backend.common.enums.user_branch_status.UserBranchStatus.ACTIVE)")
    @Mapping(target = "endedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    UserBranch toEntity(UUID tenantId, UUID userId, UUID branchId);

    //map danh sách user vào user branch
    default List<UserBranch> toEntities(UUID tenantId, UUID userId, List<UUID> branchIds){
        if(branchIds == null || branchIds.isEmpty()){
            return null;
        }
        return branchIds.stream().map(branchId -> toEntity(tenantId, userId, branchId)).toList();
    }
}
