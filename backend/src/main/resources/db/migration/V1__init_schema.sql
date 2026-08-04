-- Car Rental SaaS - full schema create script
-- PostgreSQL 15+

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================================================
-- 1) tenants
-- =========================================================
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    domain VARCHAR(255) UNIQUE NOT NULL,
    plan_tier INTEGER NOT NULL DEFAULT 1
        CHECK (plan_tier IN (1, 2, 3, 4)), -- 1: FREE, 2: BASIC, 3: PRO, 4: ENTERPRISE
    logo_url VARCHAR(500),
    contact_email VARCHAR(255),
    contact_phone VARCHAR(20),
    settings JSONB DEFAULT '{}'::jsonb,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_tenants_domain ON tenants(domain);

-- =========================================================
-- 2) users
-- =========================================================
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    phone VARCHAR(20),
    avatar_url VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    is_super_admin BOOLEAN DEFAULT FALSE,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);

-- =========================================================
-- 3) branches
-- =========================================================
CREATE TABLE branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    code VARCHAR(50),
    name VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(255),
    address TEXT,
    city VARCHAR(100),
    district VARCHAR(100),
    ward VARCHAR(100),
    opening_hours VARCHAR(255),
    status INTEGER NOT NULL DEFAULT 1
        CHECK (status IN (1, 2)), -- 1: ACTIVE, 2: INACTIVE
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    is_central BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_branch_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_branches_tenant_id ON branches(tenant_id);
CREATE INDEX idx_branches_is_central ON branches(tenant_id, is_central);

-- =========================================================
-- 4) vehicle_types
-- =========================================================
CREATE TABLE vehicle_types (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    description TEXT,
    base_price DECIMAL(12, 2) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_vehicle_type_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_vehicle_types_tenant_id ON vehicle_types(tenant_id);

-- =========================================================
-- 5) customers
-- =========================================================
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
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_customer_tenant UNIQUE (id, tenant_id)
);

CREATE INDEX idx_customers_tenant_id ON customers(tenant_id);
CREATE INDEX idx_customers_phone ON customers(tenant_id, phone);
CREATE INDEX idx_customers_email ON customers(tenant_id, email);

-- =========================================================
-- 6) user_tenants
-- =========================================================
CREATE TABLE user_tenants (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    role INTEGER NOT NULL CHECK (role IN (1, 2, 3)), -- 1: TENANT_ADMIN, 2: STAFF, 3: SALE
    joined_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, tenant_id)
);

CREATE INDEX idx_user_tenants_tenant_id ON user_tenants(tenant_id);

-- =========================================================
-- 7) user_branches
-- =========================================================
CREATE TABLE user_branches (
    user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, branch_id),
    CONSTRAINT fk_user_branch_user_tenant FOREIGN KEY (user_id, tenant_id)
        REFERENCES user_tenants(user_id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_branch_branch_tenant FOREIGN KEY (branch_id, tenant_id)
        REFERENCES branches(id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_branches_branch_id ON user_branches(branch_id);
CREATE INDEX idx_user_branches_tenant_id ON user_branches(tenant_id);

-- =========================================================
-- 8) vehicles
-- =========================================================
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
    status INTEGER NOT NULL DEFAULT 1
        CHECK (status IN (1, 2, 3, 4)), -- 1: AVAILABLE, 2: RENTED, 3: MAINTENANCE, 4: TRANSFERRED
    current_km INTEGER DEFAULT 0,
    fuel_level VARCHAR(20) DEFAULT 'full',
    images JSONB DEFAULT '[]'::jsonb,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
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

-- =========================================================
-- 9) bookings
-- =========================================================
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
    status INTEGER NOT NULL DEFAULT 1
        CHECK (status IN (1, 2, 3, 4, 5)), -- 1: HOLD, 2: CONFIRMED, 3: HANDED_OVER, 4: RETURNED, 5: CANCELLED
    hold_expires_at TIMESTAMPTZ,
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
    created_by UUID,
    handover_by UUID,
    returned_by UUID,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
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

-- =========================================================
-- 10) payments
-- =========================================================
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    booking_id UUID NOT NULL,
    method INTEGER NOT NULL
        CHECK (method IN (1, 2, 3)), -- 1: CASH, 2: BANK_TRANSFER, 3: E_WALLET
    amount DECIMAL(12, 2) NOT NULL,
    transaction_id VARCHAR(100),
    payment_type INTEGER DEFAULT 1
        CHECK (payment_type IN (1, 2, 3, 4, 5)), -- 1: DEPOSIT, 2: FULL, 3: REFUND, 4: LATE_FEE, 5: DAMAGE_FEE
    paid_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_payment_amount CHECK (amount > 0),
    CONSTRAINT chk_transaction_id CHECK (method = 1 OR transaction_id IS NOT NULL),
    CONSTRAINT fk_payment_booking_tenant FOREIGN KEY (booking_id, tenant_id)
        REFERENCES bookings(id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX idx_payments_tenant_id ON payments(tenant_id);
CREATE INDEX idx_payments_booking_id ON payments(booking_id);
CREATE INDEX idx_payments_method ON payments(tenant_id, method);

-- =========================================================
-- 11) pricing_rules
-- =========================================================
CREATE TABLE pricing_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    vehicle_type_id UUID NOT NULL REFERENCES vehicle_types(id),
    day_type INTEGER NOT NULL
        CHECK (day_type IN (1, 2)), -- 1: WEEKDAY, 2: WEEKEND
    multiplier DECIMAL(4, 2) NOT NULL DEFAULT 1.00,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pricing_rules_tenant_id ON pricing_rules(tenant_id);
CREATE INDEX idx_pricing_rules_type_day ON pricing_rules(tenant_id, vehicle_type_id, day_type);

COMMIT;
