package com.carrental.car_rental_backend.branch.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import com.carrental.car_rental_backend.branch.dto.BranchDTORequest;
import com.carrental.car_rental_backend.branch.dto.BranchDTOResponse;
import com.carrental.car_rental_backend.branch.dto.BranchSummaryDTO;

// import com.carrental.car_rental_backend.branch.entity.Branch;
import com.carrental.car_rental_backend.branch.service.BranchService;
import com.carrental.car_rental_backend.common.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequiredArgsConstructor
public class BranchController {

    // inject service để sử dụng
    private final BranchService branchService;

    // ResponseEntity địnhh nghĩa http code
    // ApiResponse cấu hình định dạng data sẽ trả về cho client
    // ModelAttribute khi sử dụng annotation này sẽ giúp lấy các tham số trên url
    // và gọi các hàm set tương ứng từ @Data
    @PreAuthorize("hasAuthority('branch:read')")
    @GetMapping("/api/v1/branchs/search")
    public ResponseEntity<ApiResponse<Page<BranchDTOResponse>>> searchBranch(
            @ModelAttribute BranchDTORequest request) {
        Page<BranchDTOResponse> result = branchService.searchBranch(request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PreAuthorize("hasAuthority('branch:read')")
    @GetMapping("/api/v1/branchs/search/combobox")
    public ResponseEntity<ApiResponse<List<BranchSummaryDTO>>> getComboBoxBranch() {
        List<BranchSummaryDTO> result = branchService.getActiveBranchesSummary();
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    // check mã quyền
    @PreAuthorize("hasAuthority('branch:create')")
    @PostMapping("/api/v1/branchs/create")
    public ResponseEntity<ApiResponse<BranchDTOResponse>> createBranch(
         @RequestBody @Validated(BranchDTORequest.CreateGroup.class) BranchDTORequest.BranchDTORequestToCreate requestToCreate) {
        return ResponseEntity.ok().body(ApiResponse.success(branchService.createBranch(requestToCreate)));
    }

    // PathVariable lấy dữ liệu nằm trên đường dẫn
    @PreAuthorize("hasAuthority('branch:read')")
    @GetMapping("/api/v1/branchs/{id}")
    public ResponseEntity<ApiResponse<BranchDTOResponse>> detailBranch(@PathVariable UUID id) {
        BranchDTOResponse branchDtoResponse = branchService.getBranchDetail(id);
        return ResponseEntity.ok().body(ApiResponse.success(branchDtoResponse));
    }

    @PreAuthorize("hasAuthority('branch:update')")
    @PostMapping("/api/v1/branchs/update/{id}")
    public ResponseEntity<ApiResponse<BranchDTOResponse>> updateBranch(
            @PathVariable UUID id, 
            @Validated(BranchDTORequest.UpdateGroup.class) @RequestBody BranchDTORequest.BranchDTORequestToUpdate branchDTORequestToUpdate) {
        return ResponseEntity.ok(ApiResponse.success(branchService.updateBranch(id, branchDTORequestToUpdate)));
    }
    
    @PreAuthorize("hasAuthority('branch:status')")
    @DeleteMapping("/api/v1/branchs/delete/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBranch(@PathVariable UUID id, Boolean status){
        branchService.updateBranchStatus(id, status);
        return  ResponseEntity.ok().body(ApiResponse.success(null));
    }
}
