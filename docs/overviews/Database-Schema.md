# Database Schema - Car Rental SaaS

**Cập nhật:** 06/07/2026 — Đồng bộ user model (N-N với tenants, N-N với branches), bỏ CENTRAL_MANAGER, căn chỉnh với Multi-Tenant-Multi-Branch.md.

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
            ┌──────────────┬─────────────┼─────────────┬──────────────┐
            │ 1:N          │ 1:N         │ 1:N         │ 1:N          │ 1:N
            ▼              ▼             ▼             ▼              ▼
  ┌─────────────────┐ ┌───────────┐ ┌────────────┐ ┌──────────┐ ┌───────────────┐
  │    branches     │ │  vehicles │ │  bookings  │ │customers │ │ vehicle_types │
  │─────────────────│ │───────────│ │────────────│ │──────────│ │───────────────│
  │ id (PK)         │ │ id (PK)   │ │ id (PK)    │ │ id (PK)  │ │ id (PK)       │
  │ tenant_id (FK)  │ │tenant(FK) │ │ tenant(FK) │ │tenant(FK)│ │ tenant_id(FK) │
  │ name            │ │branch(FK) │ │ branch(FK) │ │ name     │ │ name          │
  │ address         │ │type (FK)  │ │customer(FK)│ │ phone    │ │ base_price    │
  │ phone           │ │plate      │ │ vehicle(FK)│ │ email    │ │ description   │
  │ email           │ │ model     │ │ pickup_date│ │ address  │ │ is_active     │
  │ lat / lng       │ │ color     │ │return_date │ │ id_card  │ │ created_at    │
  │ is_central      │ │ year      │ │ status     │ │driver_lic│ └──────┬────────┘
  │ is_active       │ │ status    │ │total_amount│ │ notes    │        │
  │ created_at      │ │ current_km│ │deposit_amt │ │ is_active│        │
  └────────┬────────┘ │ fuel      │ │ late_fee   │ │created_at│        │
           │          │ images    │ │ damage_fee │ └──────────┘        │
           │          │ is_active │ │ notes      │                     │
           │          │ created_at│ │ created_at │                     │
           │          └─────┬─────┘ └──────┬─────┘                     │
           │                │              │                           │
           │                │              │ 1:N                       │ 1:N
           │                │              ▼                           ▼
           │                │        ┌──────────────┐         ┌───────────────┐
           │                │        │   payments   │         │ pricing_rules │
           │                │        │──────────────│         │───────────────│
           │                │        │ id (PK)      │         │ id (PK)       │
           │                │        │ tenant_id(FK)│         │ tenant_id(FK) │
           │                │        │ booking_id   │         │vehicle_type   │
           │                │        │ method       │         │ day_type      │
           │                │        │ amount       │         │ multiplier    │
           │                │        │ transaction  │         │ is_active     │
           │                │        │ payment_type │         │ created_at    │
           │                │        │ paid_at      │         └───────────────┘
           │                │        │ created_at   │
           │                │        └──────────────┘
           │                │
           │                │  1:1 (FK user_tenants)
           │                │
  ┌────────┴────────┐       │       ┌──────────────────────────────┐
  │  user_branches  │       │       │        user_tenants          │
  │─────────────────│       │       │──────────────────────────────│
  │ user_id (PK,FK) │       │       │ user_id (PK,FK) ──────────┐  │
  │tenant_id(PK,FK)─┼───────┘       │ tenant_id (PK,FK)         │  │
  │ branch_id (FK)──┘               │ role                      │  │
  │ assigned_at                     │ joined_at                 │  │
  └─────────────────────────────────┘───────────────────────────│──┘
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

  Phase 2 bổ sung: vehicle_transfers (điều phối xe)
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

```sql
CREATE TABLE vehicle_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    description TEXT,
    base_price DECIMAL(12, 2) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_vehicle_type_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_vehicle_types_tenant_id ON vehicle_types(tenant_id);
```

