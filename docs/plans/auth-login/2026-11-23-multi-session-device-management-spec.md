# 📘 ĐẶC TẢ THIẾT KẾ KỸ THUẬT: PHÂN HỆ AUTH MULTI-SESSION & QUẢN LÝ THIẾT BỊ (DEVICE MANAGEMENT)

> **Dự án:** Car Rental SaaS  
> **Module:** `auth` & `security`  
> **Ngày cập nhật:** 23/11/2026  
> **Tác giả:** Tech Lead & Core Engineering Team  
> **Trạng thái:** Brainstorming Approved $\rightarrow$ Implementation Specification  

---

## 🏛️ 1. BỨC TRANH TOÀN CẢNH & ĐỘNG LỰC THAY ĐỔI (THE "WHY")

### 1.1 Vấn nạn của cơ chế Single Session cũ (`current_session_id`)
Ở phiên bản trước, hệ thống sử dụng một cột duy nhất `current_session_id UUID` trên bảng `users`. Mỗi khi có một lượt đăng nhập mới, hệ thống ghi đè UUID mới vào cột này. Ở `JwtAuthenticationFilter`, mỗi request gửi lên đều so sánh:
```java
// ❌ CƠ CHẾ CŨ: Người sau ghi đè và đá văng người trước
if (!UUID.fromString(currentSessionId).equals(user.get().getCurrentSessionId())) {
    throw new AppException(ErrorCode.UNAUTHORIZED, "Tài khoản này hiện đang có người sử dụng. Vui lòng đăng nhập lại !");
}
```

### 1.2 Thực tế vận hành nhà xe (Domain Reality):
1. **Sếp / Chủ nhà xe (`TENANT_ADMIN`):** 
   - Thường xuyên dùng song song 2 thiết bị: Sáng ngồi làm việc trên Laptop/PC văn phòng, trưa đi cafe/gặp khách thì mở **iPhone** check doanh thu.
   - Với cơ chế cũ: Vừa mở điện thoại $\rightarrow$ PC văn phòng bị đá văng $\rightarrow$ Quay lại PC làm việc $\rightarrow$ Điện thoại lại bị đá văng. Trải nghiệm cực kỳ ức chế!
2. **Nhân viên bãi xe (`STAFF`) — *Tử huyệt trải nghiệm người dùng*:**
   - Khi đón khách tại quầy, nhân viên ngồi trong phòng lạnh dùng **PC để bàn** mở form tạo đơn và gõ hợp đồng thuê xe (`booking:create`).
   - Ngay sau đó, nhân viên phải cầm **điện thoại/tablet** chạy ra bãi xe dưới trời nắng để chụp ảnh 4 góc xe, đồng hồ xăng, số ODO để làm thủ tục bàn giao xe (`booking:handover`).
   - Nếu áp dụng Single Session: Vừa đăng nhập điện thoại ngoài bãi $\rightarrow$ PC trong quầy bị văng $\rightarrow$ **Toàn bộ form hợp đồng đang gõ dở 15 phút bị mất trắng!**
3. **Văn hóa "Tài khoản quầy" (Kiosk Account):**
   - Các chi nhánh thường dùng chung 1 tài khoản cho cả ca trực (ví dụ: `staff_caugiay@rentcar.vn`). Nếu 2 nhân viên cùng trực 1 ca mà đăng nhập đá nhau thì công việc bàn giao xe hoàn toàn tê liệt.

$\implies$ **Mục tiêu nâng cấp:** Chuyển đổi sang kiến trúc **Multi-Session có quản lý thiết bị**, cho phép một tài khoản đăng nhập đồng thời trên nhiều thiết bị (PC, điện thoại, tablet), tối đa 5 thiết bị active theo thuật toán đào thải **LRU (Least Recently Used)**, đồng thời hỗ trợ người dùng xem danh sách thiết bị và chủ động đăng xuất thiết bị lạ từ xa.

---

## 🔬 2. GIẢI MÃ CÁC HIỂU LẦM KINH ĐIỂN CỦA DEV (MISTAKES & CLARIFICATIONS)

*Phần này tập trung phân tích bản chất các vấn đề kỹ thuật chuyên sâu mà đội ngũ kỹ sư dễ ngộ nhận khi chuyển dịch từ Frontend sang Backend:*

---

### ❌ Hiểu lầm 1: "Tại sao không dùng Pessimistic Lock khóa luôn bản ghi khi người dùng mở form, đợi họ sửa xong bấm Lưu rồi mới nhả ra?"

