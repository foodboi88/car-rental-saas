-- Migration V6: Align Database Schema with docs/overviews/Database-Schema.md
-- - Lược bỏ các cột thừa trong bảng branches (code, city, district, ward, opening_hours, status, is_central, is_deleted)
-- - Xóa index idx_branches_is_central
-- - Chuẩn hóa plan_tier trong bảng tenants sang SMALLINT

BEGIN;

-- 1. Xóa index không còn sử dụng
DROP INDEX IF EXISTS idx_branches_is_central;

-- 2. Lược bỏ các cột không thuộc Database-Schema.md ở bảng branches
ALTER TABLE branches DROP COLUMN IF EXISTS code;
ALTER TABLE branches DROP COLUMN IF EXISTS city;
ALTER TABLE branches DROP COLUMN IF EXISTS district;
ALTER TABLE branches DROP COLUMN IF EXISTS ward;
ALTER TABLE branches DROP COLUMN IF EXISTS opening_hours;
ALTER TABLE branches DROP COLUMN IF EXISTS status;
ALTER TABLE branches DROP COLUMN IF EXISTS is_central;
ALTER TABLE branches DROP COLUMN IF EXISTS is_deleted;

-- 3. Chuẩn hóa plan_tier sang SMALLINT
ALTER TABLE tenants ALTER COLUMN plan_tier TYPE SMALLINT;

COMMIT;
