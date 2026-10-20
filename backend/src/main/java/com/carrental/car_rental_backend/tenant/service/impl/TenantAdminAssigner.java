package com.carrental.car_rental_backend.tenant.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.carrental.car_rental_backend.account.entity.User;
import com.carrental.car_rental_backend.account.entity.UserTenant;
import com.carrental.car_rental_backend.account.repository.UserRepository;
import com.carrental.car_rental_backend.account.repository.UserTenantRepository;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.tenant.dto.request.AdminAssignmentRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.response.AdminLookupResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.CreateTenantResponseDTO;
import com.carrental.car_rental_backend.tenant.entity.Tenant;

import lombok.RequiredArgsConstructor;

// Luật gán một người làm chủ nhà xe (dùng chung cho tạo nhà xe, thêm chủ và tra cứu email).
// Không tự mở giao dịch: phải được gọi từ TenantServiceImpl để việc tạo tài khoản và gán chủ cùng nằm trong một giao dịch
@Component
@RequiredArgsConstructor
public class TenantAdminAssigner {
  public static final String TENANT_LOCKED_MESSAGE = "Nhà xe đang bị khóa, không thể thêm chủ nhà xe !";
  private static final String ACCOUNT_LOCKED_MESSAGE = "Tài khoản đang bị khóa !";
  private static final String SUPER_ADMIN_MESSAGE = "Tài khoản Super Admin không thể làm chủ nhà xe";
  private static final String STAFF_MESSAGE = "Người này đang là nhân viên của nhà xe, chưa hỗ trợ nâng quyền";

  private final UserRepository userRepository;
  private final UserTenantRepository userTenantRepository;
  private final PasswordEncoder passwordEncoder;

  // Một chủ nhà xe đã được kiểm tra xong: email đã chuẩn hóa, kèm tài khoản có sẵn (null nếu là email mới)
  public record PreparedAdmin(String email, String fullName, String password, User existingUser) {}

  // Quan hệ giữa một tài khoản có sẵn và một nhà xe, xét khi muốn gán tài khoản đó làm chủ nhà xe
  private enum MembershipConflict { NONE, ALREADY_ADMIN, STAFF }

