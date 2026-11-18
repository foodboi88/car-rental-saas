-- ============================================================================
-- Car Rental SaaS - Squash Migration V2 (Seed System & Demo Data)
-- Target: PostgreSQL 15+
-- Safe execution: INSERT ... ON CONFLICT DO NOTHING (NO TRUNCATE CASCADE)
-- ============================================================================

-- ============================================================================
-- 1. SEED SYSTEM CATALOGS (Dá»¯ liá»‡u danh má»¥c há»‡ thá»‘ng)
-- ============================================================================

-- 1.1 Danh má»¥c Loáº¡i xe System-wide (vehicle_types)
INSERT INTO vehicle_types (id, name, description, is_active) VALUES
    ('11111111-1111-1111-1111-111111111101', 'Sedan', 'Xe du lá»‹ch 4 - 5 chá»— nhá» gá»n, tiáº¿t kiá»‡m nhiÃªn liá»‡u', TRUE),
    ('11111111-1111-1111-1111-111111111102', 'SUV', 'Xe gáº§m cao 5 - 7 chá»— máº¡nh máº½, thá»ƒ thao', TRUE),
    ('11111111-1111-1111-1111-111111111103', 'MPV', 'Xe Ä‘a dá»¥ng 7 chá»— gia Ä‘Ã¬nh rá»™ng rÃ£i', TRUE),
    ('11111111-1111-1111-1111-111111111104', 'Pickup', 'Xe bÃ¡n táº£i chá»Ÿ hÃ ng vÃ  Ä‘á»‹a hÃ¬nh', TRUE),
    ('11111111-1111-1111-1111-111111111105', 'Electric', 'Xe Ã´ tÃ´ Ä‘iá»‡n thÃ´ng minh, thÃ¢n thiá»‡n mÃ´i trÆ°á»ng', TRUE)
ON CONFLICT (name) DO NOTHING;

-- 1.2 Danh má»¥c Quyá»n nguyÃªn tá»­ toÃ n há»‡ thá»‘ng (permissions)
INSERT INTO permissions (code, name, category, description) VALUES
    ('booking:read', 'Xem Ä‘Æ¡n Ä‘áº·t xe', 'BOOKING', 'Xem danh sÃ¡ch vÃ  chi tiáº¿t Ä‘Æ¡n Ä‘áº·t xe trong pháº¡m vi cho phÃ©p'),
    ('booking:create', 'Táº¡o Ä‘Æ¡n Ä‘áº·t xe', 'BOOKING', 'Táº¡o má»›i Ä‘Æ¡n Ä‘áº·t xe giá»¯ chá»— táº¡m (HOLD)'),
    ('booking:confirm_deposit', 'XÃ¡c nháº­n Ä‘áº·t cá»c', 'BOOKING', 'XÃ¡c nháº­n Ä‘Ã£ nháº­n tiá»n cá»c chuyá»ƒn Ä‘Æ¡n sang CONFIRMED'),
    ('booking:handover', 'BÃ n giao xe', 'BOOKING', 'Thá»±c hiá»‡n thá»§ tá»¥c bÃ n giao xe vÃ  chá»¥p áº£nh hiá»‡n tráº¡ng'),
    ('booking:return', 'Nháº­n láº¡i xe', 'BOOKING', 'Thá»±c hiá»‡n thá»§ tá»¥c nháº­n láº¡i xe vÃ  quyáº¿t toÃ¡n pháº¡t trá»… giá»'),
    ('booking:cancel', 'Há»§y Ä‘Æ¡n Ä‘áº·t xe', 'BOOKING', 'Há»§y Ä‘Æ¡n Ä‘áº·t xe'),
    ('vehicle:read', 'Xem thÃ´ng tin xe', 'VEHICLE', 'Xem danh sÃ¡ch vÃ  thÃ´ng tin chi tiáº¿t xe'),
    ('vehicle:manage', 'Quáº£n lÃ½ Ä‘á»™i xe', 'VEHICLE', 'ThÃªm, sá»­a, ngÆ°ng khai thÃ¡c xe'),
    ('branch:manage', 'Quáº£n lÃ½ chi nhÃ¡nh', 'BRANCH', 'ThÃªm, sá»­a cáº¥u hÃ¬nh chi nhÃ¡nh/bÃ£i xe'),
    ('account:manage', 'Quáº£n lÃ½ nhÃ¢n sá»±', 'ACCOUNT', 'Táº¡o tÃ i khoáº£n vÃ  phÃ¢n quyá»n nhÃ¢n viÃªn'),
    ('report:view_revenue', 'Xem bÃ¡o cÃ¡o doanh thu', 'REPORT', 'Xem thá»‘ng kÃª doanh thu vÃ  chá»‰ sá»‘ kinh doanh')
ON CONFLICT (code) DO NOTHING;

