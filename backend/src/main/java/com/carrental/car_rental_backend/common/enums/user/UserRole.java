package com.carrental.car_rental_backend.common.enums.user;

import com.carrental.car_rental_backend.common.enums.BaseEnum;
import com.carrental.car_rental_backend.common.exception.AppException;
import com.carrental.car_rental_backend.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

@Getter 
public enum UserRole implements BaseEnum<Integer> {

    //Enum định nghĩa vai trò, gồm 2 thuộc tính, value và giá trị tương ứng
    TENANT_ADMIN(1, "Chủ doanh nghiệp"),
    STAFF(2, "Nhân viên"),
    SALE(3, "Cộng tác viên");

    private final Integer value;
    private final String label;

    UserRole(Integer value, String label){
        this.value = value;
        this.label = label;
    }

    @Override
    @JsonValue
    public Integer getValue(){
        return this.value;
    }

    @JsonCreator
    public static UserRole fromValue(Object input){
        if(input == null) return null;
        //lấy ra tất cả các role được định nghĩa trong enum
        for (UserRole role : UserRole.values()) {
            if (input instanceof Number && role.value.equals(((Number) input).intValue())) {
                return role;
            }
        }
        throw new AppException(ErrorCode.BAD_REQUEST, "Vai trò không hợp lệ");
    }

}
