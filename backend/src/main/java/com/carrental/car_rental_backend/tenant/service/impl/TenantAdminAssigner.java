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
  // record: Khai báo một class chỉ dùng chứa dữ liệu. Có sẵn constructor, hàm đọc (tên trùng trường, ví dụ: admin.existingUser)
  public record PreparedAdmin(String email, String fullName, String password, User existingUser) {}

  // Kiểm tra cả danh sách chủ nhà xe, chưa ghi gì xuống DB
  public List<PreparedAdmin> prepareAll(List<AdminAssignmentRequestDTO> requests) {
    if (requests == null || requests.isEmpty()) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Cần ít nhất một chủ nhà xe !");
    }

    // Set: Kiểu tập hợp ko chứa phần tử trùng.
    // seenEmails: các email đã gặp trong lần gửi này, để phát hiện trùng lặp trong danh sách gửi lên.
    Set<String> seenEmails = new HashSet<>();
    List<PreparedAdmin> prepared = new ArrayList<>();
    for (AdminAssignmentRequestDTO request : requests) {
      prepared.add(this.prepare(request, seenEmails));
    }
    return prepared;
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

    // Optional: Class biểu diễn giá trị tồn tại hoặc ko (null) -> isPresent: kiểm tra có giá trị
    Optional<User> existingUser = this.userRepository.findByEmail(email);

    // Email đã có tài khoản trong hệ thống: nếu dùng được thì chỉ liên kết vào nhà xe, không tạo mới
    if (existingUser.isPresent()) {
      // Block nếu tài khoản bị khóa, hoặc là Super Admin.
      this.ensureAccountUsable(existingUser.get());
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
      // user chưa thuộc tenant nào
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
      // user có sẵn, kiểm tra có phải member của tenant không
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

  // Chỉ đọc, không ghi gì. adminRoleId là nhóm quyền chủ của nhà xe, chỉ cần khi có tenant (null khi đang tạo nhà xe mới).
  // Chạy đúng các kiểm tra của luồng gán thật, theo cùng thứ tự; lỗi nào bị ném ra thì thành lý do trả về,
  // nên lý do luôn trùng với lỗi thật khi gửi
  public AdminLookupResponseDTO lookupByEmail(String email, Tenant tenant, UUID adminRoleId) {
    String normalizedEmail = this.normalizeEmail(email);
    if (normalizedEmail.isEmpty()) {
      throw new AppException(ErrorCode.BAD_REQUEST, "Email không được để trống !");
    }

    Optional<User> existingUser = this.userRepository.findByEmail(normalizedEmail);
    String reason = null;
    try {
      if (tenant != null && !tenant.isActive()) {
        throw new AppException(ErrorCode.BAD_REQUEST, TENANT_LOCKED_MESSAGE);
      }
      if (existingUser.isPresent()) {
        this.ensureAccountUsable(existingUser.get());
        if (tenant != null) {
          this.ensureNotAlreadyMember(tenant.getId(), adminRoleId, existingUser.get());
        }
      }
    } catch (AppException exception) {
      reason = exception.getMessage();
    }

    return AdminLookupResponseDTO.builder()
      .email(normalizedEmail)
      .accountExists(existingUser.isPresent())
      .fullName(existingUser.map(User::getFullname).orElse(null))
      .canBeAdmin(reason == null)
      .reason(reason)
      .build();
  }

  private void ensureAccountUsable(User user) {
    if (!Boolean.TRUE.equals(user.getIsActive())) {
      throw new AppException(ErrorCode.BAD_REQUEST, ACCOUNT_LOCKED_MESSAGE);
    }
    if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
      throw new AppException(ErrorCode.BAD_REQUEST, SUPER_ADMIN_MESSAGE);
    }
  }

  // Một người chỉ có một vai trò mỗi nhà xe (khóa chính user_tenants là user_id + tenant_id)
  private void ensureNotAlreadyMember(UUID tenantId, UUID adminRoleId, User user) {
    Optional<UserTenant> membership = this.userTenantRepository.findByUserIdAndTenantId(user.getId(), tenantId);
    if (membership.isEmpty()) {
      return;
    }
    if (adminRoleId.equals(membership.get().getRoleId())) {
      throw new AppException(ErrorCode.TENANT_ADMIN_ALREADY_ASSIGNED);
    }
    throw new AppException(ErrorCode.BAD_REQUEST, STAFF_MESSAGE);
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }
}