* **Bản chất kỹ thuật (Tầng DB & HikariCP Connection Pool):**
  - Pessimistic Lock (`SELECT ... FOR UPDATE`) bắt buộc phải **giữ chặt 1 Database Connection** từ bể kết nối HikariCP và duy trì transaction mở (`setAutoCommit(false)`).
  - Cho đến khi transaction `commit()` hoặc `rollback()`, kết nối DB đó **hoàn toàn bị chiếm dụng**, không ai khác được dùng.
* **Kịch bản thảm họa ngoài đời:**
  - Nhân viên mở form bàn giao xe, sau đó quay sang uống trà sữa, đi vệ sinh 10 phút, hoặc quên tắt tab máy tính.
  - Bể kết nối HikariCP (chỉ có 10 - 20 connection) bị chiếm dụng cạn kiệt! Toàn bộ khách hàng bên ngoài vào xem danh sách xe đều bị xoay tít và văng lỗi `ConnectionTimeoutException`.
  - Chiếc xe bị khóa cứng ở CSDL, không một câu query nào (kể cả xem xe) chạm vào được!
* 🍜 **Ẩn dụ đời sống Việt Nam:**
  Như đi vào quán phở, vừa tới cửa chưa biết ăn gì đã lấy xích **khóa cứng luôn cái bàn ăn**, rồi ngồi lướt TikTok 45 phút không cho ai ngồi, quán phở tê liệt!
* **Quy tắc sống còn:**
  - **Pessimistic Lock (`FOR UPDATE`):** CHỈ ĐƯỢC PHÉP DÙNG trong **2 đến 5 mili-giây** tại thời điểm bấm nút chốt giao dịch nhạy cảm (như chốt giữ cọc xe - Hold Booking).
  - **Optimistic Lock (`@Version`):** BẮT BUỘC DÙNG cho suốt thời gian người dùng mở form suy nghĩ và gõ phím!

---

### ❌ Hiểu lầm 2: "Tại sao phải lưu `refresh_token_hash` vào DB? Chẳng phải gọi `jwt.verifyWith(secretKey)` là đủ biết token đúng rồi sao?"

* **Gốc rễ ra đời của `refresh_token_hash`:**
  - Trong các hệ thống ngân hàng/tài chính, người ta lưu chuỗi băm của Refresh Token vào DB để phát hiện tấn công **Token Replay Attack** (khi hacker trộm được chuỗi Refresh Token cũ mang đi xin token mới). Nếu so sánh hash không khớp với hash hiện tại trong DB $\rightarrow$ hệ thống phát hiện token bị đánh cắp và khóa tài khoản.
* **Tại sao Car Rental SaaS LOẠI BỎ trường này (Triết lý YAGNI & KISS)?**
  1. Bản thân **JWT là một tấm vé tự chứng thực (Self-contained)** có chữ ký mật mã an toàn qua `jwt.verifyWith(secretKey)`.
  2. Bên trong Refresh Token đã mang sẵn `sessionId`. Khi gọi `/refresh-token`, Backend chỉ cần giải mã token, kiểm tra chữ ký bí mật và tra cứu `user_sessions` thấy `is_revoked == false` và chưa hết hạn là đủ xác thực 100%.
  3. Bỏ trường này giúp loại bỏ hoàn toàn các bước băm SHA-256 phức tạp, không phải update DB liên tục mỗi lần refresh, giúp code nhẹ nhàng, dễ bảo trì và hiệu năng cao hơn gấp nhiều lần.

---

### ❌ Hiểu lầm 3: "Có nên đập `@Version` (Optimistic Lock) lên TOÀN BỘ mọi bảng trong CSDL không?"

* **Tuyệt đối KHÔNG!** Phải áp dụng đúng nơi, đúng chỗ:
  - **Bảng CẤM / KHÔNG CẦN DÙNG:**
    - Bảng danh mục tĩnh (`permissions`, `vehicle_types`): 99.99% chỉ đọc (`READ-ONLY`), không ai sửa đè.
    - Bảng liên kết N-N (`user_tenants`, `role_permissions`): Chỉ gồm các Foreign Keys, chỉ có thao tác `INSERT/DELETE`, không có nghiệp vụ `UPDATE`.
    - Bảng lịch sử/nhật ký (`user_sessions`, `payment_logs`): Append-only, chỉ ghi thêm, không sửa dữ liệu quá khứ.
  - **Bảng BẮT BUỘC DÙNG Optimistic Lock (`@Version`):**
    - `vehicles`: Tránh Sale ở văn phòng sửa giá thuê ghi đè lên trạng thái Bảo dưỡng do Kỹ thuật ngoài bãi cập nhật.
    - `bookings`: Tránh Nhân viên xác nhận cọc ghi đè lên thao tác tự động hủy đơn của Scheduler.
    - `customers`: Bảo vệ cờ cảnh báo nợ xấu/rủi ro `is_risk` và thông tin CCCD/GPLX.
  - **Trường hợp BẮT BUỘC DÙNG Pessimistic Lock:**
    - **Tạo Booking giữ cọc xe:** Khi 2 khách cùng đặt 1 xe cùng giờ, thao tác là `INSERT INTO bookings` (2 bản ghi mới tinh, `@Version` hoàn toàn bất lực vì chưa có bản ghi cũ để so sánh). Phải dùng `SELECT ... FOR UPDATE` trên chiếc xe cha để khóa trong 2ms!

