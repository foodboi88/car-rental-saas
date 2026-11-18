-- ============================================================================
-- Car Rental SaaS - Squash Migration V2 (Seed System & Demo Data)
-- Target: PostgreSQL 15+
-- Safe execution: INSERT ... ON CONFLICT DO NOTHING (NO TRUNCATE CASCADE)
-- ============================================================================

-- ============================================================================
-- 1. SEED SYSTEM CATALOGS (Dữ liệu danh mục hệ thống)
-- ============================================================================

-- 1.1 Danh mục Loại xe System-wide (vehicle_types)
INSERT INTO vehicle_types (id, name, description, is_active) VALUES
    ('11111111-1111-1111-1111-111111111101', 'Sedan', 'Xe du lịch 4 - 5 chỗ nhỏ gọn, tiết kiệm nhiên liệu', TRUE),
    ('11111111-1111-1111-1111-111111111102', 'SUV', 'Xe gầm cao 5 - 7 chỗ mạnh mẽ, thể thao', TRUE),
    ('11111111-1111-1111-1111-111111111103', 'MPV', 'Xe đa dụng 7 chỗ gia đình rộng rãi', TRUE),
    ('11111111-1111-1111-1111-111111111104', 'Pickup', 'Xe bán tải chở hàng và địa hình', TRUE),
    ('11111111-1111-1111-1111-111111111105', 'Electric', 'Xe ô tô điện thông minh, thân thiện môi trường', TRUE)
ON CONFLICT (name) DO NOTHING;

-- 1.2 Danh mục Quyền nguyên tử toàn hệ thống (permissions)
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

-- 1.3 Tài khoản Super Admin toàn hệ thống (Mật khẩu mặc định: password123)
INSERT INTO users (id, email, password_hash, full_name, phone, is_active, is_super_admin) VALUES
    ('00000000-0000-0000-0000-000000000001', 'superadmin@carrental.vn', '$2a$12$FSNDE/aJ92.77LvXPDhcmuxky2fmxCgyxcwY61pzd1a7Q.YPofSuG', 'Hệ Thống Super Admin', '0900000000', TRUE, TRUE)
ON CONFLICT (email) DO NOTHING;

-- ============================================================================
-- 2. SEED DEMO TENANT: RENTCAR HÀ NỘI (Phục vụ phát triển & kiểm thử API)
-- ============================================================================

-- 2.1 Tạo Nhà xe Demo: RentCar Hà Nội
INSERT INTO tenants (id, name, domain, plan_tier, contact_email, contact_phone, is_active) VALUES
    ('e41492d0-67ee-4699-b311-fee98cb37781', 'RentCar Hà Nội', 'hanoi.carrental.local', 2, 'contact@rentcarhanoi.vn', '0901000001', TRUE)
ON CONFLICT (domain) DO NOTHING;

-- 2.2 Cấu hình Nhà xe Demo (tenant_configs)
INSERT INTO tenant_configs (tenant_id, hold_timeout_minutes, late_hourly_price, default_commission_rate) VALUES
    ('e41492d0-67ee-4699-b311-fee98cb37781', 30, 100000, 5.00)
ON CONFLICT (tenant_id) DO NOTHING;

-- 2.3 Chi nhánh Nhà xe Demo (branches)
INSERT INTO branches (id, tenant_id, name, address, phone, email, latitude, longitude, is_active) VALUES
    ('33b5b9fe-c6bc-42fa-978e-8e0ea092dc6a', 'e41492d0-67ee-4699-b311-fee98cb37781', 'Chi nhánh Cầu Giấy', 'Số 1 Đường Cầu Giấy, Hà Nội', '0241000001', 'caugiay@rentcarhanoi.vn', 21.033333, 105.783333, TRUE),
    ('ea0ecc7d-bffa-40ea-9d52-3325703e7787', 'e41492d0-67ee-4699-b311-fee98cb37781', 'Chi nhánh Đống Đa', 'Số 2 Đường Xã Đàn, Hà Nội', '0241000002', 'dongda@rentcarhanoi.vn', 21.016667, 105.833333, TRUE)
ON CONFLICT (id, tenant_id) DO NOTHING;

-- 2.4 Tạo 3 Role mặc định cho Nhà xe RentCar Hà Nội
INSERT INTO roles (id, tenant_id, name, code, description, is_system_default) VALUES
    ('aaaaaaaa-1111-1111-1111-000000000001', 'e41492d0-67ee-4699-b311-fee98cb37781', 'Chủ nhà xe (Admin)', 'TENANT_ADMIN', 'Toàn quyền quản lý nhà xe RentCar Hà Nội', TRUE),
    ('aaaaaaaa-1111-1111-1111-000000000002', 'e41492d0-67ee-4699-b311-fee98cb37781', 'Nhân viên bãi xe', 'STAFF', 'Tác nghiệp giao nhận xe và quản lý bãi', TRUE),
    ('aaaaaaaa-1111-1111-1111-000000000003', 'e41492d0-67ee-4699-b311-fee98cb37781', 'Chuyên viên kinh doanh', 'SALE', 'Tạo đơn đặt xe và hưởng hoa hồng', TRUE)
ON CONFLICT (tenant_id, code) DO NOTHING;

