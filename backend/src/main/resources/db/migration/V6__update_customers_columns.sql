-- Cập nhật bảng customers theo docs/overviews/Database-Schema.md (mục 2.11)
-- Bảng customers đã tồn tại từ V1 (schema cũ) nên dùng ALTER thay vì CREATE.
-- Phạm vi CRUD cơ bản: loại bỏ id_card, driver_license (được thiết kế để lưu dữ liệu
-- mã hóa AES-256) - việc mã hóa để dành cho plan riêng sau này, không thêm cột mới cho nó ở đây.

ALTER TABLE customers DROP COLUMN id_card;
ALTER TABLE customers DROP COLUMN driver_license;
ALTER TABLE customers DROP COLUMN is_active;

ALTER TABLE customers ADD COLUMN risk_level SMALLINT NOT NULL DEFAULT 1
    CHECK (risk_level IN (1, 2, 3)); -- 1:SAFE, 2:WARNING, 3:BLACKLIST
ALTER TABLE customers ADD COLUMN blacklist_reason TEXT;

CREATE INDEX idx_customers_risk_level ON customers(tenant_id, risk_level);