---

### ❌ Hiểu lầm 4: "Tại sao Optimistic Lock bắt buộc phải có sự tham gia của Frontend?"

Nhiều dev lầm tưởng: *"Optimistic Lock là chuyện của Backend với DB, Backend tự thêm `WHERE version = ...` lúc query là xong, liên quan gì tới Frontend?"*.
* **Bản chất Stateless của HTTP REST API:**
  1. Lúc 10:00, Frontend gọi `GET /vehicles/1` $\rightarrow$ Nhận JSON có `version: 1`. Ngay lập tức, kết nối đóng lại, server hoàn toàn giải phóng bộ nhớ! Server KHÔNG HỀ BIẾT người dùng đang mở form ngắm nghía.
  2. Lúc 10:05, người khác sửa xe $\rightarrow$ Trong DB, `version` đã nhảy lên `2`.
  3. Lúc 10:10, người dùng bấm Lưu (`PUT /vehicles/1`).
  4. **NẾU FRONTEND KHÔNG GỬI KÈM `version: 1`:**
     - Backend gọi `vehicleRepository.findById(1)`. Tại thời điểm 10:10, DB đang có `version = 2`!
     - Backend gán giá trị mới và gọi `save()`. Hibernate thấy version trong đối tượng là 2 $\rightarrow$ Sinh SQL: `UPDATE vehicles SET ..., version = 3 WHERE id = 1 AND version = 2;` $\rightarrow$ **LƯU THÀNH CÔNG!**
     - Dữ liệu người sửa lúc 10:05 bị **ghi đè mất trắng (Lost Update)** mà không có lỗi nào văng ra!
* **Cách triển khai CHUẨN:**
  - Vì Backend không giữ State trong suốt 10 phút người dùng gõ phím, nên **Frontend chính là nơi duy nhất giữ bằng chứng** rằng: *"Lúc tôi mở form, bản ghi đang ở version 1"*.
  - Khi gửi `PUT`, Frontend bắt buộc phải đính kèm `"version": 1`. Backend so sánh: Version gửi lên (`1`) $\ne$ Version hiện tại trong DB (`2`) $\rightarrow$ Ném ngay `AppException(ErrorCode.CONFLICT, "Dữ liệu vừa được người khác cập nhật!")`.

---

### ❌ Hiểu lầm 5: "Sửa Auth sang Multi-Session có làm gãy code hoặc conflict với các nhánh `branch`, `tenant`, `user` đang code song song không?"

* **Hoàn toàn KHÔNG (Zero-conflict)**, nếu tuân thủ nguyên tắc **"Expand and Contract"**:
  1. **Không xóa cột cũ:** Cột `current_session_id` trên bảng `users` được giữ nguyên trong DB và trong entity `User.java` (đánh dấu `@Deprecated`). Không ai bị lỗi thiếu cột khi chạy code cũ.
  2. **Không đổi hàm của Context:** `TenantContext` giữ nguyên 100% các hàm `getTenantId()`, `getBranchId()`, `getUserId()`. Các nhánh khác chỉ dùng các hàm này.
  3. **Tạo bảng mới độc lập:** Chỉ viết migration tạo bảng `user_sessions`. Mọi thay đổi gói gọn trong package `auth/` và `security/`. Đồng đội merge vào sẽ mượt mà 100%!

---

### ❌ Hiểu lầm 6: "Mở 2 Tab trên cùng 1 trình duyệt để làm việc ở 2 Nhà xe khác nhau có được không?"

* **KHÔNG THỂ VÀ KHÔNG NÊN TRÊN CÙNG MỘT TRÌNH DUYỆT THƯỜNG!**
  - **Bản chất Cookie:** Trình duyệt quản lý Cookie theo Origin (`domain:port`). Cookie `refreshToken` dùng chung cho toàn bộ các tab của trình duyệt đó.
  - Nếu Tab 1 chọn Nhà xe Hà Nội, Tab 2 chọn Nhà xe Sài Gòn $\rightarrow$ Cookie bị ghi đè sang Sài Gòn $\rightarrow$ Khi Tab 1 Silent Refresh, nó sẽ bị kéo theo sang Nhà xe Sài Gòn!
  - **Quy chuẩn:** Trên cùng 1 trình duyệt, người dùng làm việc đồng nhất trên 1 Nhà xe. Muốn làm việc trên 2 Nhà xe độc lập cùng lúc:
    - Cách 1: Dùng 2 thiết bị khác nhau (ví dụ: PC mở Nhà xe Hà Nội, Điện thoại mở Nhà xe Sài Gòn $\rightarrow$ 2 Session ID khác nhau, chạy độc lập 100%).
    - Cách 2: Trên cùng 1 PC nhưng dùng 2 Profile Chrome khác nhau (hoặc 1 Tab thường + 1 Tab ẩn danh Incognito).

