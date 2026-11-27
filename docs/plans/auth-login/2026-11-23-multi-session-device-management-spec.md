# 📘 ĐẶC TẢ THIẾT KẾ KỸ THUẬT: PHÂN HỆ AUTH MULTI-SESSION & QUẢN LÝ THIẾT BỊ (DEVICE MANAGEMENT)

> **Dự án:** Car Rental SaaS (Java 17 / Spring Boot 4.0.7 / PostgreSQL)  
> **Module:** `auth` & `security`  
> **Ngày cập nhật:** 25/11/2026  
> **Tác giả:** Tech Lead & Core Engineering Team  
> **Trạng thái:** ✅ Spec Approved — Sẵn sàng lập kế hoạch & triển khai  

---

## 🏛️ 1. BỨC TRANH TOÀN CẢNH & ĐỘNG LỰC THAY ĐỔI (THE "WHY")

### 1.1 Vấn nạn của cơ chế Single Session cũ (`users.current_session_id`)
Ở phiên bản trước, hệ thống sử dụng một cột duy nhất `current_session_id UUID` trên bảng `users`. Mỗi khi có một lượt đăng nhập mới, hệ thống ghi đè UUID mới vào cột này, khiến mọi thiết bị đăng nhập trước đó lập tức bị đá văng ở `JwtAuthenticationFilter`.

### 1.2 Thực tế vận hành nhà xe (Domain Reality)
1. **Sếp / Chủ nhà xe (`TENANT_ADMIN`):**
   - Thường xuyên dùng song song 2 thiết bị: Sáng ngồi làm việc trên Laptop/PC văn phòng, trưa đi gặp khách mở **iPhone** xem doanh thu. Với Single Session, mở điện thoại là PC bị đá văng và ngược lại.
2. **Nhân viên bãi xe (`STAFF`) — *Tử huyệt trải nghiệm người dùng*:**
   - Ngồi quầy dùng **PC để bàn** nhập form hợp đồng (`booking:create`), đồng thời cầm **điện thoại/tablet** ra bãi xe chụp ảnh hiện trạng 4 góc xe để bàn giao (`booking:handover`). Nếu đá phiên lẫn nhau, toàn bộ form hợp đồng đang nhập dở trên PC sẽ mất trắng.
3. **Văn hóa "Tài khoản quầy" (Kiosk Account):**
   - Nhiều chi nhánh dùng chung 1 tài khoản cho ca trực. Nếu 2 nhân viên cùng trực mà đăng nhập đá nhau thì quy trình bàn giao xe bị tê liệt.

$\implies$ **Mục tiêu nâng cấp:** Chuyển đổi sang kiến trúc **Multi-Session có quản lý thiết bị** dựa trên bảng `user_sessions`, cho phép một tài khoản hoạt động đồng thời tối đa **5 thiết bị** theo thuật toán đào thải **LRU (Least Recently Used)**, áp dụng **Sliding Expiration** đồng bộ với Refresh Token TTL, hỗ trợ xem & đăng xuất thiết bị từ xa, chống lệch ngữ cảnh giữa nhiều tab trình duyệt (`X-Tenant-Id`), và dọn dẹp phiên rác định kỳ hàng đêm.

---

## 🔬 2. CÁC QUYẾT ĐỊNH KIẾN TRÚC CỐT LÕI (ARCHITECTURAL DECISIONS)

