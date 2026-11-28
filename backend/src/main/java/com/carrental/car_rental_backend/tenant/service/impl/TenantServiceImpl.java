package com.carrental.car_rental_backend.tenant.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carrental.car_rental_backend.account.entity.Permission;
import com.carrental.car_rental_backend.account.entity.Role;
import com.carrental.car_rental_backend.account.entity.RolePermission;
import com.carrental.car_rental_backend.account.entity.User;
import com.carrental.car_rental_backend.account.entity.UserTenant;
import com.carrental.car_rental_backend.account.repository.PermissionRepository;
import com.carrental.car_rental_backend.account.repository.RolePermissionRepository;
import com.carrental.car_rental_backend.account.repository.RoleRepository;
import com.carrental.car_rental_backend.account.repository.UserRepository;
import com.carrental.car_rental_backend.account.repository.UserTenantRepository;
import com.carrental.car_rental_backend.common.enums.plan_tier.PlanTierTenant;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.tenant.constant.DefaultRole;
import com.carrental.car_rental_backend.tenant.dto.request.CreateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantResponseDTO;
import com.carrental.car_rental_backend.tenant.entity.Tenant;
import com.carrental.car_rental_backend.tenant.entity.TenantConfig;
import com.carrental.car_rental_backend.tenant.repository.TenantConfigRepository;
import com.carrental.car_rental_backend.tenant.repository.TenantRepository;
import com.carrental.car_rental_backend.tenant.service.TenantService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {
  private static final int DEFAULT_HOLD_TIMEOUT_MINUTES = 30;
  private static final BigDecimal DEFAULT_LATE_HOURLY_PRICE = BigDecimal.valueOf(100_000);
  private static final BigDecimal DEFAULT_COMMISSION_RATE = BigDecimal.valueOf(5);
  private static final int MAX_PAGE_SIZE = 100;

  private final TenantRepository tenantRepository;
  private final UserRepository userRepository;
  private final UserTenantRepository userTenantRepository;
  private final PermissionRepository permissionRepository;
  private final TenantConfigRepository tenantConfigRepository;
  private final RoleRepository roleRepository;
  private final RolePermissionRepository rolePermissionRepository;

  @Override
  @Transactional
  public TenantResponseDTO createTenant(CreateTenantRequestDTO request) {
    String domain = request.getDomain();
    if (this.tenantRepository.existsByDomain(domain)) {
      throw new AppException(ErrorCode.TENANT_DOMAIN_ALREADY_EXISTS);
    }

    List<User> adminUsers = validateAdminUsers(request.getAdminUserIds());

    Map<String, Permission> permissionsByCode = loadRequiredPermissions();

    Tenant tenant = Tenant.builder()
        .name(request.getName())
        .logoUrl(request.getLogoUrl())
        .contactEmail(request.getContactEmail())
        .contactPhone(request.getContactPhone())
        .domain(domain)
        .planTier(PlanTierTenant.FREE)
        .isActive(true)
        .settings("{}")
        .build();
    Tenant savedTenant = this.tenantRepository.save(tenant);

    TenantConfig tenantConfig = TenantConfig.builder()
        .tenantId(savedTenant.getId())
        .holdTimeoutMinutes(DEFAULT_HOLD_TIMEOUT_MINUTES)
        .lateHourlyPrice(DEFAULT_LATE_HOURLY_PRICE)
        .defaultCommissionRate(DEFAULT_COMMISSION_RATE)
        .build();
    this.tenantConfigRepository.save(tenantConfig);

    List<RolePermission> rolePermissions = new ArrayList<>();
    List<UserTenant> ownerMemberships = new ArrayList<>();

    for (DefaultRole defaultRole : DefaultRole.values()) {
      Role role = Role.builder()
          .tenantId(savedTenant.getId())
          .code(defaultRole.getCode())
          .name(defaultRole.getName())
          .description(defaultRole.getDescription())
          .isSystemDefault(true)
          .build();
      Role savedRole = this.roleRepository.save(role);

      for (String permissionCode : defaultRole.getPermissionCodes()) {
        Permission permission = permissionsByCode.get(permissionCode);
        RolePermission rolePermission = RolePermission.builder()
            .roleId(savedRole.getId())
            .permissionId(permission.getId())
            .build();
        rolePermissions.add(rolePermission);
      }

      if (defaultRole == DefaultRole.TENANT_ADMIN) {
        for (User adminUser : adminUsers) {
          UserTenant ownerMembership = UserTenant.builder()
              .tenantId(savedTenant.getId())
              .userId(adminUser.getId())
              .roleId(savedRole.getId())
              .joinedAt(Instant.now())
              .build();
          ownerMemberships.add(ownerMembership);
        }
      }
    }

    this.rolePermissionRepository.saveAll(rolePermissions);
    this.userTenantRepository.saveAll(ownerMemberships);

    this.tenantRepository.flush();

    return toResponse(savedTenant);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TenantResponseDTO> listTenants(int page, int size) {
    if (page < 0) {
      throw new AppException(ErrorCode.BAD_REQUEST, "page phải lớn hơn hoặc bằng 0");
    }

    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new AppException(ErrorCode.BAD_REQUEST, String.format("size phải từ %d đến %d", 1, MAX_PAGE_SIZE));
    }

    Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    Pageable pageable = PageRequest.of(page, size, sort);
    Page<Tenant> tenantPage = this.tenantRepository.findAll(pageable);
    return tenantPage.map(TenantServiceImpl::toResponse);
  }

  private List<User> validateAdminUsers(List<UUID> adminUserIds) {
    Set<UUID> uniqueAdminUserIds = new HashSet<>(adminUserIds);
    boolean hasDuplicateAdminIds = uniqueAdminUserIds.size() < adminUserIds.size();
    if (hasDuplicateAdminIds) {
      throw new AppException(ErrorCode.TENANT_ADMIN_IDS_DUPLICATED);
    }

    List<User> adminUsers = this.userRepository.findAllById(adminUserIds);
    boolean hasMissingAdmins = adminUsers.size() < adminUserIds.size();
    if (hasMissingAdmins) {
      throw new AppException(ErrorCode.TENANT_ADMIN_NOT_FOUND);
    }

    for (User adminUser : adminUsers) {
      if (!adminUser.getIsActive()) {
        throw new AppException(ErrorCode.TENANT_ADMIN_INACTIVE);
      }
    }

    return adminUsers;
  }

  private Map<String, Permission> loadRequiredPermissions() {
    Set<String> requiredCodes = new HashSet<>();
    for (DefaultRole defaultRole : DefaultRole.values()) {
      requiredCodes.addAll(defaultRole.getPermissionCodes());
    }

    List<Permission> permissions = this.permissionRepository.findAllByCodeIn(List.copyOf(requiredCodes));
    Map<String, Permission> permissionsByCode = new HashMap<>();
    for (Permission permission : permissions) {
      permissionsByCode.put(permission.getCode(), permission);
    }

    for (String requiredCode : requiredCodes) {
      if (!permissionsByCode.containsKey(requiredCode)) {
        throw new AppException(ErrorCode.TENANT_DEFAULT_PERMISSION_MISSING);
      }
    }

    return permissionsByCode;
  }

  private static TenantResponseDTO toResponse(Tenant tenant) {
    return TenantResponseDTO.builder()
        .id(tenant.getId())
        .name(tenant.getName())
        .domain(tenant.getDomain())
        .logoUrl(tenant.getLogoUrl())
        .contactEmail(tenant.getContactEmail())
        .contactPhone(tenant.getContactPhone())
        .planTier(tenant.getPlanTier())
        .isActive(tenant.isActive())
        .createdAt(tenant.getCreatedAt())
        .updatedAt(tenant.getUpdatedAt())
        .build();
  }
}