---

### ❌ Hiểu lầm 7: "Kiểm tra ràng buộc chéo (Cross-Tenant Validation) là gì?"

* **Khái niệm:** Là nguyên tắc **TUYỆT ĐỐI KHÔNG TIN TƯỞNG MÙ QUÁNG VÀO CÁC ID CLIENT GỬI LÊN**, mọi truy vấn đều phải đối chiếu chéo với `tenant_id` lấy từ Token xác thực.
* **Ví dụ chống IDOR:**
  - Token của user mang `tenant_id = A`.
  - User gửi request cập nhật chi nhánh: `PUT /api/v1/branches/{id_chi_nhanh_B}`.
  - Service bắt buộc phải query: `branchRepository.findByIdAndTenantId(branchId, tenantId)`. Chi nhánh B thuộc Nhà xe B nên câu query trả về rỗng $\rightarrow$ Ném lỗi `403 Forbidden` ngay lập tức!

---

## 🛡️ 3. PHÂN TÍCH 5 EDGE CASES "CHÍ MẠNG" & THIẾT KẾ PHÒNG VỆ

Dưới đây là 5 kịch bản rủi ro cao nhất trong thực tế có thể gây tổn thất kinh doanh, xáo trộn dữ liệu hoặc hủy hoại uy tín nhà xe, kèm giải pháp thiết kế triệt để:

### 🔥 Edge Case 1: Trộn lẫn Tenant giữa 2 Tab trình duyệt (Cross-Tenant Data Bleed)
- **Rủi ro:** Tab 2 chuyển tenant làm ghi đè cookie `refreshToken` $\rightarrow$ Tab 1 refresh token bị đổi sang tenant của Tab 2 $\rightarrow$ Lưu nhầm đơn hàng của nhà xe này sang nhà xe khác.
- **Phòng vệ:**
  - Trên 1 trình duyệt, yêu cầu đồng nhất 1 tenant context.
  - Header request từ Frontend gửi kèm `X-Tenant-Id` của giao diện hiện tại. Backend so sánh với `tenant_id` trong JWT: nếu không khớp $\rightarrow$ Từ chối và bắt Frontend reload lại context!

### 🔥 Edge Case 2: Đào thải nhầm phiên đang làm thủ tục bàn giao xe & nhận cọc (Blind LRU Eviction)
- **Rủi ro:** Nhân viên quầy đón khách VIP, đếm cọc 50 triệu và kiểm tra xe trong 25 phút không gọi API. Sếp ở ngoài đăng nhập liên tiếp thiết bị mới làm phiên máy quầy bị LRU thu hồi. Nhân viên bấm Lưu đơn bị lỗi 401 $\rightarrow$ Văng trang Login, mất trắng form!
- **Phòng vệ (Đỉnh cao KISS & UX):**
  - **Backend (KISS/YAGNI):** Giữ nguyên thuật toán LRU tinh gọn, không thêm cờ ưu tiên hay timer phức tạp: `ORDER BY last_active_at ASC LIMIT 1`.
  - **Frontend (Auto-Save & Re-Auth Modal):** 
    - Form nhập liệu tự động lưu nháp liên tục vào `localStorage`.
    - Khi nhận lỗi `401 Unauthorized` do bị đá phiên LRU: Frontend **không tải lại trang (No Reload)**, mà bật ngay một **Modal Popup đăng nhập lại tại chỗ**: *"Phiên làm việc trên thiết bị này đã hết hạn. Vui lòng nhập mật khẩu để tiếp tục lưu đơn!"*.
    - Nhập xong có token mới $\rightarrow$ Tự động bấm Lưu lại đơn hàng! Không mất 1 ký tự, khách không phải chờ!

