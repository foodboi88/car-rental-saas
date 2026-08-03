package com.carrental.car_rental_backend.common.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.carrental.car_rental_backend.common.dto.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(AppException.class)
  public ResponseEntity<ApiResponse<Void>> handleAppException(AppException appEx) {
    ErrorCode errorCode = appEx.getErrorCode();
    log.warn("AppException: [{}] {}", errorCode.getCode(), appEx.getMessage());
    ApiResponse<Void> response = ApiResponse.error(errorCode.getCode(), appEx.getMessage());
    return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValidException(MethodArgumentNotValidException methodEx) {
    Map<String, String> invalidArguments = new HashMap<String, String>();
    methodEx.getBindingResult().getFieldErrors().forEach(error -> {
      invalidArguments.put(error.getField(), error.getDefaultMessage());
    });
    ApiResponse<Map<String, String>> response = ApiResponse.error(
      ErrorCode.VALIDATION_ERROR.getCode(), 
      ErrorCode.VALIDATION_ERROR.getMessage(), 
      invalidArguments
    );
    return ResponseEntity.badRequest().body(response);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
    log.error("Unhandled Exception: ", ex);
    ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
    ApiResponse<Void> response = ApiResponse.error(errorCode.getCode(), errorCode.getMessage());
    return ResponseEntity.internalServerError().body(response);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException accessEx) {
    log.warn("AccessDeniedException: {}", accessEx.getMessage());
    ErrorCode errorCode = ErrorCode.FORBIDDEN;
    ApiResponse<Void> response = ApiResponse.error(errorCode.getCode(), errorCode.getMessage());
    return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException authEx) {
    log.warn("AuthenticationException: {}", authEx.getMessage());
    ErrorCode errorCode = ErrorCode.UNAUTHORIZED;
    ApiResponse<Void> response = ApiResponse.error(errorCode.getCode(), errorCode.getMessage());
    return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException httpEx) {
    log.warn("HttpMessageNotReadableException: {}", httpEx.getMessage());
    ErrorCode errorCode = ErrorCode.MALFORMED_JSON;
    ApiResponse<Void> response = ApiResponse.error(errorCode.getCode(), errorCode.getMessage());
    return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
  }
}
