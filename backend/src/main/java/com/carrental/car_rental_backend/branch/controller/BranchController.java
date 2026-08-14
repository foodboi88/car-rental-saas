package com.carrental.car_rental_backend.branch.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import com.carrental.car_rental_backend.branch.dto.BranchDTORequest;
import com.carrental.car_rental_backend.branch.dto.BranchDTOResponse;
import com.carrental.car_rental_backend.branch.dto.BranchDTORequest.BranchDTORequestToCreate;
import com.carrental.car_rental_backend.branch.entity.Branch;
// import com.carrental.car_rental_backend.branch.entity.Branch;
import com.carrental.car_rental_backend.branch.service.BranchService;
import com.carrental.car_rental_backend.common.dto.ApiResponse;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
public class BranchController {

    // inject service để sử dụng
    @Autowired
    private BranchService branchService;

    // ResponseEntity địnhh nghĩa http code
    // ApiResponse cấu hình định dạng data sẽ trả về cho client
    // ModelAttribute khi sử dụng annotation này sẽ giúp lấy các tham số trên url
    // và gọi các hàm set tương ứng từ @Data
    @GetMapping("/api/v1/branchs/search")
    public ResponseEntity<ApiResponse<List<BranchDTOResponse>>> getListBranch(
            @Valid @ModelAttribute BranchDTORequest branchDTORequest) {
        return ResponseEntity.ok().body(ApiResponse.success(branchService.getListBranch(branchDTORequest)));
    }

    @PostMapping("/api/v1/branchs/create")
    public ResponseEntity<ApiResponse<BranchDTOResponse>> createBranch(
            @RequestBody @Validated(BranchDTORequest.CreateGroup.class) BranchDTORequest.BranchDTORequestToCreate branchDTORequestToCreate) {
        return ResponseEntity.ok().body(ApiResponse.success(branchService.createBranch(branchDTORequestToCreate)));
    }

    // PathVariable lấy dữ liệu nằm trên đường dẫn
    @GetMapping("/api/v1/branchs/{id}")
    public ResponseEntity<ApiResponse<BranchDTOResponse>> detailBranch(@PathVariable UUID id) {
        BranchDTOResponse branchDtoResponse = branchService.viewDetail(id);
        return ResponseEntity.ok().body(ApiResponse.success(branchDtoResponse));
    }

    @PostMapping("/api/v1/branchs/update/{id}")
    public BranchDTOResponse updateBranch(@Validated(BranchDTORequest.UpdateGroup.class) @PathVariable UUID id, BranchDTORequest.BranchDTORequestToUpdate branchDTORequestToUpdate) {
        return branchService.updateBranch(id, branchDTORequestToUpdate);
    }
    

}