### 2.4 vehicles

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
    id_card VARCHAR(20),
    driver_license VARCHAR(20),
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
    status SMALLINT NOT NULL DEFAULT 1
        CHECK (status IN (1, 2, 3, 4, 5)), -- Trạng thái đơn: 1: Giữ chỗ tạm (HOLD), 2: Đã xác nhận / Đặt cọc (CONFIRMED), 3: Đã bàn giao xe (HANDED_OVER), 4: Đã nhận lại xe (RETURNED), 5: Đã hủy (CANCELLED)
    hold_expires_at TIMESTAMP WITH TIME ZONE,
    initial_km INTEGER,
    final_km INTEGER,
    initial_fuel VARCHAR(20),
    final_fuel VARCHAR(20),
    notes TEXT,
    total_amount DECIMAL(12, 2) DEFAULT 0,
    deposit_amount DECIMAL(12, 2) DEFAULT 0,
    deposit_paid BOOLEAN DEFAULT FALSE,
    late_fee DECIMAL(12, 2) DEFAULT 0,
    damage_fee DECIMAL(12, 2) DEFAULT 0,
    cancellation_reason TEXT,
    created_by UUID, -- ID của người tạo đơn đặt xe (nhân viên hoặc sale)
    handover_by UUID, -- ID của nhân viên/admin thực hiện thủ tục giao xe cho khách
    returned_by UUID, -- ID của nhân viên/admin thực hiện nhận lại xe khi khách trả
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

### 2.7 payments

```sql
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    booking_id UUID NOT NULL,
    method SMALLINT NOT NULL
        CHECK (method IN (1, 2, 3)), -- Phương thức: 1: Tiền mặt (CASH), 2: Chuyển khoản (BANK_TRANSFER), 3: Ví điện tử (E_WALLET)
    amount DECIMAL(12, 2) NOT NULL,
    transaction_id VARCHAR(100),
    payment_type SMALLINT DEFAULT 1
        CHECK (payment_type IN (1, 2, 3, 4, 5)), -- Loại thanh toán: 1: Đặt cọc (DEPOSIT), 2: Tất toán (FULL), 3: Hoàn tiền (REFUND), 4: Phí trễ giờ (LATE_FEE), 5: Phí đền bù (DAMAGE_FEE)
    paid_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_payment_amount CHECK (amount > 0),
    CONSTRAINT chk_transaction_id CHECK (method = 1 OR transaction_id IS NOT NULL),
    CONSTRAINT fk_payment_booking_tenant FOREIGN KEY (booking_id, tenant_id)
        REFERENCES bookings(id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX idx_payments_tenant_id ON payments(tenant_id);
CREATE INDEX idx_payments_booking_id ON payments(booking_id);
CREATE INDEX idx_payments_method ON payments(tenant_id, method);
```

### 2.8 pricing_rules (đơn giản hóa MVP)

```sql
CREATE TABLE pricing_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    vehicle_type_id UUID NOT NULL REFERENCES vehicle_types(id),
    day_type SMALLINT NOT NULL
        CHECK (day_type IN (1, 2)), -- Loại ngày: 1: Ngày thường (WEEKDAY), 2: Cuối tuần (WEEKEND)
    multiplier DECIMAL(4, 2) NOT NULL DEFAULT 1.00,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pricing_rules_tenant_id ON pricing_rules(tenant_id);
CREATE INDEX idx_pricing_rules_type_day ON pricing_rules(tenant_id, vehicle_type_id, day_type);
```

**MVP:** Chỉ hỗ trợ weekday (1.0) và weekend (1.2). Season/Holiday multipliers → Phase 2.

### 2.9 vehicle_transfers — Dời sang Phase 2

Bảng `vehicle_transfers` (điều phối xe giữa các chi nhánh) được thêm vào Phase 2 khi hệ thống phục vụ các chuỗi 50+ xe có nhu cầu điều phối liên chi nhánh. MVP chưa cần bảng này.

### 2.10 users (identity — centralized, not tenant-scoped)

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

### 2.11 user_tenants (N-N: User thuộc Tenant nào, với Role gì)

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

