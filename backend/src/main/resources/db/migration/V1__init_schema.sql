-- ============================================================================
-- Car Rental SaaS - Squash Migration V1 (Initial Schema)
-- Target: PostgreSQL 15+
-- Specifications: docs/overviews/Database-Schema.md
-- ============================================================================

-- Bật extension pgcrypto để phục vụ gen_random_uuid() và mã hóa dữ liệu
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- 1. BẢNG ĐỘC LẬP (TẦNG 1: Core Systems, Catalogs & Identifiers)
-- ============================================================================

-- 1.1 tenants: Nhà xe / Doanh nghiệp cho thuê xe
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    domain VARCHAR(255) NOT NULL,
    plan_tier SMALLINT NOT NULL DEFAULT 1 CHECK (plan_tier IN (1, 2, 3, 4)),
    logo_url VARCHAR(500),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(20),
    settings JSONB DEFAULT '{}'::jsonb,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 1.2 permissions: Danh mục Quyền nguyên tử toàn hệ thống
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT
);

-- 1.3 vehicle_types: Danh mục Loại xe System-wide
CREATE TABLE vehicle_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 1.4 users: Tài khoản người dùng trung tâm
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    phone VARCHAR(20),
    avatar_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    is_super_admin BOOLEAN DEFAULT FALSE,
    last_login_at TIMESTAMP WITH TIME ZONE,
    current_session_id UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 2. BẢNG PHỤ THUỘC TẦNG 2 (Cấu hình, Chi nhánh, Nhóm quyền & Khách hàng)
-- ============================================================================

-- 2.1 tenant_configs: Cấu hình động theo nhà xe (Quan hệ 1-1)
CREATE TABLE tenant_configs (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    hold_timeout_minutes INTEGER NOT NULL DEFAULT 30 CHECK (hold_timeout_minutes > 0),
    late_hourly_price DECIMAL(12, 2) NOT NULL DEFAULT 100000 CHECK (late_hourly_price >= 0),
    default_commission_rate DECIMAL(5, 2) NOT NULL DEFAULT 5.00 CHECK (default_commission_rate >= 0 AND default_commission_rate <= 100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2.2 branches: Chi nhánh / Bãi xe trực thuộc Nhà xe
CREATE TABLE branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    address TEXT,
    phone VARCHAR(20),
    email VARCHAR(255),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_branch_tenant UNIQUE (id, tenant_id)
);

-- 2.3 roles: Danh mục Nhóm quyền động trong Tenant
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description TEXT,
    is_system_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_role_tenant UNIQUE (id, tenant_id)
);

-- 2.4 role_permissions: Bảng liên kết N-N Nhóm quyền & Quyền nguyên tử
CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- 2.5 customers: Khách hàng thuê xe & Quản lý hồ sơ / rủi ro
CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(255),
    address TEXT,
    id_card VARCHAR(255),
    driver_license VARCHAR(255),
    id_card_images JSONB DEFAULT '[]'::jsonb,
    driver_license_images JSONB DEFAULT '[]'::jsonb,
    is_risk BOOLEAN NOT NULL DEFAULT FALSE,
    risk_reason TEXT,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_customer_tenant UNIQUE (id, tenant_id)
);

-- ============================================================================
-- 3. BẢNG PHỤ THUỘC TẦNG 3 (Liên kết Thành viên & Quản lý Xe)
-- ============================================================================

-- 3.1 user_tenants: Phân quyền Người dùng theo từng Tenant (N-N)
CREATE TABLE user_tenants (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id),
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, tenant_id),
    CONSTRAINT fk_user_tenant_role FOREIGN KEY (role_id, tenant_id) 
        REFERENCES roles(id, tenant_id)
);

