package com.carrental.car_rental_backend.account.service;

import com.carrental.car_rental_backend.account.repository.UserBranchRepository;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.carrental.car_rental_backend.account.dto.UserDTORequest;
import com.carrental.car_rental_backend.account.dto.UserDTOResponse;
import com.carrental.car_rental_backend.account.entity.Role;
import com.carrental.car_rental_backend.account.entity.User;
import com.carrental.car_rental_backend.account.entity.UserBranch;
import com.carrental.car_rental_backend.account.entity.UserTenant;
import com.carrental.car_rental_backend.account.mapper.UserBranchMapper;
import com.carrental.car_rental_backend.account.mapper.UserMapper;
import com.carrental.car_rental_backend.account.mapper.UserTenantMapper;
import com.carrental.car_rental_backend.account.repository.RoleRepository;
import com.carrental.car_rental_backend.account.repository.UserRepository;
import com.carrental.car_rental_backend.account.repository.UserTenantRepository;
import com.carrental.car_rental_backend.common.enums.user.UserRole;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.security.context.TenantContext;
import com.carrental.car_rental_backend.security.principal.UserPrincipal;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserBranchRepository userBranchRepository;
    private final UserTenantRepository userTenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final UserTenantMapper userTenantMapper;
    private final UserBranchMapper userBranchMapper;

    public Page<UserDTOResponse> getListUser(UserDTORequest userDTORequest) {
        if (userDTORequest == null) {
            userDTORequest = new UserDTORequest();
        }

        //Bố sung thêm tenantId để lấy chính xác danh sách user
        UUID tenantId = TenantContext.getTenantId();

        int pageIndex = userDTORequest.getPageIndex() != null ? userDTORequest.getPageIndex() : 0;
        int pageSize = userDTORequest.getPageSize() != null ? userDTORequest.getPageSize() : 10;
        Pageable pageable = PageRequest.of(pageIndex, pageSize);

        Page<User> users = userRepository.searchUser(
            tenantId,
            userDTORequest.getFullname(),
            userDTORequest.getPhone(),
            pageable
        );
        return users.map(user -> userMapper.toResponse(user));
    }

    @Transactional
    public UserDTOResponse createUserBranch(UserDTORequest.UserDTORequestToCreate requestCreate){
        //Kiểm tra email trùng lặp
        if(userRepository.existsByEmail(requestCreate.getEmail())){
            throw new AppException(ErrorCode.BAD_REQUEST, "Email đã tồn tại");
        }
        if(requestCreate.getRole() == UserRole.STAFF && (requestCreate.getBranchIds() == null || requestCreate.getBranchIds().isEmpty())){
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Nhân viên phải được gán vào ít nhất 1 chi nhánh");
        }
        //Khởi tạo 1 đối tượng user mới, sau đó sử dụng mapper để map các thông tin tương ứng
        User user = userMapper.toEntity(requestCreate);
        User savedUser = userRepository.save(user);

        UUID tenantId = TenantContext.getTenantId();
        //Lấy ra role từ request
        String roleCode = requestCreate.getRole().name();
        //tìm kiếm xem có role tương ứng hay không
        Role role = roleRepository.findByTenantIdAndCode(tenantId, roleCode)
            .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR));
        
        UUID roleId = role.getId();
        UserTenant userTenant = userTenantMapper.toUserTenant(tenantId, savedUser.getId(), roleId);
        userTenantRepository.save(userTenant);

        if (requestCreate.getRole() == UserRole.STAFF && requestCreate.getBranchIds() != null && !requestCreate.getBranchIds().isEmpty()) {
            List<UserBranch> userBranch = userBranchMapper.toEntities(tenantId, savedUser.getId(), requestCreate.getBranchIds());
            userBranchRepository.saveAll(userBranch);
        }
        return userMapper.toResponse(savedUser);
    }

    // Sử dụng Transactional để đảm bảo nếu bị lõi sẽ rollback lại thao tác
    @Transactional 
    public UserDTOResponse updateUser(UUID id, UserDTORequest.UserDTORequestToUpdate requestUpdate){
        // Kiểm tra tài khoản cần update có thuộc tenant hiện tại không
        //Kiểm tra xem user có tồn tại không
        User existUser = userRepository.findByIdAndIsActiveTrue(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Tài khoản không tồn tại"));
        UUID tenantId = TenantContext.getTenantId();
        //tìm bản ghi tương với với điều kiện tìm kiếm theo userId và tenantId
        UserTenant userTenant = userTenantRepository.findByUserIdAndTenantId(id, tenantId).orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR, "Tài khoản không thuộc về Tenant này"));
        //Nếu có, tiến hành sử dụng mapper để map lại các thông tin cần cập nhật
        userMapper.updateUser(requestUpdate, existUser);
        //Lưu thông tin vào DB
        User updateUser = userRepository.save(existUser);
        //Nếu cần update role
        if(requestUpdate.getRole() != null){
            // tìm role có trong tenant hiện tại
            Role role = roleRepository.findByTenantIdAndCode(tenantId, requestUpdate.getRole().name()).orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR));
            //Lấy ra id của role đó
            UUID roleId = role.getId();
            //Cập nhật role mới
            userTenant.setRoleId(roleId);
            userTenantRepository.save(userTenant);
        }
        //update thông tin branch user được làm việc 
        if(requestUpdate.getBranchIds() != null){
            userBranchRepository.deleteByTenantIdAndUserId(tenantId, id);
            if(!requestUpdate.getBranchIds().isEmpty()){
                List<UserBranch> newBranches = userBranchMapper.toEntities(tenantId, id, requestUpdate.getBranchIds());
                userBranchRepository.saveAll(newBranches);
            }
        }
        //Trả lại thông tin cho client
        return userMapper.toResponse(updateUser);
    }

    @Transactional 
    public void deleteUser(UUID id){
        UUID tenantId = TenantContext.getTenantId();

        UserPrincipal currentUser = (UserPrincipal)
        // lưu trữ phiên làm việc của request hiện tại 
        // getPrincipal trả về danh tính của người gửi token
        // UserPrincipal ép kiểu về class UserPrincipal
        SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(id.equals(currentUser.getId())){
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể tự xóa tài khoản của chính mình");
        }
        User existUser = userRepository.findByIdAndIsActiveTrue(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Tài khoản không tồn tại"));
        // Thu hồi toàn bộ quyền ở các chi nhánh của nhà xe này
        userBranchRepository.deleteByTenantIdAndUserId(tenantId, id);
        // xóa liên kết trong bảng user_tenant
        userTenantRepository.deleteByUserIdAndTenantId(id, tenantId);
        existUser.setIsActive(false);
        userRepository.save(existUser);
    }
}

