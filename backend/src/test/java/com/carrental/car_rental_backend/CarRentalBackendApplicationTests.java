package com.carrental.car_rental_backend;

import org.junit.jupiter.api.Test;
import com.carrental.car_rental_backend.support.ApiIntegrationTestSupport;

/** Context smoke test dùng cùng PostgreSQL Testcontainers, không kết nối DB dev. */
class CarRentalBackendApplicationTests extends ApiIntegrationTestSupport {

	@Test
	void contextLoads() {
	}

}
