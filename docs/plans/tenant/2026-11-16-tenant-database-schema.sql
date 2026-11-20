-- THIẾT KẾ DATABASE: MODULE TENANT
-- PostgreSQL 15+.
-- Nguồn: backend/src/main/resources/db/migration/V1 đến V7.
-- Phạm vi: 7 bảng phục vụ thông tin nhà xe, cấu hình, tài khoản và phân quyền.
-- Chạy độc lập trên database trống; không đặt file này vào thư mục Flyway.
-- Không chứa dữ liệu mẫu, không thay thế lịch sử migration của ứng dụng.
-- Giữ kiểu dữ liệu, nullability, mặc định, PK, FK, UNIQUE, CHECK và index
-- theo migration; các chú thích giải thích thiết kế, không bổ sung ràng buộc.

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;
SET LOCAL search_path = public;

-- 1. Nhà xe. Nguồn: V1; V6 đổi plan_tier sang SMALLINT.
CREATE TABLE tenants (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), -- Định danh nhà xe.
    name VARCHAR(255) NOT NULL, -- Tên nhà xe.
    domain VARCHAR(255) UNIQUE NOT NULL, -- Domain duy nhất toàn hệ thống.
    plan_tier SMALLINT NOT NULL DEFAULT 1
        CHECK (plan_tier IN (1, 2, 3, 4)), -- FREE, BASIC, PRO, ENTERPRISE.
    logo_url VARCHAR(500), -- Đường dẫn logo.
    contact_email VARCHAR(255), -- Email liên hệ của nhà xe.
    contact_phone VARCHAR(20), -- Số điện thoại liên hệ của nhà xe.
    settings JSONB DEFAULT '{}'::jsonb, -- Cấu hình mở rộng dạng JSON.
    is_active BOOLEAN DEFAULT TRUE, -- Trạng thái hoạt động; false dùng cho khóa/xóa mềm.
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo.
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP -- Thời điểm cập nhật.
);

-- V1 tạo thêm index này dù UNIQUE(domain) tự tạo một unique index khác.
CREATE UNIQUE INDEX idx_tenants_domain ON tenants(domain);

-- 2. Tài khoản dùng chung nhiều nhà xe. Nguồn: V1, V7.
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), -- Định danh tài khoản.
    email VARCHAR(255) UNIQUE NOT NULL, -- Email đăng nhập duy nhất.
    password_hash VARCHAR(255) NOT NULL, -- Mật khẩu đã băm, không lưu mật khẩu thô.
    full_name VARCHAR(255), -- Họ tên người dùng.
    phone VARCHAR(20), -- Số điện thoại cá nhân.
    avatar_url VARCHAR(500), -- Đường dẫn ảnh đại diện.
    is_active BOOLEAN DEFAULT TRUE, -- Trạng thái hoạt động của tài khoản.
    is_super_admin BOOLEAN DEFAULT FALSE, -- Quyền quản trị toàn nền tảng.
    last_login_at TIMESTAMPTZ, -- Thời điểm đăng nhập gần nhất.
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo.
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, -- Thời điểm cập nhật.
    current_session_id UUID -- Định danh phiên đăng nhập gần nhất, bổ sung tại V7.
);

-- Index thường từ V1; UNIQUE(email) cũng tự tạo unique index.
CREATE INDEX idx_users_email ON users(email);

-- 3. Cấu hình vận hành, tối đa một bản ghi cho mỗi nhà xe. Nguồn: V5.
CREATE TABLE tenant_configs (
    tenant_id UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE, -- Nhà xe sở hữu cấu hình.
    hold_timeout_minutes INTEGER NOT NULL DEFAULT 30, -- Số phút giữ chỗ đơn đặt xe.
    late_hourly_price DECIMAL(12, 2) NOT NULL DEFAULT 100000, -- Phí trả trễ mỗi giờ, đồng.
    default_commission_rate DECIMAL(5, 2) NOT NULL DEFAULT 5.00, -- Hoa hồng phần trăm: 5.00 nghĩa là 5%.
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo.
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP -- Thời điểm cập nhật.
);

-- 4. Vai trò. Nguồn: V5.
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), -- Định danh vai trò.
    tenant_id UUID REFERENCES tenants(id) ON DELETE CASCADE, -- Nhà xe sở hữu; migration cho phép NULL.
    name VARCHAR(100) NOT NULL, -- Tên hiển thị vai trò.
    code VARCHAR(50) NOT NULL, -- Mã vai trò, ví dụ TENANT_ADMIN, STAFF, SALE.
    description TEXT, -- Mô tả trách nhiệm của vai trò.
    is_system_default BOOLEAN DEFAULT FALSE, -- Đánh dấu vai trò mặc định do hệ thống tạo.
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tạo.
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP -- Thời điểm cập nhật.
);

