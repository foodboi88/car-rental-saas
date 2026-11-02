package com.carrental.car_rental_backend.tenant.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.carrental.car_rental_backend.tenant.dto.request.CreateTenantRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.request.UpdateTenantRequestDTO;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class TenantContactEmailValidationTest {
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

  static Stream<Arguments> contactEmails() {
    return Stream.of(
      Arguments.of(emailWithLength(255), true),
      Arguments.of(emailWithLength(256), false),
      Arguments.of(emailWithLength(263), false),
      Arguments.of("", true),
      Arguments.of((String) null, true));
  }

  private static String emailWithLength(int length) {
    return "a".repeat(60) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "."
      + "d".repeat(63) + "." + "e".repeat(length - 253);
  }

  @ParameterizedTest
  @MethodSource("contactEmails")
  void should_limit_contact_email_on_create_to_database_column_size(String email, boolean valid) {
    var request = CreateTenantRequestDTO.builder().contactEmail(email).build();

    assertThat(validator.validateProperty(request, "contactEmail").isEmpty()).isEqualTo(valid);
  }

  @ParameterizedTest
  @MethodSource("contactEmails")
  void should_limit_contact_email_on_update_to_database_column_size(String email, boolean valid) {
    var request = UpdateTenantRequestDTO.builder().contactEmail(email).build();

    assertThat(validator.validateProperty(request, "contactEmail").isEmpty()).isEqualTo(valid);
  }
}
