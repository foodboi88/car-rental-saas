package com.carrental.car_rental_backend.tenant.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.carrental.car_rental_backend.tenant.dto.request.AdminAssignmentRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.request.CreateTenantRequestDTO;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class AdminAssignmentRequestDTOTest {
  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void closeValidator() {
    factory.close();
  }

  static Stream<Arguments> passwordByteLimits() {
    return Stream.of(
      Arguments.of("A1" + "ấ".repeat(23) + "a", true), // 72 byte UTF-8
      Arguments.of("A1" + "ấ".repeat(23) + "aa", false), // 73 byte, vẫn dưới 72 ký tự
      Arguments.of("A1" + "ấ".repeat(30), false),
      Arguments.of("A1" + "a".repeat(70), true),
      Arguments.of((String) null, true)); // Hợp lệ khi liên kết tài khoản có sẵn
  }

  @ParameterizedTest
  @MethodSource("passwordByteLimits")
  void should_validate_utf8_bytes_instead_of_only_character_count(String password, boolean valid) {
    var request = AdminAssignmentRequestDTO.builder().email("owner@example.test").password(password).build();

    var violations = validator.validate(request);

    assertThat(violations.isEmpty()).isEqualTo(valid);
    if (!valid) {
      assertThat(violations).allSatisfy(violation -> {
        assertThat(violation.getPropertyPath().toString()).isEqualTo("password");
        assertThat(violation.getMessage()).contains("byte UTF-8");
      });
    }
  }

  @Test
  void should_exclude_password_from_admin_and_nested_tenant_to_string() {
    String password = "ReviewOnly123";
    var admin = AdminAssignmentRequestDTO.builder().email("owner@example.test").password(password).build();
    var tenant = CreateTenantRequestDTO.builder().admins(List.of(admin)).build();

    assertThat(admin.toString()).contains("owner@example.test").doesNotContain(password, "password=");
    assertThat(tenant.toString()).doesNotContain(password, "password=");
  }
}
