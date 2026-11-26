package com.carrental.car_rental_backend.account.controller;

import org.springframework.web.bind.annotation.RestController;

import com.carrental.car_rental_backend.account.dto.ChangeUserStatusRequestDTO;
import com.carrental.car_rental_backend.account.dto.CreateUserRequestDTO;
import com.carrental.car_rental_backend.account.dto.ResetPasswordRequestDTO;
import com.carrental.car_rental_backend.account.dto.UpdateUserRequestDTO;
import com.carrental.car_rental_backend.account.dto.UserDetailResponseDTO;
import com.carrental.car_rental_backend.account.dto.UserSummaryResponseDTO;
import com.carrental.car_rental_backend.account.service.UserService;
import com.carrental.car_rental_backend.common.dto.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('account:manage')")
public class UserController {

    private final UserService userService;

    @GetMapping("/api/v1/users")
    public ResponseEntity<ApiResponse<Page<UserSummaryResponseDTO>>> getListUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        Page<UserSummaryResponseDTO> listUsers = this.userService.getUsers(keyword, roleId, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.success(listUsers));
    }

    @GetMapping("/api/v1/users/{id}")
    public ResponseEntity<ApiResponse<UserDetailResponseDTO>> getUser(@PathVariable UUID id) {
        UserDetailResponseDTO user = this.userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PostMapping("/api/v1/users")
    public ResponseEntity<ApiResponse<UserDetailResponseDTO>> createUser(
            @Valid @RequestBody CreateUserRequestDTO request) {
        UserDetailResponseDTO newUser = this.userService.createUser(request);
        return ResponseEntity.ok(ApiResponse.success(newUser));
    }

    @PutMapping("/api/v1/users/{id}")
    public ResponseEntity<ApiResponse<UserDetailResponseDTO>> updateUser(@PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequestDTO request) {
        UserDetailResponseDTO updateUser = this.userService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.success(updateUser));
    }

    @PatchMapping("/api/v1/users/{id}/status")
    public ResponseEntity<ApiResponse<Void>> changeStatus(@PathVariable UUID id,
            @Valid @RequestBody ChangeUserStatusRequestDTO request) {
        this.userService.changeUserStatus(id, request.getIsActive());
        return ResponseEntity.ok(ApiResponse.success(null, "Cập nhật trạng thái nhân viên thành công"));
    }

    @PostMapping("/api/v1/users/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@PathVariable UUID id,
        @Valid @RequestBody ResetPasswordRequestDTO request
    ) {
        this.userService.resetPassword(id, request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(null, "Đặt lại mật khẩu thành công"));
    }
    
}
