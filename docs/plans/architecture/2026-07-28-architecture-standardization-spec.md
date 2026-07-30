# Specification: Architecture Standardization - Car Rental SaaS

**Ngày tạo:** 2026-07-28  
**Trạng thái:** Chờ duyệt (Pending Review)  
**Mục tiêu:** Chuẩn hóa lại kiến trúc hệ thống (`docs/Architecture-Diagram.md`) bám sát nhu cầu thực tế của khách hàng, loại bỏ các thành phần dư thừa (Redis, Google Maps, CDN, Containerization, trùng lặp DB Schema) và tập trung vào hai tính năng cốt lõi: **Quét OCR giấy tờ khách hàng** và **Cơ chế Hold / Tạo đơn xe hiệu quả**.

---

## 1. Tóm tắt các thay đổi kiến trúc chính

| Thành phần cũ | Trạng thái | Giải pháp kiến trúc chuẩn hóa mới |
| :--- | :--- | :--- |
| **Redis Cache/Session** | ❌ Bỏ | Sử dụng cơ chế DB-backed Hold status (`HOLD`, `hold_expires_at = NOW() + 15m`) kết hợp Spring Boot `@Scheduled` Task quét giải phóng slot tự động. |
| **Google Maps API** | ❌ Bỏ | Quản lý xe và điểm giao nhận theo Chi nhánh (Branch) và khoảng thời gian, không cần tra cứu bản đồ địa lý. |
| **Frontend CDN** | ❌ Bỏ | Frontend (Angular SPA) được build thành tệp tĩnh, do Nginx trực tiếp phục vụ trên cùng Server. |
| **Docker / Containerization** | ❌ Bỏ | Triển khai trực tiếp (Direct VPS/Bare-metal): Spring Boot chạy dưới dạng Linux `systemd` service (`java -jar`), Nginx đóng vai trò Reverse Proxy + Web Server. Quy trình deploy đơn giản qua SSH/SCP. |
| **Vẽ Sơ đồ DB Chi tiết** | ❌ Bỏ | Tránh trùng lặp tài liệu. Toàn bộ thiết kế ERD và các bảng DB được tham chiếu trực tiếp đến file [Database-Schema.md](file:///f:/backend-training/private-car-rental/docs/Database-Schema.md). |
| **Tích hợp OCR Giấy tờ** | ✨ Mới | Mô đun OCR tự dựng mã nguồn mở **PaddleOCR** (FastAPI Microservice chạy trên CPU VPS) bóc tách tự động CCCD / GPLX từ ảnh chụp bằng điện thoại điền thông tin khách hàng. |
| **Quy trình Hold & Tạo đơn xe** | ✨ Chuẩn hóa | Thiết kế luồng đơn hàng từ `HOLD` → `CONFIRMED` → `IN_PROGRESS` → `COMPLETED` / `CANCELLED` với DB-level locking chống trùng lịch xe. |

---

## 2. Chi tiết các phần trong Architecture Diagram mới

### Phần 1: High-Level Architecture (Kiến trúc Tổng quan)
- **Clients**: Web Admin Portal & Staff Mobile-friendly App.
- **Nginx Gateway & Web Server**: Port 80/443 (HTTPS), bọc SSL, phục vụ FE static assets, reverse proxy các request `/api/*` sang Backend Port `8080`.
- **Spring Boot Backend (Monolith)**:
  - Security & Tenant Context Layer (JWT, Multi-tenant Isolation).
  - Business Services: Auth, Tenant, Branch, Vehicle (Fleet), Customer + OCR Integration, Booking + Hold Engine, Payment & Pricing.
- **Data & Storage Layer**:
  - PostgreSQL (Primary DB, Row-level tenant isolation, Pessimistic/Optimistic locking).
  - S3 / MinIO Object Storage (Lưu trữ ảnh xe và ảnh scan giấy tờ CCCD/GPLX, không dùng Local Disk).
- **OCR Engine (Self-Hosted)**:
  - PaddleOCR Engine (FastAPI Python Service tự dựng trên VPS CPU, tiền xử lý ảnh bằng OpenCV để xoay nghiêng/chống chói).
- **External Systems**:
  - SMTP Email Service.

### Phần 2: Core Feature Flows (Sơ đồ luồng nghiệp vụ trọng tâm)
1. **Luồng quét OCR Giấy tờ khách hàng**:
   - FE upload/chụp ảnh CCCD / GPLX → API `/api/v1/customers/ocr/scan` → Backend OCR Service chuyển ảnh sang PaddleOCR Engine (Self-Hosted) → OpenCV tiền xử lý xoay phẳng thẻ & bóc tách các trường (`id_card_number`, `full_name`, `dob`, `address`, `driver_license_number`) → Trả về dạng JSON cho FE để nhân viên xác nhận/autofill vào form tạo khách hàng.
2. **Luồng Hold & Quản lý đơn xe (Booking & Hold Engine)**:
   - NV/Khách chọn xe + khung giờ (`pickup_date` -> `return_date`).
   - System thực hiện Conflict Check trong PostgreSQL (kiểm tra trùng lịch đặt xe theo `vehicle_id`).
   - Tạo Booking với `status = HOLD` và `hold_expires_at = NOW() + 15m`.
   - Trong 15 phút: NV cập nhật cọc/xác nhận → Chuyển `status = CONFIRMED`.
   - Nếu hết 15 phút chưa xác nhận: `@Scheduled` Task trong Spring Boot (chạy định kỳ 1 phút/lần) tự động nhặt các đơn `HOLD` quá hạn và cập nhật `status = CANCELLED` / giải phóng giữ xe.

### Phần 3: Backend Monolith & Multi-Tenant Architecture
- Cấu trúc Layered Architecture: `Controller` → `Service` → `Repository` → `PostgreSQL`.
- Multi-tenant Strategy: Column-based isolation (`tenant_id` trên mọi bảng nghiệp vụ), kết hợp `TenantContext` & `JwtAuthFilter`.

### Phần 4: Frontend Architecture (Tóm tắt tối giản)
- Angular 17+ Single Page Application (SPA).
- Tóm tắt gọn cấu trúc `core/`, `shared/`, `features/`. Build ra tệp tĩnh `dist/` để Nginx serve trực tiếp. Không phụ thuộc CDN.

### Phần 5: Deployment Architecture (SSH & Systemd Bare-metal)
- Single VPS Linux Host (Ubuntu/Debian).
- `systemd` service: `car-rental.service` tự động khởi chạy và duy trì tiến trình `java -jar backend.jar`.
- Nginx reverse proxy bọc ngoài.
- Quy trình Deploy SSH:
  1. Local Maven build: `mvn clean package -DskipTests` → tạo file `backend-0.0.1-SNAPSHOT.jar`.
  2. SCP/SSH đẩy file `.jar` và FE build lên Server.
  3. Chạy command reload `systemd` service & reload Nginx.

---

## 3. Nội dung cập nhật chi tiết cho file `docs/Architecture-Diagram.md`

Sau khi tài liệu Spec này được chấp thuận, file [Architecture-Diagram.md](file:///f:/backend-training/private-car-rental/docs/Architecture-Diagram.md) sẽ được làm mới toàn bộ theo đúng cấu trúc trên.
