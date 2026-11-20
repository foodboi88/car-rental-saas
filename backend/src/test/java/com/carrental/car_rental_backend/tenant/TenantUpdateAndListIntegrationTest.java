package com.carrental.car_rental_backend.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.carrental.car_rental_backend.common.enums.plan_tier.PlanTierTenant;
import com.carrental.car_rental_backend.support.ApiIntegrationTestSupport;
import com.carrental.car_rental_backend.tenant.dto.request.UpdateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.entity.Tenant;
import com.carrental.car_rental_backend.tenant.repository.TenantRepository;
import com.carrental.car_rental_backend.tenant.service.TenantService;

/** Sửa nhà xe (PUT) và danh sách có tìm kiếm, lọc, phân trang. */
class TenantUpdateAndListIntegrationTest extends ApiIntegrationTestSupport {
  @Autowired private TenantRepository tenantRepository;
  @Autowired private TenantService tenantService;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void should_keep_tenant_locked_when_an_older_edit_is_saved_after_lock() throws Exception {
    String suffix = this.newSuffix();
    UUID tenantId = UUID.fromString(this.createTenantAndGetId(this.superAdminToken(), suffix));
    UUID actorId = this.jdbcTemplate.queryForObject(
      "SELECT id FROM users WHERE email = ?", UUID.class, SUPER_ADMIN_EMAIL);
    String updatedName = "Nhà xe đã sửa " + suffix;
    UpdateTenantRequestDTO request = UpdateTenantRequestDTO.builder()
      .name(updatedName).domain(this.domainOf(suffix)).planTier(PlanTierTenant.BASIC).build();

    TransactionTemplate editTransaction = new TransactionTemplate(this.transactionManager);
    TransactionTemplate lockTransaction = new TransactionTemplate(this.transactionManager);
    lockTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    // Hai giao dịch độc lập chạy xen kẽ, không cần sleep hoặc thay đổi bean dùng chung của bộ test.
    editTransaction.executeWithoutResult(editStatus -> {
      Tenant staleTenant = this.tenantRepository.findById(tenantId).orElseThrow();
      assertThat(staleTenant.isActive()).isTrue();

      // Khóa và commit trước khi giao dịch sửa tiếp tục lưu dữ liệu đã đọc từ trước.
      lockTransaction.executeWithoutResult(lockStatus ->
        this.tenantService.updateTenantStatus(tenantId, false, actorId));

      assertThat(staleTenant.isActive()).isTrue(); // Persistence Context cũ chưa được refresh.
      this.tenantService.updateTenant(tenantId, request, actorId);
    });

    assertThat(this.jdbcTemplate.queryForObject(
      "SELECT is_active FROM tenants WHERE id = ?", Boolean.class, tenantId)).isFalse();
    assertThat(this.jdbcTemplate.queryForObject(
      "SELECT name FROM tenants WHERE id = ?", String.class, tenantId)).isEqualTo(updatedName);
  }

  @Test
  void should_reject_contact_email_longer_than_column_and_keep_existing_value() throws Exception {
    String token = this.superAdminToken();
    String suffix = this.newSuffix();
    String tenantId = this.createTenantWithContact(token, suffix);
    Map<String, Object> request = this.requiredFieldsOnly(suffix);
    request.put("contactEmail", "a".repeat(60) + "@" + "b".repeat(63) + "."
      + "c".repeat(63) + "." + "d".repeat(63) + ".eeeee");

    ApiResult result = this.sendJson("PUT", "/api/v1/tenants/" + tenantId, token, request);

    assertThat(result.status()).isEqualTo(400);
    assertThat(result.json().at("/data").has("contactEmail")).isTrue();
    assertThat(this.jdbcTemplate.queryForObject("SELECT contact_email FROM tenants WHERE id = ?::uuid",
      String.class, tenantId)).isEqualTo("lienhe-" + suffix + "@integration.test");
  }

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
  void should_keep_pages_in_stable_order_when_tenants_have_the_same_created_at() throws Exception {
    String token = this.superAdminToken();
    String keyword = "same-time-" + this.newSuffix();
    Timestamp createdAt = Timestamp.from(Instant.parse("2026-10-01T00:00:00Z"));
    List<String> expectedIds = new ArrayList<>();
    for (int index = 1; index <= 25; index++) {
      UUID tenantId = new UUID(0, index);
      this.jdbcTemplate.update(
        "INSERT INTO tenants (id, name, domain, created_at) VALUES (?, ?, ?, ?)",
        tenantId, keyword, keyword + "-" + index, createdAt);
      expectedIds.add(tenantId.toString());
    }
    Collections.reverse(expectedIds);

    // ID lớn hơn nhưng tạo sớm hơn: phải đứng sau nhóm trên vì createdAt vẫn là tiêu chí chính.
    UUID olderTenantId = new UUID(0, 100);
    this.jdbcTemplate.update(
      "INSERT INTO tenants (id, name, domain, created_at) VALUES (?, ?, ?, ?)",
      olderTenantId, keyword, keyword + "-older", Timestamp.from(createdAt.toInstant().minusSeconds(1)));
    expectedIds.add(olderTenantId.toString());

    List<String> actualIds = new ArrayList<>();
    for (int page = 0; page < 3; page++) {
      ApiResult result = this.send("GET",
        "/api/v1/tenants?keyword=" + keyword + "&page=" + page + "&size=10", token, null);
      assertThat(result.status()).isEqualTo(200);
      assertThat(result.json().at("/data/page/totalElements").asInt()).isEqualTo(26);
      result.json().at("/data/content").forEach(tenant -> actualIds.add(tenant.get("id").asText()));
    }

    assertThat(actualIds).containsExactlyElementsOf(expectedIds);
    assertThat(actualIds).doesNotHaveDuplicates();
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