### 🔥 Edge Case 3: Lệch vị trí thực tế của xe do "Stale Branch Context"
- **Rủi ro:** Nhân viên sáng trực Cầu Giấy, chiều sang Đống Đa làm thủ tục nhận trả xe nhưng quên bấm đổi chi nhánh trên điện thoại (Token vẫn mang Cầu Giấy) $\rightarrow$ Xe thực tế ở Đống Đa nhưng phần mềm lưu ở Cầu Giấy $\rightarrow$ Hôm sau khách tới Cầu Giấy không có xe giao.
- **Phòng vệ:**
  - Khi làm thủ tục Bàn giao (`handover`) và Nhận lại xe (`return`): Backend **lấy chi nhánh từ chính đơn hàng (`booking.getBranchId()`)**, TUYỆT ĐỐI KHÔNG tin vào `activeBranchId` trong token!
  - Màn hình danh sách đơn hàng đã tự động lọc `WHERE branch_id = :activeBranchId`, nhân viên ở sai chi nhánh sẽ không thấy đơn hiển thị, buộc phải bấm đổi sang đúng chi nhánh.

### 🔥 Edge Case 4: Cửa sổ 15 phút của Access Token khi Nhân viên bị sa thải (Zombie Token Window)
- **Rủi ro:** Sếp bấm khóa tài khoản và thu hồi thiết bị của nhân viên gian lận. Nhưng Access Token cũ vẫn còn hạn 15 phút về mặt mật mã $\rightarrow$ Nhân viên vẫn gọi API tải trộm dữ liệu khách hàng.
- **Phòng vệ:**
  - **Khóa tài khoản "Một phát chết toàn bộ":** Khi khóa tài khoản, Service thực hiện nguyên tử:
    ```java
    user.setIsActive(false);
    userSessionRepository.revokeAllByUserId(userId);
    ```
  - Trong `JwtAuthenticationFilter`: Bắt buộc kiểm tra `session.isRevoked == false` ở mọi request $\rightarrow$ Chặn đứng 100% các thiết bị của kẻ gian trong vòng 0.001 giây!

### 🔥 Edge Case 5: "Song kiếm hợp bích" — Cùng 1 nghiệp vụ Booking kết hợp cả 2 loại Lock
- **Rủi ro:** 
  - Nguy cơ 1: 2 khách cùng đặt 1 xe cùng khung giờ (Double Booking).
  - Nguy cơ 2: Đơn đang chờ cọc, Sale ở văn phòng bấm xác nhận cọc chuyển khoản, Nhân viên bãi cùng lúc bấm xác nhận cọc tiền mặt $\rightarrow$ Đơn ghi nhận 2 lần cọc (Double Payment), lệch sổ sách kế toán.
- **Phòng vệ (Song kiếm hợp bích):**
  ```
  [ GIAI ĐOẠN 1: TẠO ĐƠN MỚI (INSERT) ]
  👉 DÙNG PESSIMISTIC LOCK TRÊN XE (2 mili-giây)
  - Khóa chiếc xe cha: SELECT * FROM vehicles WHERE id = :id FOR UPDATE;
  - Check xem khung giờ có bị ai chiếm chưa -> Insert booking mới.
  - Nhả khóa xe ngay! Chống Double Booking tuyệt đối 100%.

                       │
                       ▼
  [ GIAI ĐOẠN 2: TÁC NGHIỆP TRÊN ĐƠN ĐÃ CÓ (UPDATE) ]
  👉 DÙNG OPTIMISTIC LOCK TRÊN BOOKING (@Version)
  - Đơn đã nằm trong DB (version = 1).
  - Thao tác xác nhận cọc, bàn giao, trả xe đều kiểm tra @Version.
  - Ai bấm trước thành công (version lên 2), người bấm sau bị ném lỗi Conflict:
    "Đơn này đã được xác nhận thanh toán trước đó!". Chống Double Payment triệt để!
  ```

---

## 🗄️ 4. ĐẶC TẢ CƠ SỞ DỮ LIỆU (DATABASE SPECIFICATION)

### 4.1 Migration File: `V8__create_user_sessions_table.sql`
Đường dẫn: `backend/src/main/resources/db/migration/V8__create_user_sessions_table.sql`

```sql
-- Migration V8: Quản lý Phiên đăng nhập Đa thiết bị (Multi-Session & Device Management)

CREATE TABLE IF NOT EXISTS user_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),               -- Mã phiên (Session ID mang trong JWT Token)
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,-- Người dùng sở hữu phiên
    device_name VARCHAR(255),                                    -- Tên thiết bị/trình duyệt (Parse từ User-Agent)
    ip_address VARCHAR(45),                                      -- Địa chỉ IP lúc đăng nhập (IPv4 hoặc IPv6)
    is_revoked BOOLEAN NOT NULL DEFAULT FALSE,                   -- TRUE: Đã đăng xuất / bị thu hồi
    last_active_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Thời điểm gọi API gần nhất (phục vụ LRU)
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,                -- Thời điểm hết hạn phiên (theo hạn Refresh Token)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP-- Thời điểm tạo phiên
);

-- Index tra cứu phiên của người dùng
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON user_sessions(user_id);

-- Composite Index tối ưu truy vấn đếm phiên active và tìm phiên cũ nhất để đào thải LRU
CREATE INDEX IF NOT EXISTS idx_user_sessions_lru ON user_sessions(user_id, is_revoked, last_active_at);
```

