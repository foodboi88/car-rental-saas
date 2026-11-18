-- 1. Bỏ 2 bảng không dùng đến
DROP TABLE IF EXISTS payments CASCADE;
DROP TABLE IF EXISTS pricing_rules CASCADE;
-- 2. Cập nhật bảng vehicle_types (bỏ base_price)
ALTER TABLE vehicle_types DROP COLUMN IF EXISTS base_price;
-- 3. Cập nhật bảng vehicles (thêm giá theo xe)
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS price_per_day DECIMAL(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS weekend_price_per_day DECIMAL(12, 2);
-- 4. Cập nhật bảng customers (thêm ảnh giấy tờ)
ALTER TABLE customers ADD COLUMN IF NOT EXISTS id_card_images JSONB DEFAULT '[]'::jsonb;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS driver_license_images JSONB DEFAULT '[]'::jsonb;
-- 5. Cập nhật bảng bookings (đổi tên cột cọc & thêm các cột nghiệp vụ bàn giao/thế chấp)
ALTER TABLE bookings RENAME COLUMN deposit_paid TO is_deposit_paid;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS daily_rate DECIMAL(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS collateral_type SMALLINT DEFAULT 1 CHECK (collateral_type IN (1, 2, 3));
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS collateral_notes TEXT;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS actual_handover_at TIMESTAMPTZ;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS actual_return_at TIMESTAMPTZ;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS handover_images JSONB DEFAULT '[]'::jsonb;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS return_images JSONB DEFAULT '[]'::jsonb;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS extra_km_fee DECIMAL(12, 2) DEFAULT 0;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS commission_amount DECIMAL(12, 2) DEFAULT 0;
