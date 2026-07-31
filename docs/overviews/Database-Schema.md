# Database Schema - Car Rental SaaS

**Cập nhật:** 31/07/2026 — Tối ưu hóa Database Schema sát với thực tế nghiệp vụ doanh nghiệp cho thuê xe tự lái tại Việt Nam (SMBs):
- **Bỏ bảng `payments` & `pricing_rules`**: Đơn giản hóa quản lý tài chính (chủ xe tự kiểm soát dòng tiền ngoài) và định giá trực tiếp theo xe.
- **Đưa giá thuê về từng `vehicles`**: Bỏ `base_price` ở `vehicle_types`, thêm `price_per_day` và `weekend_price_per_day` vào bảng `vehicles`.
- **Tối ưu bảng `bookings`**: Bổ sung `daily_rate` (snapshot đơn giá lúc chốt đơn), tài sản thế chấp (`collateral_type`, `collateral_notes`), trạng thái cọc (`is_deposit_paid`), tách biệt rõ `created_by` (Sale/CTV chốt đơn), `handover_by` (Staff giao xe), `returned_by` (Staff nhận xe) và `commission_amount` (hoa hồng Sale).
- **Giữ bảng `user_branches`**: Giữ mô hình N-N giữa User-Tenant và Branches để 1 nhân viên/Staff có thể được phân công làm việc tại 1 hoặc nhiều chi nhánh linh hoạt.
- **Lý giải `tenant_id`**: Giải thích rõ lý do duy trì `tenant_id` ở các bảng chính để đảm bảo hiệu năng truy vấn và bảo mật RLS.
- **Link DATABASE mới**: https://mermaid.live/edit#pako:eNq1V8tu20YU_ZUB17JjPe1ox0hULUSWDUkx2kLAYEyOxKnJGXY4TKw6XnTRZdEaRZcBGnRRoEDQdmstunCQ_9Cf9A71IkXRMhrYsAH6vubMuS_y2rCFQ426QWWTkbEk_lAOOYIfRTnhKkRv3-7tiWt0IQm3XRqiOhoaxb3u0MgxfE1dZnsUq0nweOvdhnYUKuFTudvyQohLxse7DaOQSryUZYxXF96JdNMyH8CmZYwgn9k0lTtxrCnaCWQVZKelhvhIxlLa3Bt2t2XkeimI40TMQfB79jIpDZUEhIgTn24RO8InjKNXKRfGFQo8wrFiVGq09VbPsgqoVH9h9tuNAirXz3qnBVSpW92B1TvrtfvWGlsiuifGAkfS26KyBVfEVjhwBU8B-yYU_AKFVClN7FJzkymFR1w9ls6pwvDUegwvil4pRBxH0jBMyiHTHiUcsRDblCtJvBwtXIq9phnc6Zp8SvAODW3JAsUE_18QPx9drJwnKkeZZCP3dh4DpkOKoRTVttr1YQJ7m3U7oUQmZQ61mU88FEiIhgPoKodMoKa_YPfvkXKj-z8RH9__NoHnT3_Ppu_4OF3JS_83lF5S7uAH4tjRbHrLkIpmd3_wdBSNLFRERWHcTua52e6YLzpxT_V0EzV1U52YbXjsmt2GpZtr0DO7_ZbV61nNbDQ7khLqEF_62eYBwGOabZ31nHui8luIMy29kFMYNdtGAXOwTaQDzJhWf69UrW2dJY6EmpV4URR5xgsC5hFxmoi1Ph0L5_C1Gu5P3w_L1Oxol7xELJBi_U6yMcsd6B0UMPsyCrCz0UexTlIVSZ7RKeZTqFg_QDAtIuJhl3BHaNaIAvIH7mz6j43U7O5fNGZEoCuaTkTGf3FOxpu7s7sPPOO_7DsHamaCpUY6ND7-_Ok9h_OWLfcsbt3tjkooOJX4IuLxkbPpr8CUYrPp9xzB-b8H6OPtbPpLXsM7NBAhU4kIc1d7Nv3RBgyz6V8Z0IkZu3QPCHMyvSs8PdJgh8QDMB4JX1r4xPxKz4NB2-rC80BPhJfHZmODVz3iEwG4UOkK1wcwzhQDZXo4aM2I8Yx83hOr_OY1zSKBWfWSMUAmCcTGI6rvdObef0DfRpCrS_-hROmbJF2UnE1_iAl-l1cSGkLSBUa3TulPm7mc95akcIKDLybQPODRJx591hicIxtcbhVUARTVFr8VIStHRUaj7dUeO8wJSp4UO-wocFv4PgtD2NbrUjsWRN8HahNptA_ukuPTTlOXTeO022r3Tuab5NjsNq0mPj23enqT9KzBq15Xq6r1hl4vnU5yp9ykX1of_1IZT_SNgbNcAiQM3wiYwS4J3S36UeRB8W4skET_hJHescTxGd8KE-e_AsfqGHJh2zBdT-msgeZWAoaY2XgVD7DZPGl3NcP9gdlqaXb7ZsfKYQ8_8H76Gbg2Nkja4MYoGGPJHKOuZEQLBuwRyAv8a1xrg6GhXAo8G_orwqEjEnkK0HPtFhD-tRD-0lOKaOwa9RHxQvgvCvRSWHzerqTw1uFQ2dCVatSLtYNyHMWoXxtXRn2veFDbLz0_KpfKtcNKtVQ5LBaMSSwvlferR4dH1YNKtVaCv5uC8V18dHm_WKkeFMtgX6kdHT0_OiwY1GFKyJP5J3b8pX3zHyAa-cs
---

