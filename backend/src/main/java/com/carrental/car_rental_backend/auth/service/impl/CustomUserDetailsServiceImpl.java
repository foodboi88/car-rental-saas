package com.carrental.car_rental_backend.auth.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.carrental.car_rental_backend.account.entity.Role;
import com.carrental.car_rental_backend.account.entity.User;
import com.carrental.car_rental_backend.account.entity.UserTenant;
import com.carrental.car_rental_backend.account.repository.PermissionRepository;
import com.carrental.car_rental_backend.account.repository.RoleRepository;
import com.carrental.car_rental_backend.account.repository.UserRepository;
import com.carrental.car_rental_backend.account.repository.UserTenantRepository;
import com.carrental.car_rental_backend.auth.service.CustomUserDetailsService;
import com.carrental.car_rental_backend.common.constant.RoleConstant;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.security.principal.UserPrincipal;
import com.carrental.car_rental_backend.tenant.entity.Tenant;
import com.carrental.car_rental_backend.tenant.repository.TenantRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomUserDetailsServiceImpl implements CustomUserDetailsService {

  private final UserRepository userRepository;
  private final UserTenantRepository userTenantRepository;
  private final RoleRepository roleRepository;
  private final PermissionRepository permissionRepository;
  private final TenantRepository tenantRepository;

  @Override
  public UserPrincipal loadUserByIdAndTenantId(UUID userId, UUID tenantId) {

    Optional<User> user = this.userRepository.findById(userId);
    if (user.isEmpty() || !Boolean.TRUE.equals(user.get().getIsActive())) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng hoặc tài khoản đã bị khóa");

    Optional<UserTenant> userInfoInTenant = this.userTenantRepository.findByUserIdAndTenantId(userId, tenantId);
    if (userInfoInTenant.isEmpty()) throw new AppException(ErrorCode.UNAUTHORIZED, "Người dùng không thuộc tenant này");

    UUID roleId = userInfoInTenant.get().getRoleId();
    if (roleId == null) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Người dùng chưa được phân vai trò trong nhà xe");
    
    Optional<Role> roleOfUser = this.roleRepository.findById(roleId);
    if (roleOfUser.isEmpty()) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vai trò của người dùng");

    Optional<Tenant> tenant = this.tenantRepository.findById(tenantId);
    if (tenant.isEmpty()) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhà xe");
    if (!tenant.get().isActive()) throw new AppException(ErrorCode.FORBIDDEN, "Nhà xe của bạn đang chọn đã bị khóa");

    List<String> listOfPermission = this.permissionRepository.findAllPermissionCodeByRoleId(roleId);

    return UserPrincipal.create(user.get(), tenantId, roleOfUser.get().getCode(), listOfPermission);
  }

  @Override
  public UserPrincipal loadSuperAdminById(UUID userId) {
    Optional<User> user = this.userRepository.findById(userId);
    if(user.isEmpty() || !Boolean.TRUE.equals(user.get().getIsActive())) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng hoặc tài khoản đã bị khóa");

    if(!Boolean.TRUE.equals(user.get().getIsSuperAdmin())) throw new AppException(ErrorCode.FORBIDDEN, "User không phải SUPER ADMIN");
    
    return UserPrincipal.create(user.get(), null, RoleConstant.SUPER_ADMIN, null);
  }

  @Override
  public UserPrincipal loadUserById(UUID userId) {
    Optional<User> user = this.userRepository.findById(userId);
    if(user.isEmpty() || !Boolean.TRUE.equals(user.get().getIsActive())) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng hoặc tài khoản đã bị khóa");    
    return UserPrincipal.create(user.get(), null, null, null);
  }

  
}
