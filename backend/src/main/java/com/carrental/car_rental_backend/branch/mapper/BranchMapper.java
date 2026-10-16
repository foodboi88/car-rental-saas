package com.carrental.car_rental_backend.branch.mapper;

import java.util.List;
import java.util.UUID;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.carrental.car_rental_backend.branch.dto.BranchDTORequest;
import com.carrental.car_rental_backend.branch.dto.BranchDTOResponse;
import com.carrental.car_rental_backend.branch.dto.BranchSummaryDTO;
import com.carrental.car_rental_backend.branch.entity.Branch;

// báo cho hệ thống biết đây là nơi sinh code 
@Mapper(componentModel = "spring")
public interface BranchMapper {

    // nhận vào 1 đối tượng branch và trả ra BranchDTOResponse
    // tạo ra một đối tượng BranchDTOResponse mới, sau đó lần lượt chạy các hàm 
    // entity.get() và dto.set(). Nếu 2 bên trùng tên trường sẽ tự động map giá trị cho nhau.
    BranchDTOResponse toResponse(Branch entity);

    // Lấy ra danh sách branch
    // List<BranchDTOResponse> getListBranch(List<Branch> entities);

    @Mapping (target = "isActive", constant = "true")
    // Thêm mới branch
    Branch toEntity(BranchDTORequest.BranchDTORequestToCreate dtoRequestToCreate);
    // Branch createBranch(BranchDTORequest.BranchDTORequestToCreate dtoRequestToCreate);

    // Những trường nào trong request có giá trị null thì không tiến hành gán lại giá trị đó
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    // MappingTarget có vai trò biến entity thành đói tượng branch vừa được lấy ra
    // sau đó sẽ tạo và chạy ngầm hàm set để gán dữ liệu mới vào đối tượng branch đã được lấy ra
    void updateBranch(BranchDTORequest.BranchDTORequestToUpdate dtoRequestToUpdate, @MappingTarget Branch entity);

    //chuyển danh sách entity cho dropdown
    BranchSummaryDTO toSummary(Branch entity);
    List<BranchSummaryDTO> toSummaryList(List<Branch> entities);
}
