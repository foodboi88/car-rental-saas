-- DỮ LIỆU MẪU CHỈ DÀNH CHO MÔI TRƯỜNG DEV.
-- Thư mục này chỉ được Flyway đọc khi chạy với profile `dev` (xem application-dev.yaml), production không bao giờ chạy.
-- Mật khẩu chung của mọi tài khoản mẫu là `Hieudvt@123`: sao từ hash của minh.admin (đã có sẵn từ V2).
-- Viết theo kiểu chạy lại được (ON CONFLICT DO NOTHING) để DB dev đã tự thêm dữ liệu tương tự vẫn không bị lỗi.

-- 1. Tài khoản mẫu ghi trong README, cộng hai chủ nhà xe thử
INSERT INTO users (email, password_hash, full_name, is_active, is_super_admin)
SELECT v.email, (SELECT password_hash FROM users WHERE email = 'minh.admin@rentcarhanoi.vn'), v.full_name, v.is_active, v.is_super_admin
FROM (VALUES
  ('superadmin@carrental.vn',      'Super Admin',         TRUE,  TRUE),
  ('multi.user@carrental.vn',      'Multi User',          TRUE,  FALSE),
  ('locked.user@rentcarhanoi.vn',  'Locked User',         FALSE, FALSE),
  ('chu.thu@thunghiem.vn',         'Chủ Thử Nghiệm',      TRUE,  FALSE),
  ('chu.khoa@thunghiem.vn',        'Chủ nhà xe bị khóa',  TRUE,  FALSE)
) AS v(email, full_name, is_active, is_super_admin)
ON CONFLICT (email) DO NOTHING;

-- 2. Hai nhà xe thử: một đang hoạt động (chưa có chi nhánh), một đã bị khóa (plan_tier = 1 là FREE)
INSERT INTO tenants (name, domain, plan_tier, is_active) VALUES
  ('Thử nghiệm',           'thu-nghiem.carrental.local',      1, TRUE),
  ('Thử nghiệm (đã khóa)', 'thu-nghiem-khoa.carrental.local', 1, FALSE)
ON CONFLICT (domain) DO NOTHING;

INSERT INTO tenant_configs (tenant_id, hold_timeout_minutes, late_hourly_price, default_commission_rate)
SELECT id, 30, 100000, 5.00 FROM tenants WHERE domain LIKE 'thu-nghiem%'
ON CONFLICT (tenant_id) DO NOTHING;

-- 3. Ba vai trò và quyền cho từng nhà xe thử, sao theo bộ vai trò của nhà xe Hà Nội
INSERT INTO roles (tenant_id, name, code, description, is_system_default)
SELECT nt.id, r.name, r.code, r.description, r.is_system_default
FROM tenants nt
JOIN roles r ON r.tenant_id = (SELECT id FROM tenants WHERE domain = 'hanoi.carrental.local')
WHERE nt.domain LIKE 'thu-nghiem%'
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT nr.id, rp.permission_id
FROM roles nr
JOIN tenants nt ON nt.id = nr.tenant_id AND nt.domain LIKE 'thu-nghiem%'
JOIN roles hr ON hr.code = nr.code AND hr.tenant_id = (SELECT id FROM tenants WHERE domain = 'hanoi.carrental.local')
JOIN role_permissions rp ON rp.role_id = hr.id
ON CONFLICT DO NOTHING;

-- 4. Gán người vào nhà xe (Super Admin không thuộc nhà xe nào)
INSERT INTO user_tenants (user_id, tenant_id, role_id, joined_at)
SELECT u.id, t.id, r.id, NOW()
FROM (VALUES
  ('multi.user@carrental.vn',     'hanoi.carrental.local',           'STAFF'),
  ('multi.user@carrental.vn',     'saigon.carrental.local',          'STAFF'),
  ('locked.user@rentcarhanoi.vn', 'hanoi.carrental.local',           'STAFF'),
  ('chu.thu@thunghiem.vn',        'thu-nghiem.carrental.local',      'TENANT_ADMIN'),
  ('chu.khoa@thunghiem.vn',       'thu-nghiem-khoa.carrental.local', 'TENANT_ADMIN')
) AS m(email, domain, role_code)
JOIN users u ON u.email = m.email
JOIN tenants t ON t.domain = m.domain
JOIN roles r ON r.tenant_id = t.id AND r.code = m.role_code
ON CONFLICT (user_id, tenant_id) DO NOTHING;
