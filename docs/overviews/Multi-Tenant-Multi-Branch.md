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

## 7. Cơ chế Phân quyền Multi-Branch: RBAC + ABAC

### 7.1 Bài toán

Một User có thể làm việc tại nhiều Branch trong cùng một Tenant. Cần cơ chế phân quyền đảm bảo:
- User chỉ truy cập được dữ liệu thuộc Tenant của họ (Multi-tenant isolation)
- User chỉ thấy dữ liệu thuộc các Branch họ được phân công (Multi-branch scoping)
- Cùng một Role (vd: STAFF) nhưng khác Branch sẽ thấy dữ liệu khác nhau

### 7.2 RBAC (Role-Based Access Control) — "Ai được làm gì?"

RBAC phân quyền theo **vai trò cố định** được gán cho User. Role quyết định tập hành động (permissions) được phép thực hiện.

```
VÍ DỤ PHÂN QUYỀN:
─────────────────────────────────────────────
SUPER_ADMIN    → Toàn quyền trên tất cả Tenant, bypass RLS
TENANT_ADMIN   → Full quyền trong Tenant: CRUD Branch, Vehicle, User, Booking, Report
BRANCH_MANAGER → Quản lý 1+ Branch được gán: CRUD Vehicle, Booking, Staff của branch đó
STAFF          → Tác nghiệp tại Branch: tạo Booking, giao/nhận xe, xem lịch xe
```

**Cách triển khai:**
- Mỗi User có 1 Role (`user.role` column)
- Role → Permissions mapping được định nghĩa tập trung (enum hoặc config)
- Spring Security `@PreAuthorize` kiểm tra role tại tầng API/Service
- Ví dụ: `@PreAuthorize("hasRole('TENANT_ADMIN')")` trên endpoint tạo Branch

### 7.3 ABAC (Attribute-Based Access Control) — "Trên dữ liệu nào?"

ABAC kiểm soát truy cập dựa trên **thuộc tính động** của request và resource. ABAC bổ sung cho RBAC khi cần kiểm soát mịn hơn cấp Role.

```
CÁC THUỘC TÍNH (ATTRIBUTES):
─────────────────────────────────────────────
USER attributes:     user.id, user.role, user.assigned_branches[]
RESOURCE attributes: booking.branch_id, vehicle.tenant_id, booking.status
CONTEXT attributes:  current_time, request_ip, booking.state_transition
```

**Ví dụ ABAC rules:**

| Rule | Mô tả |
|------|-------|
| `user.tenant_id == resource.tenant_id` | User chỉ đọc được dữ liệu thuộc Tenant của mình |
| `resource.branch_id IN user.assigned_branches` | User chỉ thấy dữ liệu của Branch họ được gán |
| `booking.status == 'in_progress' AND user.role == 'STAFF'` | STAFF chỉ được sửa booking đang in_progress |
| `payment.amount < 5_000_000 OR user.role == 'BRANCH_MANAGER'` | STAFF không được duyệt thanh toán > 5tr |

**Cách triển khai:**
- `TenantInterceptor` set `app.current_tenant_id` vào DB session → PostgreSQL RLS tự động filter
- `BranchContext` lưu `current_branch_id` từ request (header hoặc query param)
- Query layer tự động append `WHERE branch_id = ?` khi User có role STAFF/BRANCH_MANAGER
- SUPER_ADMIN và TENANT_ADMIN có thể switch branch context để xem dữ liệu đa chi nhánh

### 7.4 Cách RBAC + ABAC kết hợp trong 1 request

```
REQUEST: STAFF "user_A" (thuộc Branch 1, 2) gọi API GET /bookings

BƯỚC 1 — RBAC (API layer):
──────────────────────────
@PreAuthorize("hasAnyRole('STAFF', 'BRANCH_MANAGER', 'TENANT_ADMIN')")
→ Role STAFF được phép gọi endpoint này → PASS

BƯỚC 2 — ABAC (Service layer):
──────────────────────────
Xác định scope từ user context:
  user.assigned_branches = [branch_1, branch_2]
  current_branch = branch_1 (từ request header)
→ Tự động filter: SELECT * FROM bookings WHERE branch_id IN ('branch_1', 'branch_2')

BƯỚC 3 — ABAC (DB layer):
──────────────────────────
PostgreSQL RLS policy:
  USING (tenant_id = current_setting('app.current_tenant_id'))
→ DB đảm bảo không lộ data cross-tenant, kể cả khi code quên filter
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
    user_id UUID NOT NULL REFERENCES users(id),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    role SMALLINT NOT NULL CHECK (role IN (1, 2, 3)), -- 1: TENANT_ADMIN, 2: STAFF, 3: SALE
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, tenant_id)
);

-- SUPER_ADMIN: role được lưu trực tiếp trên users (không cần user_tenants)
-- vì SUPER_ADMIN có toàn quyền trên tất cả Tenant

-- N-N: Trong phạm vi 1 Tenant, User được gán vào những Branch nào
-- Chỉ áp dụng cho STAFF và SALE (TENANT_ADMIN toàn quyền tất cả Branch)
CREATE TABLE user_branches (
    user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, branch_id),
    -- FK composite: đảm bảo user thuộc cùng tenant với branch
    CONSTRAINT fk_user_branch_user_tenant FOREIGN KEY (user_id, tenant_id) 
        REFERENCES user_tenants(user_id, tenant_id) ON DELETE CASCADE,
    -- FK composite: đảm bảo branch thuộc cùng tenant với user
    CONSTRAINT fk_user_branch_branch_tenant FOREIGN KEY (branch_id, tenant_id) 
        REFERENCES branches(id, tenant_id) ON DELETE CASCADE
);
```

