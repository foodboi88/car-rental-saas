package com.carrental.car_rental_backend.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.carrental.car_rental_backend.account.entity.User;
import com.carrental.car_rental_backend.tenant.service.TenantAdminService.ValidatedAdmin;

class ValidatedAdminTest {
  private static final String PASSWORD = "ReviewOnly123";

  @Test
  void should_not_print_password_when_new_account_is_converted_to_string() {
    var admin = new ValidatedAdmin("owner@example.test", "Chủ thử", PASSWORD, null);

    assertThat(admin.toString())
      .contains("owner@example.test", "newAccount=true")
      .doesNotContain(PASSWORD);
  }

  @Test
  void should_not_print_password_or_existing_user_when_linking_existing_account() {
    var admin = new ValidatedAdmin("owner@example.test", null, PASSWORD, User.builder().build());

    assertThat(admin.toString())
      .contains("owner@example.test", "newAccount=false")
      .doesNotContain(PASSWORD, "User");
  }
}
