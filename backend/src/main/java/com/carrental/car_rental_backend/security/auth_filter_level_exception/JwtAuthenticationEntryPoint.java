package com.carrental.car_rental_backend.security.auth_filter_level_exception;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.carrental.car_rental_backend.common.dto.ApiResponse;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
  private final ObjectMapper objectMapper;

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
      throws IOException, ServletException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    ApiResponse<Void> apiResponse = ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), "Bạn cần đăng nhập để thực hiện tác vụ này hoặc Token đã hết hạn !");
    this.objectMapper.writeValue(response.getOutputStream(), apiResponse);
  }
  
}
