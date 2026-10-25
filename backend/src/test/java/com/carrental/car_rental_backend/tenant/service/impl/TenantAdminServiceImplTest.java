package com.carrental.car_rental_backend.tenant.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.carrental.car_rental_backend.account.entity.Role;
import com.carrental.car_rental_backend.account.entity.User;
import com.carrental.car_rental_backend.account.entity.UserTenant;
import com.carrental.car_rental_backend.account.repository.UserRepository;
import com.carrental.car_rental_backend.account.repository.UserTenantRepository;
import com.carrental.car_rental_backend.common.enums.plan_tier.PlanTierTenant;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.tenant.dto.request.AdminAssignmentRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.response.AdminAssignmentResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.AdminEmailCheckResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantAdminResponseDTO;
import com.carrental.car_rental_backend.tenant.entity.Tenant;
import com.carrental.car_rental_backend.tenant.repository.TenantRepository;
import com.carrental.car_rental_backend.tenant.repository.TenantRoleRepository;
import com.carrental.car_rental_backend.tenant.repository.TenantUserTenantRepository;

// Kiểm thử API 7–9 (chủ nhà xe). Các lý do không làm chủ được (AdminRejection) được kiểm tra qua checkAdminEmail,
// vì hàm này trả thẳng lý do; addTenantAdmin dùng cùng hàm kiểm tra nên chỉ cần vài ca đại diện.
@ExtendWith(MockitoExtension.class)
class TenantAdminServiceImplTest {
  private static final UUID ACTOR_ID = UUID.randomUUID();
  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID ADMIN_ROLE_ID = UUID.randomUUID();
  private static final UUID STAFF_ROLE_ID = UUID.randomUUID();
  private static final UUID SALE_ROLE_ID = UUID.randomUUID();
  private static final UUID EXISTING_USER_ID = UUID.randomUUID();
  private static final String EXISTING_EMAIL = "co.san@x.vn";
  private static final String TENANT_LOCKED_MESSAGE = "Nhà xe đang bị khóa, không thể thêm chủ nhà xe !";
  private static final String HAS_OTHER_ROLE_MESSAGE = "Người này đang là nhân viên của nhà xe, chưa hỗ trợ nâng quyền";

  @Mock private TenantRepository tenantRepository;
  @Mock private TenantRoleRepository tenantRoleRepository;
  @Mock private TenantUserTenantRepository tenantUserTenantRepository;
  @Mock private UserRepository userRepository;
  @Mock private UserTenantRepository userTenantRepository;
  @Mock private PasswordEncoder passwordEncoder;

  private TenantAdminServiceImpl tenantAdminService;

  @BeforeEach
  void setUp() {
    tenantAdminService = new TenantAdminServiceImpl(
      tenantRepository, tenantRoleRepository, tenantUserTenantRepository, userRepository, userTenantRepository, passwordEncoder);
  }

  // ---------- Dữ liệu và stub dùng chung ----------

  private AdminAssignmentRequestDTO newAccountRequest(String email) {
    return AdminAssignmentRequestDTO.builder().email(email).fullName("Nguyễn Chủ").password("Matkhau123").build();
  }

  private AdminAssignmentRequestDTO emailOnlyRequest(String email) {
    return AdminAssignmentRequestDTO.builder().email(email).build();
  }

  private Tenant activeTenant() {
    return Tenant.builder().id(TENANT_ID).name("Cũ").domain("old-domain").planTier(PlanTierTenant.FREE).isActive(true).build();
  }

  private Tenant inactiveTenant() {
    return Tenant.builder().id(TENANT_ID).name("Cũ").domain("old-domain").planTier(PlanTierTenant.FREE).isActive(false).build();
  }

  private User existingUser(Boolean isActive, Boolean isSuperAdmin) {
    return User.builder().id(EXISTING_USER_ID).email(EXISTING_EMAIL).fullname("Có Sẵn").passwordHash("hash-cu")
      .isActive(isActive).isSuperAdmin(isSuperAdmin).build();
  }

  private void stubTenant(Tenant tenant) {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
  }

  private void stubAdminRoleLookup() {
    when(tenantRoleRepository.findByTenantIdAndCode(TENANT_ID, "TENANT_ADMIN"))
      .thenReturn(Optional.of(Role.builder().id(ADMIN_ROLE_ID).tenantId(TENANT_ID).code("TENANT_ADMIN").build()));
  }