### 4.2 Giải thích chi tiết các trường bảng `user_sessions`
- **`id` (UUID PK):** Đóng vai trò là `sessionId`. Giá trị này được inject vào JWT Claims:
  - Access Token: claim `"current_session_id"` hoặc `"session_id"`.
  - Refresh Token: claim `"current_session_id"` hoặc `"session_id"`.
- **`user_id` (UUID FK):** Khóa ngoại tham chiếu đến bảng `users`. Khai báo `ON DELETE CASCADE` để khi xóa người dùng thì toàn bộ phiên đăng nhập tự động dọn sạch.
- **`device_name` (VARCHAR):** Chuỗi định danh thiết bị thân thiện (ví dụ: *"Chrome 120 on Windows 11"*, *"Safari on iOS"*). Backend tự động bóc tách từ Header `User-Agent`.
- **`ip_address` (VARCHAR):** Lưu IP đăng nhập để hiển thị trên màn hình kiểm tra an ninh tài khoản.
- **`is_revoked` (BOOLEAN):** 
  - `FALSE`: Phiên đang hoạt động bình thường.
  - `TRUE`: Phiên đã bị hủy (do người dùng bấm Đăng xuất, bị đá từ xa, hoặc bị đào thải LRU).
- **`last_active_at` (TIMESTAMPTZ):** Cập nhật mỗi khi có tương tác hoặc refresh token. Căn cứ để thuật toán LRU chọn phiên có `last_active_at` cũ nhất để thu hồi khi vượt quá 5 thiết bị.
- **`expires_at` (TIMESTAMPTZ):** Thời hạn sống tối đa của phiên (7 ngày). Dùng cho robot dọn dẹp chạy định kỳ hàng đêm: `DELETE WHERE expires_at < NOW() OR is_revoked = TRUE`.

---

## 🏛️ 5. KIẾN TRÚC CODE: NGUYÊN LÝ DEPENDENCY INVERSION (SẴN SÀNG CHO REDIS)

Để hiện tại chạy mượt trên **PostgreSQL**, nhưng tương lai khi dự án scale lớn có thể chuyển sang **Redis** trong 15 phút mà không phải sửa một dòng code nghiệp vụ nào, ta áp dụng nguyên lý **Dependency Inversion (SOLID)**:

```
                      ┌──────────────────────────────────────┐
                      │    AuthService / SecurityFilter      │
                      └──────────────────┬───────────────────┘
                                         │ (Chỉ gọi Interface trừu tượng)
                                         ▼
                      ┌──────────────────────────────────────┐
                      │     <<Interface>> SessionManager     │
                      │  - createSession(userId, ...)        │
                      │  - isSessionActive(sessionId)        │
                      │  - revokeSession(sessionId)          │
                      │  - revokeAllOtherSessions(userId)    │
                      │  - revokeAllByUserId(userId)         │
                      │  - getActiveSessions(userId)         │
                      │  - updateLastActive(sessionId)       │
                      └──────────────────┬───────────────────┘
                                         │
               ┌─────────────────────────┴─────────────────────────┐
               │                                                   │
               ▼                                                   ▼
   ┌───────────────────────────────┐                   ┌───────────────────────────────┐
   │ DatabaseSessionManager (JPA)  │                   │ RedisSessionManager (Roadmap) │
   │ 👉 Giai đoạn hiện tại:        │                   │ 👉 Khi scale lớn: Đọc/ghi     │
   │    Thao tác bảng user_sessions│                   │    Redis Hash & TTL cực nhanh │
   │    trên PostgreSQL qua JPA    │                   │    trên RAM (Zero DB hit)     │
   └───────────────────────────────┘                   └───────────────────────────────┘
```

---

## 🔄 6. ĐẶC TẢ THUẬT TOÁN & LUỒNG XỬ LÝ NGHIỆP VỤ (STEP-BY-STEP FLOW)

### 6.1 Luồng Đăng nhập (`POST /api/v1/auth/login`)
1. **Bước 1 (Xác thực thông tin):** Kiểm tra email và mật khẩu qua `passwordEncoder.matches()`. Kiểm tra tài khoản `isActive`.
2. **Bước 2 (Trích xuất thông tin thiết bị):**
   - Đọc header `User-Agent` $\rightarrow$ phân tích ra `deviceName` (ví dụ: *"Chrome 120 on Windows 11"*).
   - Đọc địa chỉ IP từ `HttpServletRequest` (hỗ trợ đọc qua proxy `X-Forwarded-For`).