CREATE INDEX idx_roles_tenant_id ON roles(tenant_id);
CREATE UNIQUE INDEX idx_roles_tenant_code ON roles(tenant_id, code);

-- 5. Danh mục quyền dùng chung. Nguồn: V5.
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(), -- Định danh quyền.
    code VARCHAR(100) UNIQUE NOT NULL, -- Mã quyền duy nhất, ví dụ booking:read.
    name VARCHAR(255) NOT NULL, -- Tên hiển thị quyền.
    category VARCHAR(50) NOT NULL, -- Nhóm nghiệp vụ như BOOKING, VEHICLE, ACCOUNT.
    description TEXT -- Mô tả hành động được phép thực hiện.
);

-- UNIQUE(code) tự tạo unique index; giữ thêm index này theo V5.
CREATE UNIQUE INDEX idx_permissions_code ON permissions(code);

-- 6. Liên kết nhiều-nhiều giữa vai trò và quyền. Nguồn: V5.
CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE, -- Vai trò nhận quyền.
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE, -- Quyền được gán.
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX idx_role_permissions_permission_id ON role_permissions(permission_id);

-- 7. Thành viên nhà xe. Nguồn: V1; V5 thay role số nguyên bằng role_id.
CREATE TABLE user_tenants (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE, -- Tài khoản được liên kết.
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE, -- Nhà xe tiếp nhận thành viên.
    joined_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP, -- Thời điểm gia nhập nhà xe.
    role_id UUID NOT NULL REFERENCES roles(id), -- Vai trò của tài khoản trong nhà xe.
    PRIMARY KEY (user_id, tenant_id),
    CONSTRAINT unique_user_tenant_role UNIQUE (user_id, tenant_id, role_id)
);

CREATE INDEX idx_user_tenants_tenant_id ON user_tenants(tenant_id);
CREATE INDEX idx_user_tenants_role_id ON user_tenants(role_id);

COMMIT;

-- GHI CHÚ ĐỂ ĐỌC THIẾT KẾ
-- 1. PostgreSQL tự tạo index cho PK và UNIQUE; không cần viết CREATE INDEX
--    riêng cho các ràng buộc đó. Các CREATE INDEX ở trên là index bổ sung.
-- 2. idx_tenants_domain, idx_users_email, idx_permissions_code trùng khóa
--    tìm kiếm với index của UNIQUE tương ứng. Giữ lại để khớp migration.
-- 3. idx_roles_tenant_id có thể được phủ bởi tiền tố của idx_roles_tenant_code;
--    đánh giá nhu cầu bằng truy vấn thực tế trước khi thay đổi migration.
-- 4. unique_user_tenant_role không tăng bảo đảm tính duy nhất so với PK
--    (user_id, tenant_id), nhưng được giữ nguyên theo V5.
-- 5. UNIQUE(tenant_id, code) trên roles cho phép nhiều bản ghi có tenant_id NULL
--    và cùng code. Không tự bổ sung quy tắc riêng cho role hệ thống.
-- 6. FK user_tenants.role_id chỉ kiểm tra role tồn tại, không kiểm tra role
--    thuộc cùng tenant. Service phải chọn role theo cả tenant_id và code.
-- 7. Migration không đặt CHECK không âm cho các giá trị trong tenant_configs;
--    không tự thêm CHECK vào bản tổng hợp này.
-- 8. DEFAULT CURRENT_TIMESTAMP không tự cập nhật updated_at khi UPDATE.
--    Tầng ứng dụng quản lý giá trị cập nhật; không thêm trigger vào script.
-- 9. FK tenant_configs chỉ bảo đảm tối đa một cấu hình cho mỗi tenant.
--    Việc tạo đủ cấu hình, ba role, quyền và ít nhất một chủ thuộc transaction
--    nghiệp vụ; không có trigger tự khởi tạo các dữ liệu này.
-- 10. V2 chứa dữ liệu mẫu; V3, V4 chủ yếu thay đổi Booking/Vehicle;
--     V5 tạo cấu hình và RBAC; V6 đổi plan_tier; V7 thêm current_session_id.
--     Branch, Vehicle và Booking tham chiếu các bảng này nhưng không thuộc
--     phạm vi schema Tenant ở đây.
