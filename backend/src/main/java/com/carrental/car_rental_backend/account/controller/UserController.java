package com.carrental.car_rental_backend.account.controller;

import org.springframework.web.bind.annotation.RestController;

import com.carrental.car_rental_backend.account.dto.CreateUserRequestDTO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;



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
        @PageableDefault(size = 10, sort = "createdAt") Pageable pageable
    ) {
        Page<UserSummaryResponseDTO> listUsers = this.userService.getUsers(keyword, roleId, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.success(listUsers));
    }

    @PostMapping("/api/v1/users")
    public ResponseEntity<ApiResponse<UserDetailResponseDTO>> createUser (
        @Valid 
        @RequestBody CreateUserRequestDTO request
    ) {
        UserDetailResponseDTO newUser = this.userService.createUser(request);
        return ResponseEntity.ok(ApiResponse.success(newUser));
    }
    
    
}
