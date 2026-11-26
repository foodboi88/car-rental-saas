-- Migration V4: Bổ sung cột version vào bảng users để hỗ trợ Optimistic Lock (Chống ghi đè đồng thời)

BEGIN;

ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

COMMIT;

