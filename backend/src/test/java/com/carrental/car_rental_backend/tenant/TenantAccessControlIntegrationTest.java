package com.carrental.car_rental_backend.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.carrental.car_rental_backend.support.ApiIntegrationTestSupport;

/** Mọi API của tenant chỉ dành cho Super Admin: không token thì 401, chủ nhà xe thì 403. */
class TenantAccessControlIntegrationTest extends ApiIntegrationTestSupport {
  private static final String BASE = "/api/v1/tenants";
  // Body hợp lệ vì Spring kiểm tra dạng body trước khi kiểm tra quyền
  private static final String CREATE_BODY = "{\"name\":\"X\",\"domain\":\"x-x\",\"admins\":[{\"email\":\"a@integration.test\",\"fullName\":\"A\",\"password\":\"Matkhau123\"}]}";
  private static final String UPDATE_BODY = "{\"name\":\"X\",\"domain\":\"x-x\",\"planTier\":\"FREE\"}";
  private static final String STATUS_BODY = "{\"isActive\":false}";
  private static final String ADMIN_BODY = "{\"email\":\"a@integration.test\",\"fullName\":\"A\",\"password\":\"Matkhau123\"}";

  static Stream<Arguments> everyTenantEndpoint() {
    return Stream.of(
      Arguments.of("POST", BASE, CREATE_BODY),
      Arguments.of("GET", BASE, null),
      Arguments.of("GET", BASE + "/" + MISSING_ID, null),
      Arguments.of("PUT", BASE + "/" + MISSING_ID, UPDATE_BODY),
      Arguments.of("PATCH", BASE + "/" + MISSING_ID + "/status", STATUS_BODY),
      Arguments.of("DELETE", BASE + "/" + MISSING_ID, null),
      Arguments.of("GET", BASE + "/" + MISSING_ID + "/admins", null),
      Arguments.of("POST", BASE + "/" + MISSING_ID + "/admins", ADMIN_BODY));
  }

  @ParameterizedTest(name = "{0} {1} khong token")
  @MethodSource("everyTenantEndpoint")
  void should_return_401_when_no_token(String method, String path, String body) throws Exception {
    ApiResult result = this.send(method, path, null, body);

    assertThat(result.status()).isEqualTo(401);
  }

  @ParameterizedTest(name = "{0} {1} chu nha xe")
  @MethodSource("everyTenantEndpoint")
  void should_return_403_when_caller_is_tenant_admin(String method, String path, String body) throws Exception {
    String suffix = this.newSuffix();
    this.createTenant(this.superAdminToken(), suffix);

    ApiResult result = this.send(method, path, this.ownerToken(suffix), body);

    assertThat(result.status()).isEqualTo(403);
  }

  @Test
  void should_not_change_anything_when_tenant_admin_tries_to_lock_another_tenant() throws Exception {
    String token = this.superAdminToken();
    String attackerSuffix = this.newSuffix();
    this.createTenant(token, attackerSuffix);
    String victimId = this.createTenantAndGetId(token, this.newSuffix());

    this.send("PATCH", BASE + "/" + victimId + "/status", this.ownerToken(attackerSuffix), STATUS_BODY);

    assertThat(this.count("SELECT count(*) FROM tenants WHERE id = ?::uuid AND is_active = true", victimId)).isEqualTo(1);
  }
}
