# 🧭 CẨM NANG HƯỚNG DẪN TỰ CODE TỪNG BƯỚC: MULTI-SESSION & QUẢN LÝ THIẾT BỊ (DEVICE MANAGEMENT)

> **Dự án:** Car Rental SaaS (Java 17 / Spring Boot 4.0.7 / PostgreSQL)  
> **Module:** `auth` & `security`  
> **Ngày tạo:** 25/11/2026  
> **Đặc tả gốc:** [2026-11-23-multi-session-device-management-spec.md](./2026-11-23-multi-session-device-management-spec.md)  
> **Nguyên tắc đào tạo ([AGENTS.md](../../../AGENTS.md)):** Hướng dẫn bằng bức tranh kiến trúc, hợp đồng Class/Method, bản chất Java Core, thuật toán văn bản và từ khóa Google — **KHÔNG GEN CODE HOÀN CHỈNH** để bạn tự tay gõ 100% và làm chủ từng dòng code!

---

## 🏛️ 1. BỨC TRANH TOÀN CẢNH & ẨN DỤ ĐỜI SỐNG

Hãy tưởng tượng hệ thống đăng nhập Multi-Session mới giống như **Bãi giữ xe thông minh cấp tối đa 5 thẻ từ cho mỗi cư dân**:

1. **Cơ chế cũ (`users.current_session_id`):** Cư dân chỉ có **1 chiếc thẻ duy nhất**. Vợ lấy thẻ mới ở cổng thì thẻ của chồng ở cơ quan lập tức bị hủy → mở cửa không được, đồ đạc đang xách trên tay rơi tung tóe.
2. **Cơ chế mới (`user_sessions`):**
   - Mỗi thiết bị (PC văn phòng, iPhone ngoài bãi, iPad...) được cấp **1 thẻ từ riêng** (`sessionId` = UUID PK trong `user_sessions`).
   - **Móc treo 5 thẻ (`SELECT * FROM users WHERE id = :id FOR UPDATE`):** Trước khi phát thẻ mới cho anh A, bảo vệ cầm tạm móc treo thẻ của anh A trong **2 mili-giây** để đếm thẻ, tránh 2 người cùng lấy thẻ thứ 5 & 6 một lúc (TOCTOU) hoặc sếp vừa bấm thu hồi toàn bộ thẻ của anh A mà cổng vẫn lỡ in thêm thẻ mới (R04).
   - **Quy tắc LRU 5 thẻ:** Nếu anh A đã có 5 thẻ đang hoạt động mà xin thêm thẻ thứ 6 → thu hồi chiếc thẻ **lâu nhất không quẹt qua cổng** (`last_active_at` cũ nhất).
   - **Throttle 5 phút:** Để tránh mỗi lần cư dân đi qua cửa lại phải mở sổ ra gạch ngày giờ gây tắc cổng (tốn write DB), bảo vệ chỉ ghi lại giờ quẹt mới vào sổ nếu **lần ghi trước đã cách đây $\ge 5$ phút**.
   - **Sliding Expiration 7 ngày:** Miễn là trong vòng 7 ngày vẫn đi qua cổng gia hạn (`/refresh-token`), thẻ lại được kéo dài hạn thêm 7 ngày nữa (đồng bộ 3 nơi: JWT ↔ Cookie ↔ DB). Quá 7 ngày không đụng tới, thẻ tự hết hạn.
   - **Idempotent Logout:** Cuối ca trực, dù thẻ ra vào tạm (Access Token 15p) đã hết giờ, nhân viên chỉ cần giơ thẻ tháng (Cookie `refreshToken`) ở quầy lễ tân là vẫn trả thẻ được ngay — **không bao giờ bắt nhập lại mật khẩu chỉ để đăng xuất**!

### 🌉 Đối chiếu tư duy Frontend (React / Angular / TypeScript)
| Khái niệm Java / Spring trong bài này | Tương đương bên Frontend / JS / TS |
| :--- | :--- |
| `SessionManager` (Interface) + `DatabaseSessionManager` (`@Service`) | TypeScript `interface StorageAdapter` + `class LocalStorageAdapter implements StorageAdapter` (đổi sang Redis sau này không sửa code gọi). |
| `TransactionTemplate` (bọc đoạn lock + DB ghi) | Tách hàm `async` gọi CPU nặng (`bcrypt`) ra trước rồi mới mở `db.transaction(async (tx) => ...)` để không giữ connection vô ích. |
| `@Lock(LockModeType.PESSIMISTIC_WRITE)` | Mutex / Semaphore cấp hàng trong PostgreSQL: luồng đến sau tự xếp hàng chờ luồng trước `commit()` (chỉ 2–5ms). |
| `@Modifying(clearAutomatically = true)` | Giống `queryClient.invalidateQueries()` của TanStack Query: xóa cache Persistence Context sau khi chạy câu `UPDATE/DELETE` trực tiếp dưới DB để không đọc nhầm dữ liệu cũ trên RAM! |
| `Boolean isCurrent` vs `boolean isCurrent` trong Lombok + Jackson | Kiểu đổi tên prop bất ngờ lúc `JSON.stringify()`: primitive `boolean isCurrent` sinh getter `isCurrent()`, Jackson mặc định cắt chữ `is` thành key JSON `"current"`! |

---

## 🗺️ 2. LỘ TRÌNH 7 CHẶNG TỰ CODE (COMPILE SẠCH SAU MỖI CHẶNG)

```
Chặng 1: DB Migration V3 + Config + ErrorCode + User/UserRepository
   │
   ▼
Chặng 2: UserSession Entity & UserSessionRepository (JPA + @Modifying)
   │
   ▼
Chặng 3: SessionManager Interface & DatabaseSessionManager (LRU + Throttle 5p + Sliding)
   │
   ▼
Chặng 4: ClientInfoExtractor + Cập nhật JwtProvider + Xóa Dead Code
   │
   ▼
Chặng 5: SecurityConfig (CORS X-Tenant-Id + Logout permitAll) & JwtAuthenticationFilter
   │
   ▼
Chặng 6: AuthServiceImpl (TransactionTemplate + 8 chỗ Refresh + Idempotent Logout)
         + AuthController (Cookie helper) + SessionController + SessionCleanupTask
   │
   ▼
Chặng 7: Tự viết 7 Unit Tests (JUnit 5 + Mockito AAA) & Chạy 7 Kịch bản Thực tế
```

