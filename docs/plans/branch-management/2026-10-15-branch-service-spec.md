# Design Spec: Quản Lý Chi Nhánh Nhà Xe (Branch Service Specification)

**Ngày tạo:** 2026-10-15  
**Trạng thái:** DRAFT / APPROVED  
**Tác giả:** Dev Team & AI Assistant  
**Module:** `branch-management`  

---

## 1. Tổng Quan (Overview)

Trong kiến trúc **SaaS Multi-Tenant & Multi-Branch** của nền tảng Car Rental:
- **Tenant (Nhà xe):** Đại diện cho một doanh nghiệp/công ty cho thuê xe độc lập (được phân cách dữ liệu triệt để qua `tenant_id`).
- **Branch (Chi nhánh):** Là các điểm kinh doanh thực tế trực thuộc một Nhà xe (ví dụ: *Chi nhánh Cầu Giấy*, *Chi nhánh Sân bay Nội Bài*, *Kho trung tâm*). Mỗi chi nhánh quản lý đội xe (`vehicles`), nhận/trả xe cho các hợp đồng thuê (`bookings`), và có các nhân viên (`STAFF`, `SALE`) được phân công làm việc.

Module **`branch-management`** chịu trách nhiệm cung cấp toàn bộ các nghiệp vụ quản lý chi nhánh cho chủ doanh nghiệp (`TENANT_ADMIN`) và cung cấp danh mục chi nhánh hoạt động cho các nhân viên tác nghiệp khi tạo hợp đồng thuê xe.

---

## 2. Yêu Cầu Nghiệp Vụ (Requirements)

### 2.1 Yêu Cầu Chức Năng (Functional Requirements)

1. **Thêm mới chi nhánh (Create Branch):**
   - Chỉ `TENANT_ADMIN` có quyền tạo chi nhánh mới cho nhà xe của mình.
   - Chi nhánh khi tạo mới luôn tự động gắn với `tenantId` lấy từ phiên đăng nhập hiện tại (`TenantContext.getTenantId()`).
   - Trạng thái mặc định khi tạo mới là `isActive = true`.

2. **Xem chi tiết chi nhánh (Get Branch Detail):**
   - Cho phép `TENANT_ADMIN` và nhân viên của nhà xe xem toàn bộ thông tin chi tiết của một chi nhánh theo `id`.
   - Bắt buộc kiểm tra chi nhánh phải thuộc cùng `tenantId` với người dùng đang truy vấn (ngăn chặn IDOR).

3. **Danh sách & Tìm kiếm phân trang (List & Search Branches with Pagination):**
   - Cung cấp giao diện bảng quản trị cho `TENANT_ADMIN`:
     - Phân trang: `page` (mặc định 0), `size` (mặc định 10), hỗ trợ sắp xếp theo thời gian tạo mới nhất.
     - Tìm kiếm linh hoạt (`keyword`): Tìm kiếm không phân biệt hoa thường theo `name`, `address`, hoặc `phone`.
     - Lọc trạng thái (`isActive`): Lọc tất cả, chỉ chi nhánh đang hoạt động, hoặc chi nhánh tạm ngưng.

4. **Danh sách chi nhánh gọn cho Dropdown (Active Branches Summary):**
   - Cung cấp danh sách rút gọn (gồm `id`, `name`, `address`, `phone`) của tất cả chi nhánh đang hoạt động (`isActive = true`) thuộc Tenant.
   - Phục vụ cho các màn hình nghiệp vụ: Chọn chi nhánh khi tạo xe mới, chọn chi nhánh giao/nhận xe khi tạo đơn booking, chọn chi nhánh làm việc (`switch-branch`).
   - Mọi nhân viên hợp lệ (`TENANT_ADMIN`, `STAFF`, `SALE`) của Tenant đều được phép gọi.

5. **Cập nhật thông tin chi nhánh (Update Branch):**
   - Cho phép `TENANT_ADMIN` chỉnh sửa các thông tin: tên, số điện thoại, email, địa chỉ chi tiết, tọa độ (`latitude`, `longitude`).
   - Chỉ cập nhật các trường có gửi giá trị lên (hỗ trợ partial update).

