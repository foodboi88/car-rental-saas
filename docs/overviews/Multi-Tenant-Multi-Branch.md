# Multi-tenant vs Multi-branch - Giải thích kiến thức nền tảng

## Mục lục
1. [Tổng quan](#1-tổng-quan)
2. [Multi-tenant: Kiến trúc phần mềm](#2-multi-tenant-kiến-trúc-phần-mềm)
3. [Multi-branch: Mô hình nghiệp vụ](#3-multi-branch-mô-hình-nghiệp-vụ)
4. [Sự khác biệt cốt lõi](#4-sự-khác-biệt-cốt-lõi)
5. [Kết hợp Multi-tenant + Multi-branch](#5-kết-hợp-multi-tenant--multi-branch)

---

## 1. Tổng quan

| Khái niệm | Loại | Quyết định bởi | Cấp độ |
|-----------|------|----------------|--------|
| **Multi-tenant** | Kiến trúc phần mềm | SaaS Provider | System-wide |
| **Multi-branch** | Mô hình nghiệp vụ | Tenant (khách hàng) | Company-wide |

---

## 2. Multi-tenant: Kiến trúc phần mềm

### 2.1 Định nghĩa

```
Multi-tenant là cách xây dựng phần mềm để phục vụ nhiều khách hàng trên
1 hệ thống duy nhất.
```

### 2.2 Câu hỏi cần trả lời

| Câu hỏi | Ý nghĩa |
|---------|---------|
| Làm sao 1 phần mềm phục vụ được nhiều công ty? | Shared infrastructure |
| Làm sao data của công ty A không thấy được công ty B? | Tenant isolation |
| Làm sao deploy/update chỉ cần làm 1 lần? | Single codebase |
| Làm sao scale khi có thêm khách hàng mới? | Auto-scaling |

### 2.3 Ví dụ kỹ thuật

```sql
-- Shared database với tenant_id
SELECT * FROM vehicles WHERE tenant_id = 'A'  -- Tenant A thấy xe A
SELECT * FROM vehicles WHERE tenant_id = 'B'  -- Tenant B thấy xe B

-- Cùng 1 database, cùng 1 code, nhưng data TÁCH BIỆT
```

### 2.4 So sánh Single vs Multi-tenant

| Aspect | Single-tenant | Multi-tenant |
|--------|---------------|--------------|
| **Codebase** | 1 cho mỗi khách hàng | 1 cho tất cả khách hàng |
| **Database** | 1 database riêng | Chia sẻ, phân biệt bằng `tenant_id` |
| **Server** | 1 server riêng | Chia sẻ tài nguyên |
| **Deploy** | Nhiều instance | 1 instance |
| **Chi phí** | Cao (mỗi khách 1 server) | Thấp (shared infrastructure) |
| **Scale** | Khó scale | Dễ scale |
| **Bảo trì** | Phức tạp | Đơn giản |

### 2.5 Minh họa

```
┌─────────────────────────────────────────────────────────────┐
│                 MULTI-TENANT ARCHITECTURE                     │
└─────────────────────────────────────────────────────────────┘

1 "INSTANCE" (1 codebase, 1 server, 1 database)
            │
            │ phục vụ
            ▼
┌─────────────────────────────────────────────────────────────┐
│                      TẤT CẢ KHÁCH HÀNG                         │
│                                                              │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐         │
│  │Tenant A │  │Tenant B │  │Tenant C │  │Tenant D │  ...     │
│  │RentCar  │  │Xe Điện  │  │Taxi MT  │  │Cho thuê  │         │
│  │   VN    │  │  Sài Gòn│  │Nha Trang│  │  xe ABC  │         │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘         │
│                                                              │
│  MỖI TENANT LÀ 1 CÔNG TY THUÊ XE ĐỘC LẬP                     │
│  - Data TÁCH BIỆT                                   │
│  - Branding riêng (logo, màu sắc)                             │
│  - Giá riêng                                                  │
│  - Nhân viên riêng                                            │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Multi-branch: Mô hình nghiệp vụ

### 3.1 Định nghĩa

```
Multi-branch là cách tổ chức kinh doanh của mỗi công ty thuê xe,
trong đó họ có thể có nhiều chi nhánh hoạt động độc lập hoặc
phối hợp với nhau.
```

### 3.2 Câu hỏi cần trả lời

| Câu hỏi | Ý nghĩa |
|---------|---------|
| Công ty có mấy chi nhánh? | Branch count |
| Chi nhánh có xe riêng hay chia sẻ? | Fleet ownership |
| Ai quản lý xe ở mỗi chi nhánh? | Branch manager |
| Khi chi nhánh thiếu xe, lấy xe từ đâu? | Central/Transfer |

### 3.3 Ví dụ nghiệp vụ

```
Tenant A: "RentCar VN" quyết định:
──────────────────────────────────────
├── Có 3 chi nhánh: Quận 1, Quận 3, Bình Thạnh
├── Mỗi chi nhánh quản lý 10 xe riêng
├── Kho trung tâm giữ 5 xe dự phòng
└── Khi thiếu xe → request từ kho trung tâm

Tenant B: "Xe Điện Sài Gòn" quyết định:
──────────────────────────────────────
├── Có 5 chi nhánh trải rộng Sài Gòn
├── Mỗi chi nhánh quản lý 15 xe riêng
├── 2 kho trung tâm (Đông và Tây Sài Gòn)
└── Tự do chuyển xe giữa các chi nhánh

→ Cùng 1 phần mềm, nhưng mỗi công ty tổ chức khác nhau
```

### 3.4 Mối quan hệ Branch-Vehicle-Customer

```
┌─────────────────────────────────────────────────────────────┐
│                  BRANCH STRUCTURE                           │
└─────────────────────────────────────────────────────────────┘

CENTRAL BRANCH (Kho trung tâm)
    │
    ├── Có fleet DỰ PHÒNG để cấp cho chi nhánh thiếu
    ├── KHÔNG phục vụ khách hàng trực tiếp (thường)
    └── Chỉ điều phối xe

BRANCH (Chi nhánh)
    │
    ├── Phục vụ khách hàng TRỰC TIẾP
    ├── Có fleet RIÊNG để cho thuê
    ├── Nhận xe từ Central khi thiếu
    └── Báo cáo doanh thu về Central
```

### 3.5 Vehicle Transfer Flow (Multi-branch)

**Lưu ý MVP:** Vehicle Transfer được lùi sang Phase 2. MVP phục vụ nhà xe nhỏ (1-2 chi nhánh, <50 xe) — họ tự gọi điện điều phối. Module này chỉ có giá trị với chuỗi 50+ xe, 3+ chi nhánh.

```
SCENARIO: Chi nhánh Quận 1 hết xe, khách cần thuê

1. CUSTOMER đặt xe tại Branch A1 (Quận 1)
   │
   ▼
2. BRANCH A1 kiểm tra fleet
   └── Xe available = 0 → Hết xe.
   │
   ▼
3. BRANCH A1 request TRANSFER từ Central
   └── "Cần 2 xe 4 chỗ gấp"
   │
   ▼
4. CENTRAL xem xe dự phòng
   └── Có 5 xe tại kho
   │
   ▼
5. CENTRAL duyệt TRANSFER
   └── Chuyển 2 xe → Branch A1
   │
   ▼
6. BRANCH A1 xác nhận nhận xe
   └── Xe available = 2
   │
   ▼
7. Xác nhận booking cho CUSTOMER
```

---

## 4. Sự khác biệt cốt lõi

```
┌─────────────────────────────────────────────────────────────────┐
│                    MULTI-TENANT                                  │
│                    (Architecture)                               │
├─────────────────────────────────────────────────────────────────┤
│  WHO?        │ Nhà phát triển phần mềm (SaaS provider)          │
│  WHAT?       │ Cách xây dựng phần mềm để phục vụ nhiều khách    │
│  WHEN?       │ Quyết định khi THIẾT KẾ phần mềm                 │
│  SCOPE        │ Toàn bộ hệ thống (system-wide)                   │
│  CHANGES?     │ Ít thay đổi sau khi đã design                   │
│  Câu hỏi      │ "Phần mềm hoạt động như thế nào?"               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    MULTI-BRANCH                                  │
│                    (Business Model)                              │
├─────────────────────────────────────────────────────────────────┤
│  WHO?        │ Khách hàng thuê SaaS (tenant)                     │
│  WHAT?       │ Cách tổ chức hoạt động kinh doanh của họ          │
│  WHEN?       │ Quyết định khi KHÁCH ĐĂNG KÝ SaaS                │
│  SCOPE        │ Trong phạm vi công ty đó (per-tenant)           │
│  CHANGES?     │ Có thể thay đổi theo nhu cầu kinh doanh         │
│  Câu hỏi      │ "Công ty tổ chức như thế nào?"                  │
└─────────────────────────────────────────────────────────────────┘
```

### Bảng so sánh

| | Multi-tenant | Multi-branch |
|---|---|---|
| **Loại** | Kiến trúc phần mềm | Mô hình nghiệp vụ |
| **Quyết định bởi** | SaaS Provider | Tenant (khách hàng) |
| **Cấp độ** | System-wide | Company-wide |
| **Thay đổi** | Hiếm khi thay đổi | Có thể thay đổi theo nhu cầu |
| **Ví dụ** | Shared DB, tenant_id column | 3 chi nhánh, xe dự phòng |
| **Câu hỏi** | "Phần mềm hoạt động như thế nào?" | "Công ty tổ chức như thế nào?" |

---

## 5. Kết hợp Multi-tenant + Multi-branch

### 5.1 Sơ đồ hoàn chỉnh

```
┌─────────────────────────────────────────────────────────────────┐
│                    CAR RENTAL SaaS PLATFORM                     │
└─────────────────────────────────────────────────────────────────┘

                    ┌─────────────────┐
                    │   SaaS Platform │
                    │  (Shared Infra) │
                    └────────┬────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│   TENANT A    │    │   TENANT B    │    │   TENANT C    │
│  "RentCar VN" │    │ "Xe Điện SG"  │    │"Taxi Nha Trang"│
└───────┬───────┘    └───────┬───────┘    └───────┬───────┘
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│   BRANCHES    │    │   BRANCHES    │    │   BRANCHES    │
│  ┌─────────┐  │    │  ┌─────────┐  │    │  ┌─────────┐  │
│ │ Central │  │    │  │ Central │  │    │  │ Central │  │
│  │ (5 xe)  │  │    │  │ (10 xe) │  │    │  │ (20 xe) │  │
│  └─────────┘  │    │  └─────────┘  │    │  └─────────┘  │
│  ┌─────────┐  │    │  ┌─────────┐  │    │  ┌─────────┐  │
│  │ Branch1 │  │    │  │ Branch1 │  │    │  │ Branch1 │  │
│  │ (10 xe) │  │    │  │ (15 xe) │  │    │  │ (25 xe) │  │
│  └─────────┘  │    │  └─────────┘  │    │  └─────────┘  │
│  ┌─────────┐  │    │  ┌─────────┐  │    │  ┌─────────┐  │
│  │ Branch2 │  │    │  │ Branch2 │  │    │  │ Branch2 │  │
│  │ (8 xe)  │  │    │  │ (12 xe) │  │    │  │ (20 xe) │  │
│  └─────────┘  │    │  └─────────┘  │    │  └─────────┘  │
└───────────────┘    └───────────────┘    └───────────────┘
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│   CUSTOMERS   │    │   CUSTOMERS   │    │   CUSTOMERS   │
│   A1, A2...  │    │   B1, B2...  │    │   C1, C2...  │
└───────────────┘    └───────────────┘    └───────────────┘
```

### 5.2 Minh họa thực tế

```
Tenant A: "RentCar VN"
├── Branch A1: Quận 1 (10 xe)
├── Branch A2: Quận 3 (8 xe)
└── Central: Kho trung tâm (5 xe)

Tenant B: "Xe Điện Sài Gòn"
├── Branch B1: Q1 (15 xe)
├── Branch B2: Q2 (12 xe)
├── Branch B3: Q7 (10 xe)
└── Central: Kho trung tâm (10 xe)

─────────────────────────────────────────────────────────────
Tenant A không thấy xe của Tenant B (multi-tenant isolation)
Branch A1 không thấy xe của Branch A2 (multi-branch, cùng tenant)
```

### 5.3 Luồng dữ liệu

```
┌─────────────────────────────────────────────────────────────┐
│                 DATA FLOW                                    │
└─────────────────────────────────────────────────────────────┘

1. Request từ User
   │
   ▼
2. JWT Token → Extract tenant_id
   │
   ▼
3. Security Filter → Validate tenant
   │
   ▼
4. Repository Layer → Auto-add tenant_id filter
   │
   ▼
5. Database → Chỉ trả về data của tenant đó
   │
   ▼
6. Response → User chỉ thấy data CỦA MÌNH
```

---

## 6. Tại sao cần cả hai?

| Mô hình | Lợi ích | Ví dụ |
|---------|---------|-------|
| **Multi-tenant** | Tiết kiệm chi phí, dễ scale, bảo trì đơn giản | 1 server cho 100+ tenant |
| **Multi-branch** | Mỗi chi nhánh tự quản lý xe, nhân viên | RentCar VN có 3 chi nhánh |

**Trong thực tế:**
- **Tenant** = Công ty cho thuê xe (khách hàng SaaS của bạn)
- **Branch** = Chi nhánh của công ty đó
- Bạn (nhà cung cấp SaaS) chỉ cần quản lý 1 hệ thống, nhưng phục vụ được nhiều công ty
- Mỗi công ty tự quản lý các chi nhánh của mình

---

## 7. Cơ chế Phân quyền: Dynamic RBAC + ABAC theo Chi nhánh

### 7.1 Bài toán Phân quyền trong Hệ thống Car Rental SaaS

Một User có thể làm việc tại nhiều Chi nhánh (Branch) trong cùng một Nhà xe (Tenant). Hệ thống cần cơ chế phân quyền 2 lớp kết hợp:
- **Lớp 1 (Hành động):** User có được phép thực hiện chức năng này hay không? (VD: Xem danh sách xe, Duyệt đơn, Giao xe).
- **Lớp 2 (Phạm vi dữ liệu):** Nếu được phép, User được xem/sửa dữ liệu của Nhà xe nào và Chi nhánh nào?
- **Yêu cầu thực tế:** Cùng có quyền xem lịch xe, nhưng nhân viên Chi nhánh Hà Nội tuyệt đối không được xem hoặc can thiệp vào xe của Chi nhánh Đà Nẵng.

---

### 7.2 Dynamic RBAC (Role-Based Access Control) — "Được làm hành động gì?"

RBAC kiểm soát quyền truy cập dựa trên **Quyền nguyên tử (Permissions)** thông qua **Nhóm quyền động (Dynamic Roles)** trong từng Tenant:

* **Bảng `permissions`:** Danh mục các hành động nghiệp vụ cố định toàn hệ thống (VD: `vehicle:read`, `vehicle:create`, `booking:create`, `booking:handover`, `booking:return`).
* **Bảng `roles`:** Các nhóm quyền được khởi tạo riêng cho từng Tenant (`tenant_id NOT NULL`), gồm các role hệ thống mặc định (`TENANT_ADMIN`, `STAFF`, `SALE`) hoặc custom role do nhà xe tự tạo thêm.
* **Bảng `role_permissions`:** Gán tập hợp các quyền nguyên tử (`permissions`) vào từng `role`.

```
CẤU TRÚC PHÂN QUYỀN ĐỘNG:
────────────────────────────────────────────────────────────────────────
TENANT_ADMIN → Sở hữu toàn bộ Permissions của Tenant.
STAFF        → booking:read, booking:create, booking:handover, booking:return, vehicle:read...
SALE         → booking:read, booking:create, customer:create...
```

* **Cách triển khai ở Backend:**
  - Token JWT chứa danh sách quyền nguyên tử (Authorities) của User ứng với Tenant hiện tại.
  - Sử dụng `@PreAuthorize("hasAuthority('booking:create')")` tại tầng Controller/Service để kiểm soát hành động.

---

### 7.3 ABAC (Attribute-Based Access Control) — "Trên dữ liệu / Chi nhánh nào?"

Sau khi RBAC xác nhận User có quyền thực hiện hành động, **ABAC** tiếp tục can thiệp ở tầng Service/Repository để giới hạn **phạm vi dòng dữ liệu (Data Scoping)** dựa trên các thuộc tính ngữ cảnh:

```
CÁC THUỘC TÍNH NGỮ CẢNH (ATTRIBUTES):
────────────────────────────────────────────────────────────────────────
THUỘC TÍNH USER:     user.tenant_id, user.active_branch_id, user.assigned_branches[]
THUỘC TÍNH DỮ LIỆU:  booking.tenant_id, booking.branch_id, vehicle.branch_id, vehicle.status
THUỘC TÍNH MÔI TRƯỜNG: current_timestamp, hold_expires_at
```

**Quy tắc lọc dữ liệu (Scoping Rules) theo Role:**

| Nhóm Role | Thuộc tính kiểm tra | Phạm vi dữ liệu nhìn thấy / tác nghiệp |
| :--- | :--- | :--- |
| **TENANT_ADMIN** | `tenant_id` | Toàn quyền xem và tác nghiệp trên **toàn bộ Chi nhánh** của Nhà xe. |
| **STAFF** | `tenant_id` + `active_branch_id` | Chỉ xem và tác nghiệp trên dữ liệu của **Chi nhánh đang trực** (`active_branch_id` nằm trong danh sách `user_branches` được phân công). |
| **SALE** | `tenant_id` + `created_by` | Xem toàn bộ xe khả dụng trong Tenant để tư vấn chốt khách, nhưng chỉ quản lý các đơn booking do chính mình tạo ra (`created_by = user.id`) để theo dõi hoa hồng. |

---

### 7.4 Cách phối hợp RBAC + ABAC trong 1 Luồng Request Thực tế

Dưới đây là luồng xử lý hoàn chỉnh khi nhân viên bãi gọi API lấy danh sách đơn thuê xe:

```
REQUEST: Nhân viên STAFF gửi request: GET /api/v1/bookings
Context: tenant_id = "TN-01", active_branch_id = "BR-HN-01"

┌─────────────────────────────────────────────────────────────────┐
│ BƯỚC 1: KIỂM TRA HÀNH ĐỘNG (RBAC tại Controller Layer)          │
├─────────────────────────────────────────────────────────────────┤
│ @PreAuthorize("hasAuthority('booking:read')")                   │
│ → User có quyền "booking:read" trong token → HỢP LỆ (PASS)      │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│ BƯỚC 2: RÀNG BUỘC PHẠM VI DỮ LIỆU (ABAC tại Service Layer)     │
├─────────────────────────────────────────────────────────────────┤
│ 1. Đọc tenant_id và active_branch_id từ TenantContext           │
│    (được JwtAuthenticationFilter bóc từ JWT Claim đã ký).       │
│ 2. Không dùng Header X-Active-Branch-Id để tránh 2 nguồn sự     │
│    thật và chống giả mạo phía Client.                           │
│ 3. Khi chuyển Tenant/Branch (/select-tenant, /switch-branch):   │
│    ID đích truyền qua Request Body → Backend xác thực quyền     │
│    (user_branches status = ACTIVE) rồi cấp JWT mới ngay!        │
│    (TENANT_ADMIN muốn lọc danh sách/báo cáo theo 1 chi nhánh    │
│     bất kỳ thì truyền Query Param ?branchId=... có check tenant)│
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
┌─────────────────────────────────────────────────────────────────┐
│ BƯỚC 3: TRUY VẤN DỮ LIỆU CÔ LẬP (Repository / SQL Layer)       │
├─────────────────────────────────────────────────────────────────┤
│ SQL Query thực thi:                                             │
│ SELECT * FROM bookings                                          │
│ WHERE tenant_id = :currentTenantId                              │
│   AND branch_id = :activeBranchId;                              │
│ → Kết quả: Chỉ trả về đơn hàng của đúng Chi nhánh Hà Nội.       │
└─────────────────────────────────────────────────────────────────┘
```

### 7.5 Mô hình dữ liệu User-Tenant-Branch

**Nguyên tắc:** Thông tin User (email, password, profile) được lưu tập trung 1 lần ở tầm hệ thống. Quan hệ User-Tenant (N-N) và User-Branch (N-N) được quản lý qua bảng liên kết. Cùng một User có thể có Role khác nhau ở các Tenant khác nhau.

```sql
-- User: thông tin định danh tập trung, không phụ thuộc Tenant
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    phone VARCHAR(20),
    avatar_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- N-N: User thuộc những Tenant nào, với Role gì trong từng Tenant
-- Cùng 1 User có thể là TENANT_ADMIN ở Tenant A, nhưng chỉ là STAFF ở Tenant B
CREATE TABLE user_tenants (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE, -- Mã ID người dùng (FK users)
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE, -- Mã ID nhà xe (FK tenants)
    role_id UUID NOT NULL REFERENCES roles(id),                   -- Mã ID nhóm quyền động (FK roles)
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Thời điểm người dùng gia nhập tenant
    PRIMARY KEY (user_id, tenant_id),
    CONSTRAINT fk_user_tenant_role FOREIGN KEY (role_id, tenant_id) REFERENCES roles(id, tenant_id)
);

-- SUPER_ADMIN: role được lưu trực tiếp trên users (không cần user_tenants)
-- vì SUPER_ADMIN có toàn quyền trên tất cả Tenant

-- N-N: Trong phạm vi 1 Tenant, User được gán vào những Branch nào
-- Áp dụng cho STAFF (Admin chọn danh sách branch để phân công) và SALE (Hệ thống tự động gán toàn bộ active branches của Tenant).
-- Cả STAFF và SALE đều phải chọn active_branch_id khi tác nghiệp. TENANT_ADMIN bypass kiểm tra user_branches.
CREATE TABLE user_branches (
    user_id UUID NOT NULL,                                       -- Mã ID người dùng (FK user_tenants)
    tenant_id UUID NOT NULL,                                     -- Mã ID nhà xe (FK user_tenants)
    branch_id UUID NOT NULL,                                     -- Mã ID chi nhánh được gán (FK branches)
    status SMALLINT NOT NULL DEFAULT 1                           -- Trạng thái làm việc: 1: ACTIVE (Đang làm), 2: SUSPENDED (Tạm dừng), 3: RESIGNED (Nghỉ việc)
        CHECK (status IN (1, 2, 3)),
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, -- Ngày bắt đầu làm việc tại chi nhánh này
    ended_at TIMESTAMP WITH TIME ZONE,                           -- Ngày kết thúc làm việc tại chi nhánh (NULL nếu đang làm)
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL,     -- ID Admin thực hiện cập nhật trạng thái/gán chi nhánh
    PRIMARY KEY (user_id, branch_id),
    CONSTRAINT fk_user_branch_user_tenant FOREIGN KEY (user_id, tenant_id)
        REFERENCES user_tenants(user_id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_branch_branch_tenant FOREIGN KEY (branch_id, tenant_id)
        REFERENCES branches(id, tenant_id) ON DELETE CASCADE
);
```

**Quy tắc phân quyền theo role:**

| Role | CODE | Phạm vi Tenant | Phạm vi Branch | Ghi chú |
|------|:---:|---------------|----------------|---------|
| `SUPER_ADMIN` | N/A | Tất cả Tenant (bypass RLS) | Tất cả Branch | Lưu trực tiếp trên `users`, không cần `user_tenants` |
| `TENANT_ADMIN`| `TENANT_ADMIN` | 1 Tenant | Tất cả Branch trong Tenant | Có `user_tenants`, không cần `user_branches` (bypass) |
| `STAFF`       | `STAFF` | 1 Tenant | 1+ Branch được phân công | Admin chọn danh sách branch trên form (`branchIds`), lưu vào `user_branches`. Chọn `active_branch_id` khi tác nghiệp. |
| `SALE`        | `SALE` | 1 Tenant | Toàn bộ Branch của Tenant | 1 Sale có thể thuộc nhiều Tenant. Tự động gán full active branches vào `user_branches`. Chọn `active_branch_id` khi tác nghiệp. |


**Ví dụ thực tế:**
```
User "Nguyen Van A" (email: anv@email.com) ├── Tenant "RentCar VN": role = TENANT_ADMIN → toàn quyền tất cả Branch └── Tenant "Xe Điện Sài Gòn": role = STAFF → Admin phân công vào Branch B1, B2 (chọn 1 Branch để trực)

User "Tran Thi B" (email: btt@email.com) ├── Tenant "RentCar VN": role = STAFF → Admin phân công vào Branch A1 └── Tenant "Taxi Nha Trang": role = SALE → kinh doanh tại Taxi Nha Trang (chọn 1 Branch để tác nghiệp)
```

**Lợi ích của thiết kế này:**
- 1 User chỉ có 1 email/password — tránh trùng lặp thông tin cá nhân khi làm việc ở nhiều nhà xe
- Khi User đổi password, cập nhật 1 lần — áp dụng cho tất cả Tenant
- Role được gán riêng cho từng Tenant — linh hoạt, phản ánh đúng thực tế (cùng 1 người có thể là quản lý ở nhà xe này, nhân viên ở nhà xe khác)
- Audit trail rõ ràng: mọi hành động đều gắn với `user_id` + `tenant_id`

### 7.6 Luồng Đăng Nhập Multi-Tenant

**Vấn đề:** User có thể thuộc nhiều Tenant. Làm sao để xác định User muốn truy cập Tenant nào sau khi đăng nhập?

```
┌─────────────────────────────────────────────────────────────────┐
│                 MULTI-TENANT LOGIN FLOW                          │
└─────────────────────────────────────────────────────────────────┘

BƯỚC 1: XÁC THỰC (Authentication)
──────────────────────────────────
User nhập email + password tại trang login chung
→ POST /auth/login { email, password }
→ Hệ thống xác thực tập trung (kiểm tra bảng users)
→ Không cần biết tenant tại bước này

BƯỚC 2: TRA DANH SÁCH TENANT
──────────────────────────────────
SELECT ut.tenant_id, ut.role, t.name, t.domain
FROM user_tenants ut
JOIN tenants t ON t.id = ut.tenant_id
WHERE ut.user_id = ?
  AND t.is_active = true

→ Kết quả trả về danh sách Tenant mà User có quyền truy cập

BƯỚC 3: QUYẾT ĐỊNH LUỒNG TIẾP THEO
──────────────────────────────────

  ┌─────────────────────────────────────────────────────┐
  │ Số Tenant User thuộc về?                             │
  └──────────────────────┬──────────────────────────────┘
                         │
           ┌─────────────┴─────────────┐
           ▼                           ▼
   ┌───────────────┐           ┌───────────────┐
   │  1 Tenant     │           │  N Tenant     │
   │  (SUPER_ADMIN │           │  (Đa số user) │
   │   cũng tính   │           │               │
   │   là 0 tenant │           │               │
   │   → bypass)   │           │               │
   └───────┬───────┘           └───────┬───────┘
           │                           │
           ▼                           ▼
   ┌───────────────┐           ┌───────────────────────────┐
   │ TỰ ĐỘNG CHỌN │           │ HIỂN THỊ TENANT PICKER    │
   │ Không cần     │           │ ┌───────────────────────┐ │
   │ màn hình chọn │           │ │ Chọn nhà xe để truy   │ │
   │               │           │ │ cập:                  │ │
   │               │           │ │                       │ │
   │               │           │ │ ○ RentCar VN          │ │
   │               │           │ │   (TENANT_ADMIN)      │ │
   │               │           │ │ ○ Xe Điện Sài Gòn     │ │
   │               │           │ │   (STAFF)             │ │
   │               │           │ │                       │ │
   │               │           │ │   [Tiếp tục]          │ │
   │               │           │ └───────────────────────┘ │
   └───────┬───────┘           └───────────┬───────────────┘
           │                               │
           └───────────────┬───────────────┘
                           ▼
BƯỚC 4: CẤP JWT CÓ TENANT CONTEXT
──────────────────────────────────
Sau khi xác định Tenant (tự động hoặc User chọn):
→ Backend tạo JWT chứa:
    {
      "sub": "user-uuid",
      "email": "user@example.vn",
      "tenant_id": "tenant-uuid",       // Tenant đã chọn
      "role": "TENANT_ADMIN",           // Role trong Tenant đã chọn
      "active_branch_id": null          // Chưa chọn chi nhánh làm việc
    }
→ Trả về danh sách chi nhánh (assignedBranches) và permissions cho Frontend

BƯỚC 4: CHỌN / CHUYỂN ĐỔI CHI NHÁNH LÀM VIỆC (switch-branch)
──────────────────────────────────────────────────────────
→ User chọn chi nhánh làm việc: "Chi nhánh Quận 1"
→ POST /api/v1/auth/switch-branch { "activeBranchId": "branch-uuid" }
→ Kiểm tra:
  * Chi nhánh có thuộc đúng tenant_id trong context không? (`branchInfo.getTenantId().equals(tenantUUID)` - Chống IDOR)
  * Chi nhánh có đang hoạt động (`isActive = true`) không?
  * Nếu là TENANT_ADMIN: Có toàn quyền vào mọi chi nhánh active (bỏ qua bảng user_branches)
  * Nếu là STAFF hoặc SALE: Bắt buộc phải có bản ghi phân công trong `user_branches` theo đúng tenant VÀ trạng thái công tác phải là `status = 1 (ACTIVE)`. Bị đình chỉ hoặc thu hồi quyền ➔ 403 Forbidden. Cả STAFF và SALE đều chọn chi nhánh làm việc để token có `active_branch_id` cho Backend biết ngữ cảnh xử lý.
→ Cấp Access Token hoàn chỉnh mang active_branch_id:
    {
      "sub": "user-uuid",
      "email": "user@example.vn",
      "tenant_id": "tenant-uuid",
      "role": "STAFF",
      "active_branch_id": "branch-uuid" // Chi nhánh đang trực
    }
→ Chuyển hướng vào màn hình nghiệp vụ chính (Dashboard chi nhánh)

BƯỚC 5: LÀM MỚI PHIÊN LÀM VIỆC TỰ ĐỘNG (SILENT REFRESH - refresh-token)
───────────────────────────────────────────────────────────────────────
Khi Access Token hết hạn (15 phút), Frontend tự động gọi ngầm:
→ POST /api/v1/auth/refresh-token (Đính kèm HttpOnly Cookie: refreshToken)
→ Backend kiểm tra chữ ký, hạn sử dụng, session id và bóc tách tenant_id, active_branch_id từ refresh token
→ Xử lý thông minh bảo tồn ngữ cảnh làm việc (Zero-Interruption UX) & Kịch bản Fallback FE:
  * Nếu đang ở màn chọn Nhà xe (Pre-tenant) → Cấp token tạm thời + danh sách availableTenants.
  * Nếu đang ở màn chọn Chi nhánh → Cấp token mang tenant_id, roleCode + danh sách assignedBranches (chỉ lấy chi nhánh active & status = 1).
  * Nếu đang làm việc tại Chi nhánh (Dashboard/Booking):
    - Xác thực lại Chi nhánh (`findByIdAndTenantId`, active) và phân quyền nhân viên (`status = 1`, trừ TENANT_ADMIN).
    - **Nếu Chi nhánh bị khóa / ngừng hoạt động / nhân viên bị thu hồi quyền:** Backend trả lỗi `403 Forbidden` ➔ Frontend bắt lỗi, hiển thị Toast cảnh báo và **tự động bắn người dùng về màn Chọn Chi Nhánh (`/select-branch`)** để chọn chi nhánh khác.
    - **Nếu Nhà xe bị khóa:** Backend trả lỗi `403 Forbidden` ➔ Frontend bắt lỗi, hiển thị Toast cảnh báo và **tự động bắn người dùng về màn Chọn Nhà Xe (`/select-tenant`)** để chọn nhà xe khác (hoặc về `/login` nếu chỉ có 1 nhà xe).
    - **Nếu bình thường:** Cấp token đầy đủ tenant_id, active_branch_id, roleCode, permissions.
→ Trả về Access Token mới trong Response Body và Refresh Token mới qua Set-Cookie header. Form đang nhập của người dùng được submit tiếp tục mà không bị gián đoạn hay mất dữ liệu!
```

**Chi tiết Khế ước API Thực tế (Backend REST APIs):**

| Endpoint | Method | Header / Cookie | Body | Mô tả chức năng |
| :--- | :---: | :--- | :--- | :--- |
| `/api/v1/auth/login` | `POST` | Public | `LoginRequestDTO` (`email`, `password`) | Xác thực tài khoản. Trả `accessToken` trong body, set HttpOnly Cookie `refreshToken`. |
| `/api/v1/auth/select-tenant` | `POST` | `Bearer <token>` | `SelectTenantRequestDTO` (`tenantId`) | Chọn nhà xe làm việc $\rightarrow$ Cấp token mang `tenant_id` và danh sách chi nhánh tương ứng. |
| `/api/v1/auth/switch-branch` | `POST` | `Bearer <token>` | `SwitchBranchRequestDTO` (`activeBranchId`) | Chọn/Đổi chi nhánh làm việc $\rightarrow$ Cấp token đầy đủ mang `active_branch_id`. |
| `/api/v1/auth/refresh-token` | `POST` | Cookie: `refreshToken` | *Trống* | Làm mới token tự động. Bảo tồn ngữ cảnh Nhà xe & Chi nhánh (Silent Refresh). |
| `/api/v1/auth/logout` | `POST` | `Bearer <token>` | *Trống* | Đăng xuất hệ thống, hủy session, xóa HttpOnly Cookie. |

**Cấu trúc Response `AuthResponseDTO` Thực tế:**

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
    "expiresIn": 900,
    "availableTenants": null,
    "user": {
      "id": "4aec2a36-fa3b-4b26-a0d9-f0b063971d9a",
      "email": "lan.staff@rentcarhanoi.vn",
      "fullname": "Trần Thị Lan",
      "phone": "09241419610",
      "tenantId": "e41492d0-67ee-4699-b311-fee98cb37781",
      "tenantName": "RentCar Hà Nội",
      "roleCode": "STAFF",
      "activeBranchId": "33b5b9fe-c6bc-42fa-978e-8e0ea092dc6a",
      "permissions": ["booking:read", "booking:create", "vehicle:read"],
      "assignedBranches": [
        {
          "branchId": "33b5b9fe-c6bc-42fa-978e-8e0ea092dc6a",
          "branchName": "Central Hà Nội",
          "address": "1 Đường 1 - Central Hà Nội"
        }
      ]
    }
  },
  "message": "Làm mới token thành công !",
  "timestamp": "2026-10-19T10:15:00Z"
}
```

---

## 8. Cơ Chế Cô Lập Dữ Liệu Ở Tầng Mã Nguồn (Thread Context Isolation)

Để đảm bảo data giữa các Tenant và Branch không bao giờ bị rò rỉ:
1. **`JwtAuthenticationFilter`:** Với mỗi HTTP Request gửi đến, Filter bóc tách các claims trong JWT:
   - Trích xuất `tenant_id` $\rightarrow$ nạp vào `TenantContext.setTenantId(tenantUUID)`
   - Trích xuất `active_branch_id` $\rightarrow$ nạp vào `TenantContext.setBranchId(branchUUID)`
   - Trích xuất `role` $\rightarrow$ nạp vào `TenantContext.setRole(role)`
2. **`TenantContext` (ThreadLocal):** Đóng vai trò là biến ngữ cảnh xuyên suốt Thread xử lý của request đó, cho phép các Repository và Service tự động lấy `tenant_id` mà không cần truyền tham số thủ công.
3. **Dọn dẹp bắt buộc (`TenantContext.clear()`):** Đặt trong khối `finally` của Filter để ngăn ngừa ô nhiễm dữ liệu giữa các luồng tái sử dụng của Tomcat Thread Pool.

---

## 9. Hiểu Đơn Giản Bản Chất

| Khái niệm | Mô hình so sánh | Ý nghĩa kỹ thuật |
| :--- | :--- | :--- |
| **Multi-tenant** | *"Một tòa nhà chung cư có nhiều căn hộ"* | Cách xây dựng hạ tầng phần mềm: 1 server, 1 DB chung, dữ liệu độc lập ngăn nắp theo từng phòng. |
| **Multi-branch** | *"Mỗi gia đình tự phân chia phòng khách, phòng ngủ"* | Mô hình nghiệp vụ kinh doanh: Từng công ty tự chia mạng lưới chi nhánh, tự gán nhân sự trực thuộc. |
| **Context Switch** | *"Rút chìa khóa phòng này để mở cửa phòng khác"* | Cơ chế cấp JWT mới: Khi chuyển nhà xe hoặc chuyển chi nhánh, token được nâng cấp claims tương ứng. |

