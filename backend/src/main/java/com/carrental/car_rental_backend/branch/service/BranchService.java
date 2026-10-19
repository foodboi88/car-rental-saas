package com.carrental.car_rental_backend.branch.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.carrental.car_rental_backend.branch.dto.BranchDTORequest.BranchDTORequestToCreate;
import com.carrental.car_rental_backend.branch.dto.BranchDTORequest.BranchDTORequestToUpdate;
import com.carrental.car_rental_backend.account.repository.UserBranchRepository;
import com.carrental.car_rental_backend.branch.dto.BranchDTORequest;
import com.carrental.car_rental_backend.branch.dto.BranchDTOResponse;
import com.carrental.car_rental_backend.branch.dto.BranchSummaryDTO;
import com.carrental.car_rental_backend.branch.entity.Branch;
import com.carrental.car_rental_backend.branch.mapper.BranchMapper;
import com.carrental.car_rental_backend.branch.repository.BranchRepository;
import com.carrental.car_rental_backend.common.constant.RoleConstant;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.carrental.car_rental_backend.security.context.TenantContext;
import com.carrental.car_rental_backend.security.principal.UserPrincipal;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor 
public class BranchService {
    // injection
    private final BranchRepository branchRepository;
    private final UserBranchRepository userBranchRepository;

    private final BranchMapper branchMapper;

    private UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication != null && authentication.getPrincipal() instanceof UserPrincipal userPrincipal){
            return userPrincipal;
        }
        // khi throw lỗi, GlobalExceptionHandler sẽ bắt lỗi, xử lý lỗi toàn cục
        throw new AppException(ErrorCode.UNAUTHORIZED);
    }
    
    //thêm mới chi nhánh
    @Transactional
    public BranchDTOResponse createBranch(BranchDTORequestToCreate requestToCreate) {
        UUID tenantId = TenantContext.getTenantId();
        if(branchRepository.existsByTenantIdAndName(tenantId, requestToCreate.getName())){
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Tên chi nhánh đã tồn tại");
        }
        //Khởi tạo đối tượng Branch
        Branch branch = branchMapper.toEntity(requestToCreate);
        //tenantId not null, MapSruct không thể tự map tenantId
        branch.setTenantId(tenantId);
        branchRepository.save(branch);
        return branchMapper.toResponse(branch);
    }

    //xem chi tiết branch
    public BranchDTOResponse getBranchDetail(UUID id){
        UUID tenantId = TenantContext.getTenantId();
        // String role = TenantContext.getRole();
        // bổ sung check branch chưa được phân quyền
        UserPrincipal currentUser = getCurrentUser();
        Branch branch = branchRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));
        boolean isAdmin = RoleConstant.SUPER_ADMIN.equals(currentUser.getRoleCode()) || RoleConstant.TENANT_ADMIN.equals(currentUser.getRoleCode());
        if(!isAdmin){
            boolean hasViewAccess = userBranchRepository.existsByTenantIdAndUserIdAndBranchId(tenantId, currentUser.getId(), id);
            if(!hasViewAccess){
                throw new AppException(ErrorCode.FORBIDDEN);
            }
        }

        return branchMapper.toResponse(branch);
    }
    @Transactional
    public BranchDTOResponse updateBranch(UUID id, BranchDTORequestToUpdate requestToUpdate){
        UUID tenantId = TenantContext.getTenantId();
        Branch existsBranch = branchRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));
        if(requestToUpdate.getName() != null && !requestToUpdate.getName().isEmpty()){
            //Kiểm tra trùng tên
            if(branchRepository.existsByTenantIdAndNameAndIdNot(tenantId, requestToUpdate.getName(), id)){
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Đã tồn tại tên chi nhánh");
            }
        }
        branchMapper.updateBranch(requestToUpdate, existsBranch);
        Branch savedBranch = branchRepository.save(existsBranch);
        return branchMapper.toResponse(savedBranch);
    }

    @Transactional 
    public BranchDTOResponse updateBranchStatus(UUID id, boolean isActive){
        UUID tenantId = TenantContext.getTenantId();
        // tìm kiếm branch, kiểm tra xem có tồn tại hay không
        Branch existsBranch = branchRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST));
        // update thông tin active
        existsBranch.setIsActive(isActive);
        // lưu lại thông tin vào DB
        Branch savedBranch = branchRepository.save(existsBranch);
        // trả về cho client
        return branchMapper.toResponse(savedBranch);
    }

    public Page<BranchDTOResponse> searchBranch(BranchDTORequest request) {
    UUID tenantId = TenantContext.getTenantId();
    Pageable pageable = PageRequest.of(request.getPageIndex(), request.getPageSize());
    
    Page<Branch> branchPage = branchRepository.searchBranch(
        tenantId, 
        request.getKeyword(), 
        request.getIsActive(), 
        pageable
    );
        // trả về response cho client
        return branchPage.map(branch -> branchMapper.toResponse(branch));
    }

    public List<BranchSummaryDTO> getActiveBranchesSummary(){
        UUID tenantId = TenantContext.getTenantId();
        List<Branch> activeBranches = branchRepository.findByTenantIdAndIsActiveTrueOrderByNameAsc(tenantId);
        return branchMapper.toSummaryList(activeBranches);
    }
}