6. **Chuyển đổi trạng thái hoạt động (Soft Delete / Toggle Active Status):**
   - Hệ thống **không hỗ trợ xóa cứng (Hard Delete)** bản ghi chi nhánh khỏi Database để đảm bảo tính toàn vẹn dữ liệu và lịch sử lăn bánh của xe cũng như hợp đồng thuê xe.
   - Cung cấp thao tác chuyển đổi trạng thái `isActive = true / false`:
     - Tạm ngưng hoạt động (`isActive = false`): Chi nhánh sẽ bị ẩn khỏi danh sách chọn khi tạo đơn booking mới, nhân viên không thể chọn làm việc tại chi nhánh này trong luồng `switch-branch`.
     - Kích hoạt lại (`isActive = true`): Chi nhánh mở cửa hoạt động bình thường trở lại.

---

### 2.2 Ràng Buộc Dữ Liệu & Quy Tắc Xác Thực (Validation Rules)

1. **Định danh Nhà xe (`tenant_id`):**
   - Tuyệt đối không nhận `tenantId` từ Request Body của Client. Luôn lấy an toàn từ `TenantContext.getTenantId()`.
2. **Tên chi nhánh (`name`):**
   - Bắt buộc (`@NotBlank`), độ dài tối đa 255 ký tự.
   - **Tính duy nhất (Uniqueness):** Trong cùng một Nhà xe (`tenant_id`), không được phép có 2 chi nhánh trùng tên nhau (không phân biệt chữ hoa/chữ thường sau khi đã trim khoảng trắng).
   - Khi cập nhật: Nếu đổi tên, phải đảm bảo tên mới không trùng với chi nhánh khác trong cùng Tenant (`tenant_id = :tenantId AND name = :name AND id != :id`).
3. **Số điện thoại (`phone`):**
   - Tùy chọn, nhưng nếu có gửi lên thì phải đúng định dạng số điện thoại Việt Nam (10 chữ số, bắt đầu bằng 03, 05, 07, 08, 09. Regex: `^0[35789]\d{8}$`).
4. **Email liên hệ (`email`):**
   - Tùy chọn, nếu có gửi lên thì phải đúng định dạng email RFC (`@Email`).
5. **Địa chỉ (`address`):**
   - Tùy chọn, độ dài tối đa 500 ký tự.
6. **Tọa độ vị trí (`latitude`, `longitude`):**
   - Tùy chọn kiểu số thập phân (`BigDecimal`), phục vụ tính năng mở rộng bản đồ và tính khoảng cách sau này.

---

## 3. Thiết Kế Kỹ Thuật (Technical Design)

### 3.1 Khế Ước Giao Tiếp RESTful API (API Contracts)

