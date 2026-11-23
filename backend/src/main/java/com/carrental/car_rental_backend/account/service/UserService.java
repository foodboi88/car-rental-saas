package com.carrental.car_rental_backend.account.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.carrental.car_rental_backend.account.dto.CreateUserRequestDTO;
import com.carrental.car_rental_backend.account.dto.UpdateUserRequestDTO;
import com.carrental.car_rental_backend.account.dto.UserDetailResponseDTO;
import com.carrental.car_rental_backend.account.dto.UserSummaryResponseDTO;

public interface UserService {

    Page<UserSummaryResponseDTO> getUsers(String keyword, UUID roleId, Boolean isActive, Pageable pageable);

    UserDetailResponseDTO getUserById(UUID userId);
    
    UserDetailResponseDTO createUser(CreateUserRequestDTO request);

    UserDetailResponseDTO updateUser(UUID userId, UpdateUserRequestDTO request);

    void changeUserStatus (UUID userId, Boolean isActive);

    void resetPassword (UUID userId, String newPassword);
}
