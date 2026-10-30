package com.carrental.car_rental_backend.tenant.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.carrental.car_rental_backend.tenant.dto.request.AdminAssignmentRequestDTO;
import com.carrental.car_rental_backend.tenant.dto.request.CreateTenantRequestDTO;

import jakarta.validation.Validation;

class CreateTenantRequestDTOTest {
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 10, 11})
  void should_accept_only_one_to_ten_admins(int count) {
    var admins = IntStream.range(0, count).mapToObj(index -> AdminAssignmentRequestDTO.builder()
      .email("owner" + index + "@example.test").build()).toList();
    var request = CreateTenantRequestDTO.builder().name("Nhà xe thử").domain("test-car").admins(admins).build();

    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var violations = factory.getValidator().validate(request);
      assertThat(violations.isEmpty()).isEqualTo(count >= 1 && count <= 10);
    }
  }
}
