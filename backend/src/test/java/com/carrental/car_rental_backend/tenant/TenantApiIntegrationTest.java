package com.carrental.car_rental_backend.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.carrental.car_rental_backend.support.ApiIntegrationTestSupport;

/** Tạo nhà xe và các luồng gắn với việc tạo: đăng nhập của chủ mới, khóa nhà xe, rollback, kiểm dạng đầu vào. */
class TenantApiIntegrationTest extends ApiIntegrationTestSupport {

  @Test
  void should_reject_password_over_bcrypt_byte_limit_before_creating_any_data() throws Exception {
    String suffix = this.newSuffix();
    Map<String, Object> request = this.tenantRequest(suffix);
    request.put("admins", List.of(this.adminOf(this.ownerEmail(suffix), "Chủ thử", "A1" + "ấ".repeat(30))));

    ApiResult result = this.createTenant(this.superAdminToken(), request);

    assertThat(result.status()).isEqualTo(400);
    assertThat(result.json().at("/error/code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(result.json().at("/data").has("admins[0].password")).isTrue();
    assertThat(this.count("SELECT count(*) FROM tenants WHERE domain = ?", this.domainOf(suffix))).isZero();
    assertThat(this.count("SELECT count(*) FROM users WHERE email = ?", this.ownerEmail(suffix))).isZero();
  }

  @Test
  void should_reject_contact_email_longer_than_database_column_before_create() throws Exception {
    String suffix = this.newSuffix();
    Map<String, Object> request = this.tenantRequest(suffix);
    request.put("contactEmail", "a".repeat(60) + "@" + "b".repeat(63) + "."
      + "c".repeat(63) + "." + "d".repeat(63) + ".eeeee");

    ApiResult result = this.createTenant(this.superAdminToken(), request);

    assertThat(result.status()).isEqualTo(400);
    assertThat(result.json().at("/data").has("contactEmail")).isTrue();
    assertThat(this.count("SELECT count(*) FROM tenants WHERE domain = ?", this.domainOf(suffix))).isZero();
  }

  @Test
  void should_return_401_when_calling_tenants_without_token() throws Exception {
    ApiResult result = this.send("GET", "/api/v1/tenants", null, null);

    assertThat(result.status()).isEqualTo(401);
  }

  @Test
  void should_create_tenant_with_config_roles_and_permissions_when_super_admin_creates_tenant() throws Exception {
    String suffix = this.newSuffix();
    ApiResult result = this.createTenant(this.superAdminToken(), suffix);

    UUID tenantId = UUID.fromString(result.json().at("/data/tenant/id").asText());
    assertThat(result.status()).isEqualTo(201);
    assertThat(this.count("SELECT count(*) FROM tenants WHERE id = ?", tenantId)).isEqualTo(1);
    assertThat(this.count("SELECT count(*) FROM tenant_configs WHERE tenant_id = ?", tenantId)).isEqualTo(1);
    assertThat(this.count("SELECT count(*) FROM roles WHERE tenant_id = ?", tenantId)).isEqualTo(3);
    List<String> allPermissions = this.jdbcTemplate.queryForList("SELECT code FROM permissions", String.class);
    assertThat(this.permissionCodes(tenantId, "TENANT_ADMIN")).containsExactlyInAnyOrderElementsOf(allPermissions);
    assertThat(this.permissionCodes(tenantId, "STAFF")).containsExactlyInAnyOrder(
      "booking:read", "booking:create", "booking:confirm_deposit", "booking:handover",
      "booking:return", "booking:cancel", "vehicle:read");
    assertThat(this.permissionCodes(tenantId, "SALE")).containsExactlyInAnyOrder(
      "booking:read", "booking:create", "booking:cancel", "vehicle:read");
    assertThat(this.count("SELECT count(*) FROM user_tenants WHERE tenant_id = ?", tenantId)).isEqualTo(1);
    assertThat(result.body().toLowerCase()).doesNotContain("password");
  }

  @Test
  void should_return_409_when_domain_already_exists() throws Exception {
    String token = this.superAdminToken();
    String suffix = this.newSuffix();
    this.createTenant(token, suffix);

    ApiResult duplicate = this.createTenant(token, suffix);

    assertThat(duplicate.status()).isEqualTo(409);
  }

  @Test
  void should_let_new_tenant_admin_login_with_all_permissions() throws Exception {
    String suffix = this.newSuffix();
    this.createTenant(this.superAdminToken(), suffix);

    ApiResult login = this.login(this.ownerEmail(suffix), OWNER_PASSWORD);

    assertThat(login.status()).isEqualTo(200);
    assertThat(login.json().at("/data/user/roleCode").asText()).isEqualTo("TENANT_ADMIN");
    assertThat(login.json().at("/data/user/permissions").size())
      .isEqualTo(this.count("SELECT count(*) FROM permissions"));
  }

  @Test
  void should_reject_login_with_403_when_tenant_is_locked() throws Exception {
    String token = this.superAdminToken();
    String suffix = this.newSuffix();
    String tenantId = this.createTenantAndGetId(token, suffix);

    ApiResult lock = this.send("PATCH", "/api/v1/tenants/" + tenantId + "/status", token, "{\"isActive\":false}");
    ApiResult login = this.login(this.ownerEmail(suffix), OWNER_PASSWORD);

    assertThat(lock.status()).isEqualTo(200);
    assertThat(login.status()).isEqualTo(403);
  }

  @Test
  void should_allow_login_again_when_tenant_is_unlocked() throws Exception {
    String token = this.superAdminToken();
    String suffix = this.newSuffix();
    String tenantId = this.createTenantAndGetId(token, suffix);
    this.send("PATCH", "/api/v1/tenants/" + tenantId + "/status", token, "{\"isActive\":false}");

    ApiResult unlock = this.send("PATCH", "/api/v1/tenants/" + tenantId + "/status", token, "{\"isActive\":true}");
    ApiResult login = this.login(this.ownerEmail(suffix), OWNER_PASSWORD);

    assertThat(unlock.status()).isEqualTo(200);
    assertThat(login.status()).isEqualTo(200);
  }

  @Test
  void should_keep_data_and_succeed_when_soft_deleting_tenant_twice() throws Exception {
    String token = this.superAdminToken();
    String suffix = this.newSuffix();
    String tenantId = this.createTenantAndGetId(token, suffix);

    ApiResult first = this.send("DELETE", "/api/v1/tenants/" + tenantId, token, null);
    ApiResult second = this.send("DELETE", "/api/v1/tenants/" + tenantId, token, null);

    assertThat(first.status()).isEqualTo(200);
    assertThat(second.status()).isEqualTo(200);
    assertThat(this.count("SELECT count(*) FROM tenants WHERE id = ?::uuid AND is_active = false", tenantId)).isEqualTo(1);
    assertThat(this.count("SELECT count(*) FROM roles WHERE tenant_id = ?::uuid", tenantId)).isEqualTo(3);
    assertThat(this.count("SELECT count(*) FROM user_tenants WHERE tenant_id = ?::uuid", tenantId)).isEqualTo(1);
  }

  @Test
  void should_write_nothing_when_one_of_the_admins_is_invalid() throws Exception {
    String suffix = this.newSuffix();
    String lockedEmail = "locked-" + suffix + "@integration.test";
    this.insertUser(lockedEmail, OWNER_PASSWORD, false, false);
    Map<String, Object> request = this.tenantRequest(suffix);
    request.put("admins", List.of(
      this.adminOf(this.ownerEmail(suffix), "Chủ hợp lệ", OWNER_PASSWORD),
      Map.of("email", lockedEmail)));

    ApiResult result = this.createTenant(this.superAdminToken(), request);

    assertThat(result.status()).isEqualTo(400);
    assertThat(this.count("SELECT count(*) FROM tenants WHERE domain = ?", this.domainOf(suffix))).isZero();
    assertThat(this.count("SELECT count(*) FROM users WHERE email = ?", this.ownerEmail(suffix))).isZero();
  }

  @Test
  void should_return_400_with_field_errors_when_request_shape_is_wrong() throws Exception {
    String body = "{\"name\":\"\",\"domain\":\"Co Hoa\",\"contactEmail\":\"sai\","
      + "\"admins\":[{\"email\":\"khong-phai-email\",\"password\":\"abc\"}]}";

    ApiResult result = this.send("POST", "/api/v1/tenants", this.superAdminToken(), body);

    assertThat(result.status()).isEqualTo(400);
    assertThat(result.json().at("/data").has("name")).isTrue();
    assertThat(result.json().at("/data").has("domain")).isTrue();
    assertThat(result.json().at("/data").has("contactEmail")).isTrue();
    assertThat(result.json().at("/data").has("admins[0].email")).isTrue();
    assertThat(result.json().at("/data").has("admins[0].password")).isTrue();
  }

  @Test
  void should_return_400_when_admin_list_is_missing() throws Exception {
    ApiResult result = this.send("POST", "/api/v1/tenants", this.superAdminToken(), "{\"name\":\"A\",\"domain\":\"abc\"}");

    assertThat(result.status()).isEqualTo(400);
    assertThat(result.json().at("/data").has("admins")).isTrue();
  }

  @Test
  void should_return_400_when_an_admin_entry_is_null() throws Exception {
    String suffix = this.newSuffix();
    Map<String, Object> request = this.tenantRequest(suffix);
    request.put("admins", java.util.Collections.singletonList(null));

    ApiResult result = this.createTenant(this.superAdminToken(), request);

    assertThat(result.status()).isEqualTo(400);
    assertThat(result.json().at("/error/code").asText()).isEqualTo("VALIDATION_ERROR");
    assertThat(result.json().at("/data").has("admins[0]")).isTrue();
    assertThat(this.count("SELECT count(*) FROM tenants WHERE domain = ?", this.domainOf(suffix))).isZero();
  }

  @Test
  void should_reject_existing_access_token_after_tenant_is_locked() throws Exception {
    String token = this.superAdminToken();
    String suffix = this.newSuffix();
    String tenantId = this.createTenantAndGetId(token, suffix);
    String ownerToken = this.ownerToken(suffix);

    ApiResult lock = this.send("PATCH", "/api/v1/tenants/" + tenantId + "/status", token, "{\"isActive\":false}");
    // Chủ chỉ có một nhà xe nên token đăng nhập đã mang tenantId: bộ lọc JWT từ chối trước khi tới select-tenant (thông báo khác selectTenant).
    ApiResult denied = this.sendJson("POST", "/api/v1/auth/select-tenant", ownerToken, Map.of("tenantId", tenantId));

    assertThat(lock.status()).isEqualTo(200);
    assertThat(denied.status()).isEqualTo(403);
    assertThat(denied.json().at("/error/code").asText()).isEqualTo("AUTH_FORBIDDEN");
    assertThat(denied.json().at("/error/message").asText()).isEqualTo("Nhà xe của bạn đang chọn đã bị khóa");
  }

  @Test
  void should_rollback_all_new_data_when_database_rejects_the_second_admin() throws Exception {
    String token = this.superAdminToken();
    String suffix = this.newSuffix();
    String rejectedEmail = "rejected-" + suffix + "@integration.test";
    String trigger = "reject_admin_" + suffix;
    Map<String, Object> request = this.tenantRequest(suffix);
    request.put("admins", List.of(
      this.adminOf(this.ownerEmail(suffix), "Chủ hợp lệ", OWNER_PASSWORD),
      this.adminOf(rejectedEmail, "Chủ bị DB từ chối", OWNER_PASSWORD)));
    List<String> tables = List.of("tenants", "tenant_configs", "roles", "role_permissions", "user_tenants");
    List<Integer> before = tables.stream().map(table -> this.count("SELECT count(*) FROM " + table)).toList();

    // Chỉ trong DB Testcontainers: gây lỗi CHECK sau khi toàn bộ bước validation đã qua.
    this.jdbcTemplate.execute("CREATE FUNCTION " + trigger + "() RETURNS trigger LANGUAGE plpgsql AS $$ "
      + "BEGIN IF NEW.email = '" + rejectedEmail + "' THEN RAISE EXCEPTION 'test rejection' USING ERRCODE = '23514'; "
      + "END IF; RETURN NEW; END $$");
    try {
      this.jdbcTemplate.execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON users FOR EACH ROW EXECUTE FUNCTION " + trigger + "()");
      ApiResult result = this.createTenant(token, request);

      assertThat(result.status()).isEqualTo(500);
      assertThat(result.json().at("/error/code").asText()).isEqualTo("ERR_500");
      assertThat(tables.stream().map(table -> this.count("SELECT count(*) FROM " + table)).toList())
        .isEqualTo(before);
      assertThat(this.count("SELECT count(*) FROM users WHERE email IN (?, ?)", this.ownerEmail(suffix), rejectedEmail)).isZero();
    } finally {
      this.jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + trigger + " ON users");
      this.jdbcTemplate.execute("DROP FUNCTION " + trigger + "()");
    }
  }

  private List<String> permissionCodes(UUID tenantId, String roleCode) {
    return this.jdbcTemplate.queryForList("""
      SELECT p.code FROM role_permissions rp
      JOIN roles r ON r.id = rp.role_id JOIN permissions p ON p.id = rp.permission_id
      WHERE r.tenant_id = ? AND r.code = ?
      """, String.class, tenantId, roleCode);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/tenants/not-a-uuid", "/api/v1/tenants?isActive=abc",
    "/api/v1/tenants?page=abc", "/api/v1/tenants?size=abc"})
  void should_return_400_when_path_or_query_has_the_wrong_type(String path) throws Exception {
    ApiResult result = this.send("GET", path, this.superAdminToken(), null);

    assertThat(result.status()).isEqualTo(400);
    assertThat(result.json().at("/error/code").asText()).isEqualTo("ERR_400");
  }
}
