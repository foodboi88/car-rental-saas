package com.carrental.car_rental_backend.account.service;

import com.carrental.car_rental_backend.account.repository.UserBranchRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
import com.carrental.car_rental_backend.branch.entity.Branch;
import com.carrental.car_rental_backend.branch.mapper.BranchMapper;
import com.carrental.car_rental_backend.branch.repository.BranchRepository;
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
    private final BranchRepository branchRepository;
    private final BranchMapper branchMapper;

    public Page<UserDTOResponse> getListUser(UserDTORequest userDTORequest) {
        if (userDTORequest == null) {
            // tạo đối tượng mới với các thông số mặc định để tránh lỗi NullPointerException
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
        //Chặn phân quyền vượt cấp (chỉ cho phép tạo STAFF hoặc SALE)
        if (requestCreate.getRole() != UserRole.STAFF && requestCreate.getRole() != UserRole.SALE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ được phép tạo tài khoản Nhân viên (STAFF) hoặc Cộng tác viên (SALE)");
        }

        UUID tenantId = TenantContext.getTenantId();
        // Validate danh sách chi nhánh (Branch) đối với STAFF
        // tạo 2 mảng rỗng
        List<UUID> uniqueBranchIds = Collections.emptyList();
        List<Branch> validBranches = Collections.emptyList();

        if (requestCreate.getRole() == UserRole.STAFF) {
            if (requestCreate.getBranchIds() == null || requestCreate.getBranchIds().isEmpty()) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Nhân viên phải được gán vào ít nhất 1 chi nhánh");
            }
            //Lọc trùng branchId
            // chuyển danh sách id thành luồng dữ liệu
            // distinct() loại bỏ phần tử trùng lặp dựa trên equals
            // toList() để gom lại thành danh sách mới
            uniqueBranchIds = requestCreate.getBranchIds().stream().distinct().toList();
            //Tìm tất cả chi nhánh theo danh sách ID
            validBranches = branchRepository.findAllById(uniqueBranchIds);
            // Kiểm tra tồn tại đủ các chi nhánh
            if (validBranches.size() != uniqueBranchIds.size()) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Một hoặc nhiều chi nhánh không tồn tại trên hệ thống");
            }
            //Kiểm tra chi nhánh thuộc tenant và đang hoạt động
            for (Branch branch : validBranches) {
                if (!tenantId.equals(branch.getTenantId())) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Chi nhánh '" + branch.getName() + "' không thuộc quyền quản lý của nhà xe");
                }
                if (Boolean.FALSE.equals(branch.getIsActive())) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Chi nhánh '" + branch.getName() + "' hiện đã dừng hoạt động");
                }
            }
        }

        // Xử lý tài khoản User (Kiểm tra case user đã có tài khoản từ trước)
        User savedUser;
        Optional<User> existingUserOpt = userRepository.findByEmail(requestCreate.getEmail());
        // kiểm tra xem email đã tồn tại chưa
        if (existingUserOpt.isPresent()) {
            User existingUser = userRepository.findByEmail(requestCreate.getEmail()).orElse(null);
            if (existingUser != null) {
                if (Boolean.FALSE.equals(existingUser.getIsActive())) {
                    throw new AppException(ErrorCode.BAD_REQUEST,
                            "Tài khoản với email này đã bị vô hiệu hóa trên hệ thống");
                }
                if (userTenantRepository.findByUserIdAndTenantId(existingUser.getId(), tenantId).isPresent()) {
                    throw new AppException(ErrorCode.BAD_REQUEST, "Tài khoản với email này đã tồn tại trong nhà xe");
                }
                savedUser = existingUser;
            } else {
                User newUser = userMapper.toEntity(requestCreate);
                savedUser = userRepository.save(newUser);
            }
        } else {
            // tạo user mới hoàn toàn 
            User newUser = userMapper.toEntity(requestCreate);
            savedUser = userRepository.save(newUser);
        }

        // lấy role tương ứng trong Tenant
        // enum Role kế thừa từ class cha, trong lớp cha đã có 1 hàm return this.name
        String roleCode = requestCreate.getRole().name();
        Role role = roleRepository.findByTenantIdAndCode(tenantId, roleCode)
            .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR, "Vai trò không hợp lệ trong hệ thống"));
        
        //lưu liên kết vào bảng user_tenants
        UUID roleId = role.getId();
        UserTenant userTenant = userTenantMapper.toUserTenant(tenantId, savedUser.getId(), roleId);
        userTenantRepository.save(userTenant);
        // lưu phân công chi nhánh nếu là STAFF
        if (requestCreate.getRole() == UserRole.STAFF) {
            List<UserBranch> userBranches = userBranchMapper.toEntities(tenantId, savedUser.getId(), uniqueBranchIds);
            userBranchRepository.saveAll(userBranches);
        }
        UserDTOResponse response = userMapper.toResponse(savedUser);
        response.setRoleCode(role.getCode());
        response.setRoleName(role.getName());

        if (requestCreate.getRole() == UserRole.STAFF) {
            response.setBranches(branchMapper.toSummaryList(validBranches));
        } else {
            response.setBranches(Collections.emptyList());
        }

        return response;
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
        
        // Kiểm tra trùng email nếu người dùng thay đổi email
        if(requestUpdate.getEmail() != null && !requestUpdate.getEmail().equalsIgnoreCase(existUser.getEmail())){
            if(userRepository.existsByEmailAndIdNot(requestUpdate.getEmail(), id)){
                throw new AppException(ErrorCode.BAD_REQUEST, "Email đã tồn tại");
            }
        }

        //Nếu có, tiến hành sử dụng mapper để map lại các thông tin cần cập nhật
        userMapper.updateUser(requestUpdate, existUser);
        //Lưu thông tin vào DB
        User updateUser = userRepository.save(existUser);
        //Nếu cần update role
        if(requestUpdate.getRole() != null){
            // Chặn phân quyền vượt cấp (chỉ cho phép STAFF hoặc SALE)
            if(requestUpdate.getRole() != UserRole.STAFF && requestUpdate.getRole() != UserRole.SALE){
                throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ được phép phân vai trò Nhân viên (STAFF) hoặc Cộng tác viên (SALE)");
            }
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
            if(!requestUpdate.getBranchIds().isEmpty()){
                // Lọc trùng branchId
                List<UUID> uniqueBranchIds = requestUpdate.getBranchIds().stream().distinct().toList();
                List<Branch> validBranches = branchRepository.findAllById(uniqueBranchIds);
                if(validBranches.size() != uniqueBranchIds.size()){
                    throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Một hoặc nhiều chi nhánh không tồn tại");
                }
                for(Branch branch : validBranches){
                    if(!tenantId.equals(branch.getTenantId())){
                        throw new AppException(ErrorCode.BAD_REQUEST, "Chi nhánh '" + branch.getName() + "' không thuộc quyền quản lý của nhà xe");
                    }
                    if(Boolean.FALSE.equals(branch.getIsActive())){
                        throw new AppException(ErrorCode.BAD_REQUEST, "Chi nhánh '" + branch.getName() + "' hiện đã dừng hoạt động");
                    }
                }
                userBranchRepository.deleteByTenantIdAndUserId(tenantId, id);
                List<UserBranch> newBranches = userBranchMapper.toEntities(tenantId, id, uniqueBranchIds);
                userBranchRepository.saveAll(newBranches);
            } else {
                // Kiểm tra nếu vai trò hiện tại là STAFF thì không được để rỗng chi nhánh
                Role userRole = roleRepository.findById(userTenant.getRoleId()).orElse(null);
                if(userRole != null && "STAFF".equals(userRole.getCode())){
                    throw new AppException(ErrorCode.VALIDATION_ERROR, "Nhân viên phải được gán vào ít nhất 1 chi nhánh");
                }
                userBranchRepository.deleteByTenantIdAndUserId(tenantId, id);
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
        // Kiểm tra xem user có thuộc tenant này không (chặn IDOR)
        userTenantRepository.findByUserIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Tài khoản không thuộc về Tenant này"));

        // Thu hồi toàn bộ quyền ở các chi nhánh của nhà xe này
        userBranchRepository.deleteByTenantIdAndUserId(tenantId, id);
        // xóa liên kết trong bảng user_tenant
        userTenantRepository.deleteByUserIdAndTenantId(id, tenantId);
    }
}

