package com.carrental.car_rental_backend.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Nền chung cho test tích hợp: chạy cả ứng dụng thật (Spring, Security, JPA, Flyway) trên một PostgreSQL
 * thật do Testcontainers dựng, gọi API qua HTTP rồi kiểm tra dữ liệu trong DB.
 * Container chỉ khởi động một lần cho mọi lớp test, nên các lớp dùng chung một ứng dụng và một DB.
 * Mỗi test tự tạo dữ liệu với hậu tố ngẫu nhiên nên không phụ thuộc thứ tự chạy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiIntegrationTestSupport {
  @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:15");

  static {
    POSTGRES.start();
  }

  protected static final String SUPER_ADMIN_EMAIL = "super.admin@integration.test";
  protected static final String SUPER_ADMIN_PASSWORD = "Integration123";
  protected static final String OWNER_PASSWORD = "Matkhau123";
  protected static final String MISSING_ID = "00000000-0000-0000-0000-000000000000";
  private static final int REQUEST_TIMEOUT_SECONDS = 30;

  public record ApiResult(int status, String body, JsonNode json) {}

  @Autowired private Environment environment;
  @Autowired protected JdbcTemplate jdbcTemplate;
  @Autowired protected PasswordEncoder passwordEncoder;
  @Autowired protected ObjectMapper objectMapper;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @BeforeEach
  void seedSuperAdmin() {
    // DB mới chạy migration chưa có Super Admin nên test tự thêm một tài khoản
    this.insertUser(SUPER_ADMIN_EMAIL, SUPER_ADMIN_PASSWORD, true, true);
  }

  protected String newSuffix() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  protected String domainOf(String suffix) {
    return "it-" + suffix;
  }

  protected String ownerEmail(String suffix) {
    return "owner-" + suffix + "@integration.test";
  }

  protected int count(String sql, Object... args) {
    Integer value = this.jdbcTemplate.queryForObject(sql, Integer.class, args);
    return value == null ? 0 : value;
  }

  protected void insertUser(String email, String password, boolean active, boolean superAdmin) {
    this.jdbcTemplate.update(
      "INSERT INTO users (email, password_hash, full_name, is_active, is_super_admin) VALUES (?, ?, ?, ?, ?) ON CONFLICT (email) DO NOTHING",
      email, this.passwordEncoder.encode(password), "Người dùng thử", active, superAdmin);
  }

  protected String superAdminToken() throws Exception {
    return this.login(SUPER_ADMIN_EMAIL, SUPER_ADMIN_PASSWORD).json().at("/data/accessToken").asText();
  }

  protected String ownerToken(String suffix) throws Exception {
    return this.login(this.ownerEmail(suffix), OWNER_PASSWORD).json().at("/data/accessToken").asText();
  }

  protected ApiResult login(String email, String password) throws Exception {
    String body = this.objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    return this.send("POST", "/api/v1/auth/login", null, body);
  }

  protected Map<String, Object> adminOf(String email, String fullName, String password) {
    return Map.of("email", email, "fullName", fullName, "password", password);
  }

  /** Yêu cầu tạo nhà xe hợp lệ, trả về Map sửa được để từng test thêm hoặc đổi trường. */
  protected Map<String, Object> tenantRequest(String suffix) {
    Map<String, Object> request = new HashMap<>();
    request.put("name", "Nhà xe " + suffix);
    request.put("domain", this.domainOf(suffix));
    request.put("planTier", "BASIC");
    request.put("admins", List.of(this.adminOf(this.ownerEmail(suffix), "Chủ nhà xe " + suffix, OWNER_PASSWORD)));
    return request;
  }

  protected ApiResult createTenant(String token, String suffix) throws Exception {
    return this.createTenant(token, this.tenantRequest(suffix));
  }

  protected ApiResult createTenant(String token, Map<String, Object> request) throws Exception {
    return this.send("POST", "/api/v1/tenants", token, this.objectMapper.writeValueAsString(request));
  }

  protected String createTenantAndGetId(String token, String suffix) throws Exception {
    return this.createTenant(token, suffix).json().at("/data/tenant/id").asText();
  }

  protected ApiResult sendJson(String method, String path, String token, Object body) throws Exception {
    return this.send(method, path, token, this.objectMapper.writeValueAsString(body));
  }

  protected ApiResult send(String method, String path, String token, String body) throws Exception {
    String port = this.environment.getProperty("local.server.port");
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
      .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS));
    if (token != null) {
      builder.header("Authorization", "Bearer " + token);
    }
    if (body != null) {
      builder.header("Content-Type", "application/json");
    }
    builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    HttpResponse<String> response = this.httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    JsonNode json = response.body().isBlank() ? this.objectMapper.nullNode() : this.objectMapper.readTree(response.body());
    return new ApiResult(response.statusCode(), response.body(), json);
  }
}
