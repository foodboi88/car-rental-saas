# Car Rental SaaS Platform

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Nền tảng phần mềm dưới dạng dịch vụ (**Multi-Tenant SaaS**) chuyên biệt phục vụ các đơn vị cho thuê xe tự lái — từ các hộ kinh doanh cá thể quy mô một chi nhánh đến các chuỗi doanh nghiệp vận tải đa chi nhánh quy mô lớn. 

Hệ thống hỗ trợ quản lý nhà xe, chi nhánh/bãi xe, quản lý đội xe, luồng đặt xe (booking engine), định giá động, thanh toán, cùng kiến trúc phân quyền động (**Dynamic RBAC & Multi-Branch Context**) bảo mật cao.

> **Trạng thái dự án:** **Phase 1 — Backend Core & Dynamic Multi-Tenant RBAC (Hoàn thành & Đã kiểm thử)**.
> Xem chi tiết tài liệu kiến trúc tại thư mục [`docs/overviews/`](docs/overviews/).

---

## 🌟 Tính Năng Nổi Bật (Key Features)

- **Multi-Tenant Architecture:** Một phiên bản triển khai (Single Deployment) phục vụ nhiều nhà xe độc lập. Cô lập dữ liệu triệt để bằng `tenant_id` và cơ chế `TenantContext` (ThreadLocal).
- **Multi-Branch Hierarchy:** Hỗ trợ mô hình chi nhánh mẹ - con (Central & Satellite Branches), phân quyền nhân viên theo chi nhánh, điều phối xe linh hoạt.
- **Dynamic RBAC & ABAC:** Phân quyền theo vai trò động (`roles`, `permissions`, `role_permissions`) theo từng nhà xe, kết hợp kiểm soát truy cập theo chi nhánh (`user_branches`).
- **Phân cấp Ngữ cảnh Đăng nhập (Contextual Authentication):**
  - **Super Admin:** Quản trị toàn hệ thống SaaS, bypass kiểm tra tenant.
  - **Nhà xe đơn (1 Tenant):** Đăng nhập tự động nạp ngữ cảnh nhà xe và danh sách chi nhánh.
  - **Đa nhà xe (>1 Tenant):** Cấp token tạm thời (pre-tenant), hỗ trợ chọn nhà xe (`select-tenant`) và chuyển đổi chi nhánh (`switch-branch`).
- **Bảo mật & Tách biệt Phiên:** Stateless JWT Authentication (Access Token TTL ngắn + Refresh Token), xử lý lỗi bảo mật tập trung tại tầng Security Filter.
- **Tự động Đồng bộ Database (Flyway):** Hệ thống Database Migrations tự động từ V1 đến V6, bảo đảm toàn vẹn dữ liệu và lược đồ bảng.

---

## 📊 Bảng Tiến Độ Triển Khai (Implementation Status)

| Phân hệ / Module | Nội dung triển khai | Trạng thái |
| :--- | :--- | :---: |
| **Database Migrations** | Flyway V1 $\rightarrow$ V6 (Schema, Seed data, Dynamic RBAC, Branch alignment) | `100% HOÀN THÀNH` |
| **Security & Context Filter** | `JwtAuthenticationFilter`, `TenantContext`, `UserPrincipal`, Filter Exception Handlers | `100% HOÀN THÀNH` |
| **Authentication Service** | `login`, `selectTenant`, `switchBranch`, `refreshToken` (Chống IDOR, kiểm soát tài khoản khóa) | `100% HOÀN THÀNH` |
| **REST API Controller** | `AuthController` (`/api/v1/auth/**`), Swagger OpenAPI 3.0 Integration | `100% HOÀN THÀNH` |
| **Fleet & Vehicle Module** | Quản lý loại xe, hồ sơ xe, trạng thái xe | `Đang triển khai` |
| **Branch Management** | CRUD chi nhánh, phân công nhân sự chi nhánh | `Đang triển khai` |
| **Booking & Pricing Engine** | Đặt cọc, giữ chỗ (HOLD), giao/nhận xe, phạt trễ giờ, bảng giá động | `Kế hoạch Phase 2` |

---

## 🛠 Công Nghệ Sử Dụng (Tech Stack)

| Tầng (Layer) | Công nghệ chính |
| :--- | :--- |
| **Backend** | Java 17, Spring Boot 3.x, Spring Security 6.x, Spring Data JPA, Hibernate |
| **Database** | PostgreSQL 15 (Shared Database, `tenant_id` Logical Isolation) |
| **Database Migration**| Flyway Migration Tool |
| **Authentication** | Stateless JWT (jjwt 0.12.x), BCrypt Password Encoder |
| **API Documentation**| SpringDoc OpenAPI 3.0 / Swagger UI |
| **Build & Tooling** | Apache Maven 3.9+, Lombok, Docker |

---

## 🚀 Hướng Dẫn Khởi Chạy Nhanh (Quick Start)

### 1. Yêu cầu môi trường (Prerequisites)
- **Java Development Kit (JDK):** Version 17 trở lên.
- **Apache Maven:** Version 3.9+ (hoặc Maven Wrapper đi kèm).
- **Docker & Docker Compose:** Dùng để chạy cơ sở dữ liệu PostgreSQL.