-- 2.5 Phân quyền cho từng Role (role_permissions)
-- TENANT_ADMIN: Full quyền
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'aaaaaaaa-1111-1111-1111-000000000001', id FROM permissions
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- STAFF: Các quyền tác nghiệp đơn hàng và xem xe
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'aaaaaaaa-1111-1111-1111-000000000002', id FROM permissions 
WHERE code IN ('booking:read', 'booking:create', 'booking:confirm_deposit', 'booking:handover', 'booking:return', 'booking:cancel', 'vehicle:read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- SALE: Quyền tạo đơn, xem xe và hủy đơn
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'aaaaaaaa-1111-1111-1111-000000000003', id FROM permissions 
WHERE code IN ('booking:read', 'booking:create', 'booking:cancel', 'vehicle:read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 2.6 Tạo Người dùng Demo (Mật khẩu: password123)
INSERT INTO users (id, email, password_hash, full_name, phone, is_active) VALUES
    ('c8df7bb9-20f8-485b-972f-2ddac689b1db', 'admin@rentcarhanoi.vn', '$2a$12$FSNDE/aJ92.77LvXPDhcmuxky2fmxCgyxcwY61pzd1a7Q.YPofSuG', 'Trần Văn Quản Lý', '0901111111', TRUE),
    ('4aec2a36-fa3b-4b26-a0d9-f0b063971d9a', 'staff@rentcarhanoi.vn', '$2a$12$FSNDE/aJ92.77LvXPDhcmuxky2fmxCgyxcwY61pzd1a7Q.YPofSuG', 'Lê Văn Nhân Viên', '0902222222', TRUE),
    ('906aa116-278b-4ebb-9712-8047cbf39465', 'sale@rentcarhanoi.vn', '$2a$12$FSNDE/aJ92.77LvXPDhcmuxky2fmxCgyxcwY61pzd1a7Q.YPofSuG', 'Nguyễn Thị Kinh Doanh', '0903333333', TRUE)
ON CONFLICT (email) DO NOTHING;

-- 2.7 Gán Người dùng vào Nhà xe (user_tenants)
INSERT INTO user_tenants (user_id, tenant_id, role_id) VALUES
    ('c8df7bb9-20f8-485b-972f-2ddac689b1db', 'e41492d0-67ee-4699-b311-fee98cb37781', 'aaaaaaaa-1111-1111-1111-000000000001'),
    ('4aec2a36-fa3b-4b26-a0d9-f0b063971d9a', 'e41492d0-67ee-4699-b311-fee98cb37781', 'aaaaaaaa-1111-1111-1111-000000000002'),
    ('906aa116-278b-4ebb-9712-8047cbf39465', 'e41492d0-67ee-4699-b311-fee98cb37781', 'aaaaaaaa-1111-1111-1111-000000000003')
ON CONFLICT (user_id, tenant_id) DO NOTHING;

-- 2.8 Phân công Chi nhánh cho Nhân viên STAFF (user_branches)
INSERT INTO user_branches (user_id, tenant_id, branch_id, status) VALUES
    ('4aec2a36-fa3b-4b26-a0d9-f0b063971d9a', 'e41492d0-67ee-4699-b311-fee98cb37781', '33b5b9fe-c6bc-42fa-978e-8e0ea092dc6a', 1),
    ('4aec2a36-fa3b-4b26-a0d9-f0b063971d9a', 'e41492d0-67ee-4699-b311-fee98cb37781', 'ea0ecc7d-bffa-40ea-9d52-3325703e7787', 1)
ON CONFLICT (user_id, branch_id) DO NOTHING;

-- 2.9 Tạo Khách hàng Demo (customers)
INSERT INTO customers (id, tenant_id, name, phone, email, address, is_risk) VALUES
    ('ccccccc1-1111-1111-1111-000000000001', 'e41492d0-67ee-4699-b311-fee98cb37781', 'Phạm Hoàng Khách', '0988888888', 'khachhang@gmail.com', 'Hà Nội', FALSE),
    ('ccccccc2-1111-1111-1111-000000000002', 'e41492d0-67ee-4699-b311-fee98cb37781', 'Lê Bị Cảnh Báo', '0977777777', 'canhbao@gmail.com', 'Hà Nội', TRUE)
ON CONFLICT (id, tenant_id) DO NOTHING;

-- 2.10 Tạo Xe Demo (vehicles)
INSERT INTO vehicles (id, tenant_id, branch_id, vehicle_type_id, license_plate, model, color, year, price_per_day, status) VALUES
    ('bbbbbbb1-1111-1111-1111-000000000001', 'e41492d0-67ee-4699-b311-fee98cb37781', '33b5b9fe-c6bc-42fa-978e-8e0ea092dc6a', '11111111-1111-1111-1111-111111111101', '30F-123.45', 'Mazda 3', 'Trắng', 2023, 800000, 1),
    ('bbbbbbb2-1111-1111-1111-000000000002', 'e41492d0-67ee-4699-b311-fee98cb37781', '33b5b9fe-c6bc-42fa-978e-8e0ea092dc6a', '11111111-1111-1111-1111-111111111102', '30F-999.99', 'Hyundai SantaFe', 'Đen', 2024, 1300000, 1),
    ('bbbbbbb3-1111-1111-1111-000000000003', 'e41492d0-67ee-4699-b311-fee98cb37781', 'ea0ecc7d-bffa-40ea-9d52-3325703e7787', '11111111-1111-1111-1111-111111111105', '30H-888.88', 'VinFast VF8', 'Xanh lam', 2024, 1100000, 1)
ON CONFLICT (id, tenant_id) DO NOTHING;

