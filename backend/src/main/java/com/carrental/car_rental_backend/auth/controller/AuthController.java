package com.carrental.car_rental_backend.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carrental.car_rental_backend.auth.dto.request.LoginRequestDTO;
import com.carrental.car_rental_backend.auth.dto.request.RefreshTokenRequestDTO;
import com.carrental.car_rental_backend.auth.dto.request.SelectTenantRequestDTO;
import com.carrental.car_rental_backend.auth.dto.request.SwitchBranchRequestDTO;
import com.carrental.car_rental_backend.auth.dto.response.AuthResponseDTO;
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
    AuthResponseDTO response = this.authService.login(loginRequestDTO);

    return ResponseEntity.ok(
      ApiResponse.success(response, "Đăng nhập thành công !")
    );
  }

  @PostMapping("/select-tenant")
  public ResponseEntity<ApiResponse<AuthResponseDTO>> selectTenant(
    @AuthenticationPrincipal UserPrincipal userPrincipal, 
    @Valid @RequestBody SelectTenantRequestDTO selectTenantRequestDTO
  ) {
    if (userPrincipal == null) throw new AppException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập trước !");
    AuthResponseDTO response = this.authService.selectTenant(userPrincipal.getId(), selectTenantRequestDTO);

    return ResponseEntity.ok(
      ApiResponse.success(response, "Chọn nhà xe thành công !")
    );
  }

  @PostMapping("/switch-branch")
  public ResponseEntity<ApiResponse<AuthResponseDTO>> switchBranch(
    @AuthenticationPrincipal UserPrincipal userPrincipal, 
    @Valid @RequestBody SwitchBranchRequestDTO switchBranchRequestDTO
  ) {
    if (userPrincipal == null) throw new AppException(ErrorCode.UNAUTHORIZED, "Vui lòng đăng nhập trước !");
    AuthResponseDTO response = this.authService.switchBranch(userPrincipal.getId(), switchBranchRequestDTO);

    return ResponseEntity.ok(
      ApiResponse.success(response, "Chọn chi nhánh thành công !")
    );
  }

  @PostMapping("/refresh-token")
  public ResponseEntity<ApiResponse<AuthResponseDTO>> refreshToken(
    @Valid @RequestBody RefreshTokenRequestDTO refreshTokenRequestDTO
  ) {
    AuthResponseDTO response = this.authService.refreshToken(refreshTokenRequestDTO);

    return ResponseEntity.ok(
      ApiResponse.success(response, "Làm mới token thành công !")
    );
  }
}
