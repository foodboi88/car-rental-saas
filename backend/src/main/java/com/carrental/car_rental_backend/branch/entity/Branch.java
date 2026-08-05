package com.carrental.car_rental_backend.branch.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity //gắn nhãn để spring biết được đây là một entity, JPA sẽ quản lý
@Table(name = "branches")
// tự động sinh method get(), set() khi sử dụng lombok
// có thể thay bằng @Data để sử dụng luôn get/set
@Setter
@Getter
@NoArgsConstructor //tự động sinh một hàm khởi tạo không tham số
@AllArgsConstructor //tự động sinh một hàm khởi tạo có đầy đủ các tham số
public class Branch {
    //khai báo các thuộc tính của branch

    @Id //đánh dấu là khóa chính
    @GeneratedValue(strategy = GenerationType.UUID) // tự động tạo UUID mỗi khi save
    @Column(name = "id", nullable = false)
    private UUID id;
    //ánh xạ tới column trong bảng, thể hiện tên column, và không được để null
    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "address")
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "district")
    private String district;

    @Column(name = "ward")
    private String ward;

    @Column(name = "opening_hours")
    private String opening_hours;

    @Column(name = "status")
    private Integer status;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "is_central")
    private Boolean is_central = false;

    @Column(name = "is_active")
    private Boolean is_active = false;

    @Column(name = "is_deleted")
    private Boolean is_deleted = false;

    @CreationTimestamp //tự động gán thời gian hiện tại khi record được tạo lần đầu
    @Column(name = "created_at")
    private OffsetDateTime created_at;

    @UpdateTimestamp //tự động cập nhật thời gian mỗi khi record save/update
    @Column(name = "updated_at")
    private OffsetDateTime updated_at;
}
