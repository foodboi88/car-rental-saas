# HƯỚNG DẪN CHI TIẾT TỰ CODE CÁC CẤU PHẦN BASE (SPRING BOOT 3.3.X)
> **Dành cho lập trình viên chuyển từ Angular sang Spring Boot - Dự án Car Rental SaaS**

---

## 📌 Lời Khuyên Cho Lập Trình Viên Từ Angular Chuyển Sang Spring Boot

| Khái niệm Angular | Khái niệm tương đương trong Spring Boot |
| :--- | :--- |
| **HTTP Interceptor** | `OncePerRequestFilter` (vd: `JwtAuthenticationFilter`) |
| **Global ErrorHandler** | `@RestControllerAdvice` + `@ExceptionHandler` |
| **TypeScript Interface / Class DTO** | Java Record / DTO class với Lombok (`@Data`, `@Builder`) |
| **Angular Service (Singleton)** | `@Service` / `@Component` được Spring IoC Container quản lý |
| **Environment.ts** | `application.yml` + `@Value` hoặc `@ConfigurationProperties` |

---

## 🎯 Thứ Tự Thực Hiện Chi Tiết (8 Bước)

```
┌────────────────────────────────────────────────────────┐
│  Bước 1: Cập nhật Dependencies trong pom.xml          │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│  Bước 2: Viết file Cấu hình application.yml            │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│  Bước 3: Xây dựng API Response & Exception Handler     │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│  Bước 4: Tạo TenantContext (ThreadLocal)               │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│  Bước 5: Xây dựng JWT Utility, Filter & SecurityConfig  │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│  Bước 6: Tạo Migration Scripts với Flyway              │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│  Bước 7: Cấu hình Swagger OpenAPI (JWT Support)        │
└──────────────────────────┬─────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────┐
│  Bước 8: Chạy ứng dụng & Kiểm thử                      │
└────────────────────────────────────────────────────────┘
```

---

## 🚀 BƯỚC 1: CẬP NHẬT DEPENDENCIES TRONG `pom.xml`

Trước tiên, mở file `pom.xml` trong thư mục `backend/` và bổ sung 2 nhóm thư viện: **JJWT (để tạo & verify JWT)** và **SpringDoc OpenAPI (Swagger UI)**.

```xml
<dependencies>
    <!-- Các dependency sẵn có: spring-boot-starter-web, spring-boot-starter-data-jpa, postgresql, flyway... -->

    <!-- 1. JJWT (Java JWT Library) -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.6</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.6</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.6</version>
        <scope>runtime</scope>
    </dependency>

    <!-- 2. SpringDoc OpenAPI 3 (Swagger UI cho Spring Boot 3.x) -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.8.5</version>
    </dependency>
</dependencies>
```

> 💡 **Giải thích:**
> * `jjwt-api`, `jjwt-impl`, `jjwt-jackson`: Bộ thư viện chuẩn mã hóa/giải mã JSON Web Token trên Java.
> * `springdoc-openapi-starter-webmvc-ui`: Tự động tạo giao diện Swagger UI tại đường dẫn `/swagger-ui.html` để test API trực quan.

---

## ⚙️ BƯỚC 2: CẤU HÌNH `application.yml`

Tạo hoặc cập nhật file tại đường dẫn: `src/main/resources/application.yml`.

```yaml
server:
  port: 8080
  servlet:
    context-path: /

spring:
  application:
    name: car-rental-backend

  # 1. Cấu hình Cơ sở dữ liệu PostgreSQL
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:car_rental_db}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      idle-timeout: 300000
      connection-timeout: 20000

  # 2. Cấu hình JPA / Hibernate
  jpa:
    hibernate:
      ddl-auto: validate # Không tự động tạo bảng, để Flyway quản lý
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect

  # 3. Cấu hình Flyway Migration
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 0

# 4. Cấu hình JWT Custom Properties
jwt:
  secret: ${JWT_SECRET:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970} # 256-bit Key Secret (BASE64)
  access-token-expiration-ms: 900000 # 15 phút (15 * 60 * 1000)
  refresh-token-expiration-ms: 604800000 # 7 ngày (7 * 24 * 60 * 60 * 1000)

# 5. Cấu hình Swagger / OpenAPI
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    operations-sorter: alpha
    tags-sorter: alpha

# 6. Logging
logging:
  level:
    root: INFO
    com.carrental.car_rental_backend: DEBUG
    org.hibernate.SQL: DEBUG
```

