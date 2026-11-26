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

    @Transactional (readOnly = true)
    @Override
    public UserDetailResponseDTO getUserById(UUID userId) {
        UUID tenantId = TenantContext.getTenantId();
        if(tenantId == null){
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        UserTenant userTenant = this.userTenantRepository.findByUserIdAndTenantId(userId, tenantId).orElseThrow(() -> 
            new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhân viên trong nhà xe")
        );

        User user = this.userRepository.findById(userId).orElseThrow(() ->
            new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy người dùng")
        );

        Role role = this.roleRepository.findByIdAndTenantId(userTenant.getRoleId(), tenantId).orElseThrow(() ->
            new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vai trò của người dùng")
        );

        List<BranchSummaryDTO> branchDTOs = this.branchRepository
                .findAllAssignedBranchesByUserIdTenantId(userId, tenantId).stream().map(
                        branch -> BranchSummaryDTO.builder().branchId(branch.getId()).branchName(branch.getName())
                                .address(branch.getAddress()).build())
                .toList();
        
        RoleSummaryDTO roleDTO = RoleSummaryDTO.builder().
            id(role.getId()).
            name(role.getName()).
            code(role.getCode()).build();

        UserDetailResponseDTO userDetailDTO = UserDetailResponseDTO.builder(). 
            id(user.getId()). 
            fullname(user.getFullname()). 
            email(user.getEmail()). 
            phone(user.getPhone()). 
            avatarURL(user.getAvatarUrl()). 
            isActive(user.getIsActive()). 
            lastLoginAt(user.getLastLoginAt()). 
            createdAt(user.getCreatedAt()). 
            role(roleDTO). 
            assignedBranches(branchDTOs).
            joinedAt(userTenant.getJoinedAt()).
            version(user.getVersion()).build();
        
        return userDetailDTO;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
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
            for (UUID branchId : uniqueBranchIds) {
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
            fullname(targetUser.getFullname()). 
            email(targetUser.getEmail()). 
            phone(targetUser.getPhone()). 
            avatarURL(targetUser.getAvatarUrl()). 
            isActive(targetUser.getIsActive()). 
            lastLoginAt(targetUser.getLastLoginAt()). 
            createdAt(targetUser.getCreatedAt()). 
            role(roleDTO). 
            assignedBranches(branchDTOs).
            joinedAt(Instant.now()).
            version(targetUser.getVersion()).build();

        return userDetailDTO;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public UserDetailResponseDTO updateUser(UUID userId, UpdateUserRequestDTO request) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentUserId = TenantContext.getUserId();

        if(tenantId == null){
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        if(userId.equals(currentUserId) && Boolean.FALSE.equals(request.getIsActive())){
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể tự vô hiệu hóa tài khoản của chính mình");
        }

        UserTenant userTenant = this.userTenantRepository.findByUserIdAndTenantId(userId, tenantId).orElseThrow(() -> 
            new AppException(ErrorCode.BAD_REQUEST)
        );

        User user = this.userRepository.findById(userId).orElseThrow(() ->
            new AppException(ErrorCode.BAD_REQUEST)
        );

        boolean checkVer = java.util.Objects.equals(user.getVersion(), request.getVersion());
        if(!checkVer){
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        Role role = this.roleRepository.findByIdAndTenantId(request.getRoleId(), tenantId).orElseThrow(() ->
            new AppException(ErrorCode.BAD_REQUEST)
        );

        if (request.getBranchIds() != null && !request.getBranchIds().isEmpty()) {
            Set<UUID> uniqueBranchIds = new HashSet<>(request.getBranchIds());
            long validCount = this.branchRepository.countByIdInAndTenantId(request.getBranchIds(), tenantId);
            if (validCount != uniqueBranchIds.size()) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Một hoặc nhiều chi nhánh không hợp lệ hoặc không thuộc nhà xe của bạn");
            }
        }

        if(request.getIsActive() == false){
            user.setCurrentSessionId(null);
        }

        user.setFullname(request.getFullname());
        user.setPhone(request.getPhone());
        user.setAvatarUrl(request.getAvatarUrl());
        user.setIsActive(request.getIsActive());
        
        user = this.userRepository.saveAndFlush(user);

        userTenant.setRoleId(request.getRoleId());
        this.userTenantRepository.save(userTenant);

        this.userBranchRepository.deleteByTenantIdAndUserId(tenantId, userId);
        this.userBranchRepository.flush();

        if (request.getBranchIds() != null && !request.getBranchIds().isEmpty()) {
            Set<UUID> uniqueBranchIds = new HashSet<>(request.getBranchIds());

            List<UserBranch> userBranches = new ArrayList<>();
            for (UUID ids : uniqueBranchIds) {
                UserBranch userBranch = UserBranch.builder().
                    tenantId(tenantId).
                    userId(userId).
                    branchId(ids).
                    status(UserBranchStatus.ACTIVE).
                    startedAt(Instant.now()).build();
                userBranches.add(userBranch);
            }
            this.userBranchRepository.saveAll(userBranches);
        }

        List<BranchSummaryDTO> branchDTOs = this.branchRepository
                .findAllAssignedBranchesByUserIdTenantId(userId, tenantId).stream()
                .map(branch -> BranchSummaryDTO.builder().
                    branchId(branch.getId()).
                    branchName(branch.getName()).
                    address(branch.getAddress()).build()).toList();
                    
        RoleSummaryDTO roleDTO = RoleSummaryDTO.builder().
            id(role.getId()).
            name(role.getName()). 
            code(role.getCode()).build();

        UserDetailResponseDTO userDetailDTO = UserDetailResponseDTO.builder(). 
            id(userId). 
            email(user.getEmail()).  
            fullname(user.getFullname()). 
            phone(user.getPhone()). 
            avatarURL(user.getAvatarUrl()). 
            isActive(user.getIsActive()). 
            role(roleDTO).
            assignedBranches(branchDTOs). 
            joinedAt(userTenant.getJoinedAt()). 
            lastLoginAt(user.getLastLoginAt()). 
            createdAt(user.getCreatedAt()). 
            version(user.getVersion())
            .build();
            
        return userDetailDTO;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void changeUserStatus(UUID userId, Boolean isActive) {
        UUID tenantId = TenantContext.getTenantId();
        UUID currentUserId = TenantContext.getUserId();

        if(tenantId == null){
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        if(userId.equals(currentUserId) && Boolean.FALSE.equals(isActive)){
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể tự vô hiệu hóa tài khoản của chính mình");
        }

        boolean exiestUserTenant = this.userTenantRepository.existsByUserIdAndTenantId(userId, tenantId);
        if(!exiestUserTenant){
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhân viên trong nhà xe");
        }

        User user = this.userRepository.findById(userId).orElseThrow(() -> 
            new AppException(ErrorCode.RESOURCE_NOT_FOUND)
        );

        user.setIsActive(isActive);
        if(Boolean.FALSE.equals(isActive)){
            user.setCurrentSessionId(null);
        }
        this.userRepository.save(user);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void resetPassword(UUID userId, String newPassword) {
        UUID tenantId = TenantContext.getTenantId();
        if(tenantId == null){
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        boolean exiestUserTenant = this.userTenantRepository.existsByUserIdAndTenantId(userId, tenantId);
        if(!exiestUserTenant){
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy nhân viên trong nhà xe");
        }
        if(this.userTenantRepository.findByUserId(userId).size() > 1){
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        
        User user = this.userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));
        if(Boolean.TRUE.equals(user.getIsSuperAdmin())){
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        user.setPasswordHash(this.passwordEncoder.encode(newPassword));
        user.setCurrentSessionId(null);
        this.userRepository.save(user);

    }
}
