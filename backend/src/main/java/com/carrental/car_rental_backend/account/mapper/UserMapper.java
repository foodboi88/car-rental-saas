package com.carrental.car_rental_backend.account.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.carrental.car_rental_backend.account.dto.UserDTORequest;
import com.carrental.car_rental_backend.account.dto.UserDTOResponse;
import com.carrental.car_rental_backend.account.entity.User;

// MapStruct: chạy lúc complie code, đọc interface sau đó tự tạo ra các getter và setter
// uses = PasswordEncoderMapper.class
// khai báo để sử dụng các logic phức tạp khác trong PasswordEncoderMapper
// khai báo Mapper(componentModel = "spring") để có thể inject để sử dụng
@Mapper(componentModel = "spring", uses = PasswordEncoderMapper.class)
public interface UserMapper {

    UserDTOResponse toResponse(User entity);

    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "isSuperAdmin", constant = "false")
    @Mapping(target = "passwordHash", source = "password", qualifiedByName = "hashPassword")
    User toEntity(UserDTORequest.UserDTORequestToCreate requestToCreate);

    //Update user
    //nếu client gửi lên 1 trường có giá trị null, không tiến hành update, giữ lại giá trị cũ
    @BeanMapping (nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)   
    //mapping target: ghi đè dữ liệu vào existUser
    void updateUser(UserDTORequest.UserDTORequestToUpdate requestToUpdate, @MappingTarget User entity);
}

