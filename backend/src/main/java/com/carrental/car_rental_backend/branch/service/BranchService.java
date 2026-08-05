package com.carrental.car_rental_backend.branch.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.carrental.car_rental_backend.branch.dto.BranchDTORequest;
import com.carrental.car_rental_backend.branch.dto.BranchDTOResponse;
import com.carrental.car_rental_backend.branch.entity.Branch;
import com.carrental.car_rental_backend.branch.repository.BranchRepository;

import lombok.Builder;

@Builder
@Service
public class BranchService {

    //injection
    @Autowired
    private BranchRepository branchRepository;

    // public List<BranchDTOResponse> getAllBranch() {
    //     // lấy ra giá trị từ trong db
    //     List<Branch> listBranch = branchRepository.findAll();
    //     //tạo một danh sách rỗng mới để chứa
    //     List<BranchDTOResponse> branchDTOResponses = new ArrayList<>();
    //     //tạo vòng lặp lấy ra thông tin của tất cả các branch
    //     for (Branch branch : listBranch)  {
    //         BranchDTOResponse dtoResponse = BranchDTOResponse.builder()
    //         .name(branch.getName()).email(branch.getEmail()).build();

    //         branchDTOResponses.add(dtoResponse);
    //     }
    //     return branchDTOResponses;
    // }


    public List<BranchDTOResponse> getAllBranch(BranchDTORequest branchDTORequest){
        //lấy ra tất cả giá trị trong db
        List<Branch> listBranchs = branchRepository.findAll();
        //tạo một danh sách rỗng để lưu data 
        List<BranchDTOResponse> branchDtoResponses = new ArrayList<>();
        //lặp tất cả các branch được lấy ra
        //for([kiểu dữ liệu]  [tham số]:[danh sách cần lặp])
        for (Branch branch : listBranchs) {
            // contains để so sánh gần đúng với tham số truyền vào
            boolean matchName = (branchDTORequest.getName() == null
                    || branch.getName().contains(branchDTORequest.getName()));
            boolean matchEmail = (branchDTORequest.getEmail() == null
                    || branch.getEmail().contains(branchDTORequest.getEmail()));
            if (matchName || matchEmail) {
                // tạo một dto mới, sử dụng builder để lấy ra các thông tin
                BranchDTOResponse dtoResponse = BranchDTOResponse.builder()
                        .name(branch.getName()).email(branch.getEmail()).build();
                // thêm vào danh sách rỗng đã tạo
                branchDtoResponses.add(dtoResponse);
            }
        }
        return branchDtoResponses; 
    }
}
