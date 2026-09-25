package com.carrental.car_rental_backend.auth.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.carrental.car_rental_backend.account.entity.Permission;
import com.carrental.car_rental_backend.account.entity.Role;
import com.carrental.car_rental_backend.account.entity.User;
import com.carrental.car_rental_backend.account.entity.UserTenant;
import com.carrental.car_rental_backend.account.repository.PermissionRepository;
import com.carrental.car_rental_backend.account.repository.RoleRepository;
import com.carrental.car_rental_backend.account.repository.UserRepository;
import com.carrental.car_rental_backend.account.repository.UserTenantRepository;
import com.carrental.car_rental_backend.auth.service.CustomUserDetailsService;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.security.principal.UserPrincipal;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomUserDetailsServiceImpl implements CustomUserDetailsService {

  private UserRepository userRepository;
  private UserTenantRepository userTenantRepository;
  private RoleRepository roleRepository;
  private PermissionRepository permissionRepository;

  public CustomUserDetailsServiceImpl(
    UserRepository userRepository,
    UserTenantRepository userTenantRepository,
    RoleRepository roleRepository,
    PermissionRepository permissionRepository
  ){
    this.userRepository = userRepository;
    this.userTenantRepository = userTenantRepository;
    this.roleRepository = roleRepository;
    this.permissionRepository = permissionRepository;
  }

  @Override
  public UserPrincipal loadUserByIdAndTenantId(UUID userId, UUID tenantId) {

    Optional<User> user = this.userRepository.findById(userId);
    if(user.isEmpty() || !user.get().getIsActive()) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng hoặc tài khoản đã bị khóa");

    Optional<UserTenant> userInfoInTenant = this.userTenantRepository.findByUserIdAndTenantId(userId, tenantId);
    if(userInfoInTenant.isEmpty()) throw new AppException(ErrorCode.UNAUTHORIZED, "Người dùng không thuộc tenant này");

    UUID roleId = userInfoInTenant.get().getRoleId();
    if(roleId == null) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Người dùng chưa được phân vai trò trong nhà xe");
    
    Optional<Role> roleOfUser = this.roleRepository.findById(roleId);
    if(roleOfUser.isEmpty()) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vai trò của người dùng");

    List<String> listOfPermission = this.permissionRepository.findAllPermissionCodeByRoleId(roleId);

    return UserPrincipal.create(user.get(), tenantId, roleOfUser.get().getCode(), listOfPermission);
  }

  @Override
  public UserPrincipal loadSuperAdminById(UUID userId) {
    Optional<User> user = this.userRepository.findById(userId);
    if(user.isEmpty() || !user.get().getIsActive()) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng hoặc tài khoản đã bị khóa");

    if(!user.get().getIsSuperAdmin()) throw new AppException(ErrorCode.FORBIDDEN, "User không phải SUPER ADMIN");
    
    return UserPrincipal.create(user.get(), null, "SUPER_ADMIN", null);
  }
}
