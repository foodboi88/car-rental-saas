package com.carrental.car_rental_backend.tenant.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carrental.car_rental_backend.account.entity.Role;
import com.carrental.car_rental_backend.account.entity.User;
import com.carrental.car_rental_backend.account.entity.UserTenant;
import com.carrental.car_rental_backend.account.repository.UserRepository;
import com.carrental.car_rental_backend.account.repository.UserTenantRepository;
import com.carrental.car_rental_backend.common.constant.RoleConstant;
import com.carrental.car_rental_backend.common.enums.plan_tier.PlanTierTenant;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.tenant.constant.DefaultRole;
import com.carrental.car_rental_backend.tenant.dto.request.AdminAssignmentRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.request.CreateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.request.UpdateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.response.CreateTenantResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantAdminResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantResponseDTO;
import com.carrental.car_rental_backend.tenant.entity.Tenant;
import com.carrental.car_rental_backend.tenant.entity.TenantConfig;
import com.carrental.car_rental_backend.tenant.repository.TenantAdminQueryRepository;
import com.carrental.car_rental_backend.tenant.repository.TenantConfigRepository;
import com.carrental.car_rental_backend.tenant.repository.TenantRepository;
import com.carrental.car_rental_backend.tenant.repository.TenantRoleRepository;
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
  // Giá trị cấu hình mặc định của nhà xe mới, khớp giá trị mặc định của bảng tenant_configs
  private static final int DEFAULT_HOLD_TIMEOUT_MINUTES = 30;
  private static final BigDecimal DEFAULT_LATE_HOURLY_PRICE = new BigDecimal("100000");
  private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("5.00");
  private static final String EMPTY_JSON = "{}";

  private final TenantRepository tenantRepository;
  private final TenantConfigRepository tenantConfigRepository;
  private final TenantRoleRepository tenantRoleRepository;
  private final TenantAdminQueryRepository tenantAdminQueryRepository;
  private final UserRepository userRepository;
  private final UserTenantRepository userTenantRepository;
  private final PasswordEncoder passwordEncoder;
  private final ObjectMapper objectMapper;

  // Một chủ nhà xe đã được kiểm tra xong: email đã chuẩn hóa, kèm tài khoản có sẵn (null nếu là email mới)
  private record PreparedAdmin(String email, String fullName, String password, User existingUser) {}

  @Override
  public CreateTenantResponseDTO createTenant(CreateTenantRequestDTO request, UUID actorId) {
    String domain = request.getDomain().trim().toLowerCase(Locale.ROOT);
    if (this.tenantRepository.existsByDomain(domain)) {
      throw new AppException(ErrorCode.TENANT_DOMAIN_ALREADY_EXISTS);
    }
    String settings = this.normalizeSettings(request.getSettings());
    // Kiểm tra hết các chủ nhà xe TRƯỚC khi ghi bất cứ thứ gì
    List<PreparedAdmin> admins = this.prepareAdminList(request.getAdmins());
    Tenant tenant = this.tenantRepository.save(
      Tenant.builder()
        .name(request.getName().trim())
        .domain(domain)
        .planTier(request.getPlanTier() != null ? request.getPlanTier() : PlanTierTenant.FREE)
        .logoUrl(request.getLogoUrl())
        .contactEmail(request.getContactEmail())
        .contactPhone(request.getContactPhone())
        .settings(settings)
        .isActive(true) // Builder không biết mặc định của DB, quên dòng này thì nhà xe mới bị khóa
        .build()
    );

    this.tenantConfigRepository.save(
      TenantConfig.builder()
        .tenantId(tenant.getId())
        .holdTimeoutMinutes(DEFAULT_HOLD_TIMEOUT_MINUTES)
        .lateHourlyPrice(DEFAULT_LATE_HOURLY_PRICE)
        .defaultCommissionRate(DEFAULT_COMMISSION_RATE)
        .build()
    );

    this.createDefaultRoles(tenant.getId());
    Role adminRole = this.findAdminRoleOrThrow(tenant.getId());

    List<CreateTenantResponseDTO.AdminResult> adminResults = new ArrayList<>();
    for (PreparedAdmin admin : admins) {
      adminResults.add(this.assignTenantAdmin(tenant.getId(), adminRole.getId(), admin));
    }

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

    String domain = request.getDomain().trim().toLowerCase(Locale.ROOT);
    if (!domain.equals(tenant.getDomain())) {
      if (this.tenantRepository.existsByDomainAndIdNot(domain, tenantId)) {
        throw new AppException(ErrorCode.TENANT_DOMAIN_ALREADY_EXISTS);
      }
      tenant.setDomain(domain);
    }
    // name, domain, planTier là bắt buộc nên luôn ghi đè.
    // Các trường tùy chọn: không gửi (null) thì GIỮ giá trị cũ, gửi chuỗi rỗng thì xóa.
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
  public TenantResponseDTO changeStatus(UUID tenantId, boolean isActive, UUID actorId) {
    Tenant tenant = this.findTenantOrThrow(tenantId);
    // Đã đúng trạng thái yêu cầu thì không làm gì, gọi lại nhiều lần vẫn thành công
    if (tenant.isActive() == isActive) {
      return this.toTenantResponse(tenant);
    }
    tenant.setActive(isActive);
    // saveAndFlush: lưu rồi ghi xuống DB ngay, để updatedAt trả về là giờ mới
    Tenant saved = this.tenantRepository.saveAndFlush(tenant);
    log.info("Người dùng {} {} nhà xe {} (id = {})", actorId, isActive ? "mở khóa" : "khóa", saved.getName(), saved.getId());
    return this.toTenantResponse(saved);
  }

  @Override
  public void deleteTenant(UUID tenantId, UUID actorId) {
    // Xóa mềm: chỉ đặt is_active = false. Không gọi repository.delete vì khóa ngoại ON DELETE CASCADE sẽ xóa sạch dữ liệu nhà xe
    this.changeStatus(tenantId, false, actorId);
  }

  @Override
  public CreateTenantResponseDTO.AdminResult addTenantAdmin(UUID tenantId, AdminAssignmentRequestDTO request, UUID actorId) {
    Tenant tenant = this.findTenantOrThrow(tenantId);
    if (!tenant.isActive()) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Nhà xe đang bị khóa, không thể thêm chủ nhà xe !");
    }
    Role adminRole = this.findAdminRoleOrThrow(tenantId);

    // Chỉ có một email nên không có gì để so trùng trong danh sách: truyền một tập rỗng
    PreparedAdmin admin = this.prepareAdmin(request, new HashSet<>());
    CreateTenantResponseDTO.AdminResult result = this.assignTenantAdmin(tenantId, adminRole.getId(), admin);
    log.info("Người dùng {} thêm chủ nhà xe cho nhà xe {} (id = {}), tài khoản mới: {}", actorId, tenant.getName(), tenantId, result.isAccountCreated());
    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public List<TenantAdminResponseDTO> listTenantAdmins(UUID tenantId) {
    this.findTenantOrThrow(tenantId);
    List<Object[]> rows = this.tenantAdminQueryRepository.findMembersByTenantIdAndRoleCode(tenantId, RoleConstant.TENANT_ADMIN);
    List<TenantAdminResponseDTO> result = new ArrayList<>();
    for (Object[] row : rows) {
      result.add(this.toTenantAdminResponse(row));
    }
    return result;
  }

  // Tạo 3 nhóm quyền mặc định cho nhà xe mới, kèm quyền của từng nhóm
  private void createDefaultRoles(UUID tenantId) {
    for (DefaultRole role : DefaultRole.values()) {
      this.tenantRoleRepository.insertSystemRole(tenantId, role.getName(), role.getCode(), role.getDescription());
      if (role.grantsAllPermissions()) {
        this.tenantRoleRepository.grantAllPermissions(tenantId, role.getCode());
      } else {
        this.tenantRoleRepository.grantPermissions(tenantId, role.getCode(), role.getPermissionCodes());
      }
    }
  }

  // Kiểm tra cả danh sách chủ nhà xe (gọi prepareAdmin cho từng người), chưa ghi gì xuống DB
  private List<PreparedAdmin> prepareAdminList(List<AdminAssignmentRequestDTO> requests) {
    if (requests == null || requests.isEmpty()) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Cần ít nhất một chủ nhà xe !");
    }
    Set<String> seenEmails = new HashSet<>();
    List<PreparedAdmin> prepared = new ArrayList<>();
    for (AdminAssignmentRequestDTO request : requests) {
      prepared.add(this.prepareAdmin(request, seenEmails));
    }
    return prepared;
  }

  private PreparedAdmin prepareAdmin(AdminAssignmentRequestDTO request, Set<String> seenEmails) {
    String email = this.normalizeEmail(request.getEmail());
    if (email.isEmpty()) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Email chủ nhà xe không được để trống !");
    }
    // add() vừa thêm email vào tập seenEmails (các email đã gặp trong lần gửi này).
    if (!seenEmails.add(email)) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Email chủ nhà xe bị trùng trong danh sách: " + email);
    }

    Optional<User> existingUser = this.userRepository.findByEmail(email);

    // Email đã có tài khoản trong hệ thống: nếu dùng được thì chỉ liên kết vào nhà xe, không tạo mới
    if (existingUser.isPresent()) {
      User user = existingUser.get();
     
      if (!Boolean.TRUE.equals(user.getIsActive())) {
        throw new AppException(ErrorCode.BAD_REQUEST, "Tài khoản " + email + " đang bị khóa !");
      }
      if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
        throw new AppException(ErrorCode.BAD_REQUEST, "Tài khoản Super Admin không thể làm chủ nhà xe: " + email);
      }
      // Tài khoản đã có: bỏ qua họ tên và mật khẩu gửi lên, không đổi gì của tài khoản
      return new PreparedAdmin(email, null, null, user);
    }

    // Email mới: bắt buộc họ tên và mật khẩu (DTO đã kiểm tra dạng mật khẩu, ở đây kiểm tra có gửi hay không)
    if (request.getFullName() == null || request.getFullName().isBlank()) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Vui lòng nhập họ tên cho tài khoản mới: " + email);
    }
    if (request.getPassword() == null || request.getPassword().isBlank()) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Vui lòng nhập mật khẩu cho tài khoản mới: " + email);
    }
    return new PreparedAdmin(email, request.getFullName().trim(), request.getPassword(), null);
  }

  // Gán một người làm chủ nhà xe: tạo tài khoản nếu chưa có, rồi liên kết vào nhà xe với nhóm quyền TENANT_ADMIN
  private CreateTenantResponseDTO.AdminResult assignTenantAdmin(UUID tenantId, UUID adminRoleId, PreparedAdmin admin) {
    User user = admin.existingUser();
    boolean accountCreated = (user == null);

    if (accountCreated) {
      user = this.userRepository.save(
        User.builder()
          .email(admin.email())
          .passwordHash(this.passwordEncoder.encode(admin.password()))
          .fullname(admin.fullName())
          .isActive(true) // Boolean không có mặc định phía Java, để null thì sau này login bị NullPointerException
          .isSuperAdmin(false)
          .build()
      );
    } else {
      this.ensureNotAlreadyMember(tenantId, adminRoleId, user);
    }

    this.userTenantRepository.save(
      UserTenant.builder()
        .userId(user.getId())
        .tenantId(tenantId)
        .roleId(adminRoleId)
        .joinedAt(Instant.now())
        .build()
    );
    return CreateTenantResponseDTO.AdminResult.builder()
      .email(admin.email())
      .accountCreated(accountCreated)
      .build();
  }

  // Một người chỉ có một vai trò mỗi nhà xe (khóa chính user_tenants là user_id + tenant_id)
  private void ensureNotAlreadyMember(UUID tenantId, UUID adminRoleId, User user) {
    Optional<UserTenant> membership = this.userTenantRepository.findByUserIdAndTenantId(user.getId(), tenantId);
    if (membership.isEmpty()) {
      return;
    }
    if (adminRoleId.equals(membership.get().getRoleId())) {
      throw new AppException(ErrorCode.TENANT_ADMIN_ALREADY_ASSIGNED, "Người này đã là chủ nhà xe của nhà xe này: " + user.getEmail());
    }
    throw new AppException(ErrorCode.BAD_REQUEST, "Người này đang là nhân viên của nhà xe, chưa hỗ trợ nâng quyền: " + user.getEmail());
  }

  // Chuỗi rỗng hoặc toàn dấu cách nghĩa là xóa giá trị (lưu null)
  private String blankToNull(String value) {
    return value.isBlank() ? null : value.trim();
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
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

  // Nhóm quyền TENANT_ADMIN của nhà xe. Thiếu là dữ liệu hỏng (nhà xe tạo tay, không qua API) nên báo 500
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

  // Mỗi dòng truy vấn là mảng [User, UserTenant]
  private TenantAdminResponseDTO toTenantAdminResponse(Object[] row) {
    User user = (User) row[0];
    UserTenant membership = (UserTenant) row[1];
    return TenantAdminResponseDTO.builder()
      .userId(user.getId())
      .email(user.getEmail())
      .fullName(user.getFullname())
      .isActive(user.getIsActive())
      .joinedAt(membership.getJoinedAt())
      .build();
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
