# 📘 CẨM NANG HƯỚNG DẪN TỰ CODE TỪNG BƯỚC: MODULE XÁC THỰC & PHÂN QUYỀN (AUTH / JWT / DYNAMIC RBAC / MULTI-TENANT)

> **Dự án:** Car Rental SaaS  
> **Module:** `auth` & `account` (Security / Authentication / Authorization)  
> **Phương pháp tiếp cận:** Giải thích bản chất kiến trúc gần gũi, trực quan (kèm ví dụ thực tế) + Hướng dẫn luồng logic, tên class, thuộc tính và annotation để bạn tự tay viết 100% mã nguồn backend.

---

## 🏛️ BỨC TRANH TOÀN CẢNH (HỆ THỐNG AN NINH TÒA NHÀ)

Để dễ hình dung toàn bộ module này hoạt động thế nào, hãy tưởng tượng hệ thống phần mềm của chúng ta như một **Tòa nhà văn phòng cao ốc đa doanh nghiệp**:

```
[ Client / Mobile Web ]
          │ (Gửi yêu cầu kèm Vé JWT)
          ▼
[ JwtAuthenticationFilter - Chú Bảo Vệ Trực Cổng ] ── (Đưa vé cho Máy soi) ──► [ JwtProvider - Máy Soi Vé ]
          │ (Vé hợp lệ!)                                                              │
          ▼                                                                           ▼
[ CustomUserDetailsService - Phòng Nhân Sự Tra Cứu ] ◄────────────────────────────────┘
          │ (Truy cứu DB: User này thuộc Nhà xe nào? Giữ chức vụ gì? Có những chìa khóa nào?)
          ▼
[ UserPrincipal - Thẻ Ra Vào Gắn Chip Đầy Đủ Quyền ]
          │ (Đeo thẻ vào người request)
          ▼
[ SecurityContextHolder & TenantContext ]
          │ (Đi qua các cửa phòng API có khóa @PreAuthorize)
          ▼
[ Controller & Service - Phòng Nghiệp Vụ ]
```

1. **`JwtAuthenticationFilter` (Chú bảo vệ trực cổng):** Đứng gác ở cửa ra vào, chặn mọi request gửi đến, yêu cầu xuất trình vé (JWT Token).
2. **`JwtProvider` (Máy in & Máy soi vé):** Chuyên in vé ra vào có mã vạch chống giả và giải mã thông tin trên vé.
3. **`CustomUserDetailsService` (Phòng nhân sự):** Khi bảo vệ cần kiểm tra chi tiết, phòng nhân sự sẽ mở sổ sách (Database) tra cứu xem người này thuộc công ty nào, đang giữ những chìa khóa quyền hạn nào.
4. **`UserPrincipal` (Thẻ ra vào gắn chip tiêu chuẩn):** Thẻ định danh mà Spring Security đọc hiểu được, trên thẻ ghi rõ ID, Email, Vai trò và toàn bộ danh sách Quyền hạn.
5. **`TenantContext` (Ngăn tủ đồ tạm thời):** Lưu thông tin Nhà xe và Chi nhánh mà người dùng đang làm việc trong suốt thời gian xử lý request đó.

---

## 🗺️ MỤC LỤC LỘ TRÌNH 5 GIAI ĐOẠN

