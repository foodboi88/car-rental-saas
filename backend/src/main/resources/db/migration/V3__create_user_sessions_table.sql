CREATE TABLE user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),               -- Mã phiên đăng nhập (Session ID) được đính kèm vào Claim current_session_id của JWT
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,-- Khóa ngoại tham chiếu người dùng sở hữu phiên
    device_name VARCHAR(255) NOT NULL DEFAULT 'Unknown Device',  -- Tên thiết bị và trình duyệt (Backend tự bóc tách từ Header User-Agent)
    ip_address VARCHAR(45),                                      -- Địa chỉ IP của thiết bị khi đăng nhập (IPv4 hoặc IPv6)
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,                   -- Công tắc thu hồi: FALSE (đang active), TRUE (đã đăng xuất / bị đào thải)
    last_active_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tương tác gần nhất (Throttle 5 phút, căn cứ chạy LRU)
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,                -- Thời điểm hết hạn phiên (Sliding Expiration: now + refresh-token-expiration-ms)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP        -- Thời điểm phiên được khởi tạo lúc đăng nhập
);

-- Composite Index phục vụ tra cứu phiên theo user_id (left-most prefix), đếm phiên active và đào thải LRU
CREATE INDEX idx_user_sessions_lru ON user_sessions(user_id, is_revoked, last_active_at);

-- Index phục vụ Scheduled Cleanup Job quét bản ghi hết hạn hoặc đã thu hồi quá hạn audit
CREATE INDEX idx_user_sessions_cleanup ON user_sessions(expires_at, is_revoked);