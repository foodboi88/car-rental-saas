package com.carrental.car_rental_backend.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.carrental.car_rental_backend.support.ApiIntegrationTestSupport;

/** Sửa nhà xe (PUT) và danh sách có tìm kiếm, lọc, phân trang. */
class TenantUpdateAndListIntegrationTest extends ApiIntegrationTestSupport {

  private Map<String, Object> requiredFieldsOnly(String suffix) {
    Map<String, Object> request = new HashMap<>();
    request.put("name", "Nhà xe đã sửa " + suffix);
    request.put("domain", this.domainOf(suffix));
    request.put("planTier", "PRO");
    return request;
  }

  private String createTenantWithContact(String token, String suffix) throws Exception {
    Map<String, Object> request = this.tenantRequest(suffix);
    request.put("contactEmail", "lienhe-" + suffix + "@integration.test");
    request.put("contactPhone", "0900000001");
    return this.createTenant(token, request).json().at("/data/tenant/id").asText();
  }

  @Test
  void should_keep_optional_fields_when_update_request_omits_them() throws Exception {
    String token = this.superAdminToken();
    String suffix = this.newSuffix();
    String tenantId = this.createTenantWithContact(token, suffix);

    ApiResult result = this.sendJson("PUT", "/api/v1/tenants/" + tenantId, token, this.requiredFieldsOnly(suffix));

    assertThat(result.status()).isEqualTo(200);
    assertThat(result.json().at("/data/name").asText()).isEqualTo("Nhà xe đã sửa " + suffix);
    assertThat(result.json().at("/data/planTier").asText()).isEqualTo("PRO");
    assertThat(result.json().at("/data/contactEmail").asText()).isEqualTo("lienhe-" + suffix + "@integration.test");
    assertThat(result.json().at("/data/contactPhone").asText()).isEqualTo("0900000001");
  }

  @Test
  void should_clear_optional_fields_when_update_sends_blank_strings() throws Exception {
    String token = this.superAdminToken();
    String suffix = this.newSuffix();
    String tenantId = this.createTenantWithContact(token, suffix);
    Map<String, Object> request = this.requiredFieldsOnly(suffix);
    request.put("contactEmail", "");
    request.put("contactPhone", "");

    ApiResult result = this.sendJson("PUT", "/api/v1/tenants/" + tenantId, token, request);

    assertThat(result.status()).isEqualTo(200);
    assertThat(result.json().at("/data/contactEmail").asText("")).isEmpty();
    assertThat(result.json().at("/data/contactPhone").asText("")).isEmpty();
  }

  @Test
  void should_return_409_when_update_uses_domain_of_another_tenant() throws Exception {
    String token = this.superAdminToken();
    String suffix = this.newSuffix();
    String otherSuffix = this.newSuffix();
    this.createTenant(token, otherSuffix);
    String tenantId = this.createTenantAndGetId(token, suffix);
    Map<String, Object> request = this.requiredFieldsOnly(suffix);
    request.put("domain", this.domainOf(otherSuffix));

    ApiResult result = this.sendJson("PUT", "/api/v1/tenants/" + tenantId, token, request);

    assertThat(result.status()).isEqualTo(409);
  }

  @Test
  void should_keep_created_at_and_change_updated_at_when_updating() throws Exception {
    String token = this.superAdminToken();
    String suffix = this.newSuffix();
    ApiResult created = this.createTenant(token, suffix);
    String tenantId = created.json().at("/data/tenant/id").asText();

    ApiResult updated = this.sendJson("PUT", "/api/v1/tenants/" + tenantId, token, this.requiredFieldsOnly(suffix));

    assertThat(updated.json().at("/data/createdAt").asText()).isEqualTo(created.json().at("/data/tenant/createdAt").asText());
    assertThat(updated.json().at("/data/updatedAt").asText()).isNotEqualTo(created.json().at("/data/tenant/updatedAt").asText());
  }

  @Test
  void should_find_only_matching_tenant_when_searching_by_keyword() throws Exception {
    String token = this.superAdminToken();
    String suffix = this.newSuffix();
    this.createTenant(token, suffix);
    this.createTenant(token, this.newSuffix());

    ApiResult result = this.send("GET", "/api/v1/tenants?keyword=" + this.domainOf(suffix), token, null);

    assertThat(result.status()).isEqualTo(200);
    assertThat(result.json().at("/data/page/totalElements").asInt()).isEqualTo(1);
  }

  @Test
  void should_filter_by_active_state_when_listing() throws Exception {
    String token = this.superAdminToken();
    String suffix = this.newSuffix();
    String tenantId = this.createTenantAndGetId(token, suffix);
    this.send("PATCH", "/api/v1/tenants/" + tenantId + "/status", token, "{\"isActive\":false}");
    String search = "/api/v1/tenants?keyword=" + this.domainOf(suffix);

    ApiResult active = this.send("GET", search + "&isActive=true", token, null);
    ApiResult locked = this.send("GET", search + "&isActive=false", token, null);

    assertThat(active.json().at("/data/page/totalElements").asInt()).isZero();
    assertThat(locked.json().at("/data/page/totalElements").asInt()).isEqualTo(1);
  }

  @Test
  void should_cap_page_size_at_100_when_client_asks_for_more() throws Exception {
    ApiResult result = this.send("GET", "/api/v1/tenants?size=500", this.superAdminToken(), null);

    assertThat(result.status()).isEqualTo(200);
    assertThat(result.json().at("/data/page/size").asInt()).isEqualTo(100);
  }

  @Test
  void should_return_404_when_getting_a_tenant_that_does_not_exist() throws Exception {
    ApiResult result = this.send("GET", "/api/v1/tenants/" + MISSING_ID, this.superAdminToken(), null);

    assertThat(result.status()).isEqualTo(404);
  }
}