3. **Bước 3 (Thuật toán đào thải LRU - Giới hạn 5 thiết bị):**
   - Đếm số phiên đang active của user: `WHERE user_id = :userId AND is_revoked = false AND expires_at > NOW()`.
   - Nếu `activeCount >= 5`:
     - Tìm phiên có `last_active_at` **nhỏ nhất (cũ nhất)**:
       ```sql
       SELECT id FROM user_sessions 
       WHERE user_id = :userId AND is_revoked = false AND expires_at > NOW() 
       ORDER BY last_active_at ASC LIMIT 1;
       ```
     - Cập nhật phiên đó: `is_revoked = true`.
4. **Bước 4 (Tạo phiên mới):**
   - Lưu bản ghi mới vào `user_sessions` với `expires_at = NOW() + 7 days`.
   - Nhận về `sessionId` (UUID).
5. **Bước 5 (Cấp Token):**
   - Sinh Access Token và Refresh Token chứa claim `current_session_id = sessionId`.
   - Trả về JSON cho client, gắn Refresh Token vào HttpOnly Cookie.

---

### 6.2 Luồng Xác thực Request (`JwtAuthenticationFilter`)
1. **Bước 1:** Trích xuất Access Token từ Header `Authorization: Bearer <token>`.
2. **Bước 2:** Xác thực chữ ký token qua `jwtProvider.validateToken(token)`.
3. **Bước 3:** Bóc tách `sessionId` từ claims.
4. **Bước 4:** Kiểm tra phiên qua `sessionManager.isSessionActive(sessionId)`:
   - Phiên tồn tại trong `user_sessions`.
   - `is_revoked == false`.
   - `expires_at > NOW()`.
   *(Nếu không thỏa mãn $\rightarrow$ Ném `AppException(ErrorCode.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn hoặc bị thu hồi ở nơi khác")`).*
5. **Bước 5:** Nạp thông tin định danh vào `SecurityContextHolder` và `TenantContext`. Cho request đi tiếp!
$\implies$ **Không còn câu lệnh so sánh `equals(user.get().getCurrentSessionId())`! Hai người đăng nhập 2 máy tính khác nhau đều có `sessionId` riêng, hoạt động song song mượt mà!**

---

### 6.3 Luồng Tự động làm mới Token (Silent Refresh - `POST /api/v1/auth/refresh-token`)
1. **Bước 1:** Lấy Refresh Token từ HttpOnly Cookie.
2. **Bước 2:** Xác thực chữ ký bí mật của Refresh Token qua `jwtProvider.validateToken()`.
3. **Bước 3:** Trích xuất `sessionId` từ claims của Refresh Token.
4. **Bước 4:** Kiểm tra `sessionManager.isSessionActive(sessionId)`. Nếu hợp lệ:
   - Cập nhật `last_active_at = Instant.now()`.
5. **Bước 5:** Cấp Access Token mới (kèm Refresh Token mới) bảo tồn nguyên vẹn ngữ cảnh (`tenantId`, `activeBranchId`, `sessionId`).

---

### 6.4 Luồng Đăng xuất & Quản lý thiết bị (Device Management APIs)

#### API 1: Đăng xuất phiên hiện tại (`POST /api/v1/auth/logout`)
- Lấy `sessionId` từ Token hiện tại.
- Gọi `sessionManager.revokeSession(sessionId)` $\rightarrow$ Cập nhật `is_revoked = true`.
- Xóa HttpOnly Cookie.

#### API 2: Lấy danh sách thiết bị đang online (`GET /api/v1/auth/sessions`)
- Lấy `userId` từ `TenantContext`.
- Query toàn bộ phiên active của user: `WHERE user_id = :userId AND is_revoked = false AND expires_at > NOW() ORDER BY last_active_at DESC`.
- Map sang danh sách `UserSessionResponseDTO`, trong đó đánh dấu `isCurrent = (session.getId().equals(currentSessionId))`.

#### API 3: Đăng xuất một thiết bị cụ thể từ xa (`DELETE /api/v1/auth/sessions/{sessionId}`)
- Kiểm tra tính sở hữu (Chống IDOR): Phiên bị xóa bắt buộc phải thuộc về `userId` hiện tại.
- Cập nhật phiên đó: `is_revoked = true`.
- Ngay lập tức, thiết bị đó ở lần gửi request tiếp theo sẽ nhận mã lỗi `401 Unauthorized` và bị đưa về màn hình đăng nhập.

#### API 4: Đăng xuất khỏi toàn bộ các thiết bị khác (`POST /api/v1/auth/sessions/revoke-others`)
- Lấy `userId` và `sessionId` của thiết bị hiện tại.
- Cập nhật: `UPDATE user_sessions SET is_revoked = true WHERE user_id = :userId AND id != :currentSessionId`.
- Giữ nguyên thiết bị hiện tại, đăng xuất tất cả các máy lạ còn lại!

