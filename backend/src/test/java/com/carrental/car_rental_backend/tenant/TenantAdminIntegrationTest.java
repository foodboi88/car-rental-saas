package com.carrental.car_rental_backend.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.carrental.car_rental_backend.support.ApiIntegrationTestSupport;

/** Thêm và xem chủ nhà xe của một nhà xe đã có. */
class TenantAdminIntegrationTest extends ApiIntegrationTestSupport {

  private ApiResult addAdmin(String token, String tenantId, Object body) throws Exception {
    return this.sendJson("POST", "/api/v1/tenants/" + tenantId + "/admins", token, body);
  }

  @Test
  void should_create_account_and_link_it_when_adding_admin_with_new_email() throws Exception {
    String token = this.superAdminToken();
    String tenantId = this.createTenantAndGetId(token, this.newSuffix());
    String email = "moi-" + this.newSuffix() + "@integration.test";

    ApiResult result = this.addAdmin(token, tenantId, this.adminOf(email, "Chủ thứ hai", OWNER_PASSWORD));

    assertThat(result.status()).isEqualTo(201);
    assertThat(result.json().at("/data/accountCreated").asBoolean()).isTrue();
    assertThat(this.count("SELECT count(*) FROM user_tenants WHERE tenant_id = ?::uuid", tenantId)).isEqualTo(2);
  }

  @Test
  void should_only_link_account_and_keep_password_and_name_when_email_already_exists() throws Exception {
    String token = this.superAdminToken();
    String existingSuffix = this.newSuffix();
    this.createTenant(token, existingSuffix);
    String tenantId = this.createTenantAndGetId(token, this.newSuffix());
    String existingEmail = this.ownerEmail(existingSuffix);

    ApiResult result = this.addAdmin(token, tenantId, this.adminOf(existingEmail, "Tên khác", "KhacHoanToan999"));

    assertThat(result.status()).isEqualTo(201);
    assertThat(result.json().at("/data/accountCreated").asBoolean()).isFalse();
    assertThat(this.login(existingEmail, OWNER_PASSWORD).status()).isEqualTo(200);
    assertThat(this.login(existingEmail, "KhacHoanToan999").status()).isEqualTo(401);
    assertThat(this.count("SELECT count(*) FROM users WHERE email = ? AND full_name = ?",
      existingEmail, "Chủ nhà xe " + existingSuffix)).isEqualTo(1);
  }

  @Test
  void should_return_409_when_account_is_already_admin_of_this_tenant() throws Exception {
    String token = this.superAdminToken();
    String suffix = this.newSuffix();
    String tenantId = this.createTenantAndGetId(token, suffix);

    ApiResult result = this.addAdmin(token, tenantId, this.adminOf(this.ownerEmail(suffix), "Chủ", OWNER_PASSWORD));

    assertThat(result.status()).isEqualTo(409);
  }

  @Test
  void should_return_400_when_tenant_is_locked() throws Exception {
    String token = this.superAdminToken();
    String tenantId = this.createTenantAndGetId(token, this.newSuffix());
    this.send("PATCH", "/api/v1/tenants/" + tenantId + "/status", token, "{\"isActive\":false}");

    ApiResult result = this.addAdmin(token, tenantId,
      this.adminOf("khoa-" + this.newSuffix() + "@integration.test", "Chủ", OWNER_PASSWORD));

    assertThat(result.status()).isEqualTo(400);
  }

  @Test
  void should_return_400_when_account_is_locked() throws Exception {
    String token = this.superAdminToken();
    String tenantId = this.createTenantAndGetId(token, this.newSuffix());
    String lockedEmail = "bi-khoa-" + this.newSuffix() + "@integration.test";
    this.insertUser(lockedEmail, OWNER_PASSWORD, false, false);

    ApiResult result = this.addAdmin(token, tenantId, this.adminOf(lockedEmail, "Chủ", OWNER_PASSWORD));

    assertThat(result.status()).isEqualTo(400);
  }

  @Test
  void should_return_400_when_account_is_super_admin() throws Exception {
    String token = this.superAdminToken();
    String tenantId = this.createTenantAndGetId(token, this.newSuffix());

    ApiResult result = this.addAdmin(token, tenantId, this.adminOf(SUPER_ADMIN_EMAIL, "Chủ", OWNER_PASSWORD));

    assertThat(result.status()).isEqualTo(400);
  }

  @Test
  void should_list_admins_without_exposing_password() throws Exception {
    String token = this.superAdminToken();
    String suffix = this.newSuffix();
    String tenantId = this.createTenantAndGetId(token, suffix);

    ApiResult result = this.send("GET", "/api/v1/tenants/" + tenantId + "/admins", token, null);

    assertThat(result.status()).isEqualTo(200);
    assertThat(result.json().at("/data").size()).isEqualTo(1);
    assertThat(result.json().at("/data/0/email").asText()).isEqualTo(this.ownerEmail(suffix));
    assertThat(result.body().toLowerCase()).doesNotContain("password");
  }
}
