-- Migration V7: Bổ sung current_session_id vào bảng users để quản lý Single Session (Chống đăng nhập đồng thời trên nhiều thiết bị)

BEGIN;

-- Bổ sung cột current_session_id để lưu định danh phiên đăng nhập gần nhất của người dùng
ALTER TABLE users ADD COLUMN IF NOT EXISTS current_session_id UUID;

COMMIT;
