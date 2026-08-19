-- Migration V5: Align Database Schema with 2026-08-15 Specification
-- - Bổ sung bảng tenant_configs (1-1 với tenants)
-- - Nâng cấp Dynamic RBAC: tạo bảng roles, permissions, role_permissions, chuyển đổi user_tenants.role sang role_id
-- - Nâng cấp user_branches (started_at, ended_at, status, updated_by)
-- - Chuẩn hóa vehicle_types System-wide (xóa tenant_id, gộp trùng lặp, UNIQUE name)
-- - Tinh gọn vehicles (thêm parking_location, inspection_expiry_date, insurance_expiry_date; bỏ current_km, fuel_level, is_active)
-- - Tinh gọn customers (bỏ is_active; thêm is_risk, risk_reason; mở rộng id_card, driver_license lên VARCHAR(255))
-- - Tinh gọn & Chuẩn hóa bookings (gộp pickup_time, return_time thành TIMESTAMPTZ; thêm payment info; bỏ km/fuel/extra fees; Composite Foreign Keys)

BEGIN;

-- ============================================================================
-- 1. BẢNG tenant_configs (Cấu hình động Nhà xe)
-- ============================================================================
CREATE TABLE IF NOT EXISTS tenant_configs (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    hold_timeout_minutes INTEGER NOT NULL DEFAULT 30,
    late_hourly_price DECIMAL(12, 2) NOT NULL DEFAULT 100000,
    default_commission_rate DECIMAL(5, 2) NOT NULL DEFAULT 5.00,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Tự động sinh bản ghi cấu hình mặc định cho các tenant đã có
INSERT INTO tenant_configs (tenant_id)
SELECT id FROM tenants
ON CONFLICT (tenant_id) DO NOTHING;

-- ============================================================================
-- 2. HỆ THỐNG PHÂN QUYỀN ĐỘNG (Dynamic RBAC: roles, permissions, role_permissions)
-- ============================================================================
CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description TEXT,
    is_system_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_roles_tenant_id ON roles(tenant_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_roles_tenant_code ON roles(tenant_id, code);

CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_permissions_code ON permissions(code);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX IF NOT EXISTS idx_role_permissions_permission_id ON role_permissions(permission_id);

-- Tạo sẵn các quyền nguyên tử (Permissions)
INSERT INTO permissions (code, name, category, description) VALUES
    ('booking:read', 'Xem đơn đặt xe', 'BOOKING', 'Xem danh sách và chi tiết đơn đặt xe trong phạm vi cho phép'),
    ('booking:create', 'Tạo đơn đặt xe', 'BOOKING', 'Tạo mới đơn đặt xe giữ chỗ tạm (HOLD)'),
    ('booking:confirm_deposit', 'Xác nhận đặt cọc', 'BOOKING', 'Xác nhận đã nhận tiền cọc chuyển đơn sang CONFIRMED'),
    ('booking:handover', 'Bàn giao xe', 'BOOKING', 'Thực hiện thủ tục bàn giao xe và chụp ảnh hiện trạng'),
    ('booking:return', 'Nhận lại xe', 'BOOKING', 'Thực hiện thủ tục nhận lại xe và quyết toán phạt trễ giờ'),
    ('booking:cancel', 'Hủy đơn đặt xe', 'BOOKING', 'Hủy đơn đặt xe'),
    ('vehicle:read', 'Xem thông tin xe', 'VEHICLE', 'Xem danh sách và thông tin chi tiết xe'),
    ('vehicle:manage', 'Quản lý đội xe', 'VEHICLE', 'Thêm, sửa, ngưng khai thác xe'),
    ('branch:manage', 'Quản lý chi nhánh', 'BRANCH', 'Thêm, sửa cấu hình chi nhánh/bãi xe'),
    ('account:manage', 'Quản lý nhân sự', 'ACCOUNT', 'Tạo tài khoản và phân quyền nhân viên'),
    ('report:view_revenue', 'Xem báo cáo doanh thu', 'REPORT', 'Xem thống kê doanh thu và chỉ số kinh doanh')
ON CONFLICT (code) DO NOTHING;

-- Tạo 3 Role mặc định cho mỗi Tenant hiện có
INSERT INTO roles (tenant_id, name, code, description, is_system_default)
SELECT t.id, 'Chủ doanh nghiệp', 'TENANT_ADMIN', 'Toàn quyền quản lý nhà xe', TRUE
FROM tenants t
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO roles (tenant_id, name, code, description, is_system_default)
SELECT t.id, 'Nhân viên nghiệp vụ', 'STAFF', 'Nhân viên điều hành và giao nhận xe tại chi nhánh', TRUE
FROM tenants t
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO roles (tenant_id, name, code, description, is_system_default)
SELECT t.id, 'Cộng tác viên kinh doanh', 'SALE', 'Cộng tác viên tạo đơn và nhận hoa hồng', TRUE
FROM tenants t
ON CONFLICT (tenant_id, code) DO NOTHING;

-- Gán quyền cho các Role
-- TENANT_ADMIN: Full permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'TENANT_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- STAFF: booking operations + vehicle:read
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'STAFF' AND p.code IN ('booking:read', 'booking:create', 'booking:confirm_deposit', 'booking:handover', 'booking:return', 'booking:cancel', 'vehicle:read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- SALE: booking:read, booking:create, booking:cancel, vehicle:read
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'SALE' AND p.code IN ('booking:read', 'booking:create', 'booking:cancel', 'vehicle:read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ============================================================================
-- 3. NÂNG CẤP user_tenants (Chuyển đổi role INT -> role_id UUID)
-- ============================================================================
ALTER TABLE user_tenants ADD COLUMN IF NOT EXISTS role_id UUID REFERENCES roles(id);

-- Cập nhật role_id dựa trên role số nguyên cũ
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_tenants' AND column_name = 'role') THEN
        UPDATE user_tenants ut
        SET role_id = r.id
        FROM roles r
        WHERE ut.tenant_id = r.tenant_id
          AND (
            (ut.role = 1 AND r.code = 'TENANT_ADMIN') OR
            (ut.role = 2 AND r.code = 'STAFF') OR
            (ut.role = 3 AND r.code = 'SALE')
          )
          AND ut.role_id IS NULL;

        -- Với bất kỳ dòng nào chưa gán được, gán mặc định STAFF
        UPDATE user_tenants ut
        SET role_id = (SELECT id FROM roles r WHERE r.tenant_id = ut.tenant_id AND r.code = 'STAFF' LIMIT 1)
        WHERE ut.role_id IS NULL;

        ALTER TABLE user_tenants ALTER COLUMN role_id SET NOT NULL;
        ALTER TABLE user_tenants DROP COLUMN role;
    END IF;
END $$;

ALTER TABLE user_tenants DROP CONSTRAINT IF EXISTS unique_user_tenant_role;
ALTER TABLE user_tenants ADD CONSTRAINT unique_user_tenant_role UNIQUE (user_id, tenant_id, role_id);
CREATE INDEX IF NOT EXISTS idx_user_tenants_role_id ON user_tenants(role_id);

-- ============================================================================
-- 4. NÂNG CẤP user_branches (started_at, ended_at, status, updated_by)
-- ============================================================================
ALTER TABLE user_branches ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;

-- Chuyển dữ liệu từ assigned_at nếu có
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'user_branches' AND column_name = 'assigned_at') THEN
        UPDATE user_branches SET started_at = assigned_at WHERE started_at IS NULL;
        ALTER TABLE user_branches DROP COLUMN assigned_at;
    END IF;
END $$;

ALTER TABLE user_branches ADD COLUMN IF NOT EXISTS ended_at TIMESTAMPTZ;
ALTER TABLE user_branches ADD COLUMN IF NOT EXISTS status SMALLINT NOT NULL DEFAULT 1 CHECK (status IN (1, 2, 3));
ALTER TABLE user_branches ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES users(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_user_branches_updated_by ON user_branches(updated_by);

-- ============================================================================
-- 5. CHUẨN HÓA vehicle_types (System-wide: Bỏ tenant_id, gộp tên duy nhất)
-- ============================================================================
-- Bỏ ràng buộc khóa ngoại cũ từ vehicles sang vehicle_types
ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS fk_vehicle_type_tenant;
ALTER TABLE vehicle_types DROP CONSTRAINT IF EXISTS unique_vehicle_type_tenant;

-- Gộp các vehicle_types trùng tên sang 1 bản ghi đại diện
DO $$
DECLARE
    rec RECORD;
    canonical_id UUID;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'vehicle_types' AND column_name = 'tenant_id') THEN
        FOR rec IN (SELECT name, MIN(id) as first_id FROM vehicle_types GROUP BY name HAVING COUNT(*) > 1) LOOP
            canonical_id := rec.first_id;
            UPDATE vehicles SET vehicle_type_id = canonical_id WHERE vehicle_type_id IN (SELECT id FROM vehicle_types WHERE name = rec.name AND id != canonical_id);
            DELETE FROM vehicle_types WHERE name = rec.name AND id != canonical_id;
        END LOOP;
        
        ALTER TABLE vehicle_types DROP COLUMN tenant_id;
    END IF;
END $$;

ALTER TABLE vehicle_types DROP CONSTRAINT IF EXISTS vehicle_types_name_key;
ALTER TABLE vehicle_types ADD CONSTRAINT vehicle_types_name_key UNIQUE (name);
CREATE INDEX IF NOT EXISTS idx_vehicle_types_name ON vehicle_types(name);

-- Thêm khóa ngoại chuẩn từ vehicles sang vehicle_types
ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS fk_vehicles_vehicle_type;
ALTER TABLE vehicles ADD CONSTRAINT fk_vehicles_vehicle_type FOREIGN KEY (vehicle_type_id) REFERENCES vehicle_types(id);

-- ============================================================================
-- 6. TINH GỌN & CẬP NHẬT BẢNG vehicles
-- ============================================================================
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS parking_location TEXT;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS inspection_expiry_date DATE;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS insurance_expiry_date DATE;

-- Loại bỏ các cột không dùng
ALTER TABLE vehicles DROP COLUMN IF EXISTS current_km;
ALTER TABLE vehicles DROP COLUMN IF EXISTS fuel_level;
ALTER TABLE vehicles DROP COLUMN IF EXISTS is_active;

-- Cập nhật check constraint status (1: AVAILABLE, 2: RENTED, 3: MAINTENANCE, 4: TRANSFERRED, 5: INACTIVE)
ALTER TABLE vehicles DROP CONSTRAINT IF EXISTS vehicles_status_check;
ALTER TABLE vehicles ADD CONSTRAINT vehicles_status_check CHECK (status IN (1, 2, 3, 4, 5));

-- ============================================================================
-- 7. TINH GỌN & BẢO MẬT BẢNG customers
-- ============================================================================
ALTER TABLE customers DROP COLUMN IF EXISTS is_active;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS is_risk BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS risk_reason TEXT;

-- Mở rộng độ dài để lưu chuỗi mã hóa đối xứng AES-256
ALTER TABLE customers ALTER COLUMN id_card TYPE VARCHAR(255);
ALTER TABLE customers ALTER COLUMN driver_license TYPE VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_customers_is_risk ON customers(tenant_id, is_risk);

-- ============================================================================
-- 8. TINH GỌN & CHUẨN HÓA BẢNG bookings
-- ============================================================================
-- Chuyển đổi sang 2 cột TIMESTAMPTZ pickup_time và return_time
DO $$
BEGIN
    -- Nếu pickup_time đang là TIME, chuyển đổi sang TIMESTAMPTZ kết hợp với pickup_date
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'bookings' AND column_name = 'pickup_time' AND data_type = 'time without time zone'
    ) THEN
        ALTER TABLE bookings ADD COLUMN temp_pickup_time TIMESTAMPTZ;
        ALTER TABLE bookings ADD COLUMN temp_return_time TIMESTAMPTZ;

        UPDATE bookings 
        SET temp_pickup_time = (pickup_date + pickup_time)::timestamptz,
            temp_return_time = (return_date + return_time)::timestamptz;

        ALTER TABLE bookings DROP COLUMN pickup_time;
        ALTER TABLE bookings DROP COLUMN return_time;
        ALTER TABLE bookings DROP COLUMN IF EXISTS pickup_date;
        ALTER TABLE bookings DROP COLUMN IF EXISTS return_date;

        ALTER TABLE bookings RENAME COLUMN temp_pickup_time TO pickup_time;
        ALTER TABLE bookings RENAME COLUMN temp_return_time TO return_time;

        ALTER TABLE bookings ALTER COLUMN pickup_time SET NOT NULL;
        ALTER TABLE bookings ALTER COLUMN return_time SET NOT NULL;
    ELSIF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'bookings' AND column_name = 'pickup_time'
    ) THEN
        ALTER TABLE bookings ADD COLUMN pickup_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
        ALTER TABLE bookings ADD COLUMN return_time TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '1 day');
        ALTER TABLE bookings DROP COLUMN IF EXISTS pickup_date;
        ALTER TABLE bookings DROP COLUMN IF EXISTS return_date;
    END IF;
