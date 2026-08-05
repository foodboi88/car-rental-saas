package com.carrental.car_rental_backend.branch.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.carrental.car_rental_backend.branch.dto.BranchDTORequest;
import com.carrental.car_rental_backend.branch.dto.BranchDTOResponse;
// import com.carrental.car_rental_backend.branch.entity.Branch;
import com.carrental.car_rental_backend.branch.service.BranchService;
import com.carrental.car_rental_backend.common.dto.ApiResponse;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
public class BranchController {

    // inject service để sử dụng
    @Autowired
    private BranchService branchService;

    // ResponseEntity địnhh nghĩa http code
    // ApiResponse cấu hình định dạng data sẽ trả về cho client
    @PostMapping("/api/v1/branchs/search")
    public ResponseEntity<ApiResponse<List<BranchDTOResponse>>> getListBranch(
            @Valid @RequestBody BranchDTORequest branchDTORequest) {
        return ResponseEntity.ok().body(ApiResponse.success(branchService.getAllBranch(branchDTORequest)));
    }
    
}
