package com.carrental.car_rental_backend.account.mapper;

import org.mapstruct.Named;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component 
public class PasswordEncoderMapper {

    private final PasswordEncoder passwordEncoder;

    public PasswordEncoderMapper(PasswordEncoder passwordEncoder){
        this.passwordEncoder = passwordEncoder;
    }

    //gán nhãn cho password để tránh hash nhầm các trường khác
    @Named("hashPassword")
    public String hashPassword(String rawPassword){
        if(rawPassword == null || rawPassword.isEmpty()){
            return null;
        }
        return passwordEncoder.encode(rawPassword);
    }

}