> 💡 **Giải thích:**
> * `${VAR:default}`: Cú pháp đọc biến môi trường, nếu không có thì lấy giá trị mặc định sau dấu `:`.
> * `ddl-auto: validate`: Đảm bảo Hibernate chỉ kiểm tra tính hợp lệ giữa Entity Java và DB, việc tạo/sửa bảng nhường hoàn toàn cho Flyway.

---

## 📦 BƯỚC 3: XÂY DỰNG CHUẨN API RESPONSE & GLOBAL EXCEPTION HANDLER

Tạo package: `com.carrental.car_rental_backend.common`

### 3.1 `ApiResponse.java` (Chuẩn hóa Output trả về cho Frontend)

Tạo file `common/dto/ApiResponse.java`:

```java
package com.carrental.car_rental_backend.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Ẩn trường null khi serialize ra JSON
public class ApiResponse<T> {

    @Builder.Default
    private boolean success = true;

    private String message;

    private T data;

    private ApiError error;

    @Builder.Default
    private Instant timestamp = Instant.now();

    // Helper method trả về thành công kèm data
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    // Helper method trả về thành công kèm data & message
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    // Helper method trả về lỗi
    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(new ApiError(code, message))
                .build();
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ApiError {
        private String code;
        private String message;
    }
}
```

---

### 3.2 `ErrorCode.java` (Enum Mã Lỗi Nghiệp Vụ)

Tạo file `common/exception/ErrorCode.java`:

```java
package com.carrental.car_rental_backend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // System Errors
    INTERNAL_SERVER_ERROR("ERR_500", "Lỗi hệ thống nội bộ", HttpStatus.INTERNAL_SERVER_ERROR),
    UNAUTHORIZED("AUTH_UNAUTHORIZED", "Chưa xác thực hoặc Token không hợp lệ", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("AUTH_FORBIDDEN", "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    BAD_REQUEST("ERR_400", "Dữ liệu yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND("ERR_404", "Không tìm thấy tài nguyên yêu cầu", HttpStatus.NOT_FOUND),

    // Business Custom Errors
    TENANT_ACCESS_DENIED("TENANT_ACCESS_DENIED", "Bạn không thuộc Tenant này", HttpStatus.FORBIDDEN),
    VEHICLE_NOT_AVAILABLE("VEHICLE_NOT_AVAILABLE", "Xe đã có lịch đặt hoặc đang bảo dưỡng", HttpStatus.CONFLICT),
    BOOKING_EXPIRED("BOOKING_EXPIRED", "Thời gian giữ chỗ tạm thời đã hết hạn", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
```

---

### 3.3 `AppException.java` (Custom Exception Class)

Tạo file `common/exception/AppException.java`:

```java
package com.carrental.car_rental_backend.common.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;

    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AppException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
```

---

### 3.4 `GlobalExceptionHandler.java` (Bộ Xử Lý Lỗi Tập Trung)

Tạo file `common/exception/GlobalExceptionHandler.java`:

```java
package com.carrental.car_rental_backend.common.exception;

import com.carrental.car_rental_backend.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice // Tương tự như ErrorHandlerInterceptor của Angular
public class GlobalExceptionHandler {

    // 1. Xử lý AppException do lập trình viên tự ném
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("AppException: [{}] {}", errorCode.getCode(), ex.getMessage());

        ApiResponse<Void> response = ApiResponse.error(errorCode.getCode(), ex.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    // 2. Xử lý Lỗi Validation Form (@Valid / @NotNull / @NotBlank)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        ApiResponse<Map<String, String>> response = ApiResponse.<Map<String, String>>builder()
                .success(false)
                .error(new ApiResponse.ApiError("VALIDATION_ERROR", "Dữ liệu đầu vào không hợp lệ"))
                .data(errors)
                .build();

        return ResponseEntity.badRequest().body(response);
    }

    // 3. Xử lý các ngoại lệ chưa lường trước (Fall-back Error)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled Exception: ", ex);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        ApiResponse<Void> response = ApiResponse.error(errorCode.getCode(), errorCode.getMessage());
        return ResponseEntity.internalServerError().body(response);
    }
}
```