> ⚠️ **Lưu ý phối hợp nhóm trước khi bắt đầu:** Nhắn nhanh anh Giang & anh Huy chốt bạn dùng số **`V3`** cho migration `user_sessions` (và **tuyệt đối không chạm sửa `V1__init_schema.sql` hay `V2__seed_system_data.sql`** để tránh nổ Flyway checksum trên máy anh Giang).

---

## 🧱 CHẶNG 1: NỀN MÓNG CSDL, CẤU HÌNH & KHÓA DÒNG `USER`

### 1.1 Tạo file Migration `V3__create_user_sessions_table.sql`
- **Đường dẫn:** `backend/src/main/resources/db/migration/V3__create_user_sessions_table.sql`
- **Nhiệm vụ:** Tạo bảng `user_sessions` và 2 composite index (`idx_user_sessions_lru`, `idx_user_sessions_cleanup`) khớp 100% với DDL tại [Spec Mục 4.1](./2026-11-23-multi-session-device-management-spec.md#41-migration-file-v3__create_user_sessions_tablesql).
- **Nhớ kỹ:** Không tạo thêm index đơn `user_id` vì `idx_user_sessions_lru(user_id, is_revoked, last_active_at)` đã bao phủ `user_id` theo quy tắc Left-most Prefix.

### 1.2 Cập nhật cấu hình `application.yaml` & `CarRentalBackendApplication.java`
- **File 1:** [application.yaml](../../../backend/src/main/resources/application.yaml)
  - Thêm `server.forward-headers-strategy: framework` (để Spring đọc `X-Forwarded-For` an toàn khi đứng sau Nginx).
  - Thêm khối `app:` với các key:
    - `app.auth.max-active-sessions: 5`
    - `app.auth.last-active-throttle-minutes: 5`
    - `app.auth.session-cleanup-cron: "0 0 2 * * *"`
    - `app.auth.revoked-audit-retention-days: 7`
    - `app.cookie.secure: ${COOKIE_SECURE:false}` *(để test điện thoại qua LAN HTTP không bị chặn cookie; production bật `true`)*.
- **File 2:** [CarRentalBackendApplication.java](../../../backend/src/main/java/com/carrental/car_rental_backend/CarRentalBackendApplication.java)
  - Gắn thêm `@EnableScheduling` lên class chính.
  - 🔬 **Giải ảo Java Core:** `@EnableScheduling` đăng ký `ScheduledAnnotationBeanPostProcessor` — lúc app start, nó dùng **Java Reflection** quét mọi Bean tìm method gắn `@Scheduled` và nạp vào một `ScheduledExecutorService` (Thread Pool nền của JVM) để chạy đúng lịch cron.

### 1.3 Cập nhật `ErrorCode.java`, `User.java` và `UserRepository.java`
- **File 1:** [ErrorCode.java](../../../backend/src/main/java/com/carrental/car_rental_backend/common/exception/ErrorCode.java)
  - Bổ sung hằng số enum: `TENANT_CONTEXT_MISMATCH` với code `"TENANT_CONTEXT_MISMATCH"`, message tiếng Việt thông báo ngữ cảnh nhà xe trên tab đã thay đổi, và `HttpStatus.CONFLICT` (`409`).
- **File 2:** [User.java](../../../backend/src/main/java/com/carrental/car_rental_backend/account/entity/User.java)
  - Chỉ gắn `@Deprecated` lên thuộc tính `currentSessionId` (tuyệt đối **không xóa** cột/thuộc tính này — nguyên lý Expand-and-Contract!).
- **File 3:** [UserRepository.java](../../../backend/src/main/java/com/carrental/car_rental_backend/account/repository/UserRepository.java)
  - Giữ nguyên `updateNewSessionId` (gắn `@Deprecated` để không vỡ code nhánh khác nếu còn gọi).
  - Khai báo thêm method khóa dòng phục vụ Login & Lockout:
    - **Chữ ký:** `Optional<User> findByIdForUpdate(@Param("id") UUID id);`
    - **Annotations:** `@Lock(LockModeType.PESSIMISTIC_WRITE)` và `@Query` chọn `User` theo `id`.
    - 🔬 **Giải ảo Spring/JPA:** Hibernate dịch `@Lock(PESSIMISTIC_WRITE)` thành hậu tố SQL `FOR UPDATE` gửi xuống PostgreSQL; dòng `users` đó bị khóa cho tới khi transaction hiện tại `commit()` hoặc `rollback()`.

- **🔍 Từ khóa Google Chặng 1:**
  - `"Spring Data JPA @Lock LockModeType.PESSIMISTIC_WRITE custom query"`
  - `"Spring Boot @EnableScheduling cron expression application.yaml"`
  - `"Spring Boot server.forward-headers-strategy framework X-Forwarded-For"`

---

## 🗄️ CHẶNG 2: TẦNG DỮ LIỆU PHIÊN (`UserSession` & `UserSessionRepository`)

### 2.1 Tạo Entity `UserSession.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/entity/UserSession.java`
- **Annotations trên Class:** `@Entity`, `@Table(name = "user_sessions")`, `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` (**⛔ CẤM dùng `@Data` trên JPA Entity!**).
- **Danh sách thuộc tính ánh xạ 1-1 với bảng `user_sessions`:**
  - `id` (`UUID`): `@Id`, `@GeneratedValue(strategy = GenerationType.UUID)`
  - `userId` (`UUID`): `@Column(name = "user_id", nullable = false)`
  - `deviceName` (`String`): `@Builder.Default`, `@Column(name = "device_name", nullable = false, length = 255)` = `"Unknown Device"`
  - `ipAddress` (`String`): `@Column(name = "ip_address", length = 45)`
  - `isRevoked` (`Boolean`): **`@Builder.Default`**, `@Column(name = "is_revoked", nullable = false)` = `false` *(nhớ bẫy Task 10: thiếu `@Builder.Default` là Lombok truyền `null` xuống cột `NOT NULL`!)*
  - `lastActiveAt` (`Instant`): `@Column(name = "last_active_at", nullable = false)`
  - `expiresAt` (`Instant`): `@Column(name = "expires_at", nullable = false)`
  - `createdAt` (`Instant`): `@Column(name = "created_at", nullable = false, updatable = false)`
- **Lifecycle Callback `@PrePersist`:** Khởi tạo `createdAt` và `lastActiveAt` bằng `Instant.now()` nếu đang `null`; nếu `deviceName` null/blank thì gán `"Unknown Device"`; nếu `isRevoked` null thì gán `false`.

### 2.2 Tạo Repository `UserSessionRepository.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/repository/UserSessionRepository.java`
- **Khai báo:** `public interface UserSessionRepository extends JpaRepository<UserSession, UUID>`
- **Danh sách các method cần định nghĩa:**
  1. `@Transactional(readOnly = true)` + `Optional<UserSession> findByIdAndUserId(UUID id, UUID userId);`
     - *Mục đích:* Lấy phiên theo cặp `(id, userId)` — chống IDOR ngay từ tầng SQL.
     - 🔬 **Giải ảo Spring Data JPA (Bắt buộc gắn `@Transactional(readOnly = true)`!):** Chỉ các method có sẵn trong `SimpleJpaRepository` (`findById`, `findAll`, `count`...) mới được Spring gắn sẵn `readOnly = true`. Custom derived query bạn tự đặt tên như `findByIdAndUserId` **KHÔNG tự động có `readOnly = true`**! Gắn tường minh `@Transactional(readOnly = true)` (của `org.springframework.transaction.annotation.Transactional`) ép Hibernate tắt Snapshot & Dirty Checking, tiết kiệm ~50% RAM cho entity và nhả HikariCP nhanh nhất (lưu ý: do chưa có Redis/Second-Level Cache nên hàm này luôn truy vấn thẳng PostgreSQL).
  2. `List<UserSession> findByUserIdAndIsRevokedFalseAndExpiresAtAfterOrderByLastActiveAtAscCreatedAtAsc(UUID userId, Instant now);` *(hoặc viết bằng `@Query` JPQL gọn hơn cho dễ đọc!)*
     - *Mục đích:* Phục vụ thuật toán LRU khi login (sắp xếp từ cũ nhất đến mới nhất; `createdAt ASC` làm luật phân định hòa tie-breaker khi 2 phiên trùng `lastActiveAt`).
  3. `List<UserSession> findByUserIdAndIsRevokedFalseAndExpiresAtAfterOrderByLastActiveAtDesc(UUID userId, Instant now);` *(hoặc `@Query`)*
     - *Mục đích:* Phục vụ API `GET /api/v1/auth/sessions` hiển thị thiết bị mới dùng nhất lên đầu.
  4. `int updateLastActiveIfStale(@Param("id") UUID id, @Param("now") Instant now, @Param("threshold") Instant threshold);`
     - **⚠️ CỰC KỲ QUAN TRỌNG (Chống `TransactionRequiredException`):** Gắn **`@Transactional`** (import `org.springframework.transaction.annotation.Transactional`) + **`@Modifying(clearAutomatically = true)`** + `@Query` cập nhật `lastActiveAt = :now` với điều kiện `id = :id AND isRevoked = false AND lastActiveAt < :threshold`.
     - 🔬 **Giải ảo Persistence Context & HikariCP:** Câu bulk `@Modifying` `UPDATE` bắn thẳng 1 câu SQL xuống PostgreSQL — **không `SELECT`, không tạo Object `UserSession` hay Snapshot trong Persistence Context**. Ngay khi hàm trả về `int` (~1ms), Spring Proxy gọi ngay `commit()` và **trả Connection về HikariCP Pool lập tức** (không giữ chờ Controller!).
     - 🔬 **Tại sao phải có Throttle 5 phút + `AND last_active_at < :threshold` trong SQL?**
       1. *PostgreSQL MVCC & Write Amplification:* Mỗi lệnh `UPDATE` trong PostgreSQL sinh ra 1 dòng rác (*dead tuple* chờ `VACUUM`) và phải ghi lại cây B-Tree Index `idx_user_sessions_lru`. Dù MVCC giúp `UPDATE` không chặn `SELECT`, biến mọi `GET` thành `UPDATE` vẫn dễ làm nghẽn đĩa/WAL và làm cạn HikariCP Pool (10 connections). Throttle 5 phút triệt tiêu >95% lệnh ghi thừa!
       2. *Kịch bản thực tế 1 tab Angular bắn 4 API song song:* Nhân viên nghỉ tay 6 phút rồi bấm F5 / mở Dashboard → trong `ngOnInit()`, **chỉ 1 tab trình duyệt duy nhất** cũng tự động bắn song song 4–5 request (`/me`, `/bookings`, `/vehicles`, `/branches`) cùng 1 mili-giây mang cùng `sessionId` → điều kiện `AND last_active_at < :threshold` trong SQL đảm bảo chỉ **1 luồng đầu tiên** ghi đĩa, các luồng sau Postgres thấy `FALSE` nên bỏ qua (0 rows updated)!
  5. `int revokeByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);`
     - Gắn `@Modifying(clearAutomatically = true)` + `@Query` cập nhật `isRevoked = true WHERE id = :id AND userId = :userId AND isRevoked = false` (trả về số dòng ảnh hưởng `0` hoặc `1`).
  6. `int revokeAllOtherSessions(@Param("userId") UUID userId, @Param("currentSessionId") UUID currentSessionId);`
     - Gắn `@Modifying(clearAutomatically = true)` + `@Query` cập nhật `isRevoked = true WHERE userId = :userId AND id <> :currentSessionId AND isRevoked = false`.
  7. `int revokeAllByUserId(@Param("userId") UUID userId);`
     - Gắn `@Modifying(clearAutomatically = true)` + `@Query` cập nhật `isRevoked = true WHERE userId = :userId AND isRevoked = false`.
  8. `int deleteExpiredAndOldRevokedSessions(@Param("auditCutoff") Instant auditCutoff);`
     - Gắn `@Modifying(clearAutomatically = true)` + `@Query` xóa bản ghi thỏa `expiresAt < :auditCutoff OR (isRevoked = true AND lastActiveAt < :auditCutoff)`.

- **🔍 Từ khóa Google Chặng 2:**
  - `"Spring Data JPA @Modifying clearAutomatically flushAutomatically explained"`
  - `"Spring Data JPA TransactionRequiredException executing an update delete query"`
  - `"Lombok @Builder.Default JPA entity pitfalls"`

---

## 🧠 CHẶNG 3: BỘ NÃO QUẢN LÝ PHIÊN (`SessionManager` & `DatabaseSessionManager`)

### 3.1 Tạo Interface `SessionManager.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/service/SessionManager.java`
- **Hợp đồng 9 method (Dependency Inversion — sẵn sàng thay bằng `RedisSessionManager` sau này):**
  1. `UUID createSessionWithLru(UUID userId, String deviceName, String ipAddress);`
  2. `boolean validateAndTouchSession(UUID sessionId, UUID userId);`
  3. `void touchAndExtendSession(UUID sessionId, UUID userId);`
  4. `boolean revokeSession(UUID userId, UUID sessionId);` *(dùng cho `logout`: trả về `true` nếu có phiên bị thu hồi, `false` nếu không tìm thấy/đã thu hồi)*
  5. `void revokeDeviceSession(UUID userId, UUID currentSessionId, UUID targetSessionId);` *(dùng cho `DELETE /sessions/{sessionId}`: chặn self-revoke ném `400 BAD_REQUEST`, gọi `revokeByIdAndUserId` == 0 ném `404 RESOURCE_NOT_FOUND` để chống IDOR — giúp Controller không chứa logic `if/else`!)*
  6. `int revokeAllOtherSessions(UUID userId, UUID currentSessionId);`
  7. `int revokeAllByUserId(UUID userId);`
  8. `List<UserSessionResponseDTO> getActiveSessions(UUID userId, UUID currentSessionId);`
  9. `int cleanupExpiredAndOldRevokedSessions();`

### 3.2 Tạo Class `DatabaseSessionManager.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/service/impl/DatabaseSessionManager.java`
- **Annotations:** `@Slf4j`, `@Service`, `@RequiredArgsConstructor` *(**KHÔNG** đặt `@Transactional` ở cấp Class để kiểm soát từng method chính xác!)*.
- **Dependencies & Config inject:**
  - `private final UserSessionRepository userSessionRepository;`
  - `private final JwtProvider jwtProvider;` *(lấy `getRefreshTokenExpirationMs()`, không hardcode 7 ngày!)*
  - `@Value("${app.auth.max-active-sessions:5}") private int maxActiveSessions;`
  - `@Value("${app.auth.last-active-throttle-minutes:5}") private long lastActiveThrottleMinutes;`
  - `@Value("${app.auth.revoked-audit-retention-days:7}") private long revokedAuditRetentionDays;`
  *(Lưu ý Lombok: Vì có field `@Value` không phải `final`, `@RequiredArgsConstructor` vẫn chỉ tạo constructor cho các field `final`, Spring sẽ inject `@Value` bình thường — hoặc chuẩn sạch hơn, truyền qua Constructor với `@Value` để dễ viết Unit Test bằng `new DatabaseSessionManager(...)` mà không cần ReflectionTestUtils!)*

- **Thuật toán từng method (viết tay theo đúng thứ tự):**
  1. **`createSessionWithLru(userId, deviceName, ipAddress)`** — gắn `@Transactional` (Spring):
     - Bước 1: Lấy `Instant now = Instant.now()`.
     - Bước 2: Truy vấn danh sách phiên active sắp xếp `lastActiveAt ASC, createdAt ASC`.
     - Bước 3: Kiểm tra nếu `activeSessions.size() >= maxActiveSessions`:
       - Tính `int evictCount = activeSessions.size() - maxActiveSessions + 1`.
       - Duyệt từ `0` đến `evictCount - 1`, đánh dấu `session.setIsRevoked(true)` (Hibernate Dirty Checking trong Persistence Context sẽ tự gom `UPDATE` khi flush!).
     - Bước 4: Tính `Instant expiresAt = now.plusMillis(jwtProvider.getRefreshTokenExpirationMs())`.
     - Bước 5: Dùng `UserSession.builder()` tạo bản ghi mới (`userId`, `deviceName`, `ipAddress`, `isRevoked = false`, `lastActiveAt = now`, `expiresAt = expiresAt`, `createdAt = now`), gọi `save()`, trả về `saved.getId()`.
  2. **`validateAndTouchSession(sessionId, userId)`** — **⛔ KHÔNG gắn `@Transactional` lên hàm này!**
     - Bước 1: Guard clause: nếu `sessionId == null || userId == null` $\rightarrow$ trả về `false`.
     - Bước 2: Gọi `userSessionRepository.findByIdAndUserId(sessionId, userId)` *(đã gắn tường minh `@Transactional(readOnly = true)` ở Repository → truy vấn thẳng PostgreSQL, tắt Snapshot/Dirty Checking, nhả HikariCP ngay)*. Nếu `isEmpty()` $\rightarrow$ trả `false`.
     - Bước 3: Lấy `UserSession session`. Kiểm tra nếu `Boolean.TRUE.equals(session.getIsRevoked())` hoặc `!session.getExpiresAt().isAfter(now)` $\rightarrow$ trả `false`.
     - Bước 4 (Throttle 5 phút): Tính `Instant threshold = now.minus(Duration.ofMinutes(lastActiveThrottleMinutes))`. Nếu `session.getLastActiveAt() == null || session.getLastActiveAt().isBefore(threshold)`:
       - Gọi `userSessionRepository.updateLastActiveIfStale(sessionId, now, threshold)` *(hàm Repository có `@Transactional` + `@Modifying` riêng → không dính Self-Invocation, không load Entity vào Persistence Context, tự `commit()` & nhả HikariCP sau ~1ms, chỉ chạy cho <5% request và chống ghi trùng khi 1 tab Angular bắn 4–5 API song song lúc load trang!)*.
     - Bước 5: Trả về `true`.
  3. **`touchAndExtendSession(sessionId, userId)`** — gắn `@Transactional` (Sliding Expiration cho `/refresh-token`):
     - Bước 1: Tìm `session` qua `findByIdAndUserId(sessionId, userId)`. Nếu rỗng / `isRevoked == true` / `!expiresAt.isAfter(now)` $\rightarrow$ ném `AppException(ErrorCode.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại!")`.
     - Bước 2: Cập nhật `session.setLastActiveAt(now)` VÀ `session.setExpiresAt(now.plusMillis(jwtProvider.getRefreshTokenExpirationMs()))`.
     - Bước 3: Gọi `save(session)` (hoặc để Hibernate Dirty Checking tự flush).
  4. **`revokeSession(userId, sessionId)`** — gắn `@Transactional` *(dùng cho `logout`)*:
     - Gọi `userSessionRepository.revokeByIdAndUserId(sessionId, userId) > 0`.
  5. **`revokeDeviceSession(userId, currentSessionId, targetSessionId)`** — gắn `@Transactional` *(dùng cho `DELETE /sessions/{sessionId}`)*:
     - Guard clause 1: Nếu `targetSessionId.equals(currentSessionId)` $\rightarrow$ ném `AppException(ErrorCode.BAD_REQUEST, "Không thể thu hồi phiên hiện tại. Vui lòng sử dụng chức năng Đăng xuất!")`.
     - Guard clause 2: Gọi `int updated = userSessionRepository.revokeByIdAndUserId(targetSessionId, userId)`. Nếu `updated == 0` $\rightarrow$ ném `AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy phiên đăng nhập cần thu hồi!")` (chống IDOR enumeration).
  6. **`revokeAllOtherSessions(userId, currentSessionId)`** — gắn `@Transactional`:
     - Gọi `userSessionRepository.revokeAllOtherSessions(userId, currentSessionId)`.
  7. **`revokeAllByUserId(userId)`** — gắn `@Transactional`:
     - Gọi `userSessionRepository.revokeAllByUserId(userId)`.
  8. **`getActiveSessions(userId, currentSessionId)`** — gắn **`@Transactional(readOnly = true)`**:
     - Lấy danh sách phiên active sắp xếp `lastActiveAt DESC`.
     - Dùng Java Stream `.map(...)` gọi private mapper `toResponseDTO(session, currentSessionId)` (Manual Mapping qua `@Builder`, tính `isCurrent = session.getId().equals(currentSessionId)`).
  9. **`cleanupExpiredAndOldRevokedSessions()`** — gắn `@Transactional`:
     - Tính `Instant auditCutoff = Instant.now().minus(Duration.ofDays(revokedAuditRetentionDays))`.
     - Gọi `userSessionRepository.deleteExpiredAndOldRevokedSessions(auditCutoff)` và trả về số dòng đã xóa.

- **🔍 Từ khóa Google Chặng 3:**
  - `"Spring @Transactional self-invocation proxy pitfall explained"`
  - `"Spring Boot constructor injection with @Value for unit testing"`
  - `"Java Instant plusMillis Duration ofMinutes comparison isBefore isAfter"`

---

## 🔧 CHẶNG 4: TIỆN ÍCH CLIENT, CẬP NHẬT `JwtProvider` & DỌN DEAD CODE

### 4.1 Tạo Utility `ClientInfoExtractor.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/util/ClientInfoExtractor.java`
- **Thiết kế:** Class tiện ích (`@Component` hoặc utility class có các hàm `public static` stateless — hoàn toàn Thread-safe vì chỉ dùng biến local trên Stack!).
- **2 method cần viết:**
  1. `extractDeviceName(HttpServletRequest request)` $\rightarrow$ `String`:
     - Đọc header `"User-Agent"`. Nếu null/rỗng $\rightarrow$ trả `"Unknown Device"`.
     - Parse gọn nhẹ (KISS — dùng `toLowerCase()` + `contains()` kiểm tra thứ tự ưu tiên: `edg` trước `chrome`, `chrome` trước `safari`, `firefox`, `postman`, `curl`; OS: `windows`, `iphone`/`ipad`, `mac os`, `android`, `linux`) $\rightarrow$ ghép chuỗi thân thiện kiểu `"Chrome on Windows"`, `"Safari on iOS"`, `"Postman"`.
     - **An toàn DB:** Luôn cắt độ dài tối đa `255` ký tự (`Math.min(result.length(), 255)`) để không bao giờ vỡ cột `VARCHAR(255)`.
  2. `extractClientIp(HttpServletRequest request)` $\rightarrow$ `String`:
     - Đọc header `"X-Forwarded-For"`. Nếu có giá trị: tách theo dấu phẩy `,` và lấy phần tử đầu tiên (`.trim()`).
     - Nếu không có: lấy `request.getRemoteAddr()`.
     - **An toàn DB:** Nếu kết quả dài hơn `45` ký tự (kẻ xấu spam header giả) $\rightarrow$ cắt tối đa `45` ký tự khớp cột `VARCHAR(45)`.

### 4.2 Mở rộng `JwtProvider.java` & Xóa file thừa
- **Chỉnh sửa** [JwtProvider.java](../../../backend/src/main/java/com/carrental/car_rental_backend/security/jwt/JwtProvider.java):
  - Thêm 2 getter: `getRefreshTokenExpirationMs()` (trả `this.refreshTokenExpirationMs`) và `getRefreshTokenExpirationInSeconds()` (trả `this.refreshTokenExpirationMs / 1000`).
- **Xóa dead code:** Xóa file [RefreshTokenRequestDTO.java](../../../backend/src/main/java/com/carrental/car_rental_backend/auth/dto/request/RefreshTokenRequestDTO.java) (kiểm tra bằng tìm kiếm toàn dự án để chắc chắn không còn nơi nào import trước khi xóa).

- **🔍 Từ khóa Google Chặng 4:**
  - `"Spring Boot get client IP address X-Forwarded-For HttpServletRequest"`
  - `"Parse User-Agent string browser OS Java without heavy library"`

---

## 🛡️ CHẶNG 5: TRẠM GÁC AN NINH (`SecurityConfig` & `JwtAuthenticationFilter`)

### 5.1 Chỉnh sửa `SecurityConfig.java`
- **File:** [SecurityConfig.java](../../../backend/src/main/java/com/carrental/car_rental_backend/security/config/SecurityConfig.java)
- **2 thay đổi chính:**
  1. Trong `corsConfigurationSource()` (`setAllowedHeaders`): **Thêm `"X-Tenant-Id"`** và **gỡ `"X-Active-Branch-Id"`**.
  2. Trong `filterChain()` (`authorizeHttpRequests`): Thêm **`"/api/v1/auth/logout"`** vào `.requestMatchers(...).permitAll()` (hỗ trợ Idempotent Logout khi Access Token đã hết hạn).

### 5.2 Chỉnh sửa `JwtAuthenticationFilter.java`
- **File:** [JwtAuthenticationFilter.java](../../../backend/src/main/java/com/carrental/car_rental_backend/security/jwt/JwtAuthenticationFilter.java)
- **Thay đổi Dependency:**
  - **GỠ `UserRepository`** (bỏ hẳn trường `private final UserRepository userRepository;` — tiết kiệm 1 câu query DB trên mỗi request!).
  - **THÊM `SessionManager`**: `private final SessionManager sessionManager;`
- **Cập nhật Whitelist `PUBLIC_ENDPOINTS`:**
  - Bổ sung `"/api/v1/auth/logout"` để Access Token hết hạn không bị Filter chặn `401`.
- **Cập nhật thuật toán trong `doFilterInternal(...)` theo đúng thứ tự:**
  1. Sau khi parse `Claims` và kiểm tra `userIdStr` + `tokenType == ACCESS`:
     - Parse `userUUID = parseUuidOrUnauthorized(userIdStr)` và `sessionUUID = parseUuidOrUnauthorized(currentSessionIdStr)`.
     - *(Viết private helper `parseUuidOrUnauthorized(String raw)` bọc `UUID.fromString(raw)` trong `try-catch (IllegalArgumentException)` → ném **`AppException(ErrorCode.UNAUTHORIZED, ...)`**, tuyệt đối không ném `400` hay `500`!).*
  2. **Kiểm tra phiên & Throttle 5 phút:**
     - Gọi `if (!sessionManager.validateAndTouchSession(sessionUUID, userUUID))` $\rightarrow$ ném `AppException(ErrorCode.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn hoặc bị thu hồi từ thiết bị khác. Vui lòng đăng nhập lại !")`.
  3. **Xác định `isUserLevelEndpoint`:**
     - Kiểm tra `servletPath` khớp `"/api/v1/auth/select-tenant"` **HOẶC** `pathMatcher.match("/api/v1/auth/sessions/**", servletPath)` **HOẶC** `"/api/v1/auth/sessions".equals(servletPath)`.
     - *Ý nghĩa:* Khi Tenant bị khóa (`is_active = false`), user vẫn gọi được `/select-tenant` (chuyển sang nhà xe khác) và `/sessions/**` (xem & đá thiết bị lạ) mà không bị kẹt `403`!
  4. **Kiểm tra chống lệch Tab `X-Tenant-Id` (Edge Case 1):**
     - Nếu `!isUserLevelEndpoint && !RoleConstant.SUPER_ADMIN.equals(role) && StringUtils.hasText(tenantIdStr)`:
       - Đọc `String headerTenantId = request.getHeader("X-Tenant-Id")`.
       - Nếu `StringUtils.hasText(headerTenantId)`:
         - Bọc `UUID.fromString(headerTenantId)` trong `try-catch (IllegalArgumentException)`: nếu lỗi format hoặc `!headerTenantUUID.equals(tenantUUID)` $\rightarrow$ ném **`AppException(ErrorCode.TENANT_CONTEXT_MISMATCH)`** (HTTP `409 Conflict`).
  5. **Nạp `TenantContext` & `UserPrincipal`:**
     - Giữ nguyên logic nạp `TenantContext` (khi `isUserLevelEndpoint == true` thì không set `tenantId` và đi vào nhánh `loadUserById(userUUID)`).
     - Giữ nguyên khối `catch (Exception)` gọi `handlerExceptionResolver.resolveException(...)` + clear context + `return;` và khối `finally` gọi `TenantContext.clear()`.

- **🔍 Từ khóa Google Chặng 5:**
  - `"Spring Security AntPathMatcher match wildcard patterns"`
  - `"Spring HandlerExceptionResolver inside OncePerRequestFilter"`

---

## ⚙️ CHẶNG 6: NGHIỆP VỤ AUTH, QUẢN LÝ THIẾT BỊ & DỌN DẸP TỰ ĐỘNG

### 6.1 Tạo DTO `UserSessionResponseDTO.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/dto/response/UserSessionResponseDTO.java`
- **Annotations:** `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`
- **Các trường:** `id` (`UUID`), `deviceName` (`String`), `ipAddress` (`String`), `lastActiveAt` (`Instant`), `createdAt` (`Instant`), `expiresAt` (`Instant`), và **`isCurrent` (`Boolean` — Wrapper, hoặc `boolean` có `@JsonProperty("isCurrent")`)**.
- 🔬 **Giải ảo Jackson:** JavaBeans Spec quy định primitive `boolean` dùng tiền tố `is` (`isCurrent()`), nên Jackson tự bóc `"is"` ra thành key `"current"`. Dùng `Boolean` (object wrapper) buộc Lombok tạo `getIsCurrent()` → Jackson giữ nguyên `"isCurrent"`.

### 6.2 Refactor `AuthService.java` & `AuthServiceImpl.java`
- **File:** [AuthService.java](../../../backend/src/main/java/com/carrental/car_rental_backend/auth/service/AuthService.java) & [AuthServiceImpl.java](../../../backend/src/main/java/com/carrental/car_rental_backend/auth/service/impl/AuthServiceImpl.java)
- **Cập nhật chữ ký trong `AuthService`:**
  - `AuthServiceResult login(LoginRequestDTO request, String deviceName, String ipAddress);`
  - `void logout(String authorizationHeader, String refreshTokenCookie);`
- **Cập nhật quan trọng trong `AuthServiceImpl`:**
  1. **Sửa Import & Bỏ `@Transactional` ở cấp Class:**
     - Đổi `import jakarta.transaction.Transactional;` $\rightarrow$ **`import org.springframework.transaction.annotation.Transactional;`**.
     - **Xóa `@Transactional` ở đầu class `AuthServiceImpl`**, chuyển sang đặt tường minh trên từng method ghi (`selectTenant`, `switchBranch`, `refreshToken`, `logout`).
  2. **Inject thêm dependencies:**
     - `private final SessionManager sessionManager;`
     - `private final TransactionTemplate transactionTemplate;` *(Spring Boot tự tạo Bean `TransactionTemplate` sẵn — chỉ cần khai báo `private final`, Lombok inject tự động!)*
  3. **Viết lại `login(request, deviceName, ipAddress)` — KHÔNG gắn `@Transactional` lên đầu hàm:**
     - **Ngoài Transaction (0 DB Connection bị giữ khi BCrypt chạy):**
       - Tìm `User` qua `userRepository.findByEmail(request.getEmail())`.
       - Kiểm tra rỗng hoặc `!passwordEncoder.matches(...)` $\rightarrow$ ném `401 UNAUTHORIZED`.
       - Kiểm tra nhanh `!Boolean.TRUE.equals(user.getIsActive())` $\rightarrow$ ném `401 UNAUTHORIZED`.
     - **Trong Transaction ngắn 2–5ms (`transactionTemplate.execute(status -> { ... })`):**
       - Khóa dòng `User`: `User lockedUser = userRepository.findByIdForUpdate(userId).orElseThrow(...)`.
       - Kiểm tra lại `!Boolean.TRUE.equals(lockedUser.getIsActive())` $\rightarrow$ ném `401 UNAUTHORIZED` *(bảo vệ R04 chống Admin khóa đúng tích tắc login!)*.
       - Cập nhật `lockedUser.setLastLoginAt(Instant.now())`.
       - Tạo phiên LRU: `UUID sessionId = sessionManager.createSessionWithLru(lockedUser.getId(), deviceName, ipAddress)`.
       - Thực hiện 3 nhánh build token & response như cũ (Super Admin / 1 Active Tenant / >1 Active Tenant), truyền **`sessionId`** mới sinh vào `generateAccessToken` & `generateRefreshToken`.
  4. **Cập nhật `refreshToken(inputRefreshToken)` — gắn `@Transactional`:**
     - Kiểm tra `inputRefreshToken` không rỗng $\rightarrow$ `validateToken` $\rightarrow$ check `token_type == REFRESH`.
     - Trích `userUUID` và `sessionUUID` (parse UUID trong `try-catch`, sai/thiếu $\rightarrow$ `401 UNAUTHORIZED`).
     - Kiểm tra `User` tồn tại và `isActive == true` (`401`).
     - **Gọi `sessionManager.touchAndExtendSession(sessionUUID, userUUID)` TRƯỚC khi kiểm tra Tenant/Branch** *(đảm bảo phiên bị thu hồi luôn trả `401` trước `403`, đồng thời gia hạn trượt cả `last_active_at` và `expires_at`)*.
     - **⚠️ RÀ SOÁT TỬ HUYỆT (8 chỗ cũ!):** Tìm và thay **toàn bộ 8 lời gọi** `userInfo.getCurrentSessionId()` ở 4 nhánh trong `refreshToken()` thành **`sessionUUID`**!
  5. **Viết lại `logout(authorizationHeader, refreshTokenCookie)` (Idempotent Logout — không cần `@Transactional` ở ngoài vì chỉ gọi `sessionManager.revokeSession`):**
     - Bước 1: Gọi private helper `extractSessionFromHeaderOrCookie(authorizationHeader, refreshTokenCookie)`:
       - Nếu `authorizationHeader` bắt đầu bằng `"Bearer "`: cắt token, trong `try-catch` gọi `jwtProvider.validateToken` + `parseClaims`, check `token_type == ACCESS`, trích `(userUUID, sessionUUID)`.
       - Nếu bước trên không lấy được (Access Token thiếu hoặc đã hết hạn): kiểm tra `refreshTokenCookie`, trong `try-catch` gọi `jwtProvider.validateToken` + `parseClaims`, check `token_type == REFRESH`, trích `(userUUID, sessionUUID)`.
     - Bước 2: Nếu lấy được cặp `(userUUID, sessionUUID)` hợp lệ $\rightarrow$ gọi `sessionManager.revokeSession(userUUID, sessionUUID)`.
     - Bước 3: Dọn `TenantContext.clear()` và `SecurityContextHolder.clearContext()` (không bao giờ ném lỗi nếu token đã hết hạn!).
  6. **Cập nhật `CustomUserDetailsServiceImpl.java`:**
     - Đổi `import jakarta.transaction.Transactional;` $\rightarrow$ `import org.springframework.transaction.annotation.Transactional;` và chuyển sang **`@Transactional(readOnly = true)`**.

### 6.3 Refactor `AuthController.java` & Tạo `SessionController.java`
- **Chỉnh sửa** [AuthController.java](../../../backend/src/main/java/com/carrental/car_rental_backend/auth/controller/AuthController.java):
  - Inject thêm `JwtProvider` (hoặc `@Value("${jwt.refresh-token-expiration-ms}")`), `@Value("${app.cookie.secure:false}") private boolean cookieSecure;` và `ClientInfoExtractor`.
  - Tạo **1 private helper** `buildRefreshTokenCookie(String tokenValue, long maxAgeSeconds)` dùng chung cho cả 5 endpoint (`login`, `select-tenant`, `switch-branch`, `refresh-token`, `logout`), dùng `.secure(cookieSecure)` và `maxAge` động (riêng `logout` truyền `""` và `0`).
  - Ở `login(...)`: thêm tham số `HttpServletRequest httpRequest`, trích `deviceName` & `ipAddress` qua `ClientInfoExtractor` rồi truyền vào `authService.login(...)`.
  - Ở `logout(...)`: nhận `@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader` và `@CookieValue(name = "refreshToken", required = false) String refreshTokenCookie`, gọi `authService.logout(...)`, luôn trả `200 OK` kèm cookie `Max-Age = 0`.

- **Tạo mới `SessionController.java`:**
  - **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/controller/SessionController.java`
  - **Annotations:** `@RestController`, `@RequestMapping("/api/v1/auth/sessions")`, `@RequiredArgsConstructor`
  - **Dependency:** `private final SessionManager sessionManager;`
  - **3 Endpoints (mỏng nhẹ — chỉ gọi Service và bọc `ApiResponse.success(...)`):**
    1. `@GetMapping` (`GET /api/v1/auth/sessions`): Lấy `userId = TenantContext.getUserId()`, `currentSessionId = TenantContext.getCurrentSessionId()` $\rightarrow$ gọi `sessionManager.getActiveSessions(userId, currentSessionId)`.
    2. `@DeleteMapping("/{sessionId}")` (`DELETE /api/v1/auth/sessions/{sessionId}`):
       - Chú ý: Logic kiểm tra **chặn self-revoke (`400`)** và **không tồn tại / IDOR (`404`)** nên đặt ở tầng Service (ví dụ viết thêm method `revokeDeviceSession(UUID userId, UUID currentSessionId, UUID targetSessionId)` trong `SessionManager` / `DatabaseSessionManager`) để Controller không chứa `if/else` nghiệp vụ (tuân thủ nghiêm Mục 2.1 của `JAVA_SPRING_CODE_CONVENTIONS.md`!).
    3. `@PostMapping("/revoke-others")` (`POST /api/v1/auth/sessions/revoke-others`): Lấy `userId`, `currentSessionId` $\rightarrow$ gọi `int revokedCount = sessionManager.revokeAllOtherSessions(userId, currentSessionId)` $\rightarrow$ trả về `ApiResponse.success(Map.of("revokedCount", revokedCount), ...)` (hoặc DTO nhỏ).

### 6.4 Tạo Scheduled Job `SessionCleanupTask.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/scheduler/SessionCleanupTask.java`
- **Annotations:** `@Slf4j`, `@Component`, `@RequiredArgsConstructor`
- **Dependency:** `private final SessionManager sessionManager;`
- **Method:** `cleanupSessions()` gắn `@Scheduled(cron = "${app.auth.session-cleanup-cron:0 0 2 * * *}")`:
  - Gọi `int deleted = sessionManager.cleanupExpiredAndOldRevokedSessions();`
  - Ghi log `log.info(...)` số bản ghi đã dọn (bọc `try-catch` ghi `log.error(...)` để nếu DB chập chờn không làm âm thầm chết log scheduler).

- **🔍 Từ khóa Google Chặng 6:**
  - `"Spring TransactionTemplate execute programmatic transaction vs @Transactional"`
  - `"Spring Boot @CookieValue required false ResponseCookie SameSite Strict"`
  - `"Jackson @JsonProperty boolean field starting with is Lombok"`

---

## 🧪 CHẶNG 7: TỰ KIỂM CHỨNG BẰNG UNIT TEST (AAA PATTERN) & THỰC CHIẾN

### 7.1 Viết Unit Test (JUnit 5 + Mockito — KHÔNG cần bật DB thật!)
Tạo 2 file test trong `backend/src/test/java/com/carrental/car_rental_backend/auth/service/impl/`:
1. **`DatabaseSessionManagerTest.java`** (`@ExtendWith(MockitoExtension.class)`):
   - Mock `UserSessionRepository` và `JwtProvider`.
   - Khởi tạo trực tiếp `new DatabaseSessionManager(...)` trong `@BeforeEach` với `maxActiveSessions = 5`, `throttleMinutes = 5`, `auditRetentionDays = 7`.
   - Viết đủ các test theo **Arrange → Act → Assert**:
     1. `should_evict_oldest_session_when_active_sessions_reach_limit` (5 phiên sẵn → tạo mới thu hồi đúng phiên cũ nhất).
     2. `should_evict_multiple_excess_sessions_when_active_sessions_exceed_limit` (7 phiên sẵn → thu hồi 3 phiên cũ nhất).
     3. `should_not_update_last_active_when_within_throttle_window` (`verify(repo, never()).updateLastActiveIfStale(...)`).
     4. `should_update_last_active_when_throttle_window_exceeded` (`verify(repo, times(1)).updateLastActiveIfStale(...)`).
     5. `should_return_false_when_session_is_revoked_or_expired_or_not_found`.
     6. `should_extend_expires_at_when_touch_and_extend_session_succeeds` (Sliding Expiration).
     7. `should_throw_bad_request_when_revoking_current_session` (Self-revoke `400`).
     8. `should_throw_not_found_when_revoking_other_users_session` (IDOR `404`).
2. **`AuthServiceImplTest.java`** (`@ExtendWith(MockitoExtension.class)`):
   - Test `refreshToken` đảm bảo cấp token mới với `sessionId` từ Refresh Token đầu vào (không phải `user.currentSessionId`) và gọi `touchAndExtendSession`.
   - Test `logout` với **Access Token hết hạn + Refresh Token Cookie hợp lệ** vẫn thu hồi đúng phiên mà không ném ngoại lệ!

- **Chạy test xác nhận xanh 100%:** Chạy lệnh `.\mvnw test` trong thư mục `backend/`.

- **🔍 Từ khóa Google Chặng 7:**
  - `"JUnit 5 Mockito @ExtendWith MockitoExtension ArgumentCaptor verify never"`
  - `"Mockito mock TransactionTemplate execute callback"`
  - `"JUnit 5 assertThrows AppException ErrorCode"`

### 7.2 Kiểm thử Thực chiến (7 Kịch bản trên Swagger / 2 Trình duyệt / Điện thoại LAN)
Chạy `.\mvnw spring-boot:run` và tự nghiệm thu lần lượt **7 kịch bản thực tế** tại [Spec Mục 8.2](./2026-11-23-multi-session-device-management-spec.md#82-kiểm-thử-tích-hợp--thực-tế-manual--swagger--multi-browser-checklist).

---

## ⚠️ BẢNG MẸO NÉ BẪY KINH ĐIỂN (PITFALLS CHEATSHEET)

| # | Bẫy dễ vấp khi code | Cách né triệt để |
| :-: | :--- | :--- |
| **1** | Quên sót 1 trong **8 chỗ** `userInfo.getCurrentSessionId()` trong `AuthServiceImpl.refreshToken()` | Sau khi sửa xong, bấm `Ctrl+Shift+F` tìm `getCurrentSessionId` trong toàn bộ `AuthServiceImpl.java` — kết quả phải là **0 chỗ** (chỉ còn `TenantContext.getCurrentSessionId()` ở `selectTenant` & `switchBranch`)! |
| **2** | Import nhầm `jakarta.transaction.Transactional` | Kiểm tra đầu file `AuthServiceImpl`, `CustomUserDetailsServiceImpl`, `DatabaseSessionManager`, `UserSessionRepository` — tất cả bắt buộc là `org.springframework.transaction.annotation.Transactional`. |
| **3** | JSON trả ra `"current": true` thay vì `"isCurrent": true` | Dùng kiểu Wrapper `Boolean isCurrent` (hoặc `@JsonProperty("isCurrent")`) trên `UserSessionResponseDTO` và nhìn tận mắt response JSON trên Swagger! |
| **4** | Quên đặt `UserSession.isRevoked` có `@Builder.Default` | Bắt buộc có `@Builder.Default private Boolean isRevoked = false;` để tránh lỗi `null value in column "is_revoked" violates not-null constraint`. |
| **5** | Đặt `@Transactional` bao trùm cả `passwordEncoder.matches()` | Dùng `TransactionTemplate` mở transaction **sau khi** verify BCrypt xong để không giam cầm HikariCP connection. |
| **6** | Test bằng điện thoại qua IP LAN (`http://192.168.x.x`) mà Cookie không gửi | Kiểm tra `application.yaml` đã để `app.cookie.secure: ${COOKIE_SECURE:false}` và `AuthController` đọc từ biến đó thay vì hardcode `.secure(true)`. |

---

## 🎯 BƯỚC TIẾP THEO (BƯỚC 4: TỰ CODE & SOCRATIC REVIEW)
Bạn hãy mở IDE và bắt đầu tự gõ từ **Chặng 1 → Chặng 7**:
- Nếu vướng lỗi biên dịch, lỗi Hibernate/Spring AOP hay thắc mắc bản chất dòng nào, cứ paste lỗi lên — tôi sẽ giải thích nguyên nhân gốc (kèm ví dụ đồ chơi và từ khóa Google) để bạn tự sửa!
- Khi hoàn thành từng chặng (hoặc xong toàn bộ), hãy báo tôi review Socratic trước khi bạn tạo commit nhé!