> **SUPER_ADMIN:** không có entry trong `user_tenants`. Hệ thống nhận diện SUPER_ADMIN qua flag/cột role trên chính bảng `users`, hoặc qua 1 bảng cấu hình riêng. SUPER_ADMIN bypass toàn bộ tenant isolation.

### 2.12 user_branches (N-N: Trong 1 Tenant, User được gán vào Branch nào)

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

> **TENANT_ADMIN:** không cần entry trong `user_branches` — mặc định toàn quyền tất cả Branch trong Tenant.
> **BRANCH_MANAGER và STAFF:** bắt buộc có entry để xác định phạm vi Branch được phép truy cập.
```

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
| payments | idx_payments_tenant_id | tenant_id | |
| payments | idx_payments_booking_id | booking_id | |
| payments | idx_payments_method | tenant_id, method | |
| pricing_rules | idx_pricing_rules_tenant_id | tenant_id | |
| pricing_rules | idx_pricing_rules_type_day | tenant_id, vehicle_type_id, day_type | |
| users | idx_users_email | email | UNIQUE |
| user_tenants | idx_user_tenants_tenant_id | tenant_id | |
| user_branches | idx_user_branches_branch_id | branch_id | |
| user_branches | idx_user_branches_tenant_id | tenant_id | |

`vehicle_transfers` — dời sang Phase 2, chưa tạo indexes.

---

## 4. Multi-tenant Strategy

### 4.1 Row-Level Security (RLS)

```sql
-- Enable RLS for all tables
ALTER TABLE vehicles ENABLE ROW LEVEL SECURITY;
ALTER TABLE bookings ENABLE ROW LEVEL SECURITY;
ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
-- ... apply to all tenant-scoped tables

-- Create policy
CREATE POLICY tenant_isolation ON vehicles
    USING (
        -- Allow SUPER_ADMIN to bypass isolation filter
        (current_setting('app.current_user_role', true) = 'SUPER_ADMIN') OR
        (tenant_id = current_setting('app.current_tenant', true)::uuid)
    );
```

### 4.2 Application-Level Filtering

```java
// BaseRepository with automatic tenant filtering
public interface BaseRepository<T> {
    @Query("SELECT e FROM #{#entityName} e WHERE e.tenantId = :tenantId")
    List<T> findAllByTenantId(@Param("tenantId") UUID tenantId);
}

// Every query automatically includes tenant_id
```

### 4.3 Tenant Context Setup

```java
// TenantContext.java
public class TenantContext {
    private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();

    public static void setTenantId(UUID tenantId) {
        currentTenant.set(tenantId);
    }

    public static UUID getTenantId() {
        return currentTenant.get();
    }

    public static void clear() {
        currentTenant.remove();
    }
}

// JwtAuthenticationFilter.java
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        String tenantId = extractTenantId(request); // from JWT or subdomain
        TenantContext.setTenantId(UUID.fromString(tenantId));
        // ... continue filter chain
    }
}
```

### 4.4 Migration Path

```
Phase 1: Shared Database + tenant_id (Current)
    └── Simple, cost-effective
    └── Good for up to ~1000 tenants

Phase 2: Schema-per-tenant (Future)
    └── Better isolation
    └── Good for ~1000-10000 tenants

Phase 3: Database-per-tenant (Future)
    └── Maximum isolation
    └── Good for enterprise/critical tenants
```

---

## 5. Phase 2 Additions

Các bảng sẽ được thêm ở Phase 2 khi mở rộng phục vụ chuỗi lớn:

| Bảng | Mục đích | Trigger |
|------|----------|---------|
| `vehicle_transfers` | Điều phối xe liên chi nhánh | Tenant có >50 xe, >3 chi nhánh |
| `pricing_rules` (mở rộng) | Thêm season, holiday multipliers | Tenant yêu cầu định giá theo mùa/lễ |
| `notification_logs` | Lịch sử SMS/Email/Push | Khi tích hợp đa kênh thông báo |
| `driver_records` | Quản lý tài xế (xe có lái) | Khi mở rộng sang vertical xe có lái |