---

## 📦 7. HỢP ĐỒNG DỮ LIỆU DTO (DATA TRANSFER OBJECT CONTRACTS)

### 7.1 `UserSessionResponseDTO.java`
Đường dẫn: `backend/src/main/java/com/carrental/car_rental_backend/auth/dto/response/UserSessionResponseDTO.java`

- **Annotations:** `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- **Các trường:**
  - `id` (`UUID`): Mã phiên (Session ID).
  - `deviceName` (`String`): Tên thiết bị (VD: *"Chrome trên Windows 11"*).
  - `ipAddress` (`String`): Địa chỉ IP đăng nhập.
  - `lastActiveAt` (`Instant`): Thời điểm hoạt động gần nhất.
  - `createdAt` (`Instant`): Thời điểm đăng nhập.
  - `isCurrent` (`boolean`): Cờ đánh dấu đây có phải thiết bị mà người dùng đang cầm trên tay hay không.

---

## 🧪 8. CHECKLIST KIỂM THỬ & NGHIỆM THU (TESTING & VERIFICATION CHECKLIST)

Khi triển khai code thực tế, dev phải tự kiểm chứng 5 kịch bản sau:

- [ ] **Kịch bản 1 (Hai thiết bị hoạt động song song):**
  - Mở Trình duyệt 1 (Chrome) đăng nhập tài khoản `staff@rentcarhanoi.vn`.
  - Mở Trình duyệt 2 (Firefox / Ẩn danh) đăng nhập cùng tài khoản `staff@rentcarhanoi.vn`.
  - Cả 2 trình duyệt cùng gọi API (tạo đơn, xem xe) $\rightarrow$ Cả 2 đều thành công 200 OK, **không ai bị đá văng**.
- [ ] **Kịch bản 2 (Thuật toán đào thải LRU 5 thiết bị & Re-Auth Modal):**
  - Đăng nhập liên tục trên 5 phiên ẩn danh khác nhau. DB ghi nhận 5 dòng `is_revoked = false`.
  - Đăng nhập phiên thứ 6 $\rightarrow$ Phiên thứ 1 (cũ nhất) tự động chuyển thành `is_revoked = true`.
  - Quay lại phiên thứ 1 click chuột gọi API $\rightarrow$ Nhận lỗi `401 Unauthorized` báo phiên đã bị thu hồi.
  - Kiểm tra Frontend bật Re-Auth Modal và giữ nguyên form dữ liệu `localStorage`.
- [ ] **Kịch bản 3 (Đăng xuất từ xa):**
  - Đăng nhập trên Thiết bị A và Thiết bị B.
  - Trên Thiết bị A, gọi `GET /api/v1/auth/sessions` $\rightarrow$ Thấy Thiết bị B trong danh sách.
  - Trên Thiết bị A, bấm xóa Thiết bị B (`DELETE /api/v1/auth/sessions/{idB}`).
  - Thiết bị B click chuột gọi API $\rightarrow$ Bị đá văng về trang Login. Thiết bị A vẫn làm việc bình thường.
- [ ] **Kịch bản 4 (Khóa tài khoản thu hồi toàn bộ thiết bị):**
  - Đăng nhập 3 thiết bị. Khóa tài khoản nhân viên.
  - Toàn bộ 3 thiết bị đều bị thu hồi phiên (`is_revoked = true`), không thiết bị nào lọt qua Filter.
- [ ] **Kịch bản 5 (Tương thích ngược Git Merge):**
  - Không xóa cột `current_session_id` trên bảng `users`.
  - Các nhánh `branch`, `tenant` của đồng đội pull về compile bình thường không bị đỏ lỗi.

---

## 🎯 9. KẾT LUẬN & BÀN GIAO CHO DEV TỰ CODE
Tài liệu này đã giải quyết trọn vẹn mọi khía cạnh:
1. Nghiệp vụ thực tế nhà xe (Sếp và Nhân viên bãi không bao giờ bị mất form).
2. Tinh giản tối đa theo nguyên lý YAGNI (Bỏ `refresh_token_hash` rườm rà).
3. Đảm bảo an toàn merge code (Expand and Contract).
4. Kiến trúc cắm rút sẵn sàng cho Redis trong tương lai.
5. Phòng vệ 5 Edge Cases chí mạng bảo vệ an toàn dữ liệu và uy tín doanh nghiệp.

*Dev hãy dựa trên bản đặc tả này để tự tay gõ từng dòng code, tự kiểm chứng bằng các test case trên và làm chủ 100% phân hệ Auth của mình!*
