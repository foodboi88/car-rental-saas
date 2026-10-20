-- Migration V7: Add Granular Permissions for Account Management
-- - Bổ sung các quyền nguyên tử (Atomic Permissions) cho module Account: account:read, account:create, account:update, account:delete
-- - Gán các quyền này vào role_permissions cho vai trò TENANT_ADMIN của tất cả các Tenant

BEGIN;

-- 1. Thêm các quyền nguyên tử cho Module Account vào bảng permissions
INSERT INTO permissions (id, code, name, category, description) VALUES
    (gen_random_uuid(), 'account:read',   'Xem thông tin nhân sự',      'ACCOUNT', 'Xem danh sách, tìm kiếm và xem chi tiết nhân sự trong nhà xe'),
    (gen_random_uuid(), 'account:create', 'Tạo mới nhân sự',            'ACCOUNT', 'Tạo tài khoản nhân viên STAFF gắn chi nhánh và CTV SALE'),
    (gen_random_uuid(), 'account:update', 'Cập nhật nhân sự',           'ACCOUNT', 'Chỉnh sửa thông tin, vai trò và điều chuyển chi nhánh làm việc'),
    (gen_random_uuid(), 'account:delete', 'Thu hồi / Xóa nhân sự',      'ACCOUNT', 'Xóa hoặc thu hồi quyền của nhân sự khỏi nhà xe')
ON CONFLICT (code) DO UPDATE 
SET name = EXCLUDED.name,
    category = EXCLUDED.category,
    description = EXCLUDED.description;

-- 2. Gán toàn bộ quyền Account mới cho các Role TENANT_ADMIN hiện có trong hệ thống
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.code = 'TENANT_ADMIN'
  AND p.code IN ('account:read', 'account:create', 'account:update', 'account:delete', 'account:manage')
ON CONFLICT (role_id, permission_id) DO NOTHING;

COMMIT;