### 2. Khởi động Cơ sở dữ liệu (PostgreSQL)
Chạy container PostgreSQL với cấu hình mặc định của dự án:
```powershell
docker run --name postgres-car-rental `
  -e POSTGRES_DB=car_rental `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=password `
  -p 5432:5432 `
  -d postgres:15-alpine
```

### 3. Khởi chạy Ứng dụng Backend
Di chuyển vào thư mục `backend` và chạy ứng dụng Spring Boot:
```powershell
cd backend
mvn spring-boot:run
```
> Khi khởi động, Flyway sẽ tự động chạy các script migration từ `V1` đến `V6` để tạo bảng và nạp dữ liệu mẫu vào PostgreSQL.

### 4. Truy cập Swagger UI
Sau khi ứng dụng khởi chạy thành công tại cổng `8080`, mở trình duyệt truy cập:
👉 **`http://localhost:8080/swagger-ui/index.html`**

---

## 👥 Dữ Liệu Tài Khoản Kiểm Thử (Seed Credentials)

> 🔑 **Mật khẩu dùng chung cho TẤT CẢ các tài khoản mẫu:**
> 👉 **`Hieudvt@123`**

| Email | Vai trò (Role) | Thuộc Nhà xe (Tenant) | Chi nhánh thao tác | Mục đích kiểm thử |
| :--- | :--- | :--- | :--- | :--- |
| **`superadmin@carrental.vn`** | `SUPER_ADMIN` | Toàn hệ thống | Không giới hạn | Đăng nhập cấp cao nhất, quản lý toàn bộ hệ thống |
| **`minh.admin@rentcarhanoi.vn`** | `TENANT_ADMIN` | RentCar Hà Nội | Toàn quyền 3 chi nhánh (*Central HN, Cầu Giấy, Đống Đa*) | Test luồng 1 nhà xe, switch branch tự do cho chủ xe |
| **`lan.staff@rentcarhanoi.vn`** | `STAFF` | RentCar Hà Nội | Được gán 2 chi nhánh (*Central HN, Cầu Giấy*) | Test giới hạn chi nhánh (vào Đống Đa bị chặn 403) |
| **`multi.user@carrental.vn`** | `STAFF` | RentCar Hà Nội & Sài Gòn Auto | Đa nhà xe | Test luồng chọn nhà xe (`select-tenant`) trước khi vào chi nhánh |
| **`mai.admin@saigonauto.vn`** | `TENANT_ADMIN` | Sài Gòn Auto | Toàn quyền chi nhánh Sài Gòn | Test chống tấn công chéo nhà xe (IDOR) |
| **`locked.user@rentcarhanoi.vn`** | `STAFF` | RentCar Hà Nội | Tài khoản bị vô hiệu hóa (`is_active = false`) | Test chặn đăng nhập tài khoản bị khóa (401) |

---

## 📑 Tài Liệu Kỹ Thuật (Architecture & Documentation)

Tất cả tài liệu thiết kế và phân tích chuyên sâu được lưu trữ tại thư mục [`docs/overviews/`](docs/overviews/):

- 🏛 **[Architecture Diagram](docs/overviews/Architecture-Diagram.md):** Sơ đồ kiến trúc tổng thể, Filter chain, luồng dữ liệu và thiết kế tuần tự.
- 🗄 **[Database Schema](docs/overviews/Database-Schema.md):** Thiết kế lược đồ cơ sở dữ liệu chi tiết, các mối quan hệ bảng, chỉ mục (Index) và giải thích nghiệp vụ.
- 🏢 **[Multi-Tenant & Multi-Branch Guide](docs/overviews/Multi-Tenant-Multi-Branch.md):** Phân tích sự khác biệt giữa kiến trúc phần mềm Multi-Tenant và mô hình nghiệp vụ Multi-Branch.
- 🚀 **[Onboarding Guide](docs/overviews/onboarding.md):** Cẩm nang hướng dẫn thành viên mới tiếp cận dự án, quy ước code và hướng dẫn kiểm thử.

---

## 🛠 Sổ Tay Vận Hành & Khắc Phục Lỗi (Cheatsheet)

### 1. Dừng ứng dụng Java đang chạy ngầm trên Windows (PowerShell)
```powershell
Stop-Process -Name java -Force
```

### 2. Sửa lỗi Checksum khi chỉnh sửa file Migration Flyway
Khi có sự thay đổi nội dung file SQL migration đã chạy trước đó, Flyway sẽ chặn khởi động vì sai checksum. Chạy lệnh sau để sửa:
```powershell
mvn flyway:repair "-Dflyway.url=jdbc:postgresql://localhost:5432/car_rental" "-Dflyway.user=postgres" "-Dflyway.password=password"
```

### 3. Reset sạch Database khi cần cấu trúc lại từ đầu
Nếu cần xóa toàn bộ database và để Flyway nạp lại từ đầu:
```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO public;
```
Sau đó khởi động lại ứng dụng: `mvn spring-boot:run`.

---

## 📄 License

Dự án được phân phối dưới giấy phép [MIT License](LICENSE). Copyright (c) 2026.