-- 1.3 TÃ i khoáº£n Super Admin toÃ n há»‡ thá»‘ng (Máº­t kháº©u máº·c Ä‘á»‹nh: password123)
INSERT INTO users (id, email, password_hash, full_name, phone, is_active, is_super_admin) VALUES
    ('00000000-0000-0000-0000-000000000001', 'superadmin@carrental.vn', '$2a$12$FSNDE/aJ92.77LvXPDhcmuxky2fmxCgyxcwY61pzd1a7Q.YPofSuG', 'Há»‡ Thá»‘ng Super Admin', '0900000000', TRUE, TRUE)
ON CONFLICT (email) DO NOTHING;

-- ============================================================================
-- 2. SEED DEMO TENANT: RENTCAR HÃ€ Ná»˜I (Phá»¥c vá»¥ phÃ¡t triá»ƒn & kiá»ƒm thá»­ API)
-- ============================================================================

-- 2.1 Táº¡o NhÃ  xe Demo: RentCar HÃ  Ná»™i
INSERT INTO tenants (id, name, domain, plan_tier, contact_email, contact_phone, is_active) VALUES
    ('e41492d0-67ee-4699-b311-fee98cb37781', 'RentCar HÃ  Ná»™i', 'hanoi.carrental.local', 2, 'contact@rentcarhanoi.vn', '0901000001', TRUE)
ON CONFLICT (domain) DO NOTHING;

-- 2.2 Cáº¥u hÃ¬nh NhÃ  xe Demo (tenant_configs)
INSERT INTO tenant_configs (tenant_id, hold_timeout_minutes, late_hourly_price, default_commission_rate) VALUES
    ('e41492d0-67ee-4699-b311-fee98cb37781', 30, 100000, 5.00)
ON CONFLICT (tenant_id) DO NOTHING;

-- 2.3 Chi nhÃ¡nh NhÃ  xe Demo (branches)
INSERT INTO branches (id, tenant_id, name, address, phone, email, latitude, longitude, is_active) VALUES
    ('33b5b9fe-c6bc-42fa-978e-8e0ea092dc6a', 'e41492d0-67ee-4699-b311-fee98cb37781', 'Chi nhÃ¡nh Cáº§u Giáº¥y', 'Sá»‘ 1 ÄÆ°á»ng Cáº§u Giáº¥y, HÃ  Ná»™i', '0241000001', 'caugiay@rentcarhanoi.vn', 21.033333, 105.783333, TRUE),
    ('ea0ecc7d-bffa-40ea-9d52-3325703e7787', 'e41492d0-67ee-4699-b311-fee98cb37781', 'Chi nhÃ¡nh Äá»‘ng Äa', 'Sá»‘ 2 ÄÆ°á»ng XÃ£ ÄÃ n, HÃ  Ná»™i', '0241000002', 'dongda@rentcarhanoi.vn', 21.016667, 105.833333, TRUE)
ON CONFLICT (id, tenant_id) DO NOTHING;

-- 2.4 Táº¡o 3 Role máº·c Ä‘á»‹nh cho NhÃ  xe RentCar HÃ  Ná»™i
INSERT INTO roles (id, tenant_id, name, code, description, is_system_default) VALUES
    ('aaaaaaaa-1111-1111-1111-000000000001', 'e41492d0-67ee-4699-b311-fee98cb37781', 'Chá»§ nhÃ  xe (Admin)', 'TENANT_ADMIN', 'ToÃ n quyá»n quáº£n lÃ½ nhÃ  xe RentCar HÃ  Ná»™i', TRUE),
    ('aaaaaaaa-1111-1111-1111-000000000002', 'e41492d0-67ee-4699-b311-fee98cb37781', 'NhÃ¢n viÃªn bÃ£i xe', 'STAFF', 'TÃ¡c nghiá»‡p giao nháº­n xe vÃ  quáº£n lÃ½ bÃ£i', TRUE),
    ('aaaaaaaa-1111-1111-1111-000000000003', 'e41492d0-67ee-4699-b311-fee98cb37781', 'ChuyÃªn viÃªn kinh doanh', 'SALE', 'Táº¡o Ä‘Æ¡n Ä‘áº·t xe vÃ  hÆ°á»Ÿng hoa há»“ng', TRUE)
ON CONFLICT (tenant_id, code) DO NOTHING;