END $$;

-- Thêm các cột thanh toán & chuyển khoản
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS payment_method SMALLINT DEFAULT 1 CHECK (payment_method IN (1, 2));
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS bank_name VARCHAR(100);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS bank_account_number VARCHAR(50);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS sender_bank_name VARCHAR(100);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS sender_account_number VARCHAR(50);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS sender_account_name VARCHAR(255);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS transfer_reference VARCHAR(100);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS payment_status SMALLINT DEFAULT 1 CHECK (payment_status IN (1, 2, 3));

-- Bỏ các cột KM, Xăng và phụ phí không dùng
ALTER TABLE bookings DROP COLUMN IF EXISTS initial_km;
ALTER TABLE bookings DROP COLUMN IF EXISTS final_km;
ALTER TABLE bookings DROP COLUMN IF EXISTS initial_fuel;
ALTER TABLE bookings DROP COLUMN IF EXISTS final_fuel;
ALTER TABLE bookings DROP COLUMN IF EXISTS extra_km_fee;
ALTER TABLE bookings DROP COLUMN IF EXISTS damage_fee;

-- Mở rộng booking_code lên VARCHAR(50)
ALTER TABLE bookings ALTER COLUMN booking_code TYPE VARCHAR(50);

-- Đổi ràng buộc UNIQUE(booking_code) thành UNIQUE(tenant_id, booking_code)
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_booking_code_key;
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS unique_booking_tenant_code;
ALTER TABLE bookings ADD CONSTRAINT unique_booking_tenant_code UNIQUE (tenant_id, booking_code);

