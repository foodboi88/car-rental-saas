package com.carrental.car_rental_backend.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.support.ApiIntegrationTestSupport;
import com.carrental.car_rental_backend.tenant.dto.request.AdminAssignmentRequestDTO;
import com.carrental.car_rental_backend.tenant.entity.Tenant;
import com.carrental.car_rental_backend.tenant.repository.TenantRepository;
import com.carrental.car_rental_backend.tenant.service.TenantAdminService;
import com.carrental.car_rental_backend.tenant.service.TenantAdminService.ValidatedAdmin;

/** Kiểm tra quan hệ xuất hiện giữa bước kiểm tra và ghi, cùng rollback khi thêm nhiều chủ. */
class TenantMembershipConcurrencyIntegrationTest extends ApiIntegrationTestSupport {
  @Autowired private TenantRepository tenantRepository;
  @Autowired private TenantAdminService tenantAdminService;

  private UUID roleId(UUID tenantId, String code) {
    return this.jdbcTemplate.queryForObject(
      "SELECT id FROM roles WHERE tenant_id = ? AND code = ?", UUID.class, tenantId, code);
  }

  @ParameterizedTest
  @ValueSource(strings = {"TENANT_ADMIN", "STAFF", "SALE"})
  void should_return_409_and_keep_existing_role_when_adding_an_existing_member(String roleCode) throws Exception {
    String token = this.superAdminToken();
    UUID tenantId = UUID.fromString(this.createTenantAndGetId(token, this.newSuffix()));
    String email = "member-" + this.newSuffix() + "@integration.test";
    this.insertUser(email, OWNER_PASSWORD, true, false);
    UUID userId = this.jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    UUID existingRoleId = this.roleId(tenantId, roleCode);
    this.jdbcTemplate.update(
      "INSERT INTO user_tenants (user_id, tenant_id, role_id) VALUES (?, ?, ?)", userId, tenantId, existingRoleId);

    ApiResult result = this.sendJson("POST", "/api/v1/tenants/" + tenantId + "/admins", token, Map.of("email", email));
    ApiResult check = this.sendJson("POST", "/api/v1/tenants/admin-email-check", token,
      Map.of("email", email, "tenantId", tenantId));

    assertThat(result.status()).isEqualTo(409);
    assertThat(result.json().at("/error/code").asText()).isEqualTo("TENANT_MEMBER_ALREADY_EXISTS");
    assertThat(check.status()).isEqualTo(200);
    assertThat(check.json().at("/data/canBeAdmin").asBoolean()).isFalse();
    assertThat(check.json().at("/data/reason").asText()).isEqualTo("Người dùng đã thuộc nhà xe này");
    assertThat(this.jdbcTemplate.queryForObject(
      "SELECT role_id FROM user_tenants WHERE user_id = ? AND tenant_id = ?", UUID.class, userId, tenantId))
      .isEqualTo(existingRoleId);
  }

  @ParameterizedTest
  @ValueSource(strings = {"TENANT_ADMIN", "STAFF", "SALE"})
  void should_reject_membership_created_after_validation_without_changing_it(String roleCode) throws Exception {
    UUID tenantId = UUID.fromString(this.createTenantAndGetId(this.superAdminToken(), this.newSuffix()));
    Tenant tenant = this.tenantRepository.findById(tenantId).orElseThrow();
    UUID adminRoleId = this.roleId(tenantId, "TENANT_ADMIN");
    UUID competingRoleId = this.roleId(tenantId, roleCode);
    String email = "race-" + this.newSuffix() + "@integration.test";
    this.insertUser(email, OWNER_PASSWORD, true, false);
    UUID userId = this.jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    List<ValidatedAdmin> validated = this.tenantAdminService.validateAdminRequests(
      List.of(AdminAssignmentRequestDTO.builder().email(email).build()), tenant);

    // Một giao dịch khác đã commit quan hệ sau bước kiểm tra, trước bước ghi của yêu cầu này.
    Timestamp joinedAt = Timestamp.from(Instant.parse("2026-10-01T00:00:00Z"));
    this.jdbcTemplate.update(
      "INSERT INTO user_tenants (user_id, tenant_id, role_id, joined_at) VALUES (?, ?, ?, ?)",
      userId, tenantId, competingRoleId, joinedAt);

    assertThatThrownBy(() -> this.tenantAdminService.addAdminsToTenant(tenantId, adminRoleId, validated))
      .isInstanceOfSatisfying(AppException.class, exception ->
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TENANT_MEMBER_ALREADY_EXISTS));