-- 2.5 PhÃ¢n quyá»n cho tá»«ng Role (role_permissions)
-- TENANT_ADMIN: Full quyá»n
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'aaaaaaaa-1111-1111-1111-000000000001', id FROM permissions
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- STAFF: CÃ¡c quyá»n tÃ¡c nghiá»‡p Ä‘Æ¡n hÃ ng vÃ  xem xe
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'aaaaaaaa-1111-1111-1111-000000000002', id FROM permissions 
WHERE code IN ('booking:read', 'booking:create', 'booking:confirm_deposit', 'booking:handover', 'booking:return', 'booking:cancel', 'vehicle:read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- SALE: Quyá»n táº¡o Ä‘Æ¡n, xem xe vÃ  há»§y Ä‘Æ¡n
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'aaaaaaaa-1111-1111-1111-000000000003', id FROM permissions 
WHERE code IN ('booking:read', 'booking:create', 'booking:cancel', 'vehicle:read')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 2.6 Táº¡o NgÆ°á»i dÃ¹ng Demo (Máº­t kháº©u: password123)
INSERT INTO users (id, email, password_hash, full_name, phone, is_active) VALUES
    ('c8df7bb9-20f8-485b-972f-2ddac689b1db', 'admin@rentcarhanoi.vn', '$2a$12$FSNDE/aJ92.77LvXPDhcmuxky2fmxCgyxcwY61pzd1a7Q.YPofSuG', 'Tráº§n VÄƒn Quáº£n LÃ½', '0901111111', TRUE),
    ('4aec2a36-fa3b-4b26-a0d9-f0b063971d9a', 'staff@rentcarhanoi.vn', '$2a$12$FSNDE/aJ92.77LvXPDhcmuxky2fmxCgyxcwY61pzd1a7Q.YPofSuG', 'LÃª VÄƒn NhÃ¢n ViÃªn', '0902222222', TRUE),
    ('906aa116-278b-4ebb-9712-8047cbf39465', 'sale@rentcarhanoi.vn', '$2a$12$FSNDE/aJ92.77LvXPDhcmuxky2fmxCgyxcwY61pzd1a7Q.YPofSuG', 'Nguyá»…n Thá»‹ Kinh Doanh', '0903333333', TRUE)
ON CONFLICT (email) DO NOTHING;

-- 2.7 GÃ¡n NgÆ°á»i dÃ¹ng vÃ o NhÃ  xe (user_tenants)
INSERT INTO user_tenants (user_id, tenant_id, role_id) VALUES
    ('c8df7bb9-20f8-485b-972f-2ddac689b1db', 'e41492d0-67ee-4699-b311-fee98cb37781', 'aaaaaaaa-1111-1111-1111-000000000001'),
    ('4aec2a36-fa3b-4b26-a0d9-f0b063971d9a', 'e41492d0-67ee-4699-b311-fee98cb37781', 'aaaaaaaa-1111-1111-1111-000000000002'),
    ('906aa116-278b-4ebb-9712-8047cbf39465', 'e41492d0-67ee-4699-b311-fee98cb37781', 'aaaaaaaa-1111-1111-1111-000000000003')
ON CONFLICT (user_id, tenant_id) DO NOTHING;

-- 2.8 PhÃ¢n cÃ´ng Chi nhÃ¡nh cho NhÃ¢n viÃªn STAFF (user_branches)
INSERT INTO user_branches (user_id, tenant_id, branch_id, status) VALUES
    ('4aec2a36-fa3b-4b26-a0d9-f0b063971d9a', 'e41492d0-67ee-4699-b311-fee98cb37781', '33b5b9fe-c6bc-42fa-978e-8e0ea092dc6a', 1),
    ('4aec2a36-fa3b-4b26-a0d9-f0b063971d9a', 'e41492d0-67ee-4699-b311-fee98cb37781', 'ea0ecc7d-bffa-40ea-9d52-3325703e7787', 1)
ON CONFLICT (user_id, branch_id) DO NOTHING;

-- 2.9 Táº¡o KhÃ¡ch hÃ ng Demo (customers)
INSERT INTO customers (id, tenant_id, name, phone, email, address, is_risk) VALUES
    ('ccccccc1-1111-1111-1111-000000000001', 'e41492d0-67ee-4699-b311-fee98cb37781', 'Pháº¡m HoÃ ng KhÃ¡ch', '0988888888', 'khachhang@gmail.com', 'HÃ  Ná»™i', FALSE),
    ('ccccccc2-1111-1111-1111-000000000002', 'e41492d0-67ee-4699-b311-fee98cb37781', 'LÃª Bá»‹ Cáº£nh BÃ¡o', '0977777777', 'canhbao@gmail.com', 'HÃ  Ná»™i', TRUE)
ON CONFLICT (id, tenant_id) DO NOTHING;

-- 2.10 Táº¡o Xe Demo (vehicles)
INSERT INTO vehicles (id, tenant_id, branch_id, vehicle_type_id, license_plate, model, color, year, price_per_day, status) VALUES
    ('bbbbbbb1-1111-1111-1111-000000000001', 'e41492d0-67ee-4699-b311-fee98cb37781', '33b5b9fe-c6bc-42fa-978e-8e0ea092dc6a', '11111111-1111-1111-1111-111111111101', '30F-123.45', 'Mazda 3', 'Tráº¯ng', 2023, 800000, 1),
    ('bbbbbbb2-1111-1111-1111-000000000002', 'e41492d0-67ee-4699-b311-fee98cb37781', '33b5b9fe-c6bc-42fa-978e-8e0ea092dc6a', '11111111-1111-1111-1111-111111111102', '30F-999.99', 'Hyundai SantaFe', 'Äen', 2024, 1300000, 1),
    ('bbbbbbb3-1111-1111-1111-000000000003', 'e41492d0-67ee-4699-b311-fee98cb37781', 'ea0ecc7d-bffa-40ea-9d52-3325703e7787', '11111111-1111-1111-1111-111111111105', '30H-888.88', 'VinFast VF8', 'Xanh lam', 2024, 1100000, 1)
ON CONFLICT (id, tenant_id) DO NOTHING;


