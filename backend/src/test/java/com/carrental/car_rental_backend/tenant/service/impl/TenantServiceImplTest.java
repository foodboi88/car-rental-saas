package com.carrental.car_rental_backend.tenant.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import com.carrental.car_rental_backend.tenant.dto.request.CreateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.request.UpdateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.response.AdminLookupResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.CreateTenantResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantAdminResponseDTO;
import com.carrental.car_rental_backend.tenant.dto.response.TenantResponseDTO;
import com.carrental.car_rental_backend.tenant.entity.Tenant;
import com.carrental.car_rental_backend.tenant.entity.TenantConfig;
import com.carrental.car_rental_backend.tenant.repository.TenantAdminQueryRepository;
import com.carrental.car_rental_backend.tenant.repository.TenantConfigRepository;
import com.carrental.car_rental_backend.tenant.repository.TenantRepository;
import com.carrental.car_rental_backend.tenant.repository.TenantRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class TenantServiceImplTest {
  private static final UUID ACTOR_ID = UUID.randomUUID();
  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID ADMIN_ROLE_ID = UUID.randomUUID();
  private static final UUID STAFF_ROLE_ID = UUID.randomUUID();
  private static final UUID EXISTING_USER_ID = UUID.randomUUID();
  private static final String EXISTING_EMAIL = "co.san@x.vn";

  @Mock private TenantRepository tenantRepository;
  @Mock private TenantConfigRepository tenantConfigRepository;
  @Mock private TenantRoleRepository tenantRoleRepository;
  @Mock private TenantAdminQueryRepository tenantAdminQueryRepository;
  @Mock private UserRepository userRepository;
  @Mock private UserTenantRepository userTenantRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  private TenantServiceImpl tenantService;

  // Dựng bằng tay để dùng TenantAdminAssigner thật (với các repository giả), vì đây là phần luật gán chủ được kiểm thử qua service
  @BeforeEach
  void setUp() {
    TenantAdminAssigner adminAssigner = new TenantAdminAssigner(userRepository, userTenantRepository, passwordEncoder);
    tenantService = new TenantServiceImpl(
      tenantRepository, tenantConfigRepository, tenantRoleRepository, tenantAdminQueryRepository, adminAssigner, objectMapper);
  }

  // ---------- Dữ liệu và stub dùng chung ----------

  private AdminAssignmentRequestDTO newAccountRequest(String email) {
    return AdminAssignmentRequestDTO.builder().email(email).fullName("  Nguyễn Chủ  ").password("Matkhau123").build();
  }

  private AdminAssignmentRequestDTO emailOnlyRequest(String email) {
    return AdminAssignmentRequestDTO.builder().email(email).build();
  }

  private CreateTenantRequestDTO createRequest(AdminAssignmentRequestDTO... admins) {
    return CreateTenantRequestDTO.builder()
      .name("  Probe Car ")
      .domain(" Probe-Car ")
      .admins(List.of(admins))
      .build();
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

  private Role adminRole() {
    return Role.builder().id(ADMIN_ROLE_ID).tenantId(TENANT_ID).code("TENANT_ADMIN").build();
  }

  // tenantRepository.save trả về chính tenant được lưu, kèm id
  private void stubTenantSaving() {
    when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
      Tenant tenant = invocation.getArgument(0);
      tenant.setId(TENANT_ID);
      return tenant;
    });
  }

  private void stubAdminRoleLookup() {
    when(tenantRoleRepository.findByTenantIdAndCode(TENANT_ID, "TENANT_ADMIN")).thenReturn(Optional.of(adminRole()));
  }

  private void stubNewUserSaving() {
    when(passwordEncoder.encode(anyString())).thenReturn("hashed");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      user.setId(UUID.randomUUID());
      return user;
    });
  }

  private void stubExistingUserLookup(User user) {
    when(userRepository.findByEmail(EXISTING_EMAIL)).thenReturn(Optional.of(user));
  }

  // Dựng sẵn toàn bộ stub cho nhánh tạo nhà xe thành công với một email mới
  private CreateTenantResponseDTO createTenantWithNewAccount() {
    this.stubTenantSaving();
    this.stubAdminRoleLookup();
    this.stubNewUserSaving();
    return tenantService.createTenant(createRequest(newAccountRequest(" Chu@X.vn ")), ACTOR_ID);
  }

  private CreateTenantResponseDTO createTenantWithExistingAccount() {
    this.stubTenantSaving();
    this.stubAdminRoleLookup();
    this.stubExistingUserLookup(existingUser(true, false));
    return tenantService.createTenant(createRequest(emailOnlyRequest(" CO.SAN@x.vn ")), ACTOR_ID);
  }

  private void assertAppException(Runnable action, ErrorCode expectedCode) {
    assertThatThrownBy(action::run)
      .isInstanceOfSatisfying(AppException.class, exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedCode));
  }

  // ---------- createTenant: nhánh thành công ----------

  @Test
  void should_save_active_tenant_with_free_plan_and_normalized_fields_when_creating() {
    this.createTenantWithNewAccount();

    ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
    verify(tenantRepository).save(captor.capture());
    Tenant saved = captor.getValue();
    assertThat(saved.getDomain()).isEqualTo("probe-car");
    assertThat(saved.getName()).isEqualTo("Probe Car");
    assertThat(saved.getPlanTier()).isEqualTo(PlanTierTenant.FREE);
    assertThat(saved.isActive()).isTrue();
    assertThat(saved.getSettings()).isEqualTo("{}");
  }

  @Test
  void should_save_default_config_when_creating_tenant() {
    this.createTenantWithNewAccount();

    ArgumentCaptor<TenantConfig> captor = ArgumentCaptor.forClass(TenantConfig.class);
    verify(tenantConfigRepository).save(captor.capture());
    TenantConfig config = captor.getValue();
    assertThat(config.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(config.getHoldTimeoutMinutes()).isEqualTo(30);
    assertThat(config.getLateHourlyPrice()).isEqualByComparingTo(new BigDecimal("100000"));
    assertThat(config.getDefaultCommissionRate()).isEqualByComparingTo(new BigDecimal("5.00"));
  }

  @Test
  void should_create_three_default_roles_when_creating_tenant() {
    this.createTenantWithNewAccount();

    verify(tenantRoleRepository).insertSystemRole(eq(TENANT_ID), anyString(), eq("TENANT_ADMIN"), anyString());
    verify(tenantRoleRepository).insertSystemRole(eq(TENANT_ID), anyString(), eq("STAFF"), anyString());
    verify(tenantRoleRepository).insertSystemRole(eq(TENANT_ID), anyString(), eq("SALE"), anyString());
  }

  @Test
  void should_grant_all_permissions_only_to_tenant_admin_role() {
    this.createTenantWithNewAccount();

    verify(tenantRoleRepository).grantAllPermissions(TENANT_ID, "TENANT_ADMIN");
    verify(tenantRoleRepository, never()).grantPermissions(eq(TENANT_ID), eq("TENANT_ADMIN"), anyCollection());
  }

  @Test
  void should_grant_exactly_seven_permissions_to_staff_role() {
    this.createTenantWithNewAccount();

    verify(tenantRoleRepository).grantPermissions(TENANT_ID, "STAFF", List.of(
      "booking:read", "booking:create", "booking:confirm_deposit", "booking:handover",
      "booking:return", "booking:cancel", "vehicle:read"));
  }

  @Test
  void should_grant_exactly_four_permissions_to_sale_role() {
    this.createTenantWithNewAccount();

    verify(tenantRoleRepository).grantPermissions(TENANT_ID, "SALE", List.of(
      "booking:read", "booking:create", "booking:cancel", "vehicle:read"));
  }

  @Test
  void should_create_active_non_super_admin_account_with_encoded_password_for_new_email() {
    this.createTenantWithNewAccount();

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();
    assertThat(saved.getEmail()).isEqualTo("chu@x.vn");
    assertThat(saved.getFullname()).isEqualTo("Nguyễn Chủ");
    assertThat(saved.getPasswordHash()).isEqualTo("hashed");
    assertThat(saved.getIsActive()).isTrue();
    assertThat(saved.getIsSuperAdmin()).isFalse();
    verify(passwordEncoder).encode("Matkhau123");
  }

  @Test
  void should_link_new_account_to_tenant_with_admin_role() {
    this.createTenantWithNewAccount();

    ArgumentCaptor<UserTenant> captor = ArgumentCaptor.forClass(UserTenant.class);
    verify(userTenantRepository).save(captor.capture());
    UserTenant link = captor.getValue();
    assertThat(link.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(link.getRoleId()).isEqualTo(ADMIN_ROLE_ID);
    assertThat(link.getUserId()).isNotNull();
    assertThat(link.getJoinedAt()).isNotNull();
  }

  @Test
  void should_report_account_created_for_new_email() {
    CreateTenantResponseDTO response = this.createTenantWithNewAccount();

    assertThat(response.getAdmins()).hasSize(1);
    assertThat(response.getAdmins().get(0).getEmail()).isEqualTo("chu@x.vn");
    assertThat(response.getAdmins().get(0).isAccountCreated()).isTrue();
  }

  @Test
  void should_not_create_account_or_touch_password_when_email_already_exists() {
    this.createTenantWithExistingAccount();

    verify(userRepository, never()).save(any(User.class));
    verify(passwordEncoder, never()).encode(anyString());
  }

  @Test
  void should_link_existing_account_to_tenant_with_admin_role() {
    this.createTenantWithExistingAccount();

    ArgumentCaptor<UserTenant> captor = ArgumentCaptor.forClass(UserTenant.class);
    verify(userTenantRepository).save(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(EXISTING_USER_ID);
    assertThat(captor.getValue().getRoleId()).isEqualTo(ADMIN_ROLE_ID);
  }

  @Test
  void should_report_account_not_created_for_existing_email() {
    CreateTenantResponseDTO response = this.createTenantWithExistingAccount();

    assertThat(response.getAdmins().get(0).isAccountCreated()).isFalse();
  }

  // ---------- createTenant: nhánh lỗi, không được ghi gì ----------

  @Test
  void should_throw_conflict_and_write_nothing_when_domain_already_exists() {
    when(tenantRepository.existsByDomain("probe-car")).thenReturn(true);
    CreateTenantRequestDTO request = createRequest(newAccountRequest("a@x.vn"));

    assertAppException(() -> tenantService.createTenant(request, ACTOR_ID), ErrorCode.TENANT_DOMAIN_ALREADY_EXISTS);

    verify(tenantRepository, never()).save(any(Tenant.class));
  }

  @Test
  void should_throw_bad_request_when_admin_list_is_empty() {
    CreateTenantRequestDTO request = createRequest();

    assertAppException(() -> tenantService.createTenant(request, ACTOR_ID), ErrorCode.BAD_REQUEST);

    verify(tenantRepository, never()).save(any(Tenant.class));
  }

  @Test
  void should_throw_bad_request_when_admin_emails_duplicate_ignoring_case() {
    CreateTenantRequestDTO request = createRequest(newAccountRequest("a@x.vn"), newAccountRequest(" A@X.VN "));

    assertAppException(() -> tenantService.createTenant(request, ACTOR_ID), ErrorCode.BAD_REQUEST);

    verify(tenantRepository, never()).save(any(Tenant.class));
  }

  @Test
  void should_throw_bad_request_when_new_email_has_no_full_name() {
    CreateTenantRequestDTO request = createRequest(
      AdminAssignmentRequestDTO.builder().email("a@x.vn").password("Matkhau123").build());

    assertAppException(() -> tenantService.createTenant(request, ACTOR_ID), ErrorCode.BAD_REQUEST);
  }

  @Test
  void should_throw_bad_request_when_new_email_has_no_password() {
    CreateTenantRequestDTO request = createRequest(
      AdminAssignmentRequestDTO.builder().email("a@x.vn").fullName("A").build());

    assertAppException(() -> tenantService.createTenant(request, ACTOR_ID), ErrorCode.BAD_REQUEST);
  }

  @Test
  void should_throw_bad_request_when_existing_account_is_locked() {
    this.stubExistingUserLookup(existingUser(false, false));
    CreateTenantRequestDTO request = createRequest(emailOnlyRequest(EXISTING_EMAIL));

    assertAppException(() -> tenantService.createTenant(request, ACTOR_ID), ErrorCode.BAD_REQUEST);
  }

  @Test
  void should_throw_bad_request_when_existing_account_is_super_admin() {
    this.stubExistingUserLookup(existingUser(true, true));
    CreateTenantRequestDTO request = createRequest(emailOnlyRequest(EXISTING_EMAIL));

    assertAppException(() -> tenantService.createTenant(request, ACTOR_ID), ErrorCode.BAD_REQUEST);
  }

  @Test
  void should_throw_bad_request_when_settings_is_not_valid_json() {
    CreateTenantRequestDTO request = createRequest(newAccountRequest("a@x.vn"));
    request.setSettings("{khong phai json");

    assertAppException(() -> tenantService.createTenant(request, ACTOR_ID), ErrorCode.BAD_REQUEST);
  }

  @Test
  void should_write_nothing_when_second_admin_is_invalid() {
    CreateTenantRequestDTO request = createRequest(
      newAccountRequest("hop.le@x.vn"),
      AdminAssignmentRequestDTO.builder().email("thieu.mat.khau@x.vn").fullName("B").build());

    assertAppException(() -> tenantService.createTenant(request, ACTOR_ID), ErrorCode.BAD_REQUEST);

    verify(tenantRepository, never()).save(any(Tenant.class));
    verify(userRepository, never()).save(any(User.class));
  }

  // ---------- đọc ----------

  @Test
  void should_throw_not_found_when_getting_missing_tenant() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

    assertAppException(() -> tenantService.getTenantById(TENANT_ID), ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void should_search_with_match_all_pattern_when_keyword_is_blank() {
    Pageable pageable = PageRequest.of(0, 10);
    when(tenantRepository.search("%", null, pageable)).thenReturn(Page.empty(pageable));

    Page<TenantResponseDTO> result = tenantService.listTenants("   ", null, pageable);

    assertThat(result.getTotalElements()).isZero();
    verify(tenantRepository).search("%", null, pageable);
  }

  @Test
  void should_search_with_lowercase_wrapped_keyword_and_status_filter() {
    Pageable pageable = PageRequest.of(0, 10);
    when(tenantRepository.search("%hà nội%", true, pageable)).thenReturn(new PageImpl<>(List.of(activeTenant()), pageable, 1));

    Page<TenantResponseDTO> result = tenantService.listTenants("  HÀ Nội ", true, pageable);

    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getDomain()).isEqualTo("old-domain");
  }

  // ---------- sửa ----------

  @Test
  void should_throw_not_found_when_updating_missing_tenant() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());
    UpdateTenantRequestDTO request = UpdateTenantRequestDTO.builder().name("A").domain("a-b").planTier(PlanTierTenant.PRO).build();

    assertAppException(() -> tenantService.updateTenant(TENANT_ID, request, ACTOR_ID), ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void should_throw_conflict_when_updating_to_domain_of_another_tenant() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(activeTenant()));
    when(tenantRepository.existsByDomainAndIdNot("da-co", TENANT_ID)).thenReturn(true);
    UpdateTenantRequestDTO request = UpdateTenantRequestDTO.builder().name("A").domain("Da-Co").planTier(PlanTierTenant.PRO).build();

    assertAppException(() -> tenantService.updateTenant(TENANT_ID, request, ACTOR_ID), ErrorCode.TENANT_DOMAIN_ALREADY_EXISTS);

    verify(tenantRepository, never()).saveAndFlush(any(Tenant.class));
  }

  @Test
  void should_update_name_plan_and_contact_fields() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(activeTenant()));
    when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
    UpdateTenantRequestDTO request = UpdateTenantRequestDTO.builder()
      .name("  Tên Mới ").domain("old-domain").planTier(PlanTierTenant.PRO)
      .contactEmail("moi@x.vn").contactPhone("0900000002").build();

    TenantResponseDTO response = tenantService.updateTenant(TENANT_ID, request, ACTOR_ID);

    assertThat(response.getName()).isEqualTo("Tên Mới");
    assertThat(response.getPlanTier()).isEqualTo(PlanTierTenant.PRO);
    assertThat(response.getContactEmail()).isEqualTo("moi@x.vn");
    assertThat(response.getContactPhone()).isEqualTo("0900000002");
  }

  @Test
  void should_skip_domain_uniqueness_check_when_domain_is_unchanged() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(activeTenant()));
    when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
    UpdateTenantRequestDTO request = UpdateTenantRequestDTO.builder().name("A").domain(" OLD-domain ").planTier(PlanTierTenant.FREE).build();

    tenantService.updateTenant(TENANT_ID, request, ACTOR_ID);

    verify(tenantRepository, never()).existsByDomainAndIdNot(anyString(), any(UUID.class));
  }

  // ---------- sửa: trường tùy chọn không gửi thì giữ giá trị cũ ----------

  private Tenant tenantWithOptionalFields() {
    return Tenant.builder().id(TENANT_ID).name("Cũ").domain("old-domain").planTier(PlanTierTenant.FREE).isActive(true)
      .logoUrl("logo-cu.png").contactEmail("cu@x.vn").contactPhone("0900000001").settings("{\"mau\":\"xanh\"}").build();
  }

  private UpdateTenantRequestDTO requiredFieldsOnlyUpdate() {
    return UpdateTenantRequestDTO.builder().name("Tên Mới").domain("old-domain").planTier(PlanTierTenant.PRO).build();
  }

  private void stubTenantForUpdate(Tenant tenant) {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
    when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void should_keep_settings_when_update_request_omits_it() {
    this.stubTenantForUpdate(tenantWithOptionalFields());

    TenantResponseDTO response = tenantService.updateTenant(TENANT_ID, requiredFieldsOnlyUpdate(), ACTOR_ID);

    assertThat(response.getSettings()).isEqualTo("{\"mau\":\"xanh\"}");
  }

  @Test
  void should_keep_logo_and_contact_fields_when_update_request_omits_them() {
    this.stubTenantForUpdate(tenantWithOptionalFields());

    TenantResponseDTO response = tenantService.updateTenant(TENANT_ID, requiredFieldsOnlyUpdate(), ACTOR_ID);

    assertThat(response.getLogoUrl()).isEqualTo("logo-cu.png");
    assertThat(response.getContactEmail()).isEqualTo("cu@x.vn");
    assertThat(response.getContactPhone()).isEqualTo("0900000001");
  }

  @Test
  void should_still_overwrite_required_fields_when_optional_fields_are_omitted() {
    this.stubTenantForUpdate(tenantWithOptionalFields());

    TenantResponseDTO response = tenantService.updateTenant(TENANT_ID, requiredFieldsOnlyUpdate(), ACTOR_ID);

    assertThat(response.getName()).isEqualTo("Tên Mới");
    assertThat(response.getPlanTier()).isEqualTo(PlanTierTenant.PRO);
  }

  @Test
  void should_overwrite_optional_fields_when_update_request_sends_them() {
    this.stubTenantForUpdate(tenantWithOptionalFields());
    UpdateTenantRequestDTO request = requiredFieldsOnlyUpdate();
    request.setSettings("{\"mau\":\"do\",\"co\":1}");
    request.setLogoUrl("logo-moi.png");
    request.setContactEmail("moi@x.vn");
    request.setContactPhone("0900000002");

    TenantResponseDTO response = tenantService.updateTenant(TENANT_ID, request, ACTOR_ID);

    assertThat(response.getSettings()).isEqualTo("{\"mau\":\"do\",\"co\":1}");
    assertThat(response.getLogoUrl()).isEqualTo("logo-moi.png");
    assertThat(response.getContactEmail()).isEqualTo("moi@x.vn");
    assertThat(response.getContactPhone()).isEqualTo("0900000002");
  }

  @Test
  void should_clear_optional_contact_fields_when_update_request_sends_blank() {
    this.stubTenantForUpdate(tenantWithOptionalFields());
    UpdateTenantRequestDTO request = requiredFieldsOnlyUpdate();
    request.setLogoUrl("");
    request.setContactEmail("");
    request.setContactPhone("   ");

    TenantResponseDTO response = tenantService.updateTenant(TENANT_ID, request, ACTOR_ID);

    assertThat(response.getLogoUrl()).isNull();
    assertThat(response.getContactEmail()).isNull();
    assertThat(response.getContactPhone()).isNull();
  }

  @Test
  void should_reset_settings_to_empty_object_when_update_request_sends_blank() {
    this.stubTenantForUpdate(tenantWithOptionalFields());
    UpdateTenantRequestDTO request = requiredFieldsOnlyUpdate();
    request.setSettings("");

    TenantResponseDTO response = tenantService.updateTenant(TENANT_ID, request, ACTOR_ID);

    assertThat(response.getSettings()).isEqualTo("{}");
  }

  @Test
  void should_throw_bad_request_when_update_settings_is_not_valid_json() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenantWithOptionalFields()));
    UpdateTenantRequestDTO request = requiredFieldsOnlyUpdate();
    request.setSettings("khong phai json");

    assertAppException(() -> tenantService.updateTenant(TENANT_ID, request, ACTOR_ID), ErrorCode.BAD_REQUEST);

    verify(tenantRepository, never()).saveAndFlush(any(Tenant.class));
  }

  // ---------- khóa, mở khóa, xóa mềm ----------

  @Test
  void should_only_deactivate_tenant_and_never_delete_it_on_delete() {
    Tenant tenant = activeTenant();
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
    when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

    tenantService.deleteTenant(TENANT_ID, ACTOR_ID);

    assertThat(tenant.isActive()).isFalse();
    verify(tenantRepository).saveAndFlush(tenant);
    verify(tenantRepository, never()).delete(any(Tenant.class));
    verify(tenantRepository, never()).deleteById(any(UUID.class));
  }

  @Test
  void should_do_nothing_when_deleting_an_already_inactive_tenant() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(inactiveTenant()));

    tenantService.deleteTenant(TENANT_ID, ACTOR_ID);

    verify(tenantRepository, never()).saveAndFlush(any(Tenant.class));
  }

  @Test
  void should_reactivate_tenant_when_status_changes_to_active() {
    Tenant tenant = inactiveTenant();
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
    when(tenantRepository.saveAndFlush(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

    TenantResponseDTO response = tenantService.changeStatus(TENANT_ID, true, ACTOR_ID);

    assertThat(response.getIsActive()).isTrue();
  }

  // ---------- thêm chủ nhà xe cho nhà xe đã có ----------

  @Test
  void should_throw_bad_request_when_adding_admin_to_inactive_tenant() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(inactiveTenant()));
    AdminAssignmentRequestDTO request = newAccountRequest("a@x.vn");

    assertAppException(() -> tenantService.addTenantAdmin(TENANT_ID, request, ACTOR_ID), ErrorCode.BAD_REQUEST);
  }

  @Test
  void should_add_new_admin_to_active_tenant() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(activeTenant()));
    this.stubAdminRoleLookup();
    this.stubNewUserSaving();

    CreateTenantResponseDTO.AdminResult result = tenantService.addTenantAdmin(TENANT_ID, newAccountRequest("moi@x.vn"), ACTOR_ID);

    assertThat(result.getEmail()).isEqualTo("moi@x.vn");
    assertThat(result.isAccountCreated()).isTrue();
    verify(userTenantRepository).save(any(UserTenant.class));
  }

  @Test
  void should_throw_conflict_when_user_is_already_admin_of_tenant() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(activeTenant()));
    this.stubAdminRoleLookup();
    this.stubExistingUserLookup(existingUser(true, false));
    when(userTenantRepository.findByUserIdAndTenantId(EXISTING_USER_ID, TENANT_ID))
      .thenReturn(Optional.of(UserTenant.builder().userId(EXISTING_USER_ID).tenantId(TENANT_ID).roleId(ADMIN_ROLE_ID).build()));
    AdminAssignmentRequestDTO request = emailOnlyRequest(EXISTING_EMAIL);

    assertAppException(() -> tenantService.addTenantAdmin(TENANT_ID, request, ACTOR_ID), ErrorCode.TENANT_ADMIN_ALREADY_ASSIGNED);

    verify(userTenantRepository, never()).save(any(UserTenant.class));
  }

  @Test
  void should_throw_bad_request_when_user_is_already_staff_of_tenant() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(activeTenant()));
    this.stubAdminRoleLookup();
    this.stubExistingUserLookup(existingUser(true, false));
    when(userTenantRepository.findByUserIdAndTenantId(EXISTING_USER_ID, TENANT_ID))
      .thenReturn(Optional.of(UserTenant.builder().userId(EXISTING_USER_ID).tenantId(TENANT_ID).roleId(STAFF_ROLE_ID).build()));
    AdminAssignmentRequestDTO request = emailOnlyRequest(EXISTING_EMAIL);

    assertAppException(() -> tenantService.addTenantAdmin(TENANT_ID, request, ACTOR_ID), ErrorCode.BAD_REQUEST);

    verify(userTenantRepository, never()).save(any(UserTenant.class));
  }

  // ---------- xem chủ nhà xe ----------

  @Test
  void should_throw_not_found_when_listing_admins_of_missing_tenant() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

    assertAppException(() -> tenantService.listTenantAdmins(TENANT_ID), ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void should_map_user_and_membership_rows_to_admin_responses() {
    Instant joinedAt = Instant.parse("2026-10-05T06:15:06Z");
    UserTenant membership = UserTenant.builder().userId(EXISTING_USER_ID).tenantId(TENANT_ID).roleId(ADMIN_ROLE_ID).joinedAt(joinedAt).build();
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(activeTenant()));
    when(tenantAdminQueryRepository.findMembersByTenantIdAndRoleCode(TENANT_ID, "TENANT_ADMIN"))
      .thenReturn(List.<Object[]>of(new Object[] { existingUser(true, false), membership }));

    List<TenantAdminResponseDTO> admins = tenantService.listTenantAdmins(TENANT_ID);

    assertThat(admins).hasSize(1);
    assertThat(admins.get(0).getUserId()).isEqualTo(EXISTING_USER_ID);
    assertThat(admins.get(0).getEmail()).isEqualTo(EXISTING_EMAIL);
    assertThat(admins.get(0).getFullName()).isEqualTo("Có Sẵn");
    assertThat(admins.get(0).getIsActive()).isTrue();
    assertThat(admins.get(0).getJoinedAt()).isEqualTo(joinedAt);
  }

  // ---------- tra cứu email trước khi gán làm chủ nhà xe ----------

  @Test
  void should_throw_bad_request_when_looking_up_a_blank_email() {
    assertAppException(() -> tenantService.lookupAdminByEmail("   ", null), ErrorCode.BAD_REQUEST);
    assertAppException(() -> tenantService.lookupAdminByEmail(null, null), ErrorCode.BAD_REQUEST);
  }

  @Test
  void should_report_a_new_account_when_the_email_is_not_in_the_system() {
    when(userRepository.findByEmail("moi@x.vn")).thenReturn(Optional.empty());

    AdminLookupResponseDTO result = tenantService.lookupAdminByEmail("  Moi@X.vn ", null);

    assertThat(result.getEmail()).isEqualTo("moi@x.vn");
    assertThat(result.isAccountExists()).isFalse();
    assertThat(result.getFullName()).isNull();
    assertThat(result.isCanBeAdmin()).isTrue();
    assertThat(result.getReason()).isNull();
  }

  @Test
  void should_return_name_and_allow_assigning_when_the_account_exists_and_is_usable() {
    this.stubExistingUserLookup(existingUser(true, false));

    AdminLookupResponseDTO result = tenantService.lookupAdminByEmail(" CO.SAN@x.vn ", null);

    assertThat(result.isAccountExists()).isTrue();
    assertThat(result.getFullName()).isEqualTo("Có Sẵn");
    assertThat(result.isCanBeAdmin()).isTrue();
    assertThat(result.getReason()).isNull();
  }

  @Test
  void should_refuse_a_locked_account_with_the_same_reason_as_the_assign_error() {
    this.stubExistingUserLookup(existingUser(false, false));

    AdminLookupResponseDTO result = tenantService.lookupAdminByEmail(EXISTING_EMAIL, null);

    assertThat(result.isAccountExists()).isTrue();
    assertThat(result.isCanBeAdmin()).isFalse();
    assertThat(result.getReason()).isEqualTo("Tài khoản đang bị khóa !");
  }

  @Test
  void should_refuse_a_super_admin_account() {
    this.stubExistingUserLookup(existingUser(true, true));

    AdminLookupResponseDTO result = tenantService.lookupAdminByEmail(EXISTING_EMAIL, null);

    assertThat(result.isCanBeAdmin()).isFalse();
    assertThat(result.getReason()).contains("Super Admin").doesNotContain(EXISTING_EMAIL);
  }

  @Test
  void should_throw_not_found_when_looking_up_for_a_missing_tenant() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

    assertAppException(() -> tenantService.lookupAdminByEmail(EXISTING_EMAIL, TENANT_ID), ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  void should_refuse_even_a_new_email_when_the_tenant_is_locked() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(inactiveTenant()));
    when(userRepository.findByEmail("moi@x.vn")).thenReturn(Optional.empty());

    AdminLookupResponseDTO result = tenantService.lookupAdminByEmail("moi@x.vn", TENANT_ID);

    assertThat(result.isAccountExists()).isFalse();
    assertThat(result.isCanBeAdmin()).isFalse();
    assertThat(result.getReason()).contains("Nhà xe đang bị khóa");
  }

  @Test
  void should_report_the_locked_tenant_first_when_both_the_tenant_and_the_account_are_locked() {
    // Luồng gán thật kiểm tra nhà xe bị khóa trước tài khoản, nên lý do tra cứu phải là "nhà xe bị khóa"
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(inactiveTenant()));
    this.stubExistingUserLookup(existingUser(false, false));

    AdminLookupResponseDTO result = tenantService.lookupAdminByEmail(EXISTING_EMAIL, TENANT_ID);

    assertThat(result.isAccountExists()).isTrue();
    assertThat(result.isCanBeAdmin()).isFalse();
    assertThat(result.getReason()).isEqualTo("Nhà xe đang bị khóa, không thể thêm chủ nhà xe !");
  }

  @Test
  void should_refuse_an_account_that_is_already_admin_of_the_tenant() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(activeTenant()));
    this.stubAdminRoleLookup();
    this.stubExistingUserLookup(existingUser(true, false));
    when(userTenantRepository.findByUserIdAndTenantId(EXISTING_USER_ID, TENANT_ID))
      .thenReturn(Optional.of(UserTenant.builder().userId(EXISTING_USER_ID).tenantId(TENANT_ID).roleId(ADMIN_ROLE_ID).build()));

    AdminLookupResponseDTO result = tenantService.lookupAdminByEmail(EXISTING_EMAIL, TENANT_ID);

    assertThat(result.isCanBeAdmin()).isFalse();
    assertThat(result.getReason()).contains("đã là chủ nhà xe").doesNotContain(EXISTING_EMAIL);
  }

  @Test
  void should_refuse_an_account_that_is_staff_of_the_tenant() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(activeTenant()));
    this.stubAdminRoleLookup();
    this.stubExistingUserLookup(existingUser(true, false));
    when(userTenantRepository.findByUserIdAndTenantId(EXISTING_USER_ID, TENANT_ID))
      .thenReturn(Optional.of(UserTenant.builder().userId(EXISTING_USER_ID).tenantId(TENANT_ID).roleId(STAFF_ROLE_ID).build()));

    AdminLookupResponseDTO result = tenantService.lookupAdminByEmail(EXISTING_EMAIL, TENANT_ID);

    assertThat(result.isCanBeAdmin()).isFalse();
    assertThat(result.getReason()).contains("nhân viên");
  }

  @Test
  void should_allow_an_existing_account_that_is_not_yet_a_member_of_the_tenant_and_write_nothing() {
    when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(activeTenant()));
    this.stubExistingUserLookup(existingUser(true, false));
    when(userTenantRepository.findByUserIdAndTenantId(EXISTING_USER_ID, TENANT_ID)).thenReturn(Optional.empty());

    AdminLookupResponseDTO result = tenantService.lookupAdminByEmail(EXISTING_EMAIL, TENANT_ID);

    assertThat(result.isCanBeAdmin()).isTrue();
    verify(userRepository, never()).save(any(User.class));
    verify(userTenantRepository, never()).save(any(UserTenant.class));
  }
}
