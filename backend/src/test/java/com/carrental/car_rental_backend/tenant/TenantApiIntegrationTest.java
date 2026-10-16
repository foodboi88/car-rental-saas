package com.carrental.car_rental_backend.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.carrental.car_rental_backend.support.ApiIntegrationTestSupport;

/** Tạo nhà xe và các luồng gắn với việc tạo: đăng nhập của chủ mới, khóa nhà xe, rollback, kiểm dạng đầu vào. */
class TenantApiIntegrationTest extends ApiIntegrationTestSupport {

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
    assertThat(this.count(
      "SELECT count(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id WHERE r.tenant_id = ?", tenantId))
      .isEqualTo(22);
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
}