-- Cập nhật Composite Foreign Keys
ALTER TABLE bookings DROP CONSTRAINT IF EXISTS fk_booking_branch_tenant;
ALTER TABLE bookings ADD CONSTRAINT fk_booking_branch_tenant FOREIGN KEY (branch_id, tenant_id) REFERENCES branches(id, tenant_id);

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS fk_booking_customer_tenant;
ALTER TABLE bookings ADD CONSTRAINT fk_booking_customer_tenant FOREIGN KEY (customer_id, tenant_id) REFERENCES customers(id, tenant_id);

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS fk_booking_vehicle_tenant;
ALTER TABLE bookings ADD CONSTRAINT fk_booking_vehicle_tenant FOREIGN KEY (vehicle_id, tenant_id) REFERENCES vehicles(id, tenant_id) ON DELETE SET NULL (vehicle_id);

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS fk_booking_created_by_tenant;
ALTER TABLE bookings ADD CONSTRAINT fk_booking_created_by_tenant FOREIGN KEY (created_by, tenant_id) REFERENCES user_tenants(user_id, tenant_id) ON DELETE SET NULL (created_by);

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS fk_booking_handover_by_tenant;
ALTER TABLE bookings ADD CONSTRAINT fk_booking_handover_by_tenant FOREIGN KEY (handover_by, tenant_id) REFERENCES user_tenants(user_id, tenant_id) ON DELETE SET NULL (handover_by);

ALTER TABLE bookings DROP CONSTRAINT IF EXISTS fk_booking_returned_by_tenant;
ALTER TABLE bookings ADD CONSTRAINT fk_booking_returned_by_tenant FOREIGN KEY (returned_by, tenant_id) REFERENCES user_tenants(user_id, tenant_id) ON DELETE SET NULL (returned_by);

-- Tạo Index mới cho bookings
DROP INDEX IF EXISTS idx_bookings_dates;
DROP INDEX IF EXISTS idx_bookings_code;
CREATE INDEX IF NOT EXISTS idx_bookings_times ON bookings(tenant_id, pickup_time, return_time);
CREATE INDEX IF NOT EXISTS idx_bookings_tenant_code ON bookings(tenant_id, booking_code);
CREATE INDEX IF NOT EXISTS idx_bookings_payment_status ON bookings(tenant_id, payment_status);

COMMIT;