> [!NOTE]
> **Tham chiếu tách biệt phạm vi:** Các phân tích chuyên sâu về **Concurrency Lock (Optimistic Lock `@Version` vs Pessimistic Lock `SELECT ... FOR UPDATE`)** cho các bảng nghiệp vụ `vehicles`, `bookings`, `customers` đã được tài liệu hóa tập trung tại [Database-Schema.md](../../overviews/Database-Schema.md#47-chiến-lược-quản-lý-phiên-đa-thiết-bị-multi-session--ma-trận-khóa-đồng-thời-concurrency-locking-matrix). Tài liệu này tập trung 100% vào đặc tả phân hệ **Auth Multi-Session & Device Management**.

### 2.1 Tại sao thời hạn phiên là 7 ngày & Chọn Sliding Expiration?
- **Nguồn gốc con số 7 ngày:** Lấy động từ cấu hình `jwt.refresh-token-expiration-ms` (`604800000` ms = 7 ngày) trong `application.yaml` — tuyệt đối **không hardcode số `7`** trong mã Java.
- **Cân bằng UX & Bảo mật cho SaaS B2B:**
  - *Access Token (15 phút):* Sống ngắn vì đi kèm mọi API request.
  - *Refresh Token / Session TTL (7 ngày):* Nhân viên nghỉ cuối tuần hoặc nghỉ phép vài ngày quay lại vẫn không phải đăng nhập lại; nhưng nếu bỏ quên thiết bị/điện thoại **không dùng quá 7 ngày liên tiếp**, phiên sẽ tự hết hạn.
- **Chính sách đã chốt — Sliding Expiration (Gia hạn trượt):**
  - Mỗi thiết bị giữ một cặp `(Access Token, Refresh Token)` độc lập gắn với 1 dòng `user_sessions`.
  - Mỗi lần gọi `/api/v1/auth/refresh-token` thành công, Backend **đồng bộ cả 3 nơi** về cùng mốc `now + refreshTokenExpirationMs`:
    1. `exp` bên trong JWT Refresh Token mới.
    2. `Max-Age` của HttpOnly Cookie `refreshToken`.
    3. Cột `expires_at` (và `last_active_at`) trong bảng `user_sessions`.

### 2.2 Tại sao LOẠI BỎ `refresh_token_hash` (KISS & YAGNI)?
1. JWT là tấm vé tự chứng thực có chữ ký mật mã HMAC-SHA256 xác minh qua `jwtProvider.validateToken()`.
2. Refresh Token đã chứa sẵn claim `current_session_id` (UUID). Khi kiểm tra chữ ký hợp lệ và tra `user_sessions` thấy khớp `(id, user_id)`, `is_revoked = false`, `expires_at > now` là đủ thu hồi tức thì và đảm bảo an toàn cho quy mô dự án.
3. Không cần băm SHA-256 phức tạp và giúp giao diện `SessionManager` gọn nhẹ, sẵn sàng chuyển sang Redis.

### 2.3 Chiến lược Expand-and-Contract (Zero Git Conflict với nhánh `tenant`, `branch`, `user`)
1. **Không xóa cột cũ:** Giữ nguyên cột `current_session_id` trong bảng `users` (không sửa `V1__init_schema.sql`) và gắn `@Deprecated` trên field `currentSessionId` trong `User.java`.
2. **Không đổi API của `TenantContext`:** Giữ nguyên 100% các hàm `getTenantId()`, `getBranchId()`, `getUserId()`, `getRole()`, `getCurrentSessionId()`.
3. **Cô lập tuyệt đối trong package `auth/`:** Toàn bộ Entity, Repository, Service, Scheduler mới (`UserSession`, `UserSessionRepository`, `SessionManager`, `DatabaseSessionManager`, `SessionCleanupTask`, `SessionController`) đặt gọn trong package `auth/` — không đụng vào các file nghiệp vụ ở `tenant/`, `branch/` và hạn chế tối đa chạm file ở `account/`.
4. **Quy tắc Flyway bất biến:** Tuyệt đối không sửa `V1__init_schema.sql` hay `V2__seed_system_data.sql`; tạo mới `V3__create_user_sessions_table.sql` (chốt số `V3` với team để tránh trùng version khi merge).

### 2.4 Đa Tab cùng trình duyệt vs Đa Thiết bị trong Multi-Tenant
- Cookie `refreshToken` được trình duyệt dùng chung theo Origin (`domain:port`) cho toàn bộ tab thường.
- **Quy chuẩn:** Trên 1 profile trình duyệt, người dùng làm việc đồng nhất trên 1 Nhà xe. Để làm việc song song 2 Nhà xe khác nhau, dùng 2 thiết bị khác nhau hoặc 2 Profile trình duyệt / Tab ẩn danh (mỗi nơi 1 `sessionId` độc lập).

---

## 🛡️ 3. PHÂN TÍCH 5 EDGE CASES "CHÍ MẠNG" CỦA AUTH & THIẾT KẾ PHÒNG VỆ

### 🔥 Edge Case 1: Trộn lẫn Tenant giữa 2 Tab trình duyệt (Cross-Tab Tenant Drift)
- **Rủi ro:** Tab 2 đổi sang Nhà xe B làm ghi đè Cookie `refreshToken` $\rightarrow$ Tab 1 (đang mở màn hình Nhà xe A) thực hiện Silent Refresh nhận nhầm JWT của Nhà xe B $\rightarrow$ Lưu dữ liệu sang sai Nhà xe.
- **Phòng vệ (Triển khai ngay trong đợt này):**
  - Frontend (Angular HTTP Interceptor) tự động đính kèm Header `X-Tenant-Id` chứa `tenantId` mà tab hiện tại đang hiển thị.
  - Ở `JwtAuthenticationFilter`: Với các request nghiệp vụ có `tenant_id` trong JWT, nếu Header `X-Tenant-Id` tồn tại:
    - Parse UUID trong khối `try-catch (IllegalArgumentException)` và so khớp `headerTenantUUID.equals(jwtTenantUUID)`.
    - Nếu **không khớp** hoặc **sai định dạng UUID** $\rightarrow$ Ném `AppException(ErrorCode.TENANT_CONTEXT_MISMATCH)` (**HTTP `409 Conflict`**) — tuyệt đối không để `IllegalArgumentException` rơi xuống lỗi `500`!
  - **Miễn trừ không kiểm tra `X-Tenant-Id`:** `SUPER_ADMIN`, Pre-tenant user (`tenant_id == null`), và các endpoint cấp User / chuyển ngữ cảnh (`/api/v1/auth/select-tenant`, `/api/v1/auth/logout`, `/api/v1/auth/sessions/**`).
  - **Tương thích Swagger / Postman:** Nếu client không gửi header `X-Tenant-Id` thì bỏ qua kiểm tra (Present-then-Verify).
  - **Tại sao trả `409 Conflict` thay vì `401`?** Để Frontend phân biệt rạch ròi: `401` bật Re-Auth Modal nhập mật khẩu, còn `409 TENANT_CONTEXT_MISMATCH` hiển thị thông báo và reload/đồng bộ lại context của tab.
  - **CORS bắt buộc:** Thêm `"X-Tenant-Id"` vào `AllowedHeaders` trong `SecurityConfig.corsConfigurationSource()`.

### 🔥 Edge Case 2: Đào thải nhầm phiên đang nhập dở Form (Blind LRU Eviction)
- **Rủi ro:** Nhân viên nhập hợp đồng trong thời gian dài, bị thiết bị thứ 6 đẩy ra theo LRU $\rightarrow$ Bấm Lưu gặp `401`.
- **Phòng vệ:**
  - **Backend (KISS):** Giữ thuật toán LRU tinh gọn (`ORDER BY last_active_at ASC`) kết hợp cập nhật `last_active_at` có ngưỡng **Throttle 5 phút** trong Filter để phản ánh chính xác thiết bị nào thực sự đang tương tác.
  - **Frontend (Auto-Save & Re-Auth Modal):** Tự lưu nháp form vào `localStorage`; khi nhận `401 Unauthorized` không reload trang mà mở **Re-Auth Modal** đăng nhập tại chỗ lấy token mới rồi tự động retry request lưu form.

### 🔥 Edge Case 3: Cửa sổ 15 phút Zombie Token & Quy tắc Atomic Revocation (Khóa tài khoản / Đổi & Reset mật khẩu)
- **Rủi ro:** Nhân viên gian lận bị khóa tài khoản, bị mất điện thoại, hoặc nghỉ việc (cần đổi mật khẩu tài khoản quầy dùng chung) nhưng Access Token cũ vẫn còn hạn 15 phút hoặc Refresh Token cũ sống dài hạn với Sliding Expiration để trộm dữ liệu khách hàng (CCCD, SĐT).
- **Phòng vệ — Quy tắc Atomic Revocation (áp dụng bắt buộc cho cả module `account`):**
  1. **Admin khóa tài khoản (`is_active = false`) hoặc Admin reset mật khẩu nhân viên:** Trong cùng transaction, khóa dòng `User` (`findByIdForUpdate`), cập nhật trạng thái/mật khẩu mới và **bắt buộc gọi `sessionManager.revokeAllByUserId(userId)`** để thu hồi toàn bộ thiết bị ngay lập tức.
  2. **Người dùng tự đổi mật khẩu:** Sau khi cập nhật `password_hash`, **bắt buộc gọi `sessionManager.revokeAllOtherSessions(userId, currentSessionId)`** (đá toàn bộ thiết bị lạ, chỉ giữ lại đúng thiết bị đang thao tác đổi mật khẩu).
  3. Tại `JwtAuthenticationFilter`: Mọi request đều qua `sessionManager.validateAndTouchSession(sessionId, userId)` và `CustomUserDetailsService` (`user.isActive = true`) $\rightarrow$ Chặn đứng kẻ xấu trong tích tắc.

### 🔥 Edge Case 4: Race Condition khi Login đồng thời (TOCTOU vượt 5 thiết bị & Ghi đè Lockout - R04)
- **Tại sao ở Single Session cũ chỉ cần Atomic Conditional Update, còn sang Multi-Session BẮT BUỘC phải chuyển sang Pessimistic Lock trên dòng `users`?**
  - Ở **Single Session cũ**, đăng nhập chỉ là 1 câu `UPDATE users SET current_session_id = ... WHERE id = :id AND is_active = true` — tự nguyên tử trên 1 dòng.
  - Ở **Multi-Session mới**, đăng nhập là chuỗi nhiều thao tác trên **2 bảng (`users` + `user_sessions`)** kết thúc bằng lệnh **`INSERT` dòng mới vào `user_sessions`** (lệnh `INSERT` dòng mới khiến Optimistic Lock `@Version` bất lực).
- **Hai kịch bản lỗi chắc chắn xảy ra nếu KHÔNG khóa dòng `User`:**
  1. **Lỗi 1 — Vượt trần 5 thiết bị (TOCTOU):** User đang có 4 thiết bị active. Thiết bị 5 và 6 bấm Login cùng 1 mili-giây $\rightarrow$ cả 2 luồng cùng `SELECT` thấy `count = 4` $\rightarrow$ cả 2 đều bỏ qua bước đào thải LRU $\rightarrow$ cả 2 cùng `INSERT` $\rightarrow$ DB lọt lên **6 thiết bị active**! *(Tương tự, nếu đang có 5 thiết bị, cả 2 luồng cùng chọn thu hồi đúng thiết bị #1 rồi cả 2 cùng `INSERT` $\rightarrow$ vẫn lòi ra **6 thiết bị active**!)*
  2. **Lỗi 2 — Mất bản vá R04 khi Admin khóa tài khoản đồng thời:**
     - `t = 1ms`: Luồng Login đọc thấy `users.is_active = true`.
     - `t = 2ms`: Admin bấm Khóa tài khoản (`is_active = false` + `revokeAllByUserId`) và `COMMIT`.
     - `t = 3ms`: Luồng Login chạy tiếp `INSERT INTO user_sessions (..., is_revoked = false)` $\rightarrow$ DB sinh ra trạng thái rác mâu thuẫn: tài khoản đã bị khóa nhưng lại có 1 phiên mới đang `is_revoked = false`!
- **Phòng vệ (Row Lock 2–5ms trên dòng cha `users` làm Mutex — Tách BCrypt ra NGOÀI Transaction):**
  - **CẤM đặt `@Transactional` ở cấp Class `AuthServiceImpl`!** Trong `login()`:
    1. Chạy `userRepository.findByEmail()` và `passwordEncoder.matches()` (BCrypt tốn 50–100ms CPU) **hoàn toàn NGOÀI Transaction** $\rightarrow$ không mượn/giữ HikariCP DB Connection khi CPU đang băm mật khẩu (chống cạn Connection Pool khi bị spam sai mật khẩu!).
    2. Sau khi mật khẩu hợp lệ mới mở Transaction ngắn bằng `TransactionTemplate`:
       - Khóa cấp dòng bản ghi `User` bằng `@Lock(LockModeType.PESSIMISTIC_WRITE)` (`SELECT * FROM users WHERE id = :userId FOR UPDATE`).
       - Kiểm tra lại `user.getIsActive()` (nếu bị khóa đúng tích tắc đó $\rightarrow$ ném `401 UNAUTHORIZED`).
       - Cập nhật `user.setLastLoginAt(now)`.
       - Truy vấn các phiên active (`ORDER BY last_active_at ASC, created_at ASC`). Nếu `size >= MAX_ACTIVE_SESSIONS (5)`, thu hồi **tất cả các phiên dư** (`size - 5 + 1`) rồi `INSERT` phiên `UserSession` mới.
       - Commit nhả khóa ngay sau ~2–5ms!

### 🔥 Edge Case 5: Nhân viên bấm "Đăng xuất" khi Access Token đã hết hạn (>15 phút nghỉ tay)
- **Rủi ro thực tế tại máy quầy:** Cuối ca trực, nhân viên để máy nghỉ 20 phút (Access Token 15p đã hết hạn) rồi bấm **Đăng xuất** để bàn giao máy cho ca sau. Nếu `/api/v1/auth/logout` yêu cầu Bearer Access Token hợp lệ, Filter sẽ chặn `401` $\rightarrow$ UI bật Re-Auth Modal bắt nhập mật khẩu chỉ để... đăng xuất, hoặc không gọi được Backend để xóa **HttpOnly Cookie `refreshToken`** (vì JS bị cấm đụng HttpOnly Cookie!) $\rightarrow$ Ca sau mở máy vẫn tự động vào tài khoản ca trước!
- **Phòng vệ (Idempotent Logout):**
  - Cấu hình `POST /api/v1/auth/logout` là **`permitAll()`** ở `SecurityConfig` và đưa vào `PUBLIC_ENDPOINTS` (`shouldNotFilter`) của `JwtAuthenticationFilter` để Access Token hết hạn **không bao giờ bị Filter chặn 401**.
  - Trong `AuthServiceImpl.logout(String authorizationHeader, String refreshTokenCookie)`:
    1. Nếu có Bearer Access Token hợp lệ $\rightarrow$ lấy `(userId, sessionId)` từ Access Token.
    2. Nếu Access Token thiếu hoặc đã hết hạn $\rightarrow$ **fallback đọc `(userId, sessionId)` từ HttpOnly Cookie `refreshToken`** (có verify chữ ký & `token_type = REFRESH` qua `jwtProvider`).
    3. Nếu trích xuất được `(userId, sessionId)` hợp lệ $\rightarrow$ gọi `sessionManager.revokeSession(userId, sessionId)` đánh dấu `is_revoked = true` trong DB.
    4. **Luôn trả `200 OK` và gửi `Set-Cookie: refreshToken=; Max-Age=0`** (dù cả 2 token đều đã hết hạn/rỗng) để đảm bảo trình duyệt chắc chắn xóa sạch Cookie! *(Chống tấn công CSRF ép đăng xuất đã được bảo vệ bởi `SameSite=Strict` trên Cookie).*

---

## 🗄️ 4. ĐẶC TẢ CƠ SỞ DỮ LIỆU (DATABASE SPECIFICATION)

### 4.1 Migration File: `V3__create_user_sessions_table.sql`
- **Đường dẫn:** `backend/src/main/resources/db/migration/V3__create_user_sessions_table.sql`
- **Lưu ý phối hợp nhóm:** Do thư mục `db/migration/` đã được squash còn `V1` & `V2`, migration mới bắt buộc đánh số **`V3`** (thông báo nhóm chốt số `V3` để tránh trùng version khi merge với nhánh `tenant`/`user`).

```sql
-- Migration V3: Quản lý Phiên đăng nhập Đa thiết bị (Multi-Session & Device Management)

CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),                      -- Mã phiên (Session ID mang trong JWT claim current_session_id)
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,       -- Người dùng sở hữu phiên
    device_name VARCHAR(255) NOT NULL DEFAULT 'Unknown Device',         -- Tên thiết bị/trình duyệt (Parse từ User-Agent, cắt tối đa 255 ký tự)
    ip_address VARCHAR(45),                                             -- Địa chỉ IP lúc đăng nhập (IPv4 hoặc IPv6, tối đa 45 ký tự)
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,                          -- TRUE: Đã đăng xuất / bị đá từ xa / bị đào thải LRU
    last_active_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP, -- Thời điểm tương tác gần nhất (Throttle 5 phút, phục vụ LRU)
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,                       -- Thời điểm hết hạn phiên (Sliding Expiration: now + refresh-token-expiration-ms)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP      -- Thời điểm tạo phiên
);

-- Composite Index tối ưu: (1) Tra cứu phiên theo user_id (left-most prefix),
-- (2) Đếm phiên active & sắp xếp LRU theo last_active_at ASC, (3) Lấy danh sách thiết bị đang online
CREATE INDEX IF NOT EXISTS idx_user_sessions_lru
    ON user_sessions(user_id, is_revoked, last_active_at);

-- Index phục vụ Scheduled Cleanup Job quét bản ghi hết hạn hoặc đã thu hồi quá hạn audit
CREATE INDEX IF NOT EXISTS idx_user_sessions_cleanup
    ON user_sessions(expires_at, is_revoked);
```

> 💡 **Tối ưu Index:** Không tạo thêm index đơn `idx_user_sessions_user_id(user_id)` vì `idx_user_sessions_lru(user_id, is_revoked, last_active_at)` đã bao phủ hoàn toàn các truy vấn lọc theo `user_id` theo quy tắc **Left-most Prefix**, giúp tránh nhân đôi chi phí ghi index.

### 4.2 Giải thích các cột & Quy ước Claim JWT
- **JWT Claim thống nhất:** Giữ nguyên hằng số có sẵn `ClaimKey.CURRENT_SESSION_ID = "current_session_id"` cho cả Access Token và Refresh Token (tương thích ngược 100%, không dùng song song tên `"session_id"`).
- **`device_name`:** Parse gọn nhẹ từ header `User-Agent` (KISS — không kéo thư viện nặng vài chục MB RAM), nhận diện các trình duyệt/OS phổ biến (Chrome, Edge, Firefox, Safari, Postman / Windows, macOS, Android, iOS, Linux), fallback `"Unknown Device"`, sanitize và cắt tối đa 255 ký tự.
- **`ip_address`:** Trích xuất IP thực (ưu tiên IP đầu tiên trong `X-Forwarded-For` nếu đã tin cậy proxy qua `server.forward-headers-strategy`, fallback `request.getRemoteAddr()`), cắt an toàn tối đa 45 ký tự.

---

## 🏛️ 5. KIẾN TRÚC CODE, CẤU TRÚC FILE & QUY CHUẨN JAVA/SPRING

### 5.1 Nguyên lý Dependency Inversion (`SessionManager` — Sẵn sàng cho Redis)

```
               ┌──────────────────────────────────────────────┐
               │  AuthServiceImpl / JwtAuthenticationFilter / │
               │  SessionController / SessionCleanupTask      │
               └──────────────────────┬───────────────────────┘
                                      │ (Chỉ phụ thuộc Interface trừu tượng)
                                      ▼
               ┌──────────────────────────────────────────────┐
               │         <<Interface>> SessionManager         │
               │  - createSessionWithLru(userId, device, ip)  │
               │  - validateAndTouchSession(sessionId, userId)│
               │  - touchAndExtendSession(sessionId, userId)  │
               │  - revokeSession(userId, sessionId)          │
               │  - revokeAllOtherSessions(userId, currentId) │
               │  - revokeAllByUserId(userId)                 │
               │  - getActiveSessions(userId, currentId)      │
               │  - cleanupExpiredAndOldRevokedSessions()     │
               └──────────────────────┬───────────────────────┘
                                      │
            ┌─────────────────────────┴─────────────────────────┐
            ▼                                                   ▼
┌───────────────────────────────────┐       ┌───────────────────────────────────┐
│   DatabaseSessionManager (JPA)    │       │   RedisSessionManager (Roadmap)   │
│  👉 Hiện tại: Thao tác bảng       │       │  👉 Tương lai: Redis Hash + TTL   │
│     user_sessions trên PostgreSQL │       │     trên RAM (Zero DB hit)        │
└───────────────────────────────────┘       └───────────────────────────────────┘
```

### 5.2 Sơ đồ File cần tạo mới & chỉnh sửa (Package-by-Feature trong `auth/` & `security/`)

**Các file TẠO MỚI (hoàn toàn nằm trong `auth/`, 0% conflict nhánh `tenant`/`branch`/`account`):**
- `backend/src/main/resources/db/migration/V3__create_user_sessions_table.sql` — Migration tạo bảng & index.
- `auth/entity/UserSession.java` — JPA Entity ánh xạ `user_sessions`:
  - **Bắt buộc:** Dùng `@Entity`, `@Table(name = "user_sessions")`, `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` (**CẤM dùng `@Data` trên Entity**).
  - **Bắt buộc:** Gắn `@Builder.Default` cho `private Boolean isRevoked = false;` (tránh bẫy `@Builder` ghi đè `null` xuống cột `NOT NULL`).
  - Dùng `@PrePersist` tự gán `createdAt` và `lastActiveAt` nếu null.
- `auth/repository/UserSessionRepository.java` — Kế thừa `JpaRepository<UserSession, UUID>`.
- `auth/service/SessionManager.java` — Interface trừu tượng quản lý vòng đời phiên.
- `auth/service/impl/DatabaseSessionManager.java` — `@Service`, `@RequiredArgsConstructor`, cài đặt `SessionManager` trên PostgreSQL qua JPA.
- `auth/dto/response/UserSessionResponseDTO.java` — DTO trả về danh sách thiết bị đang hoạt động.
- `auth/controller/SessionController.java` — `@RestController`, `@RequestMapping("/api/v1/auth/sessions")`, chịu trách nhiệm 3 API quản lý thiết bị (tách khỏi `AuthController` để giữ Single Responsibility & file dưới 200 dòng).
- `auth/scheduler/SessionCleanupTask.java` — Cronjob hàng đêm dọn phiên hết hạn & phiên thu hồi quá hạn audit.
- `auth/util/ClientInfoExtractor.java` *(hoặc helper class gọn nhẹ)* — Tách riêng logic parse `User-Agent` và lấy IP an toàn từ `HttpServletRequest` (Controller chỉ gọi helper lấy 2 chuỗi `String deviceName`, `String ipAddress` rồi truyền xuống Service → Service không phụ thuộc Servlet API).

**Các file CHỈNH SỬA:**
- `CarRentalBackendApplication.java` — Thêm `@EnableScheduling`.
- `application.yaml` — Bổ sung cấu hình `app.auth.max-active-sessions: 5`, `app.auth.last-active-throttle-minutes: 5`, `app.auth.session-cleanup-cron: "0 0 2 * * *"`, `app.auth.revoked-audit-retention-days: 7`, `app.cookie.secure: ${COOKIE_SECURE:false}` (mặc định `false` ở dev để test LAN HTTP bằng điện thoại thật không bị chặn Cookie; production set `COOKIE_SECURE=true`), và `server.forward-headers-strategy: framework`.
- `common/exception/ErrorCode.java` — Thêm `TENANT_CONTEXT_MISMATCH("TENANT_CONTEXT_MISMATCH", "Ngữ cảnh nhà xe của tab đã thay đổi. Vui lòng tải lại trang để đồng bộ!", HttpStatus.CONFLICT)`.
- `security/config/SecurityConfig.java` — Thêm `"X-Tenant-Id"` vào `CorsConfiguration.setAllowedHeaders(...)`; gỡ bỏ header thừa `"X-Active-Branch-Id"` (hệ thống lấy `active_branch_id` duy nhất từ JWT Claim); thêm `"/api/v1/auth/logout"` vào danh sách `.permitAll()` để hỗ trợ Idempotent Logout khi Access Token đã hết hạn.
- `security/jwt/JwtAuthenticationFilter.java` — **Gỡ dependency `UserRepository` thừa** khỏi Filter (vì `CustomUserDetailsService` ngay bước sau đã load & kiểm tra `user.isActive`, tiết kiệm 1 câu query DB thừa trên mỗi request!); thêm `"/api/v1/auth/logout"` vào `PUBLIC_ENDPOINTS` (`shouldNotFilter`); thay kiểm tra `currentSessionId` bằng `SessionManager.validateAndTouchSession(sessionId, userId)`; chuẩn hóa `401` khi thiếu/sai format UUID; mở rộng bypass kiểm tra `tenant.isActive` cho `/sessions/**`; bổ sung kiểm tra Header `X-Tenant-Id` (catch `IllegalArgumentException` → `409 TENANT_CONTEXT_MISMATCH`).
- `security/jwt/JwtProvider.java` — Bổ sung getter `getRefreshTokenExpirationMs()` và `getRefreshTokenExpirationInSeconds()` (để đồng bộ tuyệt đối 3 nơi: DB `expires_at` ↔ JWT `exp` ↔ Cookie `Max-Age`, không hardcode 7 ngày).
- *(Dọn dẹp dead code kèm theo)* Xóa file không còn sử dụng `auth/dto/request/RefreshTokenRequestDTO.java` (do Refresh Token đã chuyển sang đọc từ HttpOnly Cookie).
- `account/entity/User.java` — Chỉ thêm annotation `@Deprecated` trên field `currentSessionId` (không xóa!).
- `account/repository/UserRepository.java` — Thêm method khóa dòng `findByIdForUpdate(UUID id)` dùng `@Lock(LockModeType.PESSIMISTIC_WRITE)` (giữ nguyên method cũ hoặc gắn `@Deprecated` để tránh vỡ compile nếu nhánh khác gọi).
- `auth/controller/AuthController.java` — Nhận `HttpServletRequest` ở `login()` để trích `deviceName` & `ipAddress` truyền vào `AuthService.login(request, deviceName, ipAddress)`; cập nhật `logout()` nhận `@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)` và `@CookieValue(name = "refreshToken", required = false)` truyền xuống `authService.logout(...)`; gom 5 đoạn tạo `ResponseCookie` lặp lại thành 1 private helper `buildRefreshTokenCookie(String token, long maxAgeSeconds)` dùng cờ `app.cookie.secure` và `jwtProvider.getRefreshTokenExpirationInSeconds()`.
- `auth/service/AuthService.java` & `auth/service/impl/AuthServiceImpl.java` — **Bỏ `@Transactional` ở cấp Class** và chuyển sang `org.springframework.transaction.annotation.Transactional` ở cấp method (sửa lỗi import `jakarta.transaction.Transactional` cũ!); ở `login()` chạy `findByEmail()` + BCrypt **ngoài** transaction rồi dùng `TransactionTemplate` mở transaction ngắn 2–5ms để khóa dòng `User` + tạo phiên LRU; dùng `SessionManager` cho `refreshToken` (sửa đủ **8 chỗ** gọi `userInfo.getCurrentSessionId()` sang dùng `sessionId` từ token, đảm bảo kiểm tra `401` trước `403`) và Idempotent `logout` (thu hồi phiên từ Access Token hoặc fallback Cookie Refresh Token).
- `auth/service/impl/CustomUserDetailsServiceImpl.java` — Chuyển sang `org.springframework.transaction.annotation.Transactional(readOnly = true)` (sửa import `jakarta.transaction.Transactional` cũ).

### 5.3 Thiết luật `@Transactional` cho `DatabaseSessionManager` (Tránh bẫy Spring AOP!)
- Luôn import **`org.springframework.transaction.annotation.Transactional`** (TUYỆT ĐỐI không dùng `jakarta.transaction.Transactional` vì không hỗ trợ `readOnly = true`).
- `getActiveSessions(...)` $\rightarrow$ `@Transactional(readOnly = true)` (tắt Hibernate Dirty Checking, nhả HikariCP nhanh nhất).
- **`validateAndTouchSession(...)` — Cấu trúc 2 lời gọi tách biệt (Tránh `TransactionRequiredException`, Self-Invocation & tối ưu HikariCP/MVCC):**
  1. **KHÔNG** gắn `@Transactional` lên toàn bộ hàm `validateAndTouchSession(...)` (nếu gắn read-write thì 100% request `GET` mở write transaction; nếu gắn `readOnly = true` thì câu `UPDATE` bị lỗi/bỏ qua!).
  2. **Bước đọc — Bắt buộc gắn tường minh `@Transactional(readOnly = true)` trên `findByIdAndUserId(UUID id, UUID userId)`:**
     - *Bản chất Spring Data JPA:* Chỉ các method có sẵn của `JpaRepository` (`findById`, `findAll`, `count`...) trong `SimpleJpaRepository` mới có sẵn `@Transactional(readOnly = true)`. Các **custom derived query** tự khai báo như `findByIdAndUserId` **CHƯA tự động có `readOnly = true`**!
     - Do chưa dùng Redis/Second-Level Cache, `findByIdAndUserId` luôn truy vấn thẳng PostgreSQL. Gắn tường minh `@Transactional(readOnly = true)` giúp Hibernate tắt hoàn toàn **Snapshot & Dirty Checking** (tiết kiệm ~50% RAM Heap cho entity đó và nhả HikariCP ngay lập tức) → **>95% request trong cửa sổ 5 phút chỉ tốn 1 câu `SELECT` read-only, 0 write lock**.
  3. **Bước ghi có điều kiện (`<5%` request) — `updateLastActiveIfStale(id, now, threshold)`:**
     - Chỉ gọi khi phiên hợp lệ VÀ `last_active_at < threshold` (`now - 5 phút`).
     - Gắn `@Transactional` + `@Modifying(clearAutomatically = true)` ngay trên method của `UserSessionRepository` (chạy qua Repository Proxy nên không bao giờ bị lỗi Self-Invocation hay `TransactionRequiredException`).
     - *Bản chất Persistence Context & HikariCP:* Câu `@Modifying` bulk `UPDATE` bắn thẳng 1 SQL xuống PostgreSQL — **không `SELECT`, không tạo Object `UserSession` hay Snapshot trong Persistence Context**. Ngay khi return `int` (~1ms), Spring gọi `commit()` và **nhả HikariCP Connection ngay lập tức** (không giữ chờ Controller).
     - *Tại sao phải Throttle 5 phút & thêm `AND last_active_at < :threshold` trong SQL `WHERE`?*
       - **Chống Write Amplification & Dead Tuples (PostgreSQL MVCC):** Trong PostgreSQL, mỗi lần `UPDATE` đều sinh phiên bản dòng mới (*dead tuple* chờ `VACUUM`) và ghi lại cây B-Tree Index `idx_user_sessions_lru` (vì `last_active_at` nằm trong Index). Dù MVCC giúp `UPDATE` không chặn `SELECT`, việc biến mọi `GET` thành `UPDATE` vẫn gây tăng tải đĩa/WAL và dễ làm cạn HikariCP Pool (10 connections).
       - **Chống ghi trùng trên cùng 1 tab Frontend:** Ngay trên **1 tab Angular duy nhất**, khi user bấm F5 hoặc mở Dashboard sau >5 phút nghỉ tay, `ngOnInit()` tự động bắn song song 4–5 API (`/me`, `/bookings`, `/vehicles`, `/branches`) cùng 1 mili-giây mang cùng `sessionId` → điều kiện `AND last_active_at < :threshold` ở tầng SQL đảm bảo chỉ **1 luồng duy nhất** thực sự ghi đĩa, các luồng còn lại Postgres bỏ qua (0 rows updated) không ghi đè vô ích!
- `createSessionWithLru(...)`, `touchAndExtendSession(...)`, `revokeSession(...)`, `revokeAllOtherSessions(...)`, `revokeAllByUserId(...)`, `cleanupExpiredAndOldRevokedSessions()` $\rightarrow$ `@Transactional`.

---

## 🔄 6. ĐẶC TẢ THUẬT TOÁN & LUỒNG XỬ LÝ CHI TIẾT (STEP-BY-STEP FLOW)

### 6.1 Luồng Đăng nhập (`POST /api/v1/auth/login`)
1. **Bước 1 (Trích xuất thông tin thiết bị tại Controller):**
   - Dùng `ClientInfoExtractor` lấy `deviceName` (parse `User-Agent`, cắt tối đa 255 ký tự) và `ipAddress` (cắt tối đa 45 ký tự), truyền xuống `authService.login(loginRequestDTO, deviceName, ipAddress)`.
2. **Bước 2 (Xác thực mật khẩu — NGOÀI Transaction, 0 DB Connection bị giữ khi BCrypt chạy):**
   - Hàm `login(...)` **KHÔNG gắn `@Transactional` toàn hàm**.
   - Tìm `User` theo `email`. Nếu không tồn tại hoặc `!passwordEncoder.matches(rawPassword, user.getPasswordHash())` $\rightarrow$ ném `AppException(ErrorCode.UNAUTHORIZED, "Sai tài khoản hoặc mật khẩu !")`.
   - Kiểm tra nhanh `isActive` (nếu `false` $\rightarrow$ ném `AppException(ErrorCode.UNAUTHORIZED, "Tài khoản của bạn đã bị khóa !")`).
3. **Bước 3 (Mở Transaction ngắn bằng `TransactionTemplate` + Khóa dòng `User` 2ms + Cập nhật `last_login_at`):**
   - Mở transaction ngắn (`transactionTemplate.execute(...)`):
     - Gọi `userRepository.findByIdForUpdate(userId)` (`PESSIMISTIC_WRITE` — chống race condition giữa Login song song và Admin khóa tài khoản).
     - Kiểm tra lại `!Boolean.TRUE.equals(lockedUser.getIsActive())` $\rightarrow$ nếu bị khóa đúng tích tắc đó, ném `401 UNAUTHORIZED`.
     - Cập nhật `lockedUser.setLastLoginAt(Instant.now())`.
4. **Bước 4 (Thuật toán đào thải LRU — Tối đa `MAX_ACTIVE_SESSIONS = 5` thiết bị):**
   - Gọi `sessionManager.createSessionWithLru(userId, deviceName, ipAddress)`:
     - Truy vấn toàn bộ phiên active của user (`is_revoked = false AND expires_at > now`) sắp xếp `last_active_at ASC, created_at ASC`.
     - Nếu `activeSessions.size() >= maxActiveSessions`:
       - Tính `evictCount = activeSessions.size() - maxActiveSessions + 1`.
       - Thu hồi (`is_revoked = true`) đúng `evictCount` phần tử đầu danh sách (các thiết bị lâu không tương tác nhất).
     - Tạo bản ghi `UserSession` mới với `isRevoked = false`, `lastActiveAt = now`, `expiresAt = now.plusMillis(refreshTokenExpirationMs)`.
     - Lưu và trả về `sessionId` (UUID mới sinh).
5. **Bước 5 (Cấp Token theo phân nhánh Super Admin / 1 Active Tenant / >1 Active Tenant):**
   - Giữ nguyên logic lọc tenant active (Task 6 - R18), sinh Access Token & Refresh Token mang claim `ClaimKey.CURRENT_SESSION_ID = sessionId`.
   - Trả về `AuthResponseDTO` và gắn Refresh Token vào HttpOnly Cookie (`SameSite=Strict`, `Path=/api/v1/auth`, `Secure` theo config, `Max-Age` tính từ `refreshTokenExpirationMs`).

---

### 6.2 Luồng Xác thực Request (`JwtAuthenticationFilter`)
1. **Bước 1 (Bỏ qua Public Endpoints):** Cập nhật `PUBLIC_ENDPOINTS` (`shouldNotFilter`) gồm: `/api/v1/auth/login`, `/api/v1/auth/refresh-token`, **`/api/v1/auth/logout`**, và Swagger docs (`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`).
2. **Bước 2 (Parse & Verify Access Token):**
   - Đọc Bearer token, gọi `jwtProvider.validateToken(token)`, trích `Claims`.
   - Kiểm tra `sub` (`userIdStr`) và `token_type == ACCESS`.
   - Trích `currentSessionIdStr = claims.get(ClaimKey.CURRENT_SESSION_ID, String.class)`.
   - Nếu thiếu hoặc parse `UUID.fromString(...)` bị lỗi (`IllegalArgumentException`) $\rightarrow$ ném **`AppException(ErrorCode.UNAUTHORIZED, "Token không chứa mã phiên hợp lệ")`** *(không trả 400 hay 500!)*.
3. **Bước 3 (Kiểm tra phiên & Throttle cập nhật `last_active_at` 5 phút):**
   - Gọi `boolean isValid = sessionManager.validateAndTouchSession(sessionUUID, userUUID)`.
   - Bên trong kiểm tra: bản ghi tồn tại với đúng `(id = sessionUUID, user_id = userUUID)`, `is_revoked == false`, `expires_at > now`.
   - Nếu `isValid == false` $\rightarrow$ ném `AppException(ErrorCode.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn hoặc bị thu hồi từ thiết bị khác. Vui lòng đăng nhập lại !")`.
   - Nếu hợp lệ và `now - last_active_at >= 5 phút` $\rightarrow$ tự động chạy `userSessionRepository.updateLastActiveIfStale(...)` cập nhật `last_active_at = now`.
4. **Bước 4 (Kiểm tra phạm vi Endpoint cấp User & Chống lệch Tab `X-Tenant-Id`):**
   - Xác định nhóm endpoint cấp User không phụ thuộc trạng thái active của Tenant cũ (`isUserLevelEndpoint`):
     - `/api/v1/auth/select-tenant`
     - `/api/v1/auth/sessions` và `/api/v1/auth/sessions/**`
   - **Kiểm tra `X-Tenant-Id` (Edge Case 1):**
     - Nếu **không phải** `isUserLevelEndpoint`, **không phải** `SUPER_ADMIN`, và JWT có `tenant_id`:
       - Đọc header `String headerTenantId = request.getHeader("X-Tenant-Id")`.
       - Nếu `StringUtils.hasText(headerTenantId)`: bọc `UUID.fromString(headerTenantId)` trong `try-catch (IllegalArgumentException)` và so khớp với `tenantUUID`. Nếu sai định dạng UUID hoặc không khớp $\rightarrow$ ném `AppException(ErrorCode.TENANT_CONTEXT_MISMATCH)` (**HTTP 409 Conflict**).
5. **Bước 5 (Nạp `TenantContext` & `SecurityContextHolder`):**
   - Nạp `userId`, `role`, `currentSessionId` (và `tenantId`, `activeBranchId` khi không phải `isUserLevelEndpoint`) vào `TenantContext`.
   - Tải `UserPrincipal` qua `CustomUserDetailsService` (nếu là `isUserLevelEndpoint` hoặc chưa có `tenantId` thì gọi `loadUserById(userUUID)` để user vẫn có thể đổi nhà xe hoặc quản lý thiết bị ngay cả khi nhà xe cũ bị khóa!).
   - Khối `finally` luôn gọi `TenantContext.clear()`.

---

### 6.3 Luồng Tự động làm mới Token (`POST /api/v1/auth/refresh-token`)
1. Đọc Refresh Token từ HttpOnly Cookie `@CookieValue(name = "refreshToken", required = false)`. Nếu rỗng $\rightarrow$ `401 UNAUTHORIZED`.
2. Xác thực chữ ký & hạn token qua `jwtProvider.validateToken()`, kiểm tra `token_type == REFRESH`.
3. Trích xuất `userUUID`, `sessionUUID` (từ claim `current_session_id` — thiếu hoặc sai format UUID $\rightarrow$ `401`), `tenantId`, `activeBranchId`.
4. Kiểm tra `User` tồn tại và `isActive == true` (không thỏa $\rightarrow$ `401 UNAUTHORIZED`).
5. **Xác thực & Gia hạn trượt (Sliding Expiration):**
   - Gọi `sessionManager.touchAndExtendSession(sessionUUID, userUUID)`:
     - Kiểm tra phiên tồn tại thuộc `userUUID`, `is_revoked == false`, `expires_at > now`. Nếu sai $\rightarrow$ ném `401 UNAUTHORIZED`.
     - Cập nhật **`last_active_at = now`** VÀ **`expires_at = now.plusMillis(refreshTokenExpirationMs)`**.
6. **Cấp cặp Token mới bảo tồn ngữ cảnh (4 nhánh: Super Admin / Pre-Tenant / Đã chọn Tenant / Đã chọn Tenant + Branch):**
   - ⚠️ **Bắt buộc:** Ở **toàn bộ các nhánh** trong `AuthServiceImpl.refreshToken()`, truyền **`sessionUUID`** (lấy từ Refresh Token đầu vào) vào `generateAccessToken(...)` & `generateRefreshToken(...)`, **tuyệt đối xóa bỏ mọi chỗ đọc `userInfo.getCurrentSessionId()`**.
   - Controller set lại HttpOnly Cookie mới với `Max-Age` đồng bộ theo `refreshTokenExpirationMs`.

---

### 6.4 Luồng Chuyển Ngữ cảnh (`select-tenant` & `switch-branch`)
- Giữ nguyên logic xác thực tenant/branch hiện có.
- Đọc `UUID sessionId = TenantContext.getCurrentSessionId()` và tiếp tục gắn đúng `sessionId` đó vào Access Token & Refresh Token mới (**không** tạo dòng session mới, **không** ảnh hưởng các thiết bị khác).

---

### 6.5 Luồng Đăng xuất & Quản lý Thiết bị (Device Management APIs)

#### API 1: Đăng xuất phiên hiện tại — Idempotent Logout (`POST /api/v1/auth/logout`)
- **Mức truy cập:** `permitAll()` (bỏ qua `JwtAuthenticationFilter` để không bị chặn `401` khi Access Token đã hết hạn!).
- Controller đọc `@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)` và `@CookieValue(name = "refreshToken", required = false)`, truyền xuống `authService.logout(authorizationHeader, refreshTokenCookie)`.
- Thuật toán trong Service:
  1. Thử bóc tách Bearer Access Token từ `authorizationHeader`, verify qua `jwtProvider` (trong `try-catch`, nếu lỗi/hết hạn thì không ném lỗi mà chuyển bước 2) $\rightarrow$ lấy `(userId, sessionId)`.
  2. Nếu bước 1 không lấy được, thử verify `refreshTokenCookie` (`token_type == REFRESH`) qua `jwtProvider` (trong `try-catch`) $\rightarrow$ lấy `(userId, sessionId)`.
  3. Nếu trích xuất được cặp `(userId, sessionId)` hợp lệ $\rightarrow$ gọi `sessionManager.revokeSession(userId, sessionId)` đánh dấu `is_revoked = true` cho **đúng phiên hiện tại** (các thiết bị khác vẫn online bình thường).
  4. Dọn sạch `TenantContext.clear()` và `SecurityContextHolder.clearContext()`.
- Controller **LUÔN** trả `200 OK` kèm `Set-Cookie: refreshToken=; Max-Age=0` để đảm bảo trình duyệt chắc chắn xóa sạch HttpOnly Cookie.

#### API 2: Lấy danh sách thiết bị đang hoạt động (`GET /api/v1/auth/sessions`)
- Lấy `userId = TenantContext.getUserId()` và `currentSessionId = TenantContext.getCurrentSessionId()`.
- Truy vấn toàn bộ phiên của user thỏa `user_id = :userId AND is_revoked = false AND expires_at > NOW()`, sắp xếp `last_active_at DESC`.
- Manual Mapping qua `@Builder` sang `List<UserSessionResponseDTO>`, với `isCurrent = session.getId().equals(currentSessionId)`.

#### API 3: Đăng xuất một thiết bị cụ thể từ xa (`DELETE /api/v1/auth/sessions/{sessionId}`)
- Lấy `userId = TenantContext.getUserId()` và `currentSessionId = TenantContext.getCurrentSessionId()`.
- **Chặn tự xóa phiên hiện tại (Guard Clause):** Nếu `sessionId.equals(currentSessionId)` $\rightarrow$ ném `AppException(ErrorCode.BAD_REQUEST, "Không thể thu hồi phiên hiện tại. Vui lòng sử dụng chức năng Đăng xuất!")`.
- **Chống IDOR & Enumeration:** Tìm kiếm/cập nhật theo điều kiện ghép **`WHERE id = :sessionId AND user_id = :userId AND is_revoked = false`**.
  - Nếu không tìm thấy (dù ID không tồn tại, đã thu hồi, hay thuộc về User khác) $\rightarrow$ đều ném chung **`AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiên đăng nhập cần thu hồi!")`** (HTTP `404`).
- Cập nhật `is_revoked = true` $\rightarrow$ Thiết bị mục tiêu bị chặn `401` ngay ở request tiếp theo.

#### API 4: Đăng xuất khỏi tất cả các thiết bị khác (`POST /api/v1/auth/sessions/revoke-others`)
- Lấy `userId = TenantContext.getUserId()` và `currentSessionId = TenantContext.getCurrentSessionId()`.
- Thực thi 1 câu lệnh bulk update nguyên tử:
  `UPDATE user_sessions SET is_revoked = true WHERE user_id = :userId AND id != :currentSessionId AND is_revoked = false`.
- Trả về số lượng thiết bị đã bị đăng xuất (hoặc message thành công kèm số lượng).

---

### 6.6 Luồng Dọn dẹp Phiên rác Định kỳ (`SessionCleanupTask`)
- Kích hoạt `@EnableScheduling` tại `CarRentalBackendApplication`.
- Lịch chạy cấu hình qua `application.yaml`: `@Scheduled(cron = "${app.auth.session-cleanup-cron:0 0 2 * * *}")` (mặc định 02:00 sáng mỗi ngày).
- Gọi `sessionManager.cleanupExpiredAndOldRevokedSessions()` thực thi xóa bản ghi thỏa:
  - Phiên đã hết hạn quá ngưỡng giữ vết audit (`expires_at < now - auditRetentionDays`), **HOẶC**
  - Phiên đã bị thu hồi và không còn hoạt động quá ngưỡng audit (`is_revoked = true AND last_active_at < now - auditRetentionDays`), với `auditRetentionDays = 7` ngày (đảm bảo giữ lại log đối soát gần nhất).
- Ghi `log.info(...)` số bản ghi đã dọn sạch.

---

## 📦 7. HỢP ĐỒNG API & DỮ LIỆU (API & DTO CONTRACTS)

### 7.1 Bảng tổng hợp REST API Endpoints

| Endpoint | Method | Auth / Bypass Tenant Lock | HTTP Status | Response Data (`ApiResponse<T>`) | Mã lỗi chính |
| :--- | :---: | :--- | :---: | :--- | :--- |
| `/api/v1/auth/login` | `POST` | Public | `200 OK` | `AuthResponseDTO` + `Set-Cookie: refreshToken` | `401 UNAUTHORIZED` |
| `/api/v1/auth/refresh-token` | `POST` | Public (Cookie `refreshToken`) | `200 OK` | `AuthResponseDTO` + `Set-Cookie: refreshToken` | `401 UNAUTHORIZED`, `403 FORBIDDEN` |
| `/api/v1/auth/logout` | `POST` | Public — Idempotent *(Bearer hoặc Cookie `refreshToken`)* | `200 OK` | `String` + Luôn Clear Cookie (`Max-Age=0`) | *(Luôn 200 OK — Idempotent)* |
| `/api/v1/auth/sessions` | `GET` | Authenticated *(bypass tenant lock)* | `200 OK` | `List<UserSessionResponseDTO>` | `401 UNAUTHORIZED` |
| `/api/v1/auth/sessions/{sessionId}` | `DELETE` | Authenticated *(bypass tenant lock)* | `200 OK` | `String` (Thông báo thu hồi thành công) | `400 BAD_REQUEST` (self-revoke), `404 RESOURCE_NOT_FOUND` (không tồn tại / IDOR) |
| `/api/v1/auth/sessions/revoke-others` | `POST` | Authenticated *(bypass tenant lock)* | `200 OK` | `Map` / `RevokeSessionsResponseDTO` (`revokedCount`) | `401 UNAUTHORIZED` |

### 7.2 `UserSessionResponseDTO.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/dto/response/UserSessionResponseDTO.java`
- **Annotations:** `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- **Các trường:**
  - `id` (`UUID`): Mã phiên (`sessionId`).
  - `deviceName` (`String`): Tên thiết bị / trình duyệt (VD: *"Chrome on Windows"*).
  - `ipAddress` (`String`): Địa chỉ IP đăng nhập.
  - `lastActiveAt` (`Instant`): Thời điểm tương tác gần nhất.
  - `createdAt` (`Instant`): Thời điểm đăng nhập.
  - `expiresAt` (`Instant`): Thời điểm hết hạn dự kiến của phiên.
  - `isCurrent` (`Boolean` — **hoặc** `boolean` kèm `@JsonProperty("isCurrent")`):
    > ⚠️ **Bẫy Jackson + Lombok:** Nếu khai báo primitive `boolean isCurrent` với `@Data`, Lombok sinh getter `isCurrent()` làm Jackson tự cắt chữ `"is"` thành JSON key **`"current"`** thay vì **`"isCurrent"`**! Bắt buộc dùng kiểu Wrapper `Boolean isCurrent` (Lombok sinh `getIsCurrent()` → JSON giữ đúng `"isCurrent"`) hoặc gắn `@JsonProperty("isCurrent")`.

---

## 🧪 8. CHIẾN LƯỢC KIỂM THỬ & NGHIỆM THU (TESTING & VERIFICATION)

### 8.1 Unit Tests bắt buộc (JUnit 5 + Mockito — AAA Pattern)
Tạo test class cho `DatabaseSessionManagerTest` và `AuthServiceImplTest` phủ tối thiểu các kịch bản sau (đặt tên theo chuẩn `should_<expected>_when_<condition>`):

- [ ] `should_evict_oldest_session_when_active_sessions_reach_limit`: User đã có 5 phiên active $\rightarrow$ tạo phiên thứ 6 phải thu hồi đúng phiên có `last_active_at` cũ nhất và tổng active vẫn bằng 5.
- [ ] `should_evict_multiple_excess_sessions_when_active_sessions_exceed_limit`: DB đang có sẵn 7 phiên active $\rightarrow$ tạo phiên mới phải thu hồi 3 phiên cũ nhất để tổng active quay về 5.
- [ ] `should_update_last_active_only_when_throttle_threshold_exceeded`: Gọi `validateAndTouchSession` trong vòng 5 phút **không** gọi lệnh `UPDATE`; gọi sau khi quá 5 phút **có** gọi `UPDATE`.
- [ ] `should_reject_session_when_expired_or_revoked_or_wrong_user`: Kiểm tra 3 nhánh trả `false` (hết hạn `expires_at < now`, `is_revoked = true`, sai `userId`).
- [ ] `should_extend_expires_at_and_preserve_session_id_when_refresh_token`: Kiểm tra Sliding Expiration cập nhật cả `last_active_at` và `expires_at = now + refreshTokenExpirationMs`, đồng thời token mới giữ nguyên `sessionId` của thiết bị đó (không lấy `user.currentSessionId`).
- [ ] `should_throw_400_when_user_revokes_current_session`: Tự xóa `currentSessionId` qua `DELETE /sessions/{id}` $\rightarrow$ ném `AppException(BAD_REQUEST)`.
- [ ] `should_throw_404_when_revoking_another_users_session`: User A truyền `sessionId` của User B (IDOR) $\rightarrow$ ném `AppException(RESOURCE_NOT_FOUND)`.

### 8.2 Kiểm thử Tích hợp & Thực tế (Manual / Swagger / Multi-Browser Checklist)
- [ ] **Kịch bản 1 (Đa thiết bị song song):** Đăng nhập cùng 1 tài khoản trên Chrome và Firefox/Ẩn danh (hoặc điện thoại qua LAN HTTP với `COOKIE_SECURE=false`). Cả 2 gọi API đều `200 OK`, không đá nhau.
- [ ] **Kịch bản 2 (LRU 5 thiết bị + Throttle 5 phút):** Đăng nhập 5 phiên, tương tác phiên 1 sau >5 phút để đẩy `last_active_at` lên mới nhất, đăng nhập phiên thứ 6 $\rightarrow$ Phiên thứ 2 (cũ nhất) bị đá `401`, phiên 1 vẫn sống!
- [ ] **Kịch bản 3 (Quản lý thiết bị & IDOR):** Thiết bị A gọi `GET /api/v1/auth/sessions` thấy đúng `isCurrent: true` cho A và `false` cho B; xóa B bằng `DELETE /sessions/{idB}` → B lập tức nhận `401`; thử xóa A bằng `DELETE /sessions/{idA}` → nhận `400`; thử xóa session của user khác → nhận `404`.
- [ ] **Kịch bản 4 (Idempotent Logout & Revoke-Others):** `POST /sessions/revoke-others` đá sạch mọi máy trừ máy hiện tại; `POST /logout` chỉ đá máy hiện tại, máy khác vẫn dùng bình thường; đặc biệt thử gửi `POST /logout` kèm **Access Token đã hết hạn** nhưng còn **Cookie `refreshToken`** $\rightarrow$ vẫn thu hồi đúng phiên trong DB, trả `200 OK` và xóa sạch Cookie (`Max-Age=0`)!
- [ ] **Kịch bản 5 (Chống lệch Tab `X-Tenant-Id`):** Gửi API nghiệp vụ với JWT của Tenant A nhưng Header `X-Tenant-Id` của Tenant B (hoặc chuỗi rác `"not-a-uuid"`) $\rightarrow$ Nhận `409 TENANT_CONTEXT_MISMATCH` (không văng 500); không gửi Header trên Swagger vẫn chạy bình thường.
- [ ] **Kịch bản 6 (Tenant bị khóa vẫn Logout / Quản lý thiết bị được):** Khóa Tenant A (`is_active = false`), user đang cầm token của Tenant A vẫn gọi được `/select-tenant`, `/logout`, `/sessions` mà không bị kẹt `403`.
- [ ] **Kịch bản 7 (Bảo toàn `sessionId` qua chuyển ngữ cảnh & Git Merge an toàn):** Gọi `select-tenant` và `switch-branch` không sinh thêm dòng rác trong `user_sessions`; cột `users.current_session_id` còn nguyên (`@Deprecated`), không xung đột khi merge với nhánh `tenant`, `branch`, `user`.

---

## 🎯 9. TỪ KHÓA TÌM KIẾM CHUẨN (GOOGLE KEYWORDS CHO DEV TỰ CODE)
- `"Spring Data JPA @Lock PESSIMISTIC_WRITE findById"`
- `"Spring Data JPA @Modifying clearAutomatically atomic update"`
- `"Spring Boot @Transactional readOnly vs jakarta.transaction.Transactional"`
- `"Jackson Lombok boolean isCurrent serializes as current fix"`
- `"Spring Boot @EnableScheduling @Scheduled cron expression"`
- `"Spring Boot server.forward-headers-strategy X-Forwarded-For"`
- `"JUnit 5 Mockito AAA pattern verify never called"`
