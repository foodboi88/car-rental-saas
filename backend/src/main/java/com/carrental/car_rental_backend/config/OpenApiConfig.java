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
        .components(
          new Components().addSecuritySchemes(
            securitySchemeName,
            new SecurityScheme()
              .name(securitySchemeName)
              .type(SecurityScheme.Type.HTTP)
              .scheme("bearer")
              .bearerFormat("JWT")
            )
        );
    }
}