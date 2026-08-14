package com.carrental.car_rental_backend.account.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.Builder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "email")
    private String emailAccount;

    @Column(name = "password_hash")
    private String password;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "phone")
    private String phoneNumber;

    @Column(name = "avatar_url")
    private String avtURL;

    @Builder.Default 
    @Column(name = "is_active")
    private Boolean isActive = true;

    @Builder.Default
    //theo nguyên tắc đặc quyền tối thiểu, bất kì tài khoản nào mới tạo đều phải ở mức quyền thấp nhất
    @Column(name = "is_super_admin")
    private Boolean isSuperAdmin = false;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updateAt; 

}