| Method | Endpoint | Quyền hạn | Mô tả chức năng |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/branches` | `TENANT_ADMIN` | Thêm mới một chi nhánh |
| `GET` | `/api/v1/branches` | `TENANT_ADMIN` | Danh sách chi nhánh quản trị (phân trang, tìm kiếm) |
| `GET` | `/api/v1/branches/active` | `TENANT_ADMIN`, `STAFF`, `SALE` | Danh sách rút gọn các chi nhánh đang hoạt động |
| `GET` | `/api/v1/branches/{id}` | `TENANT_ADMIN`, `STAFF` | Xem chi tiết một chi nhánh theo UUID |
| `PUT` | `/api/v1/branches/{id}` | `TENANT_ADMIN` | Cập nhật thông tin chi nhánh |
| `PATCH` | `/api/v1/branches/{id}/status` | `TENANT_ADMIN` | Bật / Tắt trạng thái hoạt động (Soft delete) |

---

### 3.2 Chi Tiết Các API

#### API 1: Tạo mới chi nhánh
* **Method / URL:** `POST /api/v1/branches`
* **Authorization:** `Bearer <token>` (Role: `TENANT_ADMIN`)
* **Request Body (`BranchDTORequestToCreate`):**
  ```json
  {
    "name": "Chi nhánh Cầu Giấy",
    "phone": "0335735689",
    "email": "caugiay@rentcar.vn",
    "address": "Số 123 Đường Cầu Giấy, Phường Quan Hoa, Quận Cầu Giấy, Hà Nội",
    "latitude": 21.033333,
    "longitude": 105.799999
  }
  ```
* **Response:** HTTP 200 OK
  ```json
  {
    "success": true,
    "data": {
      "id": "1f53b3d4-8d8d-4d11-884f-d0ca6b03feca",
      "name": "Chi nhánh Cầu Giấy",
      "phone": "0335735689",
      "email": "caugiay@rentcar.vn",
      "address": "Số 123 Đường Cầu Giấy, Phường Quan Hoa, Quận Cầu Giấy, Hà Nội",
      "latitude": 21.033333,
      "longitude": 105.799999,
      "isActive": true,
      "createdAt": "2026-10-15T10:30:00Z",
      "updatedAt": "2026-10-15T10:30:00Z"
    },
    "message": "Tạo chi nhánh thành công",
    "timestamp": "2026-10-15T10:30:00.123Z"
  }
  ```

---

#### API 2: Lấy danh sách chi nhánh (Phân trang & Tìm kiếm)
* **Method / URL:** `GET /api/v1/branches?keyword=Cau+Giay&isActive=true&page=0&size=10`
* **Authorization:** `Bearer <token>` (Role: `TENANT_ADMIN`)
* **Query Parameters:**
  - `keyword` *(String, Optional)*: Từ khóa tìm kiếm theo tên, địa chỉ hoặc số điện thoại.
  - `isActive` *(Boolean, Optional)*: `true` (đang chạy), `false` (tạm ngưng), bỏ trống (lấy tất cả).
  - `page` *(Integer, default 0)*: Số trang (0-indexed).
  - `size` *(Integer, default 10)*: Số phần tử trên mỗi trang.
* **Response:** HTTP 200 OK
  ```json
  {
    "success": true,
    "data": {
      "content": [
        {
          "id": "1f53b3d4-8d8d-4d11-884f-d0ca6b03feca",
          "name": "Chi nhánh Cầu Giấy",
          "phone": "0335735689",
          "email": "caugiay@rentcar.vn",
          "address": "Số 123 Đường Cầu Giấy, Hà Nội",
          "latitude": 21.033333,
          "longitude": 105.799999,
          "isActive": true,
          "createdAt": "2026-10-15T10:30:00Z",
          "updatedAt": "2026-10-15T10:30:00Z"
        }
      ],
      "page": {
        "size": 10,
        "number": 0,
        "totalElements": 1,
        "totalPages": 1
      }
    },
    "message": "Thành công",
    "timestamp": "2026-10-15T10:31:00.000Z"
  }
  ```

---

#### API 3: Danh sách rút gọn chi nhánh đang hoạt động (Dropdown)
* **Method / URL:** `GET /api/v1/branches/active`
* **Authorization:** `Bearer <token>` (Role: `TENANT_ADMIN`, `STAFF`, `SALE`)
* **Response:** HTTP 200 OK
  ```json
  {
    "success": true,
    "data": [
      {
        "id": "1f53b3d4-8d8d-4d11-884f-d0ca6b03feca",
        "name": "Chi nhánh Cầu Giấy",
        "address": "Số 123 Đường Cầu Giấy, Hà Nội",
        "phone": "0335735689"
      }
    ],
    "message": "Thành công",
    "timestamp": "2026-10-15T10:32:00.000Z"
  }
  ```

---

#### API 4: Xem chi tiết chi nhánh
* **Method / URL:** `GET /api/v1/branches/{id}`
* **Authorization:** `Bearer <token>` (Role: `TENANT_ADMIN`, `STAFF`)
* **Response:** HTTP 200 OK (Chi tiết đối tượng `BranchDTOResponse`).
* **Lỗi tiềm ẩn:**
  - Nếu `id` không tồn tại hoặc không thuộc `tenant_id` của user: Trả về `404 Not Found` (`RESOURCE_NOT_FOUND`, "Chi nhánh không tồn tại").

---

#### API 5: Cập nhật thông tin chi nhánh
* **Method / URL:** `PUT /api/v1/branches/{id}`
* **Authorization:** `Bearer <token>` (Role: `TENANT_ADMIN`)
* **Request Body (`BranchDTORequestToUpdate`):**
  ```json
  {
    "name": "Chi nhánh Cầu Giấy - Cơ sở 1",
    "phone": "0987654321",
    "email": "caugiay.updated@rentcar.vn",
    "address": "Số 123 Đường Cầu Giấy, Hà Nội",
    "latitude": 21.033333,
    "longitude": 105.799999
  }
  ```
* **Xử lý nghiệp vụ:**
  - Nếu tên mới khác tên cũ, kiểm tra `branchRepository.existsByTenantIdAndNameAndIdNot(tenantId, request.getName(), id)`. Nếu trùng $\rightarrow$ Báo lỗi `400 Bad Request` ("Tên chi nhánh đã tồn tại trong nhà xe").
  - MapStruct thực hiện cập nhật các trường không null vào entity và lưu xuống database.

---

#### API 6: Bật / Tắt trạng thái hoạt động (Soft Delete / Toggle Status)
* **Method / URL:** `PATCH /api/v1/branches/{id}/status`
* **Authorization:** `Bearer <token>` (Role: `TENANT_ADMIN`)
* **Request Body:**
  ```json
  {
    "isActive": false
  }
  ```
* **Xử lý nghiệp vụ:**
  - Kiểm tra chi nhánh thuộc đúng `tenantId`.
  - Cập nhật trường `isActive` của entity thành giá trị truyền lên và `updatedAt = Instant.now()`.
  - Lưu vào database và trả về DTO đã cập nhật.

---

## 4. Thiết Kế Cơ Sở Dữ Liệu & Truy Vấn (Database & Query Strategy)

### 4.1 Cấu trúc Entity `Branch.java` (Đã chuẩn hóa V6)
```java
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "branches")
public class Branch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "address")
    private String address;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "is_active")
    private Boolean isActive;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