**Quy tắc phân quyền theo role:**

| Role | Giá trị số | Phạm vi Tenant | Phạm vi Branch | Ghi chú |
|------|:---:|---------------|----------------|---------|
| `SUPER_ADMIN` | N/A | Tất cả Tenant (bypass RLS) | Tất cả Branch | Lưu trực tiếp trên `users`, không cần `user_tenants` |
| `TENANT_ADMIN`| `1` | 1 Tenant | Tất cả Branch trong Tenant | Có `user_tenants`, không cần `user_branches` |
| `STAFF`       | `2` | 1 Tenant | 1+ Branch được gán | Có `user_tenants` + `user_branches` (giao, nhận xe và tạo đơn) |
| `SALE`        | `3` | 1 Tenant | 1+ Branch được gán | Có `user_tenants` + `user_branches` (chỉ được tạo đơn, không được giao/nhận xe) |

**Ví dụ thực tế:**
```
User "Nguyen Van A" (email: anv@email.com)
├── Tenant "RentCar VN":     role = 1 (TENANT_ADMIN) → toàn quyền tất cả Branch
└── Tenant "Xe Điện Sài Gòn": role = 2 (STAFF)        → chỉ được gán Branch B1

User "Tran Thi B" (email: btt@email.com)
├── Tenant "RentCar VN":     role = 2 (STAFF)        → chỉ được gán Branch A1, A2
└── Tenant "Taxi Nha Trang": role = 3 (SALE)         → chỉ được gán Branch C1 (tạo đơn)
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
      "user_id": "uuid",
      "tenant_id": "uuid",       // Tenant đã chọn
      "branch_ids": ["id1"],     // Các Branch User được gán (nếu có)
      "role": "TENANT_ADMIN",    // Role TRONG Tenant đã chọn
      "exp": ...
    }
→ Redirect vào dashboard của Tenant đó

BƯỚC 5: SWITCH TENANT (không cần re-login)
──────────────────────────────────
Khi đang ở trong Tenant A, User muốn chuyển sang Tenant B:
→ Click avatar → "Chuyển nhà xe" → Chọn Tenant B
→ POST /auth/switch-tenant { tenant_id }
→ Hệ thống kiểm tra User có quyền trong Tenant B không
→ Cấp JWT mới với tenant_id = B, role = role trong Tenant B
→ Redirect về dashboard của Tenant B
→ Không cần nhập lại email/password
```

**Chi tiết API:**

| Endpoint | Mô tả |
|----------|-------|
| `POST /auth/login` | Xác thực email+password, trả về danh sách Tenant (nếu > 1) hoặc JWT (nếu = 1) |
| `POST /auth/select-tenant` | User chọn Tenant từ picker → nhận JWT với tenant context |
| `POST /auth/switch-tenant` | Đổi Tenant khi đang đăng nhập → JWT mới |
| `GET /auth/me/tenants` | Lấy danh sách Tenant hiện tại User có thể truy cập |

**Response `POST /auth/login` khi User có nhiều Tenant:**
```json
{
  "requires_tenant_selection": true,
  "tenants": [
    {
      "tenant_id": "uuid-a",
      "name": "RentCar VN",
      "role": "TENANT_ADMIN"
    },
    {
      "tenant_id": "uuid-b",
      "name": "Xe Điện Sài Gòn",
      "role": "STAFF"
    }
  ]
}
```

**Response `POST /auth/login` khi User có 1 Tenant (tự động chọn):**
```json
{
  "requires_tenant_selection": false,
  "access_token": "eyJhbGciOiJI...",
  "tenant": {
    "id": "uuid-a",
    "name": "RentCar VN"
  },
  "user": {
    "role": "TENANT_ADMIN"
  }
}
```

---

## 8. Hiểu đơn giản

| | Giải thích bằng bất động sản |
|---|---|
| **Multi-tenant** | "Nhà xây như thế nào?" - 1 tòa nhà có nhiều căn hộ, mỗi căn 1 gia đình |
| **Multi-branch** | "Người thuê sắp xếp nội thất ra sao?" - Mỗi gia đình tự trang trí theo ý thích |

**Tóm lại:**
- **Multi-tenant Architecture** = Cách xây dựng (kỹ thuật)
- **Multi-branch Business Model** = Cách tổ chức (kinh doanh)