- [Giai đoạn 1: Xây dựng các DTO Request & Response (Hợp đồng giao tiếp)](#giai-đoạn-1-xây-dựng-các-dto-nhận--trả-dữ-liệu-authdto)
- [Giai đoạn 2: Xây dựng JPA Entity & Repository (Sổ sách dữ liệu)](#giai-đoạn-2-xây-dựng-entity--repository-account)
- [Giai đoạn 3: Hạ tầng Bảo mật Security & UserPrincipal (Đội ngũ an ninh)](#giai-đoạn-3-hạ-tầng-bảo-mật-security--principal--jwt)
- [Giai đoạn 4: Triển khai Logic Nghiệp vụ Service (Bộ não xử lý)](#giai-đoạn-4-triển-khai-logic-nghiệp-vụ-authservice)
- [Giai đoạn 5: Xây dựng REST API Controller (Quầy lễ tân)](#giai-đoạn-5-xây-dựng-rest-api-controller-authcontroller)
- [Checklist Kiểm thử & Lưu ý sống còn](#checklist-kiểm-thử--lưu-ý-sống-còn)

---

## 📦 GIAI ĐOẠN 1: XÂY DỰNG CÁC DTO NHẬN & TRẢ DỮ LIỆU (`auth/dto/`)

> **DTO (Data Transfer Object) là gì?**  
> DTO giống như **"Tờ phiếu điền thông tin"** và **"Bì thư kết quả"**. Chúng giúp bảo vệ Database (không để lộ các cột nhạy cảm như `password_hash` ra ngoài) và chuẩn hóa dữ liệu đầu vào/đầu ra giữa Frontend và Backend.

---

### 1. File `LoginRequestDTO.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/dto/request/LoginRequestDTO.java`
- **💡 Vai trò thực tế:** Là **"Tờ phiếu đăng nhập"** mà người dùng điền Email và Mật khẩu ở màn hình đăng nhập chung của hệ thống.
- **Annotations cho Class:** `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- **Các thuộc tính:**
  - `email` (`String`): Email đăng nhập. Gắn `@NotBlank(message = "Email không được để trống")` và `@Email(message = "Email không đúng định dạng")`.
  - `password` (`String`): Mật khẩu thô do người dùng nhập. Gắn `@NotBlank(message = "Mật khẩu không được để trống")`.

---

### 2. File `SelectTenantRequestDTO.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/dto/request/SelectTenantRequestDTO.java`
- **💡 Vai trò thực tế:** Là **"Phiếu chọn nơi làm việc"**. Trong hệ thống SaaS, một người có thể làm việc cho nhiều nhà xe (ví dụ: làm Admin ở Nhà xe A, làm Sale ở Nhà xe B). Khi đăng nhập xong, người dùng gửi phiếu này lên để báo cho hệ thống biết họ muốn vào làm việc tại Nhà xe nào và Chi nhánh nào.
- **Annotations cho Class:** `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- **Các thuộc tính:**
  - `tenantId` (`UUID`): ID của nhà xe muốn truy cập. Gắn `@NotNull(message = "Mã nhà xe không được để trống")`.
  - `activeBranchId` (`UUID`): ID chi nhánh trực tiếp làm việc (không bắt buộc, dành cho nhân viên bãi xe chọn ca trực).

---

### 3. File `RefreshTokenRequestDTO.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/dto/request/RefreshTokenRequestDTO.java`
- **💡 Vai trò thực tế:** Là **"Phiếu xin gia hạn vé"**. Access Token chỉ có hạn 15 phút (để bảo mật). Khi Access Token hết hạn, Frontend sẽ dùng Refresh Token gửi phiếu này lên để xin cấp lại Access Token mới mà không bắt người dùng phải gõ lại mật khẩu.
- **Annotations cho Class:** `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- **Các thuộc tính:**
  - `refreshToken` (`String`): Chuỗi token dài hạn. Gắn `@NotBlank(message = "Refresh token không được để trống")`.

---

### 4. File `AuthResponseDTO.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/dto/response/AuthResponseDTO.java`
- **💡 Vai trò thực tế:** Là **"Bì thư kết quả phản hồi"** sau khi người dùng đăng nhập hoặc chọn nhà xe thành công. Nó chứa vé vào cổng (`accessToken`, `refreshToken`), thông tin người dùng và danh sách các nhà xe mà người này có quyền truy cập.
- **Annotations cho Class:** `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- **Các thuộc tính:**
  - `accessToken` (`String`): Chuỗi JWT mang quyền truy cập (chỉ có khi đã chọn xong Tenant).
  - `refreshToken` (`String`): Chuỗi JWT làm mới phiên dài hạn (7 ngày).
  - `tokenType` (`String`): Gán mặc định là `"Bearer"`.
  - `expiresIn` (`long`): Thời gian sống của Access Token tính bằng giây (ví dụ: `900` giây = 15 phút).
  - `user` (`UserInfoResponseDTO`): Hồ sơ chi tiết của người dùng.
  - `availableTenants` (`List<TenantSummaryDTO>`): Danh sách các nhà xe mà tài khoản này tham gia (để hiển thị Tenant Picker nếu thuộc $\ge 2$ nhà xe).

---

### 5. File `UserInfoResponseDTO.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/dto/response/UserInfoResponseDTO.java`
- **💡 Vai trò thực tế:** Là **"Bản sơ yếu lý lịch thu nhỏ"** trả về cho Frontend, giúp giao diện biết người dùng tên gì, thuộc nhà xe nào, giữ vai trò gì (`role`), có những quyền gì (`permissions`) để ẩn/hiện các nút bấm trên màn hình cho phù hợp.
- **Annotations cho Class:** `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- **Các thuộc tính:**
  - `id` (`UUID`): ID định danh người dùng.
  - `email` (`String`)
  - `fullName` (`String`)
  - `phone` (`String`)
  - `tenantId` (`UUID`): ID nhà xe đang làm việc.
  - `tenantName` (`String`): Tên nhà xe.
  - `role` (`String`): Mã vai trò (VD: `TENANT_ADMIN`, `STAFF`, `SALE`).
  - `permissions` (`List<String>`): Mảng chứa các chuỗi mã quyền (VD: `booking:create`, `vehicle:read`...).
  - `activeBranchId` (`UUID`): Chi nhánh đang làm việc hiện tại.
  - `assignedBranches` (`List<BranchSummaryDTO>`): Danh sách các chi nhánh được phân công.

---

### 6. File `TenantSummaryDTO.java` & `BranchSummaryDTO.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/dto/response/`
- **💡 Vai trò thực tế:** Là các **"Thẻ tóm tắt thông tin"** gọn nhẹ để hiển thị lên các dropdown hoặc danh sách chọn Nhà xe / Chi nhánh trên giao diện di động.
- **Trong `TenantSummaryDTO.java`:** `tenantId` (`UUID`), `tenantName` (`String`), `domain` (`String`), `role` (`String`), `isActive` (`Boolean`).
- **Trong `BranchSummaryDTO.java`:** `branchId` (`UUID`), `branchName` (`String`), `branchCode` (`String`), `address` (`String`).

---

## 🗄️ GIAI ĐOẠN 2: XÂY DỰNG ENTITY & REPOSITORY (`account/`)

> **JPA Entity & Repository là gì?**  
> - **Entity:** Là các Java Class ánh xạ 1-1 với các bảng trong Database PostgreSQL. Mỗi đối tượng Entity đại diện cho 1 dòng dữ liệu (Row).  
> - **Repository:** Là "Thủ kho dữ liệu" — cung cấp các hàm tìm kiếm, thêm, sửa, xóa dữ liệu từ Database mà không cần phải viết câu lệnh SQL thủ công.

---

### 1. Entity `User.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/account/entity/User.java`
- **Bảng tương ứng:** `users`
- **💡 Vai trò thực tế:** Đại diện cho **Tài khoản định danh người dùng duy nhất** trong toàn bộ hệ sinh thái SaaS. Dù một người làm việc cho 10 nhà xe khác nhau, họ cũng chỉ có **1 dòng duy nhất** trong bảng này (chung 1 email, 1 mật khẩu mã hóa).
- **Annotations:** `@Entity`, `@Table(name = "users")`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- **Mapping các cột:**
  - `id` (`UUID`): `@Id`, `@GeneratedValue(strategy = GenerationType.UUID)`, `@Column(name = "id", nullable = false, updatable = false)`
  - `email` (`String`): `@Column(name = "email", nullable = false, unique = true)`
  - `passwordHash` (`String`): `@Column(name = "password_hash", nullable = false)`
  - `fullName` (`String`): `@Column(name = "full_name")`
  - `phone` (`String`): `@Column(name = "phone")`
  - `avatarUrl` (`String`): `@Column(name = "avatar_url")`
  - `isActive` (`Boolean`): `@Column(name = "is_active")`
  - `isSuperAdmin` (`Boolean`): `@Column(name = "is_super_admin")`
  - `createdAt`, `updatedAt` (`Instant`): Gắn `@CreationTimestamp`, `@UpdateTimestamp`.

---

### 2. Entity `Role.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/account/entity/Role.java`
- **Bảng tương ứng:** `roles`
- **💡 Vai trò thực tế:** Đại diện cho **Nhóm chức vụ / Vai trò** trong từng Nhà xe (VD: Chủ nhà xe `TENANT_ADMIN`, Nhân viên giao xe `STAFF`, Nhân viên kinh doanh `SALE`). Trong hệ thống Dynamic RBAC, mỗi nhà xe có thể tự định nghĩa nhóm vai trò riêng của họ.
- **Annotations:** `@Entity`, `@Table(name = "roles")`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- **Mapping các cột:** `id` (`UUID`), `tenantId` (`UUID`), `name` (`String`), `code` (`String`), `description` (`String`), `isSystemDefault` (`Boolean`).

---

### 3. Entity `Permission.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/account/entity/Permission.java`
- **Bảng tương ứng:** `permissions`
- **💡 Vai trò thực tế:** Đại diện cho **Từng chiếc chìa khóa quyền hạn nguyên tử** trong toàn hệ thống (VD: `booking:create` là quyền tạo đơn, `vehicle:update` là quyền sửa xe, `report:view` là quyền xem doanh thu).
- **Annotations:** `@Entity`, `@Table(name = "permissions")`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- **Mapping các cột:** `id` (`UUID`), `code` (`String`), `name` (`String`), `category` (`String`), `description` (`String`).

---

### 4. Entity `UserTenant.java` & Khóa chính liên hợp `UserTenantId.java`

#### A. File `UserTenantId.java`:
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/account/entity/UserTenantId.java`
- **💡 Vai trò thực tế:** Trong bảng `user_tenants`, khóa chính gồm **2 cột ghép lại**: `(user_id, tenant_id)`. Trong JPA, khi một bảng có khóa chính liên hợp gồm nhiều cột, ta phải tạo một class riêng để đại diện cho bộ khóa này.
- **Khai báo:** `public class UserTenantId implements Serializable` *(Bắt buộc phải `implements Serializable`)*.
- **Annotations:** `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`
- **Thuộc tính:** `userId` (`UUID`), `tenantId` (`UUID`).

#### B. File `UserTenant.java`:
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/account/entity/UserTenant.java`
- **Bảng tương ứng:** `user_tenants`
- **💡 Vai trò thực tế:** Là **"Cuốn sổ liên kết N-N"** ghi nhận: *Người dùng A đang làm việc tại Nhà xe B với Nhóm chức vụ C (`role_id`)*.
- **Annotations:** `@Entity`, `@Table(name = "user_tenants")`, `@IdClass(UserTenantId.class)`, `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`
- **Mapping các cột:** `userId` (`UUID` - `@Id`), `tenantId` (`UUID` - `@Id`), `roleId` (`UUID`), `joinedAt` (`Instant`).

---

### 5. Xây dựng Repository (`account/repository/`)

> ⚠️ **LƯU Ý QUAN TRỌNG:** Tất cả Repository trong Spring Data JPA bắt buộc phải là **`public interface`** kế thừa `JpaRepository<Entity, ID_Type>`, **không phải là `class`**!

#### a. `UserRepository.java`
- **Khai báo:** `public interface UserRepository extends JpaRepository<User, UUID>`
- **Annotation:** `@Repository`
- **Methods:**
  - `Optional<User> findByEmail(String email)`: Dùng khi đăng nhập để tra cứu người dùng theo email.
  - `Optional<User> findByIdAndIsActiveTrue(UUID id)`: Tra cứu người dùng theo ID và đảm bảo tài khoản chưa bị khóa.
  - `boolean existsByEmail(String email)`: Dùng khi tạo tài khoản mới để kiểm tra trùng email.

#### b. `UserTenantRepository.java`
- **Khai báo:** `public interface UserTenantRepository extends JpaRepository<UserTenant, UserTenantId>`
- **Annotation:** `@Repository`
- **Methods:**
  - `Optional<UserTenant> findByUserIdAndTenantId(UUID userId, UUID tenantId)`: Kiểm tra user có thực sự thuộc về nhà xe này hay không.
  - `List<UserTenant> findByUserId(UUID userId)`: Lấy toàn bộ danh sách các nhà xe mà một người dùng tham gia (phục vụ màn hình Tenant Picker).

#### c. `PermissionRepository.java`
- **Khai báo:** `public interface PermissionRepository extends JpaRepository<Permission, UUID>`
- **Annotation:** `@Repository`
- **Method quan trọng (JPQL / Native Query):**
  - **Tên method:** `List<String> findPermissionCodesByRoleId(@Param("roleId") UUID roleId)`
  - **💡 Vai trò thực tế:** Khi biết một người giữ chức vụ gì (`roleId`), hàm này sẽ truy tìm trong bảng `role_permissions` để gom về **tất cả các mã quyền hạn dạng chữ** (ví dụ: `["booking:create", "vehicle:view", "customer:scan"]`).
  - **Logic query:** Join bảng `permissions` và `role_permissions` theo điều kiện `role_id = :roleId` để lấy ra danh sách cột `code`.

#### d. `RoleRepository.java`
- **Khai báo:** `public interface RoleRepository extends JpaRepository<Role, UUID>`
- **Annotation:** `@Repository`
- **Method:** `Optional<Role> findByIdAndTenantId(UUID id, UUID tenantId)`

---

## 🛡️ GIAI ĐOẠN 3: HẠ TẦNG BẢO MẬT (SECURITY / PRINCIPAL / JWT)

---

### 1. Lớp Định danh `UserPrincipal.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/security/principal/UserPrincipal.java`
- **Triển khai:** `public class UserPrincipal implements UserDetails`
- **💡 Vai trò thực tế:** Là **"Tấm thẻ nhân viên gắn chip chuẩn hóa"**. Spring Security không hiểu Entity `User` của bạn là gì, nó chỉ hiểu các đối tượng tuân thủ chuẩn `UserDetails`. `UserPrincipal` bọc lấy thông tin User + Tenant hiện tại + Danh sách quyền hạn (`authorities`) để Spring Security kiểm soát phân quyền trên từng API.
- **Annotations:** `@Getter`, `@AllArgsConstructor`, `@Builder`
- **Thuộc tính:**
  - `id` (`UUID`): ID của user.
  - `tenantId` (`UUID`): ID nhà xe hiện tại.
  - `email` (`String`): Email.
  - `password` (`String`): Mật khẩu băm (password_hash).
  - `roleCode` (`String`): Tên mã role (VD: `TENANT_ADMIN`).
  - `authorities` (`Collection<? extends GrantedAuthority>`): Tập hợp các quyền nguyên tử.
  - `isActive` (`boolean`): Trạng thái hoạt động.
- **Method Static Factory `create(...)`:**
  - **Chữ ký:** `public static UserPrincipal create(User user, UUID tenantId, String roleCode, List<String> permissions)`
  - **Logic xử lý:**
    1. Lặp qua danh sách chuỗi `permissions`, biến mỗi chuỗi `code` thành `new SimpleGrantedAuthority(code)`.
    2. Nếu có `roleCode`, thêm 1 authority mang tiền tố: `new SimpleGrantedAuthority("ROLE_" + roleCode)`.
    3. Dùng `UserPrincipal.builder()` nạp đầy đủ thông tin vào đối tượng và trả về.
- **Override các method bắt buộc của `UserDetails`:**
  - `getAuthorities()` $\rightarrow$ Trả về `this.authorities`.
  - `getPassword()` $\rightarrow$ Trả về `this.password`.
  - `getUsername()` $\rightarrow$ Trả về `this.email`.
  - `isAccountNonExpired()`, `isAccountNonLocked()`, `isCredentialsNonExpired()` $\rightarrow$ Luôn trả về `true`.
  - `isEnabled()` $\rightarrow$ Trả về `this.isActive`.

---

### 2. File `JwtProvider.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/security/jwt/JwtProvider.java`
- **💡 Vai trò thực tế:** Là **"Máy in & Máy soi vé chống giả"**. Nó dùng thuật toán mã hóa (HMAC-SHA256) và khóa bí mật (`jwt.secret`) để sinh ra chuỗi ký tự JWT cấp cho Client, đồng thời giải mã và kiểm tra hạn sử dụng của vé khi Client gửi request lên.
- **Các method cần viết:**
  - `generateAccessToken(UUID userId, String email, String role, UUID tenantId, UUID activeBranchId)`: Tạo Access Token (15 phút) mang theo các thông tin định danh cốt lõi.
  - `generateRefreshToken(UUID userId)`: Tạo Refresh Token (7 ngày) chỉ mang theo `userId`.
  - `parseClaims(String token)`: Giải mã token để lấy thông tin bên trong (Payload).
  - `validateToken(String token)`: Trả về `true` nếu vé hợp lệ, trả về `false` nếu vé bị chỉnh sửa hoặc hết hạn.
  - `getUserIdFromToken(String token)`: Trích xuất `userId` từ token.

---

### 3. File `JwtAuthenticationFilter.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/security/jwt/JwtAuthenticationFilter.java`
- **Kế thừa:** `OncePerRequestFilter`
- **💡 Vai trò thực tế:** Là **"Chú bảo vệ trực tại cửa tòa nhà"**. Mọi request HTTP gửi lên server đều phải đi qua filter này đầu tiên:
  1. Bảo vệ kiểm tra xem request có mang theo vé `Authorization: Bearer <token>` không.
  2. Nếu có vé hợp lệ: Gọi `CustomUserDetailsService` tra cứu danh sách quyền mới nhất từ DB, nạp thông tin người dùng vào `SecurityContextHolder` và nạp `tenantId` vào `TenantContext`.
  3. Cho phép request đi tiếp vào bên trong Controller.
  4. **Khối `finally`:** Khi request xử lý xong và chuẩn bị trả response về, bảo vệ sẽ gọi `TenantContext.clear()` để dọn sạch dữ liệu tạm, tránh rò rỉ thông tin sang người khác.

---

### 4. Xử lý Lỗi Ngoại Lệ Bảo Mật (401 & 403)

- **`JwtAuthenticationEntryPoint.java` (Bắt lỗi 401 - Chưa có vé):**
  - **💡 Vai trò:** Khi người dùng chưa đăng nhập hoặc token đã hết hạn mà cố tình gọi API bảo mật $\rightarrow$ Trả về JSON chuẩn `401 Unauthorized` kèm thông báo *"Bạn cần đăng nhập để thực hiện thao tác này"*.
- **`CustomAccessDeniedHandler.java` (Bắt lỗi 403 - Đi nhầm phòng cấm):**
  - **💡 Vai trò:** Khi nhân viên thường (`STAFF`) cố tình gọi API của Giám đốc (`TENANT_ADMIN`) $\rightarrow$ Trả về JSON chuẩn `403 Forbidden` kèm thông báo *"Bạn không có quyền thực hiện chức năng này"*.

---

### 5. File `SecurityConfig.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/security/config/SecurityConfig.java`
- **💡 Vai trò thực tế:** Là **"Bản nội quy an ninh toàn bộ tòa nhà"**. Quy định:
  - Cửa nào được mở tự do không cần vé (`/api/v1/auth/**`, `/swagger-ui/**`).
  - Cửa nào bắt buộc phải có vé (`anyRequest().authenticated()`).
  - Chỉ định `JwtAuthenticationFilter` đứng gác ở vị trí đầu tiên.

---

## 💼 GIAI ĐOẠN 4: LOGIC NGHIỆP VỤ XÁC THỰC (`auth/service/`)

---

### 1. `CustomUserDetailsService.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/service/CustomUserDetailsService.java`
- **Annotation:** `@Service`, `@RequiredArgsConstructor`
- **💡 Vai trò thực tế:** Là **"Bộ phận nhân sự tra cứu hồ sơ"**. Khi có `userId` và `tenantId`, Service này sẽ:
  1. Tìm `User` trong DB (kiểm tra tài khoản có bị khóa không).
  2. Tìm vai trò `Role` của user tại `Tenant` đó.
  3. Lấy toàn bộ danh sách `permissions` của vai trò đó.
  4. Đóng gói tất cả thành một đối tượng `UserPrincipal` hoàn chỉnh.

---

### 2. `AuthService.java` & `AuthServiceImpl.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/service/impl/AuthServiceImpl.java`
- **Annotation:** `@Service`, `@RequiredArgsConstructor`, `@Transactional`
- **💡 Vai trò thực tế:** Là **"Bộ não xử lý toàn bộ quy trình Đăng nhập & Đổi ca làm việc"**.

#### 🔹 Quy trình xử lý hàm `login(LoginRequestDTO request)`:
1. **Kiểm tra Email & Mật khẩu:** Tìm `User` theo email $\rightarrow$ So khớp mật khẩu bằng `passwordEncoder.matches()` $\rightarrow$ Nếu sai ném lỗi `UNAUTHORIZED`.
2. **Kiểm tra Khóa tài khoản:** Nếu `user.getIsActive() == false` $\rightarrow$ Ném lỗi `FORBIDDEN` ("Tài khoản đã bị khóa").
3. **Tra cứu danh sách Nhà xe:** Gọi `userTenantRepository.findByUserId(user.getId())`.
4. **Phân nhánh xử lý:**
   - **Trường hợp 1 (User chỉ thuộc đúng 1 Nhà xe):** Tự động chọn nhà xe đó, nạp quyền hạn, sinh cặp `accessToken` + `refreshToken` và trả về `AuthResponseDTO` để vào thẳng Dashboard.
   - **Trường hợp 2 (User thuộc $\ge 2$ Nhà xe):** Cấp `refreshToken` tạm thời, trả về danh sách `availableTenants` để Frontend hiển thị màn hình chọn Nhà xe (Tenant Picker).

#### 🔹 Quy trình xử lý hàm `selectTenant(UUID userId, SelectTenantRequestDTO request)`:
1. Xác minh user thực sự có quyền tại `request.getTenantId()`.
2. Sinh `accessToken` chính thức mang đầy đủ claims: `userId`, `email`, `roleCode`, `tenantId`, `activeBranchId`.
3. Lấy danh sách chi nhánh được phân công từ `user_branches`.
4. Đóng gói `UserInfoResponseDTO` và trả về `AuthResponseDTO`.

#### 🔹 Quy trình xử lý hàm `refreshToken(RefreshTokenRequestDTO request)`:
1. Soi kiểm tra chữ ký và hạn của `refreshToken`.
2. Lấy `userId` từ token $\rightarrow$ Kiểm tra tài khoản trong DB.
3. Cấp phát `accessToken` mới và trả về.

#### 🔹 Quy trình xử lý hàm `getMe()`:
1. Lấy `Authentication` từ `SecurityContextHolder`.
2. Ép kiểu sang `UserPrincipal` $\rightarrow$ Map sang `UserInfoResponseDTO` trả về thông tin người dùng đang đăng nhập.

---

## 🌐 GIAI ĐOẠN 5: XÂY DỰNG REST API CONTROLLER (`auth/controller/`)

### File `AuthController.java`
- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/auth/controller/AuthController.java`
- **💡 Vai trò thực tế:** Là **"Quầy lễ tân đón tiếp khách"**. Nhận các HTTP Request gửi đến từ Internet, kiểm tra dữ liệu đầu vào (`@Valid`) và chuyển cho `AuthService` xử lý, sau đó bọc kết quả vào `ApiResponse` trả về cho Client.
- **Các API Endpoints:**
  - `POST /api/v1/auth/login`: Tiếp nhận đăng nhập.
  - `POST /api/v1/auth/select-tenant`: Tiếp nhận lựa chọn nhà xe làm việc.
  - `POST /api/v1/auth/refresh-token`: Tiếp nhận yêu cầu làm mới token.
  - `GET /api/v1/auth/me`: Trả về thông tin của chính người dùng đang đăng nhập.

---

## ⚠️ CHECKLIST LƯU Ý SỐNG CÒN KHI CODE

| Điểm cần nhớ | Lý do kỹ thuật |
| :--- | :--- |
| **Không dùng `@NotBlank` cho `UUID`** | `@NotBlank` chỉ dành cho `String`. Kiểu `UUID` bắt buộc dùng `@NotNull(message = "...")` nếu không sẽ nổ lỗi crash app. |
| **Không dùng `@Data` trên JPA Entity** | Chỉ dùng `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` để tránh vòng lặp đệ quy `hashCode/equals` làm tràn RAM (`StackOverflowError`). |
| **Class `UserTenantId` phải `implements Serializable`** | Đây là chuẩn bắt buộc của JPA cho khóa chính phức hợp gồm nhiều cột. |
| **Luôn dọn dẹp `TenantContext.clear()` trong `finally`** | Tránh rò rỉ dữ liệu giữa các User trong Thread Pool của Tomcat. |
| **Repository bắt buộc là `interface`** | Kế thừa `JpaRepository<Entity, ID>` để Spring Data JPA tự động sinh mã proxy truy vấn DB. |
