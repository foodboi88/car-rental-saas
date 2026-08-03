package com.carrental.car_rental_backend.common.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
  @Builder.Default
  private boolean success = true;

  private T data;

  private String message;

  private ErrorApi error;

  @Builder.Default
  private Instant timestamp = Instant.now();

  public static <T> ApiResponse<T> success(T data) {
    return ApiResponse.<T>builder()
      .data(data)
      .success(true)
      .build();
  }

  public static <T> ApiResponse<T> success(T data, String message) {
    return ApiResponse.<T>builder()
      .data(data)
      .success(true)
      .message(message)
      .build();
  }

  public static <T> ApiResponse<T> error(String code, String message) {
    return ApiResponse.<T>builder()
      .success(false)
      .error(ErrorApi.builder().code(code).message(message).build())
      .build();
  }

  public static <T> ApiResponse<T> error(String code, String message, T data){
    return ApiResponse.<T>builder()
      .success(false)
      .error(ErrorApi.builder().code(code).message(message).build())
      .data(data)
      .build();
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ErrorApi {
    private String code;
    private String message;
  }
}
