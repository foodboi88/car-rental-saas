package com.carrental.car_rental_backend.tenant.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carrental.car_rental_backend.account.entity.Role;
import com.carrental.car_rental_backend.account.entity.User;
import com.carrental.car_rental_backend.account.entity.UserTenant;
import com.carrental.car_rental_backend.account.repository.UserRepository;
import com.carrental.car_rental_backend.account.repository.UserTenantRepository;
import com.carrental.car_rental_backend.common.constant.RoleConstant;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.tenant.constant.AdminRejection;
import com.carrental.car_rental_backend.tenant.dto.request.AdminAssignmentRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.response.AdminAssignmentResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.AdminEmailCheckResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantAdminResponseDTO;
import com.carrental.car_rental_backend.tenant.entity.Tenant;
import com.carrental.car_rental_backend.tenant.repository.TenantRepository;
import com.carrental.car_rental_backend.tenant.repository.TenantRoleRepository;
import com.carrental.car_rental_backend.tenant.repository.TenantUserTenantRepository;
import com.carrental.car_rental_backend.tenant.service.TenantAdminService;
import com.carrental.car_rental_backend.tenant.service.TenantAdminService.ValidatedAdmin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TenantAdminServiceImpl implements TenantAdminService {
  private final TenantRepository tenantRepository;
  private final TenantRoleRepository tenantRoleRepository;
  private final TenantUserTenantRepository tenantUserTenantRepository;
  private final UserRepository userRepository;
  private final UserTenantRepository userTenantRepository;
  private final PasswordEncoder passwordEncoder;

  private AdminAssignmentResponseDTO addAdminToTenant(UUID tenantId, UUID adminRoleId, ValidatedAdmin admin) {
    User user = admin.isNewAccount() ? this.createAccount(admin) : admin.existingUser();
    this.userTenantRepository.save(
      UserTenant.builder()
        .userId(user.getId())
        .tenantId(tenantId)
        .roleId(adminRoleId)
        .joinedAt(Instant.now())
        .build()
    );
    return AdminAssignmentResponseDTO.builder()
      .email(admin.email())
      .accountCreated(admin.isNewAccount())
      .build();
  }

  private User createAccount(ValidatedAdmin admin) {
    return this.userRepository.save(
      User.builder()
        .email(admin.email())
        .passwordHash(this.passwordEncoder.encode(admin.password()))
        .fullname(admin.fullName())
        .isActive(true)
        .isSuperAdmin(false)
        .build()
    );
  }

  @Override
  public List<ValidatedAdmin> validateAdminRequests(List<AdminAssignmentRequestDTO> requests, Tenant tenant, UUID adminRoleId) {
    Set<String> emailsInRequest = new HashSet<>();
    List<ValidatedAdmin> validatedAdmins = new ArrayList<>();
    for (AdminAssignmentRequestDTO request : requests) {
      validatedAdmins.add(this.validateAdminRequest(request, tenant, adminRoleId, emailsInRequest));
    }
    return validatedAdmins;
  }

  private ValidatedAdmin validateAdminRequest(AdminAssignmentRequestDTO request, Tenant tenant, UUID adminRoleId, Set<String> emailsInRequest) {
    String email = this.normalizeEmail(request.getEmail());
    if (!emailsInRequest.add(email)) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Email chủ nhà xe bị trùng trong danh sách: " + email);
    }

    User existingUser = this.userRepository.findByEmail(email).orElse(null);
    Optional<AdminRejection> rejection = this.findRejection(tenant, existingUser, adminRoleId);
    if (rejection.isPresent()) {
      throw rejection.get().toException();
    }

    if (existingUser != null) {
      return new ValidatedAdmin(email, null, null, existingUser);
    }
    if (request.getFullName() == null || request.getFullName().isBlank()) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Vui lòng nhập họ tên cho tài khoản mới: " + email);
    }
    if (request.getPassword() == null || request.getPassword().isBlank()) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Vui lòng nhập mật khẩu cho tài khoản mới: " + email);
    }
    return new ValidatedAdmin(email, request.getFullName().trim(), request.getPassword(), null);
  }

  @Override 
  public List<AdminAssignmentResponseDTO> addAdminsToTenant(UUID tenantId, UUID adminRoleId, List<ValidatedAdmin> admins) {
    List<AdminAssignmentResponseDTO> results = new ArrayList<>();
    for (ValidatedAdmin admin : admins) {
      results.add(this.addAdminToTenant(tenantId, adminRoleId, admin));
    }
    return results;
  }

  @Override
  public AdminAssignmentResponseDTO addTenantAdmin(UUID tenantId, AdminAssignmentRequestDTO request, UUID actorId) {
    Tenant tenant = this.findTenantOrThrow(tenantId);
    UUID adminRoleId = this.findAdminRoleOrThrow(tenantId).getId();

    List<ValidatedAdmin> admins = this.validateAdminRequests(List.of(request), tenant, adminRoleId);
    AdminAssignmentResponseDTO result = this.addAdminsToTenant(tenantId, adminRoleId, admins).get(0);

    log.info("Người dùng {} thêm chủ cho nhà xe {} (id = {}), tài khoản mới: {}", actorId, tenant.getName(), tenantId, result.isAccountCreated());
    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public List<TenantAdminResponseDTO> listTenantAdmins(UUID tenantId) {
    this.findTenantOrThrow(tenantId);
    return this.tenantUserTenantRepository.findAdmins(tenantId);
  }

  @Override
  @Transactional(readOnly = true)
  public AdminEmailCheckResponseDTO checkAdminEmail(String email, UUID tenantId) {
    Tenant tenant = tenantId == null ? null : this.findTenantOrThrow(tenantId);
    UUID adminRoleId = tenant == null ? null : this.findAdminRoleOrThrow(tenantId).getId();

    String normalizedEmail = this.normalizeEmail(email);
    Optional<User> existingUser = this.userRepository.findByEmail(normalizedEmail);
    Optional<AdminRejection> rejection = this.findRejection(tenant, existingUser.orElse(null), adminRoleId);

    return AdminEmailCheckResponseDTO.builder()
      .email(normalizedEmail)
      .accountExists(existingUser.isPresent())
      .fullName(existingUser.map(User::getFullname).orElse(null))
      .canBeAdmin(rejection.isEmpty())
      .reason(rejection.map(AdminRejection::getMessage).orElse(null))
      .build();
  }

  private Optional<AdminRejection> findRejection(Tenant tenant, User user, UUID adminRoleId) {
    if (tenant != null && !tenant.isActive()) {
      return Optional.of(AdminRejection.TENANT_LOCKED);
    }
    if (user == null) {
      return Optional.empty();
    }
    if (!Boolean.TRUE.equals(user.getIsActive())) {
      return Optional.of(AdminRejection.ACCOUNT_LOCKED);
    }
    if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
      return Optional.of(AdminRejection.SUPER_ADMIN);
    }
    if (tenant == null) {
      return Optional.empty();
    }
    Optional<UserTenant> membership = this.userTenantRepository.findByUserIdAndTenantId(user.getId(), tenant.getId());
    if (membership.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(adminRoleId.equals(membership.get().getRoleId())
      ? AdminRejection.ALREADY_ADMIN
      : AdminRejection.HAS_OTHER_ROLE);
  }

  private Tenant findTenantOrThrow(UUID tenantId) {
    Optional<Tenant> tenant = this.tenantRepository.findById(tenantId);
    if (tenant.isEmpty()) {
      throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhà xe !");
    }
    return tenant.get();
  }

  private Role findAdminRoleOrThrow(UUID tenantId) {
    Optional<Role> adminRole = this.tenantRoleRepository.findByTenantIdAndCode(tenantId, RoleConstant.TENANT_ADMIN);
    if (adminRole.isEmpty()) {
      throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Nhà xe chưa có nhóm quyền chủ nhà xe !");
    }
    return adminRole.get();
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }
}
