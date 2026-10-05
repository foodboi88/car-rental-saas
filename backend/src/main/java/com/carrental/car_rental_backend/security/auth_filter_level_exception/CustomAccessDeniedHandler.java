package com.carrental.car_rental_backend.security.auth_filter_level_exception;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
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
public class CustomAccessDeniedHandler implements AccessDeniedHandler{
  private final ObjectMapper objectMapper;

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response,
      AccessDeniedException accessDeniedException) throws IOException, ServletException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    ApiResponse<Void> apiResponse = ApiResponse.error(ErrorCode.FORBIDDEN.getCode(), "Bạn không có quyền sử dụng chức năng này !");
    this.objectMapper.writeValue(response.getOutputStream(), apiResponse);
  }
  
}
