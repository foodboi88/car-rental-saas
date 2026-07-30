# Car Rental SaaS Platform

A multi-tenant SaaS platform for car rental businesses — from single-branch local garages to multi-branch enterprise chains. Provides tenant onboarding, branch/vehicle/booking management, dynamic pricing, payments, and a public booking website.

> **Status:** Phase 0 — Design & Learning. Design documents are complete; source code is in development. See [Roadmap](docs/Roadmap.md).

---

## Features

- **Multi-tenant** — 1 deployment serves many rental companies, with strict `tenant_id` data isolation
- **Multi-branch** — central + satellite branches, with vehicle transfers between them
- **Vehicle management** — CRUD, status tracking (available / rented / maintenance)
- **Booking flow** — create, update, cancel; conflict detection
- **Dynamic pricing** — `BasePrice × DayMultiplier × SeasonMultiplier` (weekday / weekend / holiday / peak season)
- **Subscriptions** — FREE / BASIC / PRO / ENTERPRISE plans with per-plan limits
- **Payments** — cash, bank transfer, e-wallet
- **Reports** — revenue by day / month / branch
- **Notifications** — SMS & email (OTP, confirmations, return reminders)

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.x, Spring Security, Spring Data JPA |
| Frontend | Next.js 14, React 18, TypeScript |
| Database | PostgreSQL (shared, `tenant_id` isolation) |
| Cache / Session | Redis |
| File storage | MinIO / S3 |
| Auth | JWT (access + refresh) |
| Container | Docker, Docker Compose |
| License | MIT |

---

## Repository Layout

```
car-rental-backend/
├── docs/          # Design documents (spec, API, schema, security, roadmap, …)
├── backend/       # Spring Boot application (in development)
├── frontend/      # Next.js 14 application (App Router, in development)
├── docker-compose.yml
├── docker-compose.prod.yml
├── LICENSE
└── README.md
```

The intended directory layout, naming conventions, and module structure are documented in [Project Structure](docs/Project-Structure.md).

---

## Documentation

All design documents live under [`docs/`](docs/):

| Document | Description |
|----------|-------------|
| [Project Info](docs/Project-Info.md) | Scope, audience, modules, success criteria |
| [Roadmap](docs/Roadmap.md) | Phase-by-phase plan and milestones |
| [API Specification](docs/API-Specification.md) | REST endpoints, request/response shapes |
| [Database Schema](docs/Database-Schema.md) | Tables, relationships, indexes |
| [Architecture Diagram](docs/Architecture-Diagram.md) | System, backend, deployment views |
| [Security Design](docs/Security-Design.md) | Auth, JWT, tenant isolation, threats |
| [Project Structure](docs/Project-Structure.md) | Code layout, naming, coding rules |
| [Multi-Tenant & Multi-Branch](docs/Multi-Tenant-Multi-Branch.md) | Tenant & branch model |
| [Subscription Plans](docs/Subscription-Plans.md) | Plan tiers and limits |
| [User Flows](docs/User-Flows.md) | Admin, staff, and customer flows |
| [Learning Progress](docs/Learning-Progress.md) | Team tech-learning tracker |

---

## Quick Start

> Source code is not yet committed. Once Phase 1 lands (target: Week 8), this section will include:
> 1. `docker-compose up` to start PostgreSQL + Redis + backend + frontend
> 2. Default admin credentials
> 3. `localhost:8080` (API) and `localhost:3000` (UI)

For now, see [Roadmap](docs/Roadmap.md) for the build plan.

---

## Team

3-person team, 3 roles — see [Project Info](docs/Project-Info.md#4-thành-phần-tham-gia-dự-án).

| Role | Focus |
|------|-------|
| Backend Lead | Spring Boot APIs, security, database |
| Frontend Lead | Next.js UI, components, pages |
| DevOps / Shared | Docker, CI/CD, infrastructure |

---

## License

[MIT](LICENSE) — Copyright (c) 2026 dinhtuan99


## Lệnh start dự án
```
 & '..\.tools\apache-maven-3.9.6\bin\mvn.cmd' spring-boot:run
```


## Cài và chạy dự án bằng MVN
Bước 1: Copy đường dẫn thư mục Maven
Bạn hãy di chuyển thư mục chứa các file trong ảnh của bạn đến một nơi cố định trên máy tính để tránh lỡ tay xóa mất (Khuyên dùng: lưu vào ổ C hoặc D, ví dụ: C:\Program Files\apache-maven-3.9.6).
Mở thư mục đó ra, click chuột vào thanh địa chỉ ở phía trên cùng của cửa sổ File Explorer để bôi đen toàn bộ đường dẫn (Ví dụ: C:\Program Files\apache-maven-3.9.6).
Nhấn Ctrl + C để copy đường dẫn này.


Bước 2: Mở cửa sổ Biến môi trường (Environment Variables)
Nhấn phím Windows trên bàn phím (hoặc click nút Start ở góc màn hình).
Gõ chữ: env hoặc environment
Chọn dòng "Edit the system environment variables" (Chỉnh sửa các biến môi trường hệ thống).
Click vào nút "Environment Variables..." ở góc dưới bên phải cửa sổ vừa hiện lên.


Bước 3: Tạo biến hệ thống MAVEN_HOME
Tại khung phía dưới có tên là System variables (Biến hệ thống), click vào nút New...
Điền thông tin như sau:
Variable name: MAVEN_HOME
Variable value: Nhấn Ctrl + V để dán đường dẫn bạn đã copy ở Bước 1 vào.
Nhấn OK.


Bước 4: Cấu hình biến Path
Vẫn tại khung System variables đó, cuộn xuống tìm dòng có tên là Path (hoặc PATH).
Click chọn dòng đó và nhấn nút Edit...
Cửa sổ mới hiện ra, click chọn nút New ở bên phải.
Gõ chính xác dòng sau: %MAVEN_HOME%\bin
Nhấn OK cho tất cả các cửa sổ để lưu lại cấu hình.


Bước 5: Kiểm tra xem đã dùng được chưa
Quan trọng: Bạn phải tắt toàn bộ các Terminal, VS Code hoặc Command Prompt đang mở đi (để hệ thống cập nhật cấu hình mới).
Mở một Terminal hoặc VS Code mới lên, di chuyển vào thư mục /backend của dự án và chạy thử lệnh:
cmd
mvn spring-boot:run
Hoặc kiểm tra phiên bản:
cmd
mvn -version


## Chạy image Postgresql qua Docker
```
docker run --name postgres-car-rental `
  -e POSTGRES_DB=car_rental `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=password `
  -p 5432:5432 `
  -d postgres:15-alpine
```

## Link Swagger local
```
http://localhost:8080/swagger-ui/index.html
```


## Reset database khi muốn chuẩn hóa db theo Flyway
Sau khi điều chỉnh lại versioned mới (xóa file cũ và thêm mới), hãy chạy: mvn clean spring-boot:run 
Sau đó reset database:
```
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO public;
```

Rồi mới spring-boot:run lại


## Dừng service java đang chạy
```
Stop-Process -Name java -Force
```


## Lệnh sửa lỗi checksum mỗi khi sửa file Versioned
```
mvn flyway:repair "-Dflyway.url=jdbc:postgresql://localhost:5432/car_rental" "-Dflyway.user=postgres" "-Dflyway.password=password"
```