## Mục lục
1. [ER Diagram](#1-er-diagram)
2. [Tables](#2-tables)
3. [Indexes](#3-indexes)
4. [Multi-tenant Strategy](#4-multi-tenant-strategy)
5. [Phase 2 Additions](#5-phase-2-additions)

---

## 1. ER Diagram

```
                               ┌──────────────────────┐
                               │       tenants        │
                               │──────────────────────│
                               │ id (PK)              │
                               │ name                 │
                               │ domain               │
                               │ plan_tier            │
                               │ logo_url             │
                               │ contact_email        │
                               │ contact_phone        │
                               │ settings (JSONB)     │
                               │ created_at           │
                               │ updated_at           │
                               └──────────┬───────────┘
                                          │
            ┌──────────────┬──────────────┼──────────────┬──────────────┐
            │ 1:N          │ 1:N          │ 1:N          │ 1:N          │ 1:N
            ▼              ▼              ▼              ▼              ▼
  ┌─────────────────┐ ┌────────────┐ ┌────────────┐ ┌──────────┐ ┌───────────────┐
  │    branches     │ │  vehicles  │ │  bookings  │ │customers │ │ vehicle_types │
  │─────────────────│ │────────────│ │────────────│ │──────────│ │───────────────│
  │ id (PK)         │ │ id (PK)    │ │ id (PK)    │ │ id (PK)  │ │ id (PK)       │
  │ tenant_id (FK)  │ │ tenant(FK) │ │ tenant(FK) │ │tenant(FK)│ │ tenant_id(FK) │
  │ name            │ │ branch(FK) │ │ branch(FK) │ │ name     │ │ name          │
  │ address         │ │ type (FK)  │ │customer(FK)│ │ phone    │ │ description   │
  │ phone           │ │ plate      │ │ vehicle(FK)│ │ email    │ │ is_active     │
  │ email           │ │ model      │ │ pickup_date│ │ address  │ │ created_at    │
  │ lat / lng       │ │ color      │ │return_date │ │ id_card  │ └───────────────┘
  │ is_central      │ │ year       │ │ status     │ │driver_lic│
  │ is_active       │ │ status     │ │daily_rate  │ │ notes    │
  │ created_at      │ │ current_km │ │total_amount│ │ is_active│
  └────────┬────────┘ │ fuel       │ │deposit_amt │ │created_at│
           │          │price_per_day││is_dep_paid │ └──────────┘
           │          │images      │ │collateral  │
           │          │ is_active  │ │created_by  │ (Sale/CTV)
           │          │ created_at │ │handover_by │ (Staff giao xe)
           │          └────────────┘ │returned_by │ (Staff nhận xe)
           │                         │commission  │
           │                         │ created_at │
           │                         └────────────┘
           │
           │  1:N (FK user_branches)
           ▼
  ┌─────────────────────────────────┐       ┌──────────────────────────────┐
  │  user_branches (N-N Branch)     │       │        user_tenants          │
  │─────────────────────────────────│       │──────────────────────────────│
  │ user_id (PK,FK)                 │       │ user_id (PK,FK) ──────────┐  │
  │ tenant_id (PK,FK)───────────────┼───────┤ tenant_id (PK,FK)         │  │
  │ branch_id (PK,FK)               │       │ role                      │  │
  │ assigned_at                     │       │ joined_at                 │  │
  └─────────────────────────────────┘       └───────────────────────────│──┘
                                                                        │
                                                                        │ N:1
                                                                        ▼
                                                             ┌──────────────────┐
                                                             │      users       │
                                                             │──────────────────│
                                                             │ id (PK)          │
                                                             │ email (UNIQUE)   │
                                                             │ password_hash    │
                                                             │ full_name        │
                                                             │ phone            │
                                                             │ avatar_url       │
                                                             │ is_active        │
                                                             │ last_login_at    │
                                                             │ created_at       │
                                                             │ updated_at       │
                                                             └──────────────────┘
```

---

## 2. Tables

### 2.1 tenants

```sql
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    domain VARCHAR(255) UNIQUE NOT NULL,
    plan_tier SMALLINT NOT NULL DEFAULT 1
        CHECK (plan_tier IN (1, 2, 3, 4)), -- Gói dịch vụ: 1: FREE, 2: BASIC, 3: PRO, 4: ENTERPRISE
    logo_url VARCHAR(500),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(20),
    settings JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_tenants_domain ON tenants(domain);
```

### 2.2 branches

```sql
CREATE TABLE branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    address TEXT,
    phone VARCHAR(20),
    email VARCHAR(255),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    is_central BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_branch_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_branches_tenant_id ON branches(tenant_id);
CREATE INDEX idx_branches_is_central ON branches(tenant_id, is_central);
```

### 2.3 vehicle_types

> **Thay đổi:** Loại bỏ cột `base_price`. Bảng `vehicle_types` chỉ đóng vai trò phân loại nhóm xe (SEDAN 4 chỗ, SUV 7 chỗ, Bán tải, v.v.). Giá thuê cụ thể được định vị theo từng chiếc xe trong bảng `vehicles`.

```sql
CREATE TABLE vehicle_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_vehicle_type_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_vehicle_types_tenant_id ON vehicle_types(tenant_id);
```

### 2.4 vehicles

> **Cập nhật:** Đưa cột `price_per_day` (giá thuê ngày thường) và `weekend_price_per_day` (giá thuê cuối tuần - tùy chọn) trực tiếp vào bảng `vehicles`. Thực tế tại VN, xe cùng dòng nhưng đời xe (năm sản xuất) hoặc phiên bản (số sàn / số tự động) khác nhau sẽ có giá thuê khác nhau.

```sql
CREATE TABLE vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID,
    vehicle_type_id UUID NOT NULL,
    license_plate VARCHAR(20) NOT NULL,
    model VARCHAR(100),
    color VARCHAR(30),
    year INTEGER,
    price_per_day DECIMAL(12, 2) NOT NULL DEFAULT 0, -- Giá thuê niêm yết ngày thường (VNĐ/ngày)
    weekend_price_per_day DECIMAL(12, 2),            -- Giá thuê niêm yết cuối tuần (tùy chọn)
    description TEXT,
    status SMALLINT NOT NULL DEFAULT 1
        CHECK (status IN (1, 2, 3, 4)), -- Trạng thái xe: 1: Sẵn sàng (AVAILABLE), 2: Đang thuê (RENTED), 3: Bảo dưỡng (MAINTENANCE), 4: Điều phối (TRANSFERRED)
    current_km INTEGER DEFAULT 0,
    fuel_level VARCHAR(20) DEFAULT 'full',
    images JSONB DEFAULT '[]',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicle_branch_tenant FOREIGN KEY (branch_id, tenant_id)
        REFERENCES branches(id, tenant_id) ON DELETE SET NULL (branch_id),
    CONSTRAINT fk_vehicle_type_tenant FOREIGN KEY (vehicle_type_id, tenant_id)
        REFERENCES vehicle_types(id, tenant_id),
    CONSTRAINT unique_vehicle_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_vehicles_tenant_id ON vehicles(tenant_id);
CREATE INDEX idx_vehicles_branch_id ON vehicles(branch_id);
CREATE INDEX idx_vehicles_status ON vehicles(tenant_id, status);
CREATE INDEX idx_vehicles_license_plate ON vehicles(tenant_id, license_plate);
CREATE UNIQUE INDEX idx_vehicles_tenant_license ON vehicles(tenant_id, license_plate);
```

### 2.5 customers

```sql
CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(255),
    address TEXT,
    id_card VARCHAR(20),        -- Mã hóa AES-256
    driver_license VARCHAR(20), -- Mã hóa AES-256
    id_card_images JSONB DEFAULT '[]',        -- Mảng đường dẫn ảnh CCCD lưu trên VPS local (VD: ["/uploads/tenants/.../cccd_front.jpg"])
    driver_license_images JSONB DEFAULT '[]', -- Mảng đường dẫn ảnh GPLX lưu trên VPS local
    notes TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_customer_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_customers_tenant_id ON customers(tenant_id);
CREATE INDEX idx_customers_phone ON customers(tenant_id, phone);
CREATE INDEX idx_customers_email ON customers(tenant_id, email);
```

### 2.6 bookings

> **Cập nhật nghiệp vụ Việt Nam & Quy trình Giao/Nhận xe (Handover & Return):**
> 1. `daily_rate`: Lưu đơn giá thuê/ngày thỏa thuận tại thời điểm chốt đơn (tránh ảnh hưởng khi xe đổi giá sau này).
> 2. `collateral_type` & `collateral_notes`: Tài sản thế chấp (Xe máy + Đăng ký xe hoặc Tiền mặt 15-20 triệu - Bắt buộc trong nghiệp vụ VN).
> 3. **Luồng Bàn giao xe (Handover Check-in)**:
>    - `actual_handover_at`: Thời điểm **thực tế** giao chìa khóa cho khách.
>    - `initial_km` & `initial_fuel`: Số km đồng hồ & Mức nhiên liệu ban đầu.
>    - `handover_images`: Mảng ảnh hiện trạng xe lúc giao (vết xước cũ, km, nhiên liệu).
>    - `handover_by`: ID nhân viên bãi trực tiếp làm biên bản bàn giao.
> 4. **Luồng Nhận lại xe (Return Check-out)**:
>    - `actual_return_at`: Thời điểm **thực tế** nhận lại xe (Dùng tính tiền trễ giờ `late_fee` tự động).
>    - `final_km` & `final_fuel`: Số km đồng hồ & Mức nhiên liệu khi trả xe.
>    - `return_images`: Mảng ảnh hiện trạng xe khi trả (đối chiếu va quẹt/hư hỏng mới).
>    - `returned_by`: ID nhân viên bãi trực tiếp nhận lại xe.
>    - `extra_km_fee`: Phí phụ trội km (khi khách đi vượt quá hạn mức km/ngày).
>    - `late_fee` & `damage_fee`: Phí trễ giờ và Phí đền bù hư hỏng.
> 5. `created_by` & `commission_amount`: ID Sale/CTV chốt đơn (cố định) và Số tiền hoa hồng.

```sql
CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    vehicle_id UUID,
    booking_code VARCHAR(20) UNIQUE NOT NULL,
    pickup_date DATE NOT NULL,
    return_date DATE NOT NULL,
    pickup_time TIME,
    return_time TIME,
    actual_handover_at TIMESTAMP WITH TIME ZONE,   -- Thời điểm thực tế giao chìa khóa cho khách
    actual_return_at TIMESTAMP WITH TIME ZONE,     -- Thời điểm thực tế nhận lại xe (Dùng tự động tính trễ giờ)
    status SMALLINT NOT NULL DEFAULT 1
        CHECK (status IN (1, 2, 3, 4, 5)), -- 1: HOLD, 2: CONFIRMED, 3: HANDED_OVER, 4: RETURNED, 5: CANCELLED
    hold_expires_at TIMESTAMP WITH TIME ZONE,
    daily_rate DECIMAL(12, 2) NOT NULL DEFAULT 0,  -- Đơn giá thuê/ngày chốt tại thời điểm đặt xe
    total_amount DECIMAL(12, 2) DEFAULT 0,         -- Tổng giá trị hợp đồng thuê xe
    deposit_amount DECIMAL(12, 2) DEFAULT 0,       -- Số tiền cọc giữ xe
    is_deposit_paid BOOLEAN DEFAULT FALSE,         -- Trạng thái đã nhận tiền cọc giữ xe
    collateral_type SMALLINT DEFAULT 1
        CHECK (collateral_type IN (1, 2, 3)),      -- Tài sản thế chấp: 1: XE_MAY (Xe máy + Đăng ký), 2: TIEN_MAT (Tiền mặt cọc), 3: KHAC
    collateral_notes TEXT,                          -- Mô tả tài sản thế chấp (VD: Xe Wave RSX BKS 29X1-12345 + Cavet chính chủ)
    initial_km INTEGER,
    final_km INTEGER,
    initial_fuel VARCHAR(20),
    final_fuel VARCHAR(20),
    handover_images JSONB DEFAULT '[]',            -- Ảnh tình trạng xe lúc bàn giao (vết xước, km, xăng)
    return_images JSONB DEFAULT '[]',              -- Ảnh tình trạng xe lúc nhận lại (vết xước mới, km, xăng)
    extra_km_fee DECIMAL(12, 2) DEFAULT 0,         -- Phí phụ trội số km (nếu vượt quá giới hạn km/ngày)
    late_fee DECIMAL(12, 2) DEFAULT 0,             -- Phí trễ giờ
    damage_fee DECIMAL(12, 2) DEFAULT 0,           -- Phí đền bù hư hỏng
    notes TEXT,
    cancellation_reason TEXT,
    created_by UUID,           -- ID người tạo đơn (Sale / CTV / Admin) - Dùng tính hoa hồng
    handover_by UUID,          -- ID nhân viên bãi làm thủ tục giao xe
    returned_by UUID,          -- ID nhân viên bãi nhận lại xe
    commission_amount DECIMAL(12, 2) DEFAULT 0, -- Số tiền hoa hồng chi trả cho Sale/CTV
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_booking_branch_tenant FOREIGN KEY (branch_id, tenant_id)
        REFERENCES branches(id, tenant_id),
    CONSTRAINT fk_booking_customer_tenant FOREIGN KEY (customer_id, tenant_id)
        REFERENCES customers(id, tenant_id),
    CONSTRAINT fk_booking_vehicle_tenant FOREIGN KEY (vehicle_id, tenant_id)
        REFERENCES vehicles(id, tenant_id) ON DELETE SET NULL (vehicle_id),
    CONSTRAINT fk_booking_created_by_tenant FOREIGN KEY (created_by, tenant_id)
        REFERENCES user_tenants(user_id, tenant_id) ON DELETE SET NULL (created_by),
    CONSTRAINT fk_booking_handover_by_tenant FOREIGN KEY (handover_by, tenant_id)
        REFERENCES user_tenants(user_id, tenant_id) ON DELETE SET NULL (handover_by),
    CONSTRAINT fk_booking_returned_by_tenant FOREIGN KEY (returned_by, tenant_id)
        REFERENCES user_tenants(user_id, tenant_id) ON DELETE SET NULL (returned_by),
    CONSTRAINT unique_booking_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_bookings_tenant_id ON bookings(tenant_id);
CREATE INDEX idx_bookings_branch_id ON bookings(branch_id);
CREATE INDEX idx_bookings_customer_id ON bookings(customer_id);
CREATE INDEX idx_bookings_vehicle_id ON bookings(vehicle_id);
CREATE INDEX idx_bookings_status ON bookings(tenant_id, status);
CREATE INDEX idx_bookings_dates ON bookings(tenant_id, pickup_date, return_date);
CREATE INDEX idx_bookings_code ON bookings(booking_code);
CREATE INDEX idx_bookings_tenant_created_by ON bookings(tenant_id, created_by);
CREATE INDEX idx_bookings_tenant_handover_by ON bookings(tenant_id, handover_by);
CREATE INDEX idx_bookings_tenant_returned_by ON bookings(tenant_id, returned_by);
```

### 2.7 Bỏ bảng `payments` & `pricing_rules`

> **Lý do loại bỏ:**
> 1. `payments`: Các shop cho thuê xe tự lái vừa và nhỏ ở Việt Nam tự kiểm soát dòng tiền mặt / tài khoản ngân hàng bên ngoài. Hệ thống SaaS chỉ cần theo dõi các số liệu trên đơn hàng trong bảng `bookings` (`deposit_amount`, `is_deposit_paid`, `total_amount`, `late_fee`, `damage_fee`).
> 2. `pricing_rules`: Công thức nhân giá tự động theo ngày lễ/cuối tuần phức tạp vượt quá nhu cầu MVP. Giá thuê xe được thiết lập trực tiếp tại bảng `vehicles` (`price_per_day`, `weekend_price_per_day`) và được ghi nhận chốt theo từng đơn tại `bookings.daily_rate`.

### 2.8 users (identity — centralized, not tenant-scoped)

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    phone VARCHAR(20),
    avatar_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    is_super_admin BOOLEAN DEFAULT FALSE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
```

### 2.9 user_tenants (N-N: User thuộc Tenant nào, với Role gì)

```sql
CREATE TABLE user_tenants (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    role SMALLINT NOT NULL CHECK (role IN (1, 2, 3)), -- Vai trò: 1: TENANT_ADMIN (Chủ doanh nghiệp), 2: STAFF (Nhân viên), 3: SALE (Cộng tác viên kinh doanh)
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, tenant_id)
);

CREATE INDEX idx_user_tenants_tenant_id ON user_tenants(tenant_id);
```

> **SUPER_ADMIN:** không có entry trong `user_tenants`. Bypasses toàn bộ RLS.
> **TENANT_ADMIN:** không cần entry trong `user_branches` — mặc định toàn quyền tất cả Branch trong Tenant.

### 2.10 user_branches (N-N: Trong 1 Tenant, User được gán vào Branch nào)

```sql
CREATE TABLE user_branches (
    user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, branch_id),
    CONSTRAINT fk_user_branch_user_tenant FOREIGN KEY (user_id, tenant_id)
        REFERENCES user_tenants(user_id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_branch_branch_tenant FOREIGN KEY (branch_id, tenant_id)
        REFERENCES branches(id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_branches_branch_id ON user_branches(branch_id);
CREATE INDEX idx_user_branches_tenant_id ON user_branches(tenant_id);
```

> **STAFF và SALE:** có 1 hoặc nhiều entry trong `user_branches` để xác định danh sách các chi nhánh được phép truy cập/thao tác.

---

## 3. Indexes

### 3.1 Summary of Indexes

| Table | Index Name | Columns | Type |
|-------|------------|----------|------|
| tenants | idx_tenants_domain | domain | UNIQUE |
| branches | idx_branches_tenant_id | tenant_id | |
| branches | idx_branches_is_central | tenant_id, is_central | |
| vehicle_types | idx_vehicle_types_tenant_id | tenant_id | |
| vehicles | idx_vehicles_tenant_id | tenant_id | |
| vehicles | idx_vehicles_branch_id | branch_id | |
| vehicles | idx_vehicles_status | tenant_id, status | |
| vehicles | idx_vehicles_license_plate | tenant_id, license_plate | |
| vehicles | idx_vehicles_tenant_license | tenant_id, license_plate | UNIQUE |
| customers | idx_customers_tenant_id | tenant_id | |
| customers | idx_customers_phone | tenant_id, phone | |
| customers | idx_customers_email | tenant_id, email | |
| bookings | idx_bookings_tenant_id | tenant_id | |
| bookings | idx_bookings_branch_id | branch_id | |
| bookings | idx_bookings_customer_id | customer_id | |
| bookings | idx_bookings_vehicle_id | vehicle_id | |
| bookings | idx_bookings_status | tenant_id, status | |
| bookings | idx_bookings_dates | tenant_id, pickup_date, return_date | |
| bookings | idx_bookings_code | booking_code | UNIQUE |
| bookings | idx_bookings_tenant_created_by | tenant_id, created_by | |
| bookings | idx_bookings_tenant_handover_by | tenant_id, handover_by | |
| bookings | idx_bookings_tenant_returned_by | tenant_id, returned_by | |
| users | idx_users_email | email | UNIQUE |
| user_tenants | idx_user_tenants_tenant_id | tenant_id | |
| user_branches | idx_user_branches_branch_id | branch_id | |
| user_branches | idx_user_branches_tenant_id | tenant_id | |

---

## 4. Multi-tenant Strategy

### 4.1 Lý do duy trì `tenant_id` trên các bảng gốc/độc lập
Trong mô hình **Shared Database (Row-Level Security)**, mặc dù về mặt lý thuyết 3NF có thể truy ngược `tenant_id` từ `vehicles` qua `vehicle_types` hay từ `bookings` qua `vehicles`, việc lưu `tenant_id` trực tiếp trên các bảng chính (`branches`, `vehicles`, `bookings`, `customers`, `user_tenants`) là **bắt buộc** vì các lý do:

1. **Hiệu năng truy vấn (Index Scan)**: Cho phép truy vấn danh sách xe hoặc đơn hàng của một tenant bằng câu lệnh `WHERE tenant_id = ?` với Index Scan O(log N), tránh việc phải `JOIN` qua 2-3 bảng trung gian chỉ để lọc tenant.
2. **Bảo mật tuyệt đối (Row-Level Security & Anti-IDOR)**: Các framework ORM (Spring Data JPA) và PostgreSQL RLS tự động chèn `WHERE tenant_id = :currentTenant` vào mọi câu SQL. Nếu bảng không có `tenant_id`, lập trình viên dễ bỏ sót `JOIN` dẫn tới lỗ hổng rò rỉ dữ liệu giữa các bãi xe.
3. **Ràng buộc toàn vẹn dữ liệu (Composite Foreign Key)**: Đảm bảo không thể gán nhầm xe của Tenant A sang chi nhánh của Tenant B thông qua FK `(branch_id, tenant_id) REFERENCES branches(id, tenant_id)`.

### 4.2 Row-Level Security (RLS) Policy

```sql
-- Enable RLS cho các bảng chính
ALTER TABLE vehicles ENABLE ROW LEVEL SECURITY;
ALTER TABLE bookings ENABLE ROW LEVEL SECURITY;
ALTER TABLE customers ENABLE ROW LEVEL SECURITY;

-- Policy lọc tenant tự động
CREATE POLICY tenant_isolation ON vehicles
    USING (
        (current_setting('app.current_user_role', true) = 'SUPER_ADMIN') OR
        (tenant_id = current_setting('app.current_tenant', true)::uuid)
    );
```

---

## 5. Phase 2 Additions

Các bảng mở rộng trong Phase 2 khi hệ thống phục vụ chuỗi lớn:

| Bảng | Mục đích | Trigger mở rộng |
|------|----------|-----------------|
| `vehicle_transfers` | Điều phối xe liên chi nhánh | Chuỗi xe >50 xe, >3 chi nhánh |
| `pricing_rules` | Tự động hóa nhân hệ số ngày lễ, Tết | Khi chủ bãi muốn tự động đổi giá theo mùa |
| `commission_settlements` | Quyết toán hoa hồng CTV định kỳ | Khi đội ngũ CTV đạt số lượng lớn |