    assertThat(this.count("SELECT count(*) FROM user_tenants WHERE user_id = ? AND tenant_id = ?", userId, tenantId))
      .isEqualTo(1);
    assertThat(this.jdbcTemplate.queryForObject(
      "SELECT role_id FROM user_tenants WHERE user_id = ? AND tenant_id = ?", UUID.class, userId, tenantId))
      .isEqualTo(competingRoleId);
    assertThat(this.jdbcTemplate.queryForObject(
      "SELECT joined_at FROM user_tenants WHERE user_id = ? AND tenant_id = ?", Timestamp.class, userId, tenantId))
      .isEqualTo(joinedAt);
  }

  @Test
  void should_allow_only_one_of_two_concurrent_requests_to_add_the_same_user() throws Exception {
    String token = this.superAdminToken();
    UUID tenantId = UUID.fromString(this.createTenantAndGetId(token, this.newSuffix()));
    String email = "double-add-" + this.newSuffix() + "@integration.test";
    this.insertUser(email, OWNER_PASSWORD, true, false);
    UUID userId = this.jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<ApiResult> request = () -> {
      ready.countDown();
      if (!start.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Hai yêu cầu không bắt đầu đúng thời hạn");
      }
      return this.sendJson("POST", "/api/v1/tenants/" + tenantId + "/admins", token, Map.of("email", email));
    };
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<ApiResult> first = executor.submit(request);
      Future<ApiResult> second = executor.submit(request);
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      List<ApiResult> results = List.of(first.get(40, TimeUnit.SECONDS), second.get(40, TimeUnit.SECONDS));

      assertThat(results).extracting(ApiResult::status).containsExactlyInAnyOrder(201, 409);
      ApiResult rejected = results.stream().filter(result -> result.status() == 409).findFirst().orElseThrow();
      assertThat(rejected.json().at("/error/code").asText()).isEqualTo("TENANT_MEMBER_ALREADY_EXISTS");
      assertThat(this.count("SELECT count(*) FROM user_tenants WHERE user_id = ? AND tenant_id = ?", userId, tenantId))
        .isEqualTo(1);
      assertThat(this.jdbcTemplate.queryForObject(
        "SELECT role_id FROM user_tenants WHERE user_id = ? AND tenant_id = ?", UUID.class, userId, tenantId))
        .isEqualTo(this.roleId(tenantId, "TENANT_ADMIN"));
    } finally {
      start.countDown();
      executor.shutdownNow();
      executor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void should_roll_back_earlier_account_and_membership_when_a_later_membership_conflicts() throws Exception {
    UUID tenantId = UUID.fromString(this.createTenantAndGetId(this.superAdminToken(), this.newSuffix()));
    Tenant tenant = this.tenantRepository.findById(tenantId).orElseThrow();
    UUID adminRoleId = this.roleId(tenantId, "TENANT_ADMIN");
    String newEmail = "rollback-new-" + this.newSuffix() + "@integration.test";
    String existingEmail = "rollback-existing-" + this.newSuffix() + "@integration.test";
    this.insertUser(existingEmail, OWNER_PASSWORD, true, false);
    UUID existingUserId = this.jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, existingEmail);
    List<ValidatedAdmin> validated = this.tenantAdminService.validateAdminRequests(List.of(
      AdminAssignmentRequestDTO.builder().email(newEmail).fullName("Chủ mới").password(OWNER_PASSWORD).build(),
      AdminAssignmentRequestDTO.builder().email(existingEmail).build()), tenant);
    UUID staffRoleId = this.roleId(tenantId, "STAFF");
    this.jdbcTemplate.update(
      "INSERT INTO user_tenants (user_id, tenant_id, role_id) VALUES (?, ?, ?)", existingUserId, tenantId, staffRoleId);

    assertThatThrownBy(() -> this.tenantAdminService.addAdminsToTenant(tenantId, adminRoleId, validated))
      .isInstanceOfSatisfying(AppException.class, exception ->
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TENANT_MEMBER_ALREADY_EXISTS));

    assertThat(this.count("SELECT count(*) FROM users WHERE email = ?", newEmail)).isZero();
    // Chỉ còn chủ ban đầu và quan hệ STAFF do giao dịch khác tạo.
    assertThat(this.count("SELECT count(*) FROM user_tenants WHERE tenant_id = ?", tenantId)).isEqualTo(2);
    assertThat(this.jdbcTemplate.queryForObject(
      "SELECT role_id FROM user_tenants WHERE user_id = ? AND tenant_id = ?", UUID.class, existingUserId, tenantId))
      .isEqualTo(staffRoleId);
  }
}
