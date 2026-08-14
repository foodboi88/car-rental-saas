package com.carrental.car_rental_backend.branch.service;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.carrental.car_rental_backend.branch.dto.BranchDTORequest;
import com.carrental.car_rental_backend.branch.dto.BranchDTORequest.BranchDTORequestToCreate;
import com.carrental.car_rental_backend.branch.dto.BranchDTORequest.BranchDTORequestToUpdate;
import com.carrental.car_rental_backend.branch.dto.BranchDTOResponse;
import com.carrental.car_rental_backend.branch.entity.Branch;
import com.carrental.car_rental_backend.branch.mapper.BranchMapper;
import com.carrental.car_rental_backend.branch.repository.BranchRepository;

import lombok.Builder;

@Builder
@Service
public class BranchService {
    // injection
    @Autowired
    private BranchRepository branchRepository;

    private final BranchMapper branchMapper;

    //danh sách, tìm kiếm 
    public List<BranchDTOResponse> getListBranch(BranchDTORequest branchDTORequest) {
        if (branchDTORequest == null) {
            branchDTORequest = new BranchDTORequest();
        }

        List<Branch> branches = branchRepository.searchBranch(
            branchDTORequest.getName(),
            branchDTORequest.getCode(),
            branchDTORequest.getPhone()
        );
        return branchMapper.getListBranch(branches);
        // return branchRepository
        //         .searchBranch(branchDTORequest.getName(), branchDTORequest.getCode(), branchDTORequest.getPhone())
        //         .stream().map(branch -> BranchDTOResponse.builder()
        //                 .name(branch.getName())
        //                 .email(branch.getEmail()).build())
        //         .toList();
    }

    //thêm mới chi nhánh
    public BranchDTOResponse createBranch(BranchDTORequestToCreate branchDTORequestToCreate) {

        // Branch branch = branchMapper.createBranch(branchDTORequestToCreate);

        Branch branch = Branch.builder().name(branchDTORequestToCreate.getName())
                .tenantId(UUID.fromString("e41492d0-67ee-4699-b311-fee98cb37781"))
                .code(branchDTORequestToCreate.getCode())
                .phone(branchDTORequestToCreate.getPhone())
                .address(branchDTORequestToCreate.getAddress())
                .city(branchDTORequestToCreate.getCity())
                .district(branchDTORequestToCreate.getDistrict())
                .status(1)
                .build();
        Branch savedBranch = branchRepository.save(branch);
        return BranchDTOResponse.builder().name(savedBranch.getName()).email(savedBranch.getEmail()).build();
    }

    //xem chi tiết
    public BranchDTOResponse viewDetail(UUID id){
        Branch branch = branchRepository.findById(id).orElseThrow();
        return BranchDTOResponse.builder().name(branch.getName()).email(branch.getEmail()).build();
    }

    public BranchDTOResponse updateBranch(UUID id, BranchDTORequestToUpdate branchDTORequestToUpdate){
        Branch existingBranch = branchRepository.findById(id).orElseThrow();

        branchMapper.updateBranch(branchDTORequestToUpdate, existingBranch);

        Branch savedBranch = branchRepository.save(existingBranch);
        return branchMapper.toResponse(savedBranch);
    }
}
