package com.carrental.car_rental_backend.auth.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carrental.car_rental_backend.auth.dto.request.LoginRequestDTO;
import com.carrental.car_rental_backend.auth.dto.request.SelectTenantRequestDTO;
import com.carrental.car_rental_backend.auth.dto.request.SwitchBranchRequestDTO;
import com.carrental.car_rental_backend.auth.dto.response.AuthResponseDTO;
import com.carrental.car_rental_backend.auth.dto.response.AuthServiceResult;
import com.carrental.car_rental_backend.auth.service.AuthService;
import com.carrental.car_rental_backend.common.dto.ApiResponse;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.security.principal.UserPrincipal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService authService;

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponseDTO>> login(@Valid @RequestBody LoginRequestDTO loginRequestDTO) {
    AuthServiceResult result = this.authService.login(loginRequestDTO);
    ResponseCookie httpCookie = ResponseCookie.from("refreshToken", result.getRefreshToken())
      .httpOnly(true)
      .secure(true)
      .path("/api/v1/auth") // Chỉ gửi cookie qua các api có Request mapping auth
      .maxAge(7 * 24 * 60 * 60)
      .sameSite("Strict") // Chống tấn công CSRF (Tự động gửi cookie khi vào web có mã độc)
      .build();

    return ResponseEntity.ok()
      .header(HttpHeaders.SET_COOKIE, httpCookie.toString())
      .body(ApiResponse.success(
        AuthResponseDTO.builder()
          .accessToken(result.getAccessToken())
          .availableTenants(result.getAvailableTenants())
          .expiresIn(result.getExpiresIn())
          .user(result.getUser())
          .build(), 
        "Đăng nhập thành công !"
      ));
  }

  @PostMapping("/select-tenant")
  public ResponseEntity<ApiResponse<AuthResponseDTO>> selectTenant(
    @AuthenticationPrincipal UserPrincipal userPrincipal, 
    @Valid @RequestBody SelectTenantRequestDTO selectTenantRequestDTO
  ) {
    if (userPrincipal == null) throw new AppException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập trước !");
    AuthServiceResult result = this.authService.selectTenant(userPrincipal.getId(), selectTenantRequestDTO);

    ResponseCookie httpCookie = ResponseCookie.from("refreshToken", result.getRefreshToken())
      .httpOnly(true)
      .secure(true)
      .path("/api/v1/auth") // Chỉ gửi cookie qua các api có Request mapping auth
      .maxAge(7 * 24 * 60 * 60)
      .sameSite("Strict") // Chống tấn công CSRF (Tự động gửi cookie khi vào web có mã độc)
      .build();

    return ResponseEntity.ok()
      .header(HttpHeaders.SET_COOKIE, httpCookie.toString())
      .body(ApiResponse.success(
        AuthResponseDTO.builder()
          .accessToken(result.getAccessToken())
          .availableTenants(result.getAvailableTenants())
          .expiresIn(result.getExpiresIn())
          .user(result.getUser())
          .build(), 
        "Chọn nhà xe thành công !"
      ));
  }

  @PostMapping("/switch-branch")
  public ResponseEntity<ApiResponse<AuthResponseDTO>> switchBranch(
    @AuthenticationPrincipal UserPrincipal userPrincipal, 
    @Valid @RequestBody SwitchBranchRequestDTO switchBranchRequestDTO
  ) {
    if (userPrincipal == null) throw new AppException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập trước !");
    AuthServiceResult result = this.authService.switchBranch(userPrincipal.getId(), switchBranchRequestDTO);

    ResponseCookie httpCookie = ResponseCookie.from("refreshToken", result.getRefreshToken())
      .httpOnly(true)
      .secure(true)
      .path("/api/v1/auth") // Chỉ gửi cookie qua các api có Request mapping auth
      .maxAge(7 * 24 * 60 * 60)
      .sameSite("Strict") // Chống tấn công CSRF (Tự động gửi cookie khi vào web có mã độc)
      .build();

    return ResponseEntity.ok()
      .header(HttpHeaders.SET_COOKIE, httpCookie.toString())
      .body(ApiResponse.success(
        AuthResponseDTO.builder()
          .accessToken(result.getAccessToken())
          .availableTenants(result.getAvailableTenants())
          .expiresIn(result.getExpiresIn())
          .user(result.getUser())
          .build(),
        "Chọn chi nhánh thành công !"
      ));
  }

  @PostMapping("/refresh-token")
  public ResponseEntity<ApiResponse<AuthResponseDTO>> refreshToken(
    @CookieValue(name = "refreshToken", required = false) String refreshToken
  ) {
    AuthServiceResult result = this.authService.refreshToken(refreshToken);

    ResponseCookie httpCookie = ResponseCookie.from("refreshToken", result.getRefreshToken())
      .httpOnly(true)
      .secure(true)
      .path("/api/v1/auth") // Chỉ gửi cookie qua các api có Request mapping auth
      .maxAge(7 * 24 * 60 * 60)
      .sameSite("Strict") // Chống tấn công CSRF (Tự động gửi cookie khi vào web có mã độc)
      .build();

    return ResponseEntity.ok()
      .header(HttpHeaders.SET_COOKIE, httpCookie.toString())
      .body(ApiResponse.success(
        AuthResponseDTO.builder()
          .accessToken(result.getAccessToken())
          .availableTenants(result.getAvailableTenants())
          .expiresIn(result.getExpiresIn())
          .user(result.getUser())
          .build(),
        "Làm mới token thành công !"
      ));
  }

  @PostMapping("/logout")
  public ResponseEntity<ApiResponse<String>> logout() {
    this.authService.logout();

    ResponseCookie httpCookie = ResponseCookie.from("refreshToken", "")
      .httpOnly(true)
      .secure(true)
      .path("/api/v1/auth")
      .maxAge(0) // Sử dụng để ra lệnh cho client xóa cookie này 
      .sameSite("Strict")
      .build();

    return ResponseEntity.ok()
      .header(HttpHeaders.SET_COOKIE, httpCookie.toString())
      .body(ApiResponse.success("Đã đăng xuất hệ thống !"));
  }
}