  // Kiểm tra cả danh sách chủ nhà xe (gọi prepareOne cho từng người), chưa ghi gì xuống DB
  public List<PreparedAdmin> prepareAll(List<AdminAssignmentRequestDTO> requests) {
    if (requests == null || requests.isEmpty()) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Cần ít nhất một chủ nhà xe !");
    }
    Set<String> seenEmails = new HashSet<>();
    List<PreparedAdmin> prepared = new ArrayList<>();
    for (AdminAssignmentRequestDTO request : requests) {
      prepared.add(this.prepare(request, seenEmails));
    }
    return prepared;
  }

  // Kiểm tra một chủ nhà xe. Chỉ có một email nên không có gì để so trùng trong danh sách
  public PreparedAdmin prepareOne(AdminAssignmentRequestDTO request) {
    return this.prepare(request, new HashSet<>());
  }

  private PreparedAdmin prepare(AdminAssignmentRequestDTO request, Set<String> seenEmails) {
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
      // Block nếu tài khoản bị khóa, hoặc là Super Admin.
      Optional<String> blockReason = this.accountBlockReason(existingUser.get());
      if (blockReason.isPresent()) {
        throw new AppException(ErrorCode.BAD_REQUEST, blockReason.get());
      }
      // Tài khoản đã có: bỏ qua họ tên và mật khẩu gửi lên.
      return new PreparedAdmin(email, null, null, existingUser.get());
    }

    if (request.getFullName() == null || request.getFullName().isBlank()) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Vui lòng nhập họ tên cho tài khoản mới: " + email);
    }
    if (request.getPassword() == null || request.getPassword().isBlank()) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Vui lòng nhập mật khẩu cho tài khoản mới: " + email);
    }
    return new PreparedAdmin(email, request.getFullName().trim(), request.getPassword(), null);
  }

  // Gán một người làm chủ nhà xe: tạo tài khoản nếu chưa có, rồi liên kết vào nhà xe với nhóm quyền TENANT_ADMIN
  public CreateTenantResponseDTO.AdminResult assign(UUID tenantId, UUID adminRoleId, PreparedAdmin admin) {
    User user = admin.existingUser();
    boolean needsNewAccount = (user == null);

    if (needsNewAccount) {
      user = this.userRepository.save(
        User.builder()
          .email(admin.email())
          .passwordHash(this.passwordEncoder.encode(admin.password()))
          .fullname(admin.fullName())
          .isActive(true)
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
      .accountCreated(needsNewAccount)
      .build();
  }

  public AdminLookupResponseDTO lookupByEmail(String email, Tenant tenant, Supplier<UUID> adminRoleId) {
    String normalizedEmail = this.normalizeEmail(email);
    if (normalizedEmail.isEmpty()) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Email không được để trống !");
    }

    Optional<User> existingUser = this.userRepository.findByEmail(normalizedEmail);
    String reason = (tenant != null && !tenant.isActive()) ? TENANT_LOCKED_MESSAGE : null;
    if (reason == null && existingUser.isPresent()) {
      User user = existingUser.get();
      reason = this.accountBlockReason(user)
        .orElseGet(() -> tenant == null ? null : this.membershipBlockReason(tenant.getId(), user, adminRoleId));
    }

    return AdminLookupResponseDTO.builder()
      .email(normalizedEmail)
      .accountExists(existingUser.isPresent())
      .fullName(existingUser.map(User::getFullname).orElse(null))
      .canBeAdmin(reason == null)
      .reason(reason)
      .build();
  }

  // Một người chỉ có một vai trò mỗi nhà xe (khóa chính user_tenants là user_id + tenant_id).
  // Đây là chỗ duy nhất quyết định xung đột: luồng gán thật (ném lỗi) và lookupByEmail (trả lý do) đều dùng.
  // adminRoleId truyền dưới dạng Supplier để chỉ tra nhóm quyền khi người này đã là thành viên
  private MembershipConflict findMembershipConflict(UUID tenantId, User user, Supplier<UUID> adminRoleId) {
    Optional<UserTenant> membership = this.userTenantRepository.findByUserIdAndTenantId(user.getId(), tenantId);
    if (membership.isEmpty()) {
      return MembershipConflict.NONE;
    }
    return adminRoleId.get().equals(membership.get().getRoleId()) ? MembershipConflict.ALREADY_ADMIN : MembershipConflict.STAFF;
  }

  private void ensureNotAlreadyMember(UUID tenantId, UUID adminRoleId, User user) {
    switch (this.findMembershipConflict(tenantId, user, () -> adminRoleId)) {
      case ALREADY_ADMIN -> throw new AppException(ErrorCode.TENANT_ADMIN_ALREADY_ASSIGNED);
      case STAFF -> throw new AppException(ErrorCode.BAD_REQUEST, STAFF_MESSAGE);
      case NONE -> { }
    }
  }

  // Lý do tài khoản có sẵn không thêm được vào nhà xe này vì đã là thành viên (chủ hoặc nhân viên). Null nghĩa là chưa là thành viên
  private String membershipBlockReason(UUID tenantId, User user, Supplier<UUID> adminRoleId) {
    return switch (this.findMembershipConflict(tenantId, user, adminRoleId)) {
      case ALREADY_ADMIN -> ErrorCode.TENANT_ADMIN_ALREADY_ASSIGNED.getMessage();
      case STAFF -> STAFF_MESSAGE;
      case NONE -> null;
    };
  }

  // Lý do một tài khoản có sẵn không làm chủ nhà xe được (bị khóa, hoặc là Super Admin).
  private Optional<String> accountBlockReason(User user) {
    if (!Boolean.TRUE.equals(user.getIsActive())) {
      return Optional.of(ACCOUNT_LOCKED_MESSAGE);
    }
    if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
      return Optional.of(SUPER_ADMIN_MESSAGE);
    }
    return Optional.empty();
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }
}