-- 3.2 vehicles: Danh sách Xe cho thuê
CREATE TABLE vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID,
    vehicle_type_id UUID NOT NULL REFERENCES vehicle_types(id),
    license_plate VARCHAR(20) NOT NULL,
    model VARCHAR(100),
    color VARCHAR(30),
    year INTEGER,
    price_per_day DECIMAL(12, 2) NOT NULL DEFAULT 0 CHECK (price_per_day >= 0),
    weekend_price_per_day DECIMAL(12, 2) CHECK (weekend_price_per_day IS NULL OR weekend_price_per_day >= 0),
    parking_location TEXT,
    inspection_expiry_date DATE,
    insurance_expiry_date DATE,
    description TEXT,
    status SMALLINT NOT NULL DEFAULT 1 CHECK (status IN (1, 2, 3, 4, 5)),
    images JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicle_branch_tenant FOREIGN KEY (branch_id, tenant_id)
        REFERENCES branches(id, tenant_id) ON DELETE SET NULL (branch_id),
    CONSTRAINT unique_vehicle_tenant UNIQUE (id, tenant_id)
);

-- ============================================================================
-- 4. BẢNG PHỤ THUỘC TẦNG 4 (Gán Chi nhánh cho Nhân sự)
-- ============================================================================

-- 4.1 user_branches: Phân công Nhân viên trực tại Chi nhánh (N-N)
CREATE TABLE user_branches (
    user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1 CHECK (status IN (1, 2, 3)),
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP WITH TIME ZONE,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,
    PRIMARY KEY (user_id, branch_id),
    CONSTRAINT fk_user_branch_user_tenant FOREIGN KEY (user_id, tenant_id)
        REFERENCES user_tenants(user_id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_branch_branch_tenant FOREIGN KEY (branch_id, tenant_id)
        REFERENCES branches(id, tenant_id) ON DELETE CASCADE
);

-- ============================================================================
-- 5. BẢNG PHỤ THUỘC TẦNG 5 (Hợp đồng Thuê xe & Giao dịch)
-- ============================================================================

-- 5.1 bookings: Đơn hàng thuê xe, Giữ cọc & Quyết toán
CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    vehicle_id UUID,
    booking_code VARCHAR(50) NOT NULL,
    pickup_time TIMESTAMP WITH TIME ZONE NOT NULL,
    return_time TIMESTAMP WITH TIME ZONE NOT NULL,
    actual_handover_at TIMESTAMP WITH TIME ZONE,
    actual_return_at TIMESTAMP WITH TIME ZONE,
    status SMALLINT NOT NULL DEFAULT 1 CHECK (status IN (1, 2, 3, 4, 5)),
    hold_expires_at TIMESTAMP WITH TIME ZONE,
    daily_rate DECIMAL(12, 2) NOT NULL DEFAULT 0,
    total_amount DECIMAL(12, 2) DEFAULT 0,
    deposit_amount DECIMAL(12, 2) DEFAULT 0,
    is_deposit_paid BOOLEAN DEFAULT FALSE,
    payment_method SMALLINT DEFAULT 1 CHECK (payment_method IN (1, 2)),
    bank_name VARCHAR(100),
    bank_account_number VARCHAR(50),
    sender_bank_name VARCHAR(100),
    sender_account_number VARCHAR(50),
    sender_account_name VARCHAR(255),
    transfer_reference VARCHAR(100),
    payment_status SMALLINT DEFAULT 1 CHECK (payment_status IN (1, 2, 3)),
    collateral_type SMALLINT DEFAULT 1 CHECK (collateral_type IN (1, 2, 3)),
    collateral_notes TEXT,
    handover_images JSONB DEFAULT '[]'::jsonb,
    return_images JSONB DEFAULT '[]'::jsonb,
    late_fee DECIMAL(12, 2) DEFAULT 0,
    notes TEXT,
    cancellation_reason TEXT,
    created_by UUID,
    handover_by UUID,
    returned_by UUID,
    commission_amount DECIMAL(12, 2) DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Ràng buộc toàn vẹn dữ liệu cấp Database
    CONSTRAINT fk_booking_branch_tenant FOREIGN KEY (branch_id, tenant_id)
        REFERENCES branches(id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_customer_tenant FOREIGN KEY (customer_id, tenant_id)
        REFERENCES customers(id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_vehicle_tenant FOREIGN KEY (vehicle_id, tenant_id)
        REFERENCES vehicles(id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_booking_created_by_tenant FOREIGN KEY (created_by, tenant_id)
        REFERENCES user_tenants(user_id, tenant_id) ON DELETE SET NULL (created_by),
    CONSTRAINT fk_booking_handover_by_tenant FOREIGN KEY (handover_by, tenant_id)
        REFERENCES user_tenants(user_id, tenant_id) ON DELETE SET NULL (handover_by),
    CONSTRAINT fk_booking_returned_by_tenant FOREIGN KEY (returned_by, tenant_id)
        REFERENCES user_tenants(user_id, tenant_id) ON DELETE SET NULL (returned_by),
    CONSTRAINT unique_booking_tenant UNIQUE (id, tenant_id),
    CONSTRAINT check_booking_times CHECK (pickup_time < return_time),
    CONSTRAINT check_booking_amounts CHECK (daily_rate >= 0 AND total_amount >= 0 AND deposit_amount >= 0)
);

-- ============================================================================
-- 6. HỆ THỐNG CHỈ MỤC TỐI ƯU TRUY VẤN (INDEXES)
-- ============================================================================

-- 6.1 tenants & branches
CREATE UNIQUE INDEX idx_tenants_domain ON tenants(domain);
CREATE INDEX idx_branches_tenant_id ON branches(tenant_id);

-- 6.2 roles & permissions
CREATE INDEX idx_roles_tenant_id ON roles(tenant_id);
CREATE UNIQUE INDEX idx_roles_tenant_code ON roles(tenant_id, code);
CREATE UNIQUE INDEX idx_permissions_code ON permissions(code);
CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- 6.3 users & assignments
CREATE UNIQUE INDEX idx_users_email ON users(email);
CREATE INDEX idx_user_tenants_tenant_id ON user_tenants(tenant_id);
CREATE INDEX idx_user_tenants_role_id ON user_tenants(role_id);
CREATE INDEX idx_user_branches_branch_id ON user_branches(branch_id);
CREATE INDEX idx_user_branches_tenant_id ON user_branches(tenant_id);
CREATE INDEX idx_user_branches_updated_by ON user_branches(updated_by);

-- 6.4 vehicle_types & vehicles
CREATE UNIQUE INDEX idx_vehicle_types_name ON vehicle_types(name);
CREATE INDEX idx_vehicles_tenant_branch ON vehicles(tenant_id, branch_id);
CREATE INDEX idx_vehicles_status ON vehicles(tenant_id, status);
CREATE UNIQUE INDEX idx_vehicles_tenant_license ON vehicles(tenant_id, license_plate);

-- 6.5 customers
CREATE INDEX idx_customers_tenant_id ON customers(tenant_id);
CREATE UNIQUE INDEX idx_customers_tenant_phone ON customers(tenant_id, phone);
CREATE INDEX idx_customers_email ON customers(tenant_id, email);
CREATE INDEX idx_customers_is_risk ON customers(tenant_id, is_risk);

-- 6.6 bookings
CREATE INDEX idx_bookings_tenant_id ON bookings(tenant_id);
CREATE INDEX idx_bookings_branch_id ON bookings(branch_id);
CREATE INDEX idx_bookings_customer_id ON bookings(customer_id);
CREATE INDEX idx_bookings_vehicle_id ON bookings(vehicle_id);
CREATE INDEX idx_bookings_status ON bookings(tenant_id, status);
CREATE INDEX idx_bookings_times ON bookings(tenant_id, pickup_time, return_time);
CREATE UNIQUE INDEX idx_bookings_tenant_code ON bookings(tenant_id, booking_code);
CREATE INDEX idx_bookings_payment_status ON bookings(tenant_id, payment_status);
