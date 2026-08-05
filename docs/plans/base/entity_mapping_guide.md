# 🎤 TÀI LIỆU HƯỚNG DẪN MAPPING ENTITY & CẤU HÌNH DDL-AUTO VALIDATE
> **Dành cho buổi họp/chia sẻ kỹ thuật với Team - Dự án Car Rental SaaS**

---

## 📌 PHẦN 1: MỤC TIÊU & TẠI SAO DÙNG `spring.jpa.hibernate.ddl-auto: validate`?

### 1.1 Khái niệm `ddl-auto: validate` là gì?
Trong file `application.yaml` của chúng ta có cấu hình:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate # Chỉ kiểm tra cấu hình Entity so với DB, KHÔNG tự sửa DB
```

### 1.2 Lý do dự án chọn cấu hình này:
1. **Source of Truth duy nhất là Flyway Migration**: 
   - Mọi thay đổi về cấu trúc bảng (thêm/sửa/xóa cột, index, constraint) **bắt buộc** phải thông qua script Flyway (`V1__...sql`, `V2__...sql`, `V4__...sql`).
   - Hibernate **không được phép** tự ý sinh DDL (`create`, `update`, `create-drop`) để tránh tình trạng làm nát DB hoặc mất dữ liệu khi dev/deploy.
2. **Phát hiện lỗi Mapping sớm (Fail-Fast Principle)**:
   - Khi chạy `mvn spring-boot:run` hoặc khởi động ứng dụng, Hibernate sẽ quét toàn bộ các class `@Entity`.
   - Nếu phát hiện **sai tên cột, sai kiểu dữ liệu, thiếu cột NOT NULL**, ứng dụng sẽ **Crash ngay lập tức (`SchemaManagementException`)**.
   - Điều này ép toàn bộ team phải viết Entity chuẩn chỉ 100% trước khi có thể chạy thử tính năng.

---

## 📐 PHẦN 2: BẢNG CHUẨN HÓA ÁNH XẠ KIỂU DỮ LIỆU (POSTGRESQL ↔ JAVA ENTITY)

Khi tạo Entity, các bạn tra cứu trực tiếp bảng này để chọn kiểu dữ liệu Java tương ứng:

| Kiểu dữ liệu trong DB (Postgres) | Kiểu dữ liệu tương ứng trong Java Entity | Ghi chú & Ví dụ Annotations |
| :--- | :--- | :--- |
| **`UUID`** | `java.util.UUID` | `@Id @Column(name = "id") private UUID id;` |
| **`VARCHAR(n)` / `TEXT`** | `String` | `@Column(name = "license_plate", length = 20) private String licensePlate;` |
| **`SMALLINT` / `INTEGER`** | `Integer` hoặc `Short` | `@Column(name = "status") private Integer status;` |
| **`DECIMAL(12, 2)`** | `java.math.BigDecimal` | ⚠️ **BẮT BUỘC dùng `BigDecimal`**, KHÔNG dùng `Double` hay `Float` để tránh sai số tính toán tài chính! |
| **`BOOLEAN`** | `Boolean` | `@Column(name = "is_active") private Boolean isActive;` |
| **`TIMESTAMPTZ`** (Timestamp with TZ) | `java.time.OffsetDateTime` hoặc `Instant` | `@Column(name = "created_at") private OffsetDateTime createdAt;` |
| **`DATE`** | `java.time.LocalDate` | `@Column(name = "pickup_date") private LocalDate pickupDate;` |
| **`TIME`** | `java.time.LocalTime` | `@Column(name = "pickup_time") private LocalTime pickupTime;` |
| **`JSONB`** | `String` (hoặc Custom Object) | `@Column(name = "images", columnDefinition = "jsonb") private String images;` |

---

## 🏷️ PHẦN 3: QUY TẮC ĐẶT TÊN & ANNOTATIONS CHUẨN

1. **Quy tắc Naming Convention**:
   * Database Postgres: Chuẩn **`snake_case`** (ví dụ: `price_per_day`, `tenant_id`, `created_at`).
   * Java Entity Class: Chuẩn **`camelCase`** (ví dụ: `pricePerDay`, `tenantId`, `createdAt`).
2. **Luôn chỉ định rõ `@Column(name = "...")`**:
   * Mặc dù NamingStrategy của Hibernate có thể tự chuyển `camelCase` thành `snake_case`, nhưng việc viết rõ `@Column(name = "price_per_day")` sẽ giúp code tường minh và đảm bảo pass `validate` 100%.
3. **Sử dụng Lombok một cách an toàn**:
   * Dùng `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`.
   * ⚠️ **KHÔNG DÙNG `@Data` hoặc `@EqualsAndHashCode` trên Entity** vì có thể gây ra vòng lặp vô tận (Infinite Loop) hoặc nổ StackOverflowError khi có quan hệ N-1 / 1-N!

---

## 💻 PHẦN 4: CODE MẪU CHUẨN PASSED VALIDATE (VÍ DỤ `VehicleEntity.java`)

Các thành viên có thể copy mẫu code chuẩn dưới đây làm khung khi viết Entity mới:

```java
package com.carrental.car_rental_backend.vehicle.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "vehicle_type_id", nullable = false)
    private UUID vehicleTypeId;

    @Column(name = "license_plate", nullable = false, length = 20)
    private String licensePlate;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "color", length = 30)
    private String color;

    @Column(name = "year")
    private Integer year;

    @Column(name = "price_per_day", nullable = false)
    private BigDecimal pricePerDay;

    @Column(name = "weekend_price_per_day")
    private BigDecimal weekendPricePerDay;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false)
    private Integer status; // 1: AVAILABLE, 2: RENTED, 3: MAINTENANCE, 4: TRANSFERRED

    @Column(name = "current_km")
    private Integer currentKm;

    @Column(name = "fuel_level", length = 20)
    private String fuelLevel;

    @Column(name = "images", columnDefinition = "jsonb")
    private String images;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
        if (this.isActive == null) this.isActive = true;
        if (this.status == null) this.status = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
```

---

## 🚨 PHẦN 5: TOP 4 LỖI THƯỜNG GẶP KHIẾN APP CRASH KHI VALIDATE DB

Khi chạy app mà bị nổ lỗi `SchemaManagementException: Schema-validation: missing column [...]` hoặc `wrong column type [...]`, hãy check ngay 4 nguyên nhân sau:

1. **Sai kiểu dữ liệu (Wrong Column Type)**:
   - *Nguyên nhân:* DB là `DECIMAL(12,2)` mà Entity khai báo `Double` / `Float`.
   - *Khắc phục:* Đổi thành `BigDecimal`.
2. **Sai hoặc thiếu tên cột `@Column(name = "...")`**:
   - *Nguyên nhân:* DB là `is_deposit_paid` nhưng Java khai báo `private Boolean depositPaid;` mà quên gán `@Column(name = "is_deposit_paid")`.
3. **Thiếu kiểu Ngày tháng (Date/Time Type)**:
   - *Nguyên nhân:* DB là `TIMESTAMPTZ` mà Java dùng `java.util.Date` hoặc `java.sql.Date`.
   - *Khắc phục:* Đổi sang Java 8 Date Time API: `OffsetDateTime` hoặc `Instant`.
4. **Quên tạo file Migration Flyway trước**:
   - *Nguyên nhân:* Thêm trường mới trong Java Entity nhưng chưa viết file `.sql` trong Flyway để thêm cột trong DB Postgres.
   - *Khắc phục:* Tạo file script Flyway mới (ví dụ `V5__add_new_column.sql`) trước khi start app.