### 4.2 Chiến Lược Truy Vấn Repository (`BranchRepository.java`)
```java
@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {

    // 1. Kiểm tra tồn tại theo tên khi thêm mới
    boolean existsByTenantIdAndName(UUID tenantId, String name);

    // 2. Kiểm tra tồn tại theo tên khi cập nhật (loại trừ chính chi nhánh đang sửa)
    boolean existsByTenantIdAndNameAndIdNot(UUID tenantId, String name, UUID id);

    // 3. Tìm theo ID kèm xác thực Tenant (Chống IDOR)
    Optional<Branch> findByIdAndTenantId(UUID id, UUID tenantId);

    // 4. Lấy tất cả chi nhánh đang active của Tenant (cho dropdown)
    List<Branch> findByTenantIdAndIsActiveTrueOrderByNameAsc(UUID tenantId);

    // 5. Tìm kiếm phân trang có điều kiện (keyword & status)
    @Query("""
        SELECT b FROM Branch b 
        WHERE b.tenantId = :tenantId
          AND (:isActive IS NULL OR b.isActive = :isActive)
          AND (:keyword IS NULL OR LOWER(b.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                OR LOWER(b.address) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                OR b.phone LIKE CONCAT('%', :keyword, '%'))
        ORDER BY b.createdAt DESC
    """)
    Page<Branch> searchBranches(
        @Param("tenantId") UUID tenantId,
        @Param("keyword") String keyword,
        @Param("isActive") Boolean isActive,
        Pageable pageable
    );
}
```

---

## 5. Kế Hoạch Kiểm Thử (Testing Strategy)

### 5.1 Các Trường Hợp Kiểm Thử Trọng Yếu (Critical Test Cases)
1. **Happy Paths:**
   - Tạo chi nhánh với đầy đủ thông tin hợp lệ $\rightarrow$ Trả về 200, `tenantId` chuẩn xác, `isActive = true`.
   - Tạo chi nhánh chỉ có tên bắt buộc (các trường khác null) $\rightarrow$ Thành công.
   - Tìm kiếm theo từ khóa tên hoặc địa chỉ $\rightarrow$ Trả về đúng kết quả phân trang của Tenant đó.
   - Lấy danh sách dropdown $\rightarrow$ Chỉ trả về các chi nhánh có `isActive = true`.
   - Chuyển `isActive = false` $\rightarrow$ Chi nhánh không còn xuất hiện trong dropdown.
2. **Security & Multi-Tenant (Bảo mật cách ly):**
   - Tenant A cố tình gọi API xem chi tiết hoặc sửa chi nhánh của Tenant B $\rightarrow$ Ném `404 Not Found` (ngăn chặn rò rỉ IDOR).
   - Gọi API tạo/sửa chi nhánh với tài khoản `STAFF` $\rightarrow$ Bị chặn `403 Forbidden`.
3. **Data Validations & Boundary:**
   - Tạo chi nhánh với tên trùng lặp trong cùng Tenant $\rightarrow$ Ném lỗi `400 Bad Request` ("Tên chi nhánh đã tồn tại").
   - Tạo 2 chi nhánh cùng tên nhưng ở **2 Tenant khác nhau** $\rightarrow$ Cả hai đều tạo thành công (độc lập giữa các tenant).
   - Truyền SĐT sai định dạng (ví dụ `123456`) $\rightarrow$ Ném lỗi validation `400`.