  private void stubExistingUserLookup(User user) {
    when(userRepository.findByEmail(EXISTING_EMAIL)).thenReturn(Optional.of(user));
  }

  // Người dùng có sẵn đang thuộc nhà xe với nhóm quyền roleId
  private void stubMembership(UUID roleId) {
    when(userTenantRepository.findByUserIdAndTenantId(EXISTING_USER_ID, TENANT_ID))
      .thenReturn(Optional.of(UserTenant.builder().userId(EXISTING_USER_ID).tenantId(TENANT_ID).roleId(roleId).build()));
  }

  private void stubNewUserSaving() {
    when(passwordEncoder.encode(anyString())).thenReturn("hashed");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      user.setId(UUID.randomUUID());
      return user;
    });
  }

  private void assertAppException(Runnable action, ErrorCode expectedCode) {
    assertThatThrownBy(action::run)
      .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedCode));
  }

  // ---------- addTenantAdmin (API 8) ----------

  @Test
  void should_throw_not_found_when_adding_admin_to_missing_tenant() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
    AdminAssignmentRequestDTO request = newAccountRequest("a@x.vn");

    assertAppException(() -> tenantAdminService.addTenantAdmin(TENANT_ID, request, ACTOR_ID), ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void should_refuse_with_tenant_locked_message_and_write_nothing_when_adding_admin_to_inactive_tenant() {
    this.stubTenant(inactiveTenant());
    this.stubAdminRoleLookup();
    AdminAssignmentRequestDTO request = newAccountRequest("a@x.vn");

    assertThatThrownBy(() -> tenantAdminService.addTenantAdmin(TENANT_ID, request, ACTOR_ID))
      .isInstanceOfSatisfying(AppException.class, exception -> {
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(exception.getMessage()).isEqualTo(TENANT_LOCKED_MESSAGE);
      });

    verify(userRepository, never()).save(any(User.class));
    verify(userTenantRepository, never()).save(any(UserTenant.class));
  }

  @Test
  void should_create_account_and_link_it_when_adding_admin_with_new_email() {
    this.stubTenant(activeTenant());
    this.stubAdminRoleLookup();
    this.stubNewUserSaving();

    AdminAssignmentResponseDTO result = tenantAdminService.addTenantAdmin(TENANT_ID, newAccountRequest(" Moi@X.vn "), ACTOR_ID);

    assertThat(result.getEmail()).isEqualTo("moi@x.vn");
    assertThat(result.isAccountCreated()).isTrue();
    verify(userTenantRepository).save(any(UserTenant.class));
  }

  @Test
  void should_only_link_existing_account_that_is_not_yet_a_member() {
    this.stubTenant(activeTenant());
    this.stubAdminRoleLookup();
    this.stubExistingUserLookup(existingUser(true, false));
    when(userTenantRepository.findByUserIdAndTenantId(EXISTING_USER_ID, TENANT_ID)).thenReturn(Optional.empty());

    AdminAssignmentResponseDTO result = tenantAdminService.addTenantAdmin(TENANT_ID, emailOnlyRequest(EXISTING_EMAIL), ACTOR_ID);

    assertThat(result.isAccountCreated()).isFalse();
    verify(userRepository, never()).save(any(User.class));
    verify(userTenantRepository).save(any(UserTenant.class));
  }

  @Test
  void should_throw_conflict_and_write_nothing_when_user_is_already_admin_of_tenant() {
    this.stubTenant(activeTenant());
    this.stubAdminRoleLookup();
    this.stubExistingUserLookup(existingUser(true, false));
    this.stubMembership(ADMIN_ROLE_ID);
    AdminAssignmentRequestDTO request = emailOnlyRequest(EXISTING_EMAIL);

    assertAppException(() -> tenantAdminService.addTenantAdmin(TENANT_ID, request, ACTOR_ID), ErrorCode.TENANT_ADMIN_ALREADY_ASSIGNED);

    verify(userTenantRepository, never()).save(any(UserTenant.class));
  }

  @Test
  void should_throw_bad_request_and_write_nothing_when_user_is_staff_of_tenant() {
    this.stubTenant(activeTenant());
    this.stubAdminRoleLookup();
    this.stubExistingUserLookup(existingUser(true, false));
    this.stubMembership(STAFF_ROLE_ID);
    AdminAssignmentRequestDTO request = emailOnlyRequest(EXISTING_EMAIL);

    assertAppException(() -> tenantAdminService.addTenantAdmin(TENANT_ID, request, ACTOR_ID), ErrorCode.BAD_REQUEST);

    verify(userTenantRepository, never()).save(any(UserTenant.class));
  }

  @Test
  void should_throw_bad_request_and_write_nothing_when_user_is_sale_of_tenant() {
    this.stubTenant(activeTenant());
    this.stubAdminRoleLookup();
    this.stubExistingUserLookup(existingUser(true, false));
    this.stubMembership(SALE_ROLE_ID);
    AdminAssignmentRequestDTO request = emailOnlyRequest(EXISTING_EMAIL);

    assertAppException(() -> tenantAdminService.addTenantAdmin(TENANT_ID, request, ACTOR_ID), ErrorCode.BAD_REQUEST);

    verify(userTenantRepository, never()).save(any(UserTenant.class));
  }

  // ---------- listTenantAdmins (API 7) ----------

  @Test
  void should_throw_not_found_when_listing_admins_of_missing_tenant() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

    assertAppException(() -> tenantAdminService.listTenantAdmins(TENANT_ID), ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void should_return_admins_of_tenant_from_repository() {
    TenantAdminResponseDTO admin = TenantAdminResponseDTO.builder()
      .userId(EXISTING_USER_ID).email(EXISTING_EMAIL).fullName("Có Sẵn").isActive(true)
      .joinedAt(Instant.parse("2026-10-05T06:15:06Z")).build();
    this.stubTenant(activeTenant());
    when(tenantUserTenantRepository.findAdmins(TENANT_ID)).thenReturn(List.of(admin));

    List<TenantAdminResponseDTO> admins = tenantAdminService.listTenantAdmins(TENANT_ID);

    assertThat(admins).containsExactly(admin);
  }

  // ---------- checkAdminEmail (API 9): không có nhà xe, đang tạo nhà xe mới ----------

  @Test
  void should_report_a_new_account_when_the_email_is_not_in_the_system() {
    when(userRepository.findByEmail("moi@x.vn")).thenReturn(Optional.empty());

    AdminEmailCheckResponseDTO result = tenantAdminService.checkAdminEmail("  Moi@X.vn ", null);

    assertThat(result.getEmail()).isEqualTo("moi@x.vn");
    assertThat(result.isAccountExists()).isFalse();
    assertThat(result.getFullName()).isNull();
    assertThat(result.isCanBeAdmin()).isTrue();
    assertThat(result.getReason()).isNull();
  }

  @Test
  void should_return_name_and_allow_when_the_account_exists_and_is_usable() {
    this.stubExistingUserLookup(existingUser(true, false));

    AdminEmailCheckResponseDTO result = tenantAdminService.checkAdminEmail(" CO.SAN@x.vn ", null);

    assertThat(result.isAccountExists()).isTrue();
    assertThat(result.getFullName()).isEqualTo("Có Sẵn");
    assertThat(result.isCanBeAdmin()).isTrue();
    assertThat(result.getReason()).isNull();
  }

  @Test
  void should_refuse_a_locked_account_with_the_same_reason_as_the_assign_error() {
    this.stubExistingUserLookup(existingUser(false, false));

    AdminEmailCheckResponseDTO result = tenantAdminService.checkAdminEmail(EXISTING_EMAIL, null);

    assertThat(result.isAccountExists()).isTrue();
    assertThat(result.isCanBeAdmin()).isFalse();
    assertThat(result.getReason()).isEqualTo("Tài khoản đang bị khóa !");
  }

  @Test
  void should_refuse_a_super_admin_account() {
    this.stubExistingUserLookup(existingUser(true, true));

    AdminEmailCheckResponseDTO result = tenantAdminService.checkAdminEmail(EXISTING_EMAIL, null);

    assertThat(result.isCanBeAdmin()).isFalse();
    assertThat(result.getReason()).isEqualTo("Tài khoản Super Admin không thể làm chủ nhà xe");
  }

  // ---------- checkAdminEmail (API 9): có nhà xe ----------

  @Test
  void should_throw_not_found_when_checking_for_a_missing_tenant() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

    assertAppException(() -> tenantAdminService.checkAdminEmail(EXISTING_EMAIL, TENANT_ID), ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void should_throw_internal_error_when_checking_for_a_tenant_without_an_admin_role() {
    // Nhà xe tạo tay, không qua API nên thiếu nhóm quyền chủ: báo lỗi dữ liệu hỏng với mọi email, kể cả email mới
    this.stubTenant(activeTenant());
    when(tenantRoleRepository.findByTenantIdAndCode(TENANT_ID, "TENANT_ADMIN")).thenReturn(Optional.empty());

    assertAppException(() -> tenantAdminService.checkAdminEmail("moi@x.vn", TENANT_ID), ErrorCode.INTERNAL_SERVER_ERROR);

    verify(userRepository, never()).findByEmail(anyString());
  }

  @Test
  void should_refuse_even_a_new_email_when_the_tenant_is_locked() {
    this.stubTenant(inactiveTenant());
    this.stubAdminRoleLookup();
    when(userRepository.findByEmail("moi@x.vn")).thenReturn(Optional.empty());

    AdminEmailCheckResponseDTO result = tenantAdminService.checkAdminEmail("moi@x.vn", TENANT_ID);

    assertThat(result.isAccountExists()).isFalse();
    assertThat(result.isCanBeAdmin()).isFalse();
    assertThat(result.getReason()).isEqualTo(TENANT_LOCKED_MESSAGE);
  }

  @Test
  void should_report_the_locked_tenant_first_when_both_the_tenant_and_the_account_are_locked() {
    // findRejection kiểm tra nhà xe bị khóa trước tài khoản, nên khi có cả hai lý do thì báo "nhà xe bị khóa"
    this.stubTenant(inactiveTenant());
    this.stubAdminRoleLookup();
    this.stubExistingUserLookup(existingUser(false, false));

    AdminEmailCheckResponseDTO result = tenantAdminService.checkAdminEmail(EXISTING_EMAIL, TENANT_ID);

    assertThat(result.isAccountExists()).isTrue();
    assertThat(result.isCanBeAdmin()).isFalse();
    assertThat(result.getReason()).isEqualTo(TENANT_LOCKED_MESSAGE);
  }

  @Test
  void should_refuse_an_account_that_is_already_admin_of_the_tenant() {
    this.stubTenant(activeTenant());
    this.stubAdminRoleLookup();
    this.stubExistingUserLookup(existingUser(true, false));
    this.stubMembership(ADMIN_ROLE_ID);

    AdminEmailCheckResponseDTO result = tenantAdminService.checkAdminEmail(EXISTING_EMAIL, TENANT_ID);

    assertThat(result.isCanBeAdmin()).isFalse();
    assertThat(result.getReason()).isEqualTo("Người này đã là chủ nhà xe của nhà xe này");
  }

  @Test
  void should_refuse_an_account_that_is_staff_of_the_tenant() {
    this.stubTenant(activeTenant());
    this.stubAdminRoleLookup();
    this.stubExistingUserLookup(existingUser(true, false));
    this.stubMembership(STAFF_ROLE_ID);

    AdminEmailCheckResponseDTO result = tenantAdminService.checkAdminEmail(EXISTING_EMAIL, TENANT_ID);

    assertThat(result.isCanBeAdmin()).isFalse();
    assertThat(result.getReason()).isEqualTo(HAS_OTHER_ROLE_MESSAGE);
  }

  @Test
  void should_refuse_an_account_that_is_sale_of_the_tenant() {
    this.stubTenant(activeTenant());
    this.stubAdminRoleLookup();
    this.stubExistingUserLookup(existingUser(true, false));
    this.stubMembership(SALE_ROLE_ID);

    AdminEmailCheckResponseDTO result = tenantAdminService.checkAdminEmail(EXISTING_EMAIL, TENANT_ID);

    assertThat(result.isCanBeAdmin()).isFalse();
    assertThat(result.getReason()).isEqualTo(HAS_OTHER_ROLE_MESSAGE);
  }

  @Test
  void should_allow_an_existing_account_that_is_not_yet_a_member_of_the_tenant_and_write_nothing() {
    this.stubTenant(activeTenant());
    this.stubAdminRoleLookup();
    this.stubExistingUserLookup(existingUser(true, false));
    when(userTenantRepository.findByUserIdAndTenantId(EXISTING_USER_ID, TENANT_ID)).thenReturn(Optional.empty());

    AdminEmailCheckResponseDTO result = tenantAdminService.checkAdminEmail(EXISTING_EMAIL, TENANT_ID);

    assertThat(result.isCanBeAdmin()).isTrue();
    verify(userRepository, never()).save(any(User.class));
    verify(userTenantRepository, never()).save(any(UserTenant.class));
  }
}
