package com.carrental.car_rental_backend.account.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carrental.car_rental_backend.account.dto.CreateUserRequestDTO;
import com.carrental.car_rental_backend.account.dto.RoleSummaryDTO;
import com.carrental.car_rental_backend.account.dto.UpdateUserRequestDTO;
import com.carrental.car_rental_backend.account.dto.UserDetailResponseDTO;
import com.carrental.car_rental_backend.account.dto.UserSummaryResponseDTO;
import com.carrental.car_rental_backend.account.entity.Role;
import com.carrental.car_rental_backend.account.entity.User;
import com.carrental.car_rental_backend.account.entity.UserBranch;
import com.carrental.car_rental_backend.account.entity.UserTenant;
import com.carrental.car_rental_backend.account.repository.RoleRepository;
import com.carrental.car_rental_backend.account.repository.UserBranchRepository;
import com.carrental.car_rental_backend.account.repository.UserRepository;
import com.carrental.car_rental_backend.account.repository.UserTenantRepository;
import com.carrental.car_rental_backend.auth.dto.response.BranchSummaryDTO;
import com.carrental.car_rental_backend.branch.repository.BranchRepository;
import com.carrental.car_rental_backend.common.enums.user_branch_status.UserBranchStatus;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.security.context.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service 
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService  {

    private final UserRepository userRepository;
    private final UserTenantRepository userTenantRepository;
    private final UserBranchRepository userBranchRepository;
    private final RoleRepository roleRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    @Override 
    public Page<UserSummaryResponseDTO> getUsers(String keyword, UUID roleId, Boolean isActive, Pageable pageable){
        UUID tenantId = TenantContext.getTenantId();

        if(tenantId == null){
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        Page<User> usersPage = this.userRepository.getUserWithFilters(tenantId, keyword, roleId, isActive, pageable);
        return usersPage.map(user -> {
            var roleOpt = this.roleRepository.findRoleByUserIdAndTenantId(user.getId(), tenantId);

            String roleName = roleOpt.map(role -> role.getName()).orElse(null);
            String roleCode = roleOpt.map(role -> role.getCode()).orElse(null);

            int branchCount = this.userBranchRepository.findAllByTenantIdAndUserId(tenantId, user.getId()).size();

            var joinAt = this.userTenantRepository.findByUserIdAndTenantId(user.getId(), tenantId);
            Instant joinedAt = joinAt.map(join -> join.getJoinedAt()).orElse(null);

            return UserSummaryResponseDTO.builder().
                    id(user.getId()).
                    fullname(user.getFullname()).
                    phone(user.getPhone()).
                    isActive(user.getIsActive()).
                    lastLoginAt(user.getLastLoginAt()).
                    roleName(roleName).
                    roleCode(roleCode).
                    joinedAt(joinedAt)
                    .assignedBranchCount(branchCount).build();
        });
    }

    @Override
    public UserDetailResponseDTO getUserById(UUID userId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getUserById'");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDetailResponseDTO createUser(CreateUserRequestDTO request) {
        
        User targetUser;
        UUID tenantId = TenantContext.getTenantId();
        if(tenantId == null){
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        Role existRole = this.roleRepository.findByIdAndTenantId(request.getRoleId(), tenantId).orElseThrow(() ->
            new AppException(ErrorCode.BAD_REQUEST)
        );

        Optional<User> userOpt = this.userRepository.findByEmailIgnoreCase(request.getEmail().trim());
        if(userOpt.isPresent()){
            User existUser = userOpt.get();
            boolean existUserInTenant = this.userTenantRepository.existsByUserIdAndTenantId(existUser.getId(), tenantId);
            if(existUserInTenant){
                throw new AppException(ErrorCode.BAD_REQUEST,"Nhân viên với email này đã tồn tại trong nhà xe");
            }
            targetUser = existUser;

        } else {
            String hash = this.passwordEncoder.encode(request.getPassword());
            User newUser = User.builder().
            email(request.getEmail()).
            passwordHash(hash). 
            fullname(request.getFullName()). 
            phone(request.getPhone()). 
            avatarUrl(request.getAvatarUrl()). 
            isActive(true). 
            isSuperAdmin(false).build();   
            targetUser = this.userRepository.save(newUser);
        }

        UserTenant userTenant = UserTenant.builder().
            userId(targetUser.getId()). 
            tenantId(tenantId). 
            roleId(existRole.getId()). 
            joinedAt(Instant.now()).build();
        this.userTenantRepository.save(userTenant);

        if(request.getBranchIds() != null && !request.getBranchIds().isEmpty()){
            
            Set<UUID> uniqueBranchIds = new HashSet<>(request.getBranchIds());

            long validBranchCount = this.branchRepository.countByIdInAndTenantId(request.getBranchIds(), tenantId);

            if(validBranchCount != uniqueBranchIds.size()){
                throw new AppException(ErrorCode.BAD_REQUEST, "Một hoặc nhiều chi nhánh không hợp lệ hoặc không thuộc nhà xe của bạn");
            }
            
            List<UserBranch> userBranches = new ArrayList<>();
            for (UUID branchId : request.getBranchIds()) {
                UserBranch userBranch = UserBranch.builder(). 
                    tenantId(tenantId). 
                    userId(targetUser.getId()). 
                    branchId(branchId). 
                    status(UserBranchStatus.ACTIVE). 
                    startedAt(Instant.now()).build();

                userBranches.add(userBranch);
            }
            this.userBranchRepository.saveAll(userBranches);
        }

        RoleSummaryDTO roleDTO = RoleSummaryDTO.builder().
            id(existRole.getId()).
            name(existRole.getName()).
            code(existRole.getCode()).build();

        List<BranchSummaryDTO> branchDTOs = this.branchRepository.
            findAllAssignedBranchesByUserIdTenantId(targetUser.getId(), tenantId).
            stream().map(branch -> BranchSummaryDTO.builder().
                    branchId(branch.getId()).
                    branchName(branch.getName()). 
                    address(branch.getAddress()).build()
            ).toList();
            
        UserDetailResponseDTO userDetailDTO = UserDetailResponseDTO.builder(). 
            id(targetUser.getId()). 
            email(targetUser.getEmail()). 
            phone(targetUser.getPhone()). 
            avatarURL(targetUser.getAvatarUrl()). 
            isActive(targetUser.getIsActive()). 
            lastLoginAt(targetUser.getLastLoginAt()). 
            createdAt(targetUser.getCreatedAt()). 
            role(roleDTO). 
            assignedBranches(branchDTOs).
            joinedAt(Instant.now()).build();

        return userDetailDTO;
    }

    @Override
    public UserDetailResponseDTO updateUser(UUID userId, UpdateUserRequestDTO request) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'updateUser'");
    }

    @Override
    public void changeUserStatus(UUID userId, Boolean isActive) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'changeUserStatus'");
    }

    @Override
    public void resetPassword(UUID userId, String newPassword) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'resetPassword'");
    }
}
