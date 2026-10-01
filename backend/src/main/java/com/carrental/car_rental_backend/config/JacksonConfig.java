package com.carrental.car_rental_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class JacksonConfig {
  @Bean
  public ObjectMapper objectMapper() {
      ObjectMapper objectMapper = new ObjectMapper();
      // Đăng ký module để hỗ trợ serialization/deserialization cho các kiểu Java 8 Time (Instant, LocalDateTime,...)
      objectMapper.registerModule(new JavaTimeModule());
      objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      return objectMapper;
  }
}