---

## 🔒 BƯỚC 4: TẠO `TenantContext` (THREADLOCAL MULTI-TENANT)

Tạo package: `com.carrental.car_rental_backend.security.context`  
Tạo file `TenantContext.java`:

```java
package com.carrental.car_rental_backend.security.context;

import java.util.UUID;

/**
 * Lưu trữ context thông tin Tenant, Active Branch và Role của request hiện tại trong ThreadLocal.
 * Mọi request đến Spring Boot sẽ được xử lý bởi 1 Thread riêng biệt.
 */
public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_BRANCH = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ROLE = new ThreadLocal<>();

    // --- TENANT ID ---
    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    // --- ACTIVE BRANCH ID ---
    public static void setBranchId(UUID branchId) {
        CURRENT_BRANCH.set(branchId);
    }

    public static UUID getBranchId() {
        return CURRENT_BRANCH.get();
    }

    // --- ROLE ---
    public static void setRole(String role) {
        CURRENT_ROLE.set(role);
    }

    public static String getRole() {
        return CURRENT_ROLE.get();
    }

    // --- CLEAR CONTEXT (BẮT BUỘC ĐỂ TRÁNH THREAD-LEAK TRONG THREAD POOL) ---
    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_BRANCH.remove();
        CURRENT_ROLE.remove();
    }
}
```

> 💡 **Tại sao lại cần `TenantContext` & `clear()`?**  
> Trong Spring Boot (Tomcat Web Server), mỗi HTTP Request được phục vụ bởi một Thread từ ThreadPool. Nếu không gọi `clear()` trong khối `finally` của Filter, thông tin `tenantId` của User A có thể bị rò rỉ sang User B khi Thread đó được tái sử dụng!

---

## 🔑 BƯỚC 5: XÂY DỰNG JWT PROVIDER, FILTER & SECURITY CONFIG

Tạo package: `com.carrental.car_rental_backend.security`

### 5.1 `JwtProvider.java` (Tạo & Giải mã JWT Token)

Tạo file `security/jwt/JwtProvider.java`:

```java
package com.carrental.car_rental_backend.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenExpirationMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration-ms}") long accessTokenExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    // Tạo Access Token
    public String generateAccessToken(UUID userId, String email, String role, UUID tenantId, UUID activeBranchId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpirationMs);

        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key);

        if (tenantId != null) {
            builder.claim("tenant_id", tenantId.toString());
        }

        if (activeBranchId != null) {
            builder.claim("active_branch_id", activeBranchId.toString());
        }

        return builder.compact();
    }

    // Validate và Parse Claims
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            log.error("Invalid JWT Token: {}", ex.getMessage());
        }
        return false;
    }
}
```

---

### 5.2 `JwtAuthenticationFilter.java` (Intercept mọi Request)

Tạo file `security/jwt/JwtAuthenticationFilter.java`:

```java
package com.carrental.car_rental_backend.security.jwt;

import com.carrental.car_rental_backend.security.context.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = parseBearerToken(request);

            if (StringUtils.hasText(token) && jwtProvider.validateToken(token)) {
                Claims claims = jwtProvider.parseClaims(token);

                String userIdStr = claims.getSubject();
                String role = claims.get("role", String.class);
                String tenantIdStr = claims.get("tenant_id", String.class);
                String activeBranchIdStr = claims.get("active_branch_id", String.class);

                // 1. Set thông tin vào TenantContext
                if (tenantIdStr != null) {
                    TenantContext.setTenantId(UUID.fromString(tenantIdStr));
                }
                if (role != null) {
                    TenantContext.setRole(role);
                }
                if (activeBranchIdStr != null) {
                    TenantContext.setBranchId(UUID.fromString(activeBranchIdStr));
                }

                // 2. Set thông tin vào Spring Security Context
                var authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
                var authentication = new UsernamePasswordAuthenticationToken(userIdStr, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            filterChain.doFilter(request, response);

        } finally {
            // RẤT QUAN TRỌNG: Xóa context để giải phóng Thread
            TenantContext.clear();
        }
    }

    private String parseBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
```

---

### 5.3 `SecurityConfig.java` (Cấu hình Phân quyền URL & CORS)

Tạo file `security/config/SecurityConfig.java`:

```java
package com.carrental.car_rental_backend.security.config;

import com.carrental.car_rental_backend.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> {}) // Sử dụng CORS mặc định
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public Endpoints (Không cần đăng nhập)
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Tất cả request khác bắt buộc phải đăng nhập
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
```

---

## 🗄️ BƯỚC 6: TẠO MIGRATION SCRIPTS VỚI FLYWAY

Tạo thư mục: `src/main/resources/db/migration/`

### 6.1 Quy tắc đặt tên file Flyway:
* Cú pháp bắt buộc: `V<Phiên_bản>__<Tên_Mô_tả>.sql` (Chú ý **2 dấu gạch dưới `__`**).
* Ví dụ: `V1__init_schema.sql`, `V2__seed_test_data.sql`.

### 6.2 File `V1__init_schema.sql`

Tạo file `src/main/resources/db/migration/V1__init_schema.sql` (chứa các câu lệnh tạo bảng cốt lõi):

```sql
-- 1. Bảng Users (Identity Tập trung)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    phone VARCHAR(20),
    avatar_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    is_super_admin BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Bảng Tenants (Nhà Xe)
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    domain VARCHAR(255) UNIQUE NOT NULL,
    plan_tier SMALLINT NOT NULL DEFAULT 1 CHECK (plan_tier IN (1, 2, 3, 4)),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Bảng User_Tenants (N-N User & Tenant)
CREATE TABLE user_tenants (
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    role SMALLINT NOT NULL CHECK (role IN (1, 2, 3)), -- 1: TENANT_ADMIN, 2: STAFF, 3: SALE
    PRIMARY KEY (user_id, tenant_id)
);

-- 4. Bảng Branches (Chi Nhánh)
CREATE TABLE branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    address TEXT,
    phone VARCHAR(20),
    is_central BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

---

## 📑 BƯỚC 7: CẤU HÌNH SWAGGER OPENAPI (KÈM NÚT JWT AUTH)

Tạo package: `com.carrental.car_rental_backend.config`  
Tạo file `OpenApiConfig.java`:

```java
package com.carrental.car_rental_backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Car Rental SaaS API Documentation")
                        .version("1.0.0")
                        .description("Tài liệu REST API cho Hệ thống Cho thuê xe tự lái"))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
```

---

## 🧪 BƯỚC 8: THỰC HÀNH KIỂM THỬ THỰC TẾ

Sau khi đã hoàn thành cả 7 bước trên, hãy kiểm tra lại bằng các bước sau:

1. **Chạy PostgreSQL trên Docker hoặc Local:**
   ```bash
   docker run --name postgres-carrental -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=car_rental_db -p 5432:5432 -d postgres:15
   ```
2. **Khởi chạy ứng dụng Spring Boot:**
   ```bash
   ./mvnw spring-boot:run
   ```
3. **Quan sát Logs khi khởi động:**
   * Flyway sẽ tự động quét và chạy file migration `V1__init_schema.sql`.
   * Log hiển thị `Successfully applied 1 migration to schema 'public'`.
4. **Truy cập Swagger UI:**
   * Mở trình duyệt truy cập: `http://localhost:8080/swagger-ui.html`
   * Bạn sẽ thấy nút **"Authorize 🔓"** xuất hiện góc trên bên phải. Nhập JWT Token vào đây để test các API bảo mật!
