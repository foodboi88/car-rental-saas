package com.carrental.car_rental_backend.account.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;

import com.carrental.car_rental_backend.account.dto.UserDTORequest;
import com.carrental.car_rental_backend.account.dto.UserDTOResponse;
import com.carrental.car_rental_backend.account.service.UserService;
import com.carrental.car_rental_backend.common.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;



@RestController
// annotation quét class, chỉ chọn những trường có "final" để đưa vào constructor
@RequiredArgsConstructor
public class UserController {

    // gán giá trị 1 lần duy nhất, không thể gán lại giá trị khác
    private final UserService userService;

    //check user có permission để gọi api hay không, check jwt authen (security context holder)
    @PreAuthorize("hasAuthority('account:read')")
    @GetMapping("/api/v1/users/search")
    public ResponseEntity<ApiResponse<Page<UserDTOResponse>>> getListUser(@ModelAttribute UserDTORequest userDTORequest) {
        return ResponseEntity.ok(ApiResponse.success(userService.getListUser(userDTORequest)));
    }

    @PreAuthorize("hasAuthority('account:create')")
    @PostMapping("/api/v1/users/create")
    public ResponseEntity<ApiResponse<UserDTOResponse>> createUserBranch(
        @Validated(UserDTORequest.CreateGroup.class) @RequestBody UserDTORequest.UserDTORequestToCreate requestCreate) {
        UserDTOResponse responseCreate = userService.createUserBranch(requestCreate);
        return ResponseEntity.ok(ApiResponse.success(responseCreate));
    }

    // sử dụng PATCH để update: Cập nhật 1 phần
    // gửi những trường cần thay đổi, những trường không thay đổi thì giữ nguyên dữ liệu cũ
    @PreAuthorize("hasAuthority('account:update')")
    @PatchMapping ("/api/v1/users/update/{id}")
    public ResponseEntity<ApiResponse<UserDTOResponse>> updateUser(
        @Validated(UserDTORequest.UpdateGroup.class)
        @PathVariable UUID id, @RequestBody UserDTORequest.UserDTORequestToUpdate requestUpdate) {
        UserDTOResponse responseUpdate = userService.updateUser(id, requestUpdate);
        return ResponseEntity.ok(ApiResponse.success(responseUpdate));
    }

    @PreAuthorize("hasAuthority('account:delete')")
    @DeleteMapping ("/api/v1/users/delete/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
    
    
}

