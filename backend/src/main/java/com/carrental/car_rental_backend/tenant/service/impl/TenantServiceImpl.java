package com.carrental.car_rental_backend.tenant.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carrental.car_rental_backend.account.entity.Role;
import com.carrental.car_rental_backend.common.constant.RoleConstant;
import com.carrental.car_rental_backend.common.enums.plan_tier.PlanTierTenant;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.tenant.constant.DefaultRole;
import com.carrental.car_rental_backend.tenant.dto.request.CreateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.request.UpdateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.response.AdminAssignmentResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.CreateTenantResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantResponseDTO;
import com.carrental.car_rental_backend.tenant.entity.Tenant;
import com.carrental.car_rental_backend.tenant.entity.TenantConfig;
import com.carrental.car_rental_backend.tenant.repository.TenantConfigRepository;
import com.carrental.car_rental_backend.tenant.repository.TenantRepository;
import com.carrental.car_rental_backend.tenant.repository.TenantRoleRepository;
import com.carrental.car_rental_backend.tenant.service.TenantAdminService;
import com.carrental.car_rental_backend.tenant.service.TenantAdminService.ValidatedAdmin;
import com.carrental.car_rental_backend.tenant.service.TenantService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TenantServiceImpl implements TenantService {
  private static final String EMPTY_JSON = "{}";

  private final TenantRepository tenantRepository;
  private final TenantConfigRepository tenantConfigRepository;
  private final TenantRoleRepository tenantRoleRepository;
  private final TenantAdminService tenantAdminService;
  private final ObjectMapper objectMapper;

  @Override
  public CreateTenantResponseDTO createTenant(CreateTenantRequestDTO request, UUID actorId) {
    // Bước kiểm tra: domain, settings và toàn bộ chủ nhà xe
    this.checkDomainNotTaken(request.getDomain());
    String settings = this.normalizeSettings(request.getSettings());
    List<ValidatedAdmin> admins = this.tenantAdminService.validateAdminRequests(request.getAdmins(), null, null);

    // Bước ghi: nhà xe -> cấu hình mặc định -> 3 vai trò -> chủ nhà xe
    Tenant tenant = this.saveTenant(request, settings);
    this.tenantConfigRepository.save(TenantConfig.withDefaults(tenant.getId()));
    UUID adminRoleId = this.createDefaultRoles(tenant.getId());
    List<AdminAssignmentResponseDTO> adminResults = this.tenantAdminService.addAdminsToTenant(tenant.getId(), adminRoleId, admins);

    log.info("Người dùng {} tạo nhà xe {} (id = {}) với {} chủ nhà xe", actorId, tenant.getName(), tenant.getId(), adminResults.size());
    return CreateTenantResponseDTO.builder()
      .tenant(this.toTenantResponse(tenant))
      .admins(adminResults)
      .build();
  }

  @Override
  @Transactional(readOnly = true)
  public TenantResponseDTO getTenantById(UUID tenantId) {
    return this.toTenantResponse(this.findTenantOrThrow(tenantId));
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TenantResponseDTO> listTenants(String keyword, Boolean isActive, Pageable pageable) {
    // Luôn truyền một chuỗi, không bao giờ null: PostgreSQL báo lỗi function lower(bytea) nếu keyword là null
    String keywordPattern = (keyword == null || keyword.isBlank())
      ? "%"
      : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    // map: đổi từng Tenant trong trang kết quả thành TenantResponseDTO
    return this.tenantRepository.search(keywordPattern, isActive, pageable).map(this::toTenantResponse);
  }

  @Override
  public TenantResponseDTO updateTenant(UUID tenantId, UpdateTenantRequestDTO request, UUID actorId) {
    Tenant tenant = this.findTenantOrThrow(tenantId);

    // Domain đã đúng dạng chữ thường nhờ @Pattern ở DTO, nên không cần chuẩn hóa
    String domain = request.getDomain();
    if (!domain.equals(tenant.getDomain())) {
      if (this.tenantRepository.existsByDomainAndIdNot(domain, tenantId)) {
        throw new AppException(ErrorCode.TENANT_DOMAIN_ALREADY_EXISTS);
      }
      tenant.setDomain(domain);
    }
    // name, domain, planTier là bắt buộc nên luôn ghi đè.
    // Các trường tùy chọn: không gửi (null) thì GIỮ giá trị cũ, gửi chuỗi rỗng thì xóa (R8).
    // Không đụng tới tenant_configs (do chủ nhà xe tự sửa)
    tenant.setName(request.getName().trim());
    tenant.setPlanTier(request.getPlanTier());
    if (request.getLogoUrl() != null) {
      tenant.setLogoUrl(this.blankToNull(request.getLogoUrl()));
    }
    if (request.getContactEmail() != null) {
      tenant.setContactEmail(this.blankToNull(request.getContactEmail()));
    }
    if (request.getContactPhone() != null) {
      tenant.setContactPhone(this.blankToNull(request.getContactPhone()));
    }
    if (request.getSettings() != null) {
      tenant.setSettings(this.normalizeSettings(request.getSettings()));
    }

    // saveAndFlush để updatedAt (@UpdateTimestamp) đã được cập nhật trước khi trả về
    Tenant saved = this.tenantRepository.saveAndFlush(tenant);
    log.info("Người dùng {} cập nhật nhà xe {} (id = {})", actorId, saved.getName(), saved.getId());
    return this.toTenantResponse(saved);
  }

  @Override
  public TenantResponseDTO updateTenantStatus(UUID tenantId, boolean isActive, UUID actorId) {
    Tenant tenant = this.findTenantOrThrow(tenantId);
    if (tenant.isActive() == isActive) {
      return this.toTenantResponse(tenant);
    }
    tenant.setActive(isActive);
    Tenant saved = this.tenantRepository.saveAndFlush(tenant);
    log.info("Người dùng {} {} nhà xe {} (id = {})", actorId, isActive ? "mở khóa" : "khóa", saved.getName(), saved.getId());
    return this.toTenantResponse(saved);
  }

  @Override
  public void deleteTenant(UUID tenantId, UUID actorId) {
    this.updateTenantStatus(tenantId, false, actorId);
  }

  private void checkDomainNotTaken(String domain) {
    if (this.tenantRepository.existsByDomain(domain)) {
      throw new AppException(ErrorCode.TENANT_DOMAIN_ALREADY_EXISTS);
    }
  }

  private Tenant saveTenant(CreateTenantRequestDTO request, String settings) {
    return this.tenantRepository.save(
      Tenant.builder()
        .name(request.getName().trim())
        .domain(request.getDomain())
        .planTier(request.getPlanTier() != null ? request.getPlanTier() : PlanTierTenant.FREE)
        .logoUrl(request.getLogoUrl())
        .contactEmail(request.getContactEmail())
        .contactPhone(request.getContactPhone())
        .settings(settings)
        .isActive(true)
        .build()
    );
  }

  private UUID createDefaultRoles(UUID tenantId) {
    for (DefaultRole role : DefaultRole.values()) {
      this.tenantRoleRepository.insertSystemRole(tenantId, role.getName(), role.getCode(), role.getDescription());
      if (role.grantsAllPermissions()) {
        this.tenantRoleRepository.grantAllPermissions(tenantId, role.getCode()); // TENANT_ADMIN
      } else {
        this.tenantRoleRepository.grantPermissions(tenantId, role.getCode(), role.getPermissionCodes()); // STAFF + SALE
      }
    }
    return this.findAdminRoleOrThrow(tenantId).getId();
  }

  private String blankToNull(String value) {
    return value.isBlank() ? null : value.trim();
  }

  private String normalizeSettings(String settings) {
    if (settings == null || settings.isBlank()) {
      return EMPTY_JSON;
    }
    try {
      this.objectMapper.readTree(settings);
    } catch (JsonProcessingException exception) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Trường settings không phải JSON hợp lệ !");
    }
    return settings;
  }

  private Role findAdminRoleOrThrow(UUID tenantId) {
    Optional<Role> adminRole = this.tenantRoleRepository.findByTenantIdAndCode(tenantId, RoleConstant.TENANT_ADMIN);
    if (adminRole.isEmpty()) {
      throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Nhà xe chưa có nhóm quyền chủ nhà xe !");
    }
    return adminRole.get();
  }

  private Tenant findTenantOrThrow(UUID tenantId) {
    Optional<Tenant> tenant = this.tenantRepository.findById(tenantId);
    if (tenant.isEmpty()) {
      throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhà xe !");
    }
    return tenant.get();
  }

  private TenantResponseDTO toTenantResponse(Tenant tenant) {
    return TenantResponseDTO.builder()
      .id(tenant.getId())
      .name(tenant.getName())
      .domain(tenant.getDomain())
      .planTier(tenant.getPlanTier())
      .logoUrl(tenant.getLogoUrl())
      .contactEmail(tenant.getContactEmail())
      .contactPhone(tenant.getContactPhone())
      .settings(tenant.getSettings())
      .isActive(tenant.isActive())
      .createdAt(tenant.getCreatedAt())
      .updatedAt(tenant.getUpdatedAt())
      .build();
  }
}
