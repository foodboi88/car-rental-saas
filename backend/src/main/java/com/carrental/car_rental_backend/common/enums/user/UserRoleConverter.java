package com.carrental.car_rental_backend.common.enums.user;

import com.carrental.car_rental_backend.common.enums.converter.AbstractEnumConverter;

public class UserRoleConverter extends AbstractEnumConverter<UserRole, Integer> {
    public UserRoleConverter(){
        super(UserRole.class);
    }
}
