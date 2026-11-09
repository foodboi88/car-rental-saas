# 🏛️ QUY CHUẨN CẤU TRÚC, CÚ PHÁP & DESIGN PATTERN (CODE CONVENTIONS)
## Dự án: Car Rental SaaS (Backend Java 21 / Spring Boot 3)

> **Mục tiêu:** Đồng bộ hóa 100% phong cách lập trình cho toàn bộ đội ngũ kỹ sư Frontend chuyển dịch sang làm Backend.  
> **Nguyên tắc cốt lõi:** **Tường minh, Trong sáng, Bám sát Java Core, Tránh Trừu tượng hóa Phức tạp (YAGNI & KISS).**

---

## 🧭 MỤC LỤC
1. [Kiến trúc Gói (Package-by-Feature / Modular Monolith)](#1-kiến-trúc-gói-package-by-feature)
2. [Phân tầng Trách nhiệm (Controller - Service - Repository - Entity - DTO)](#2-phân-tầng-trách-nhiệm)
3. [Quy chuẩn Cú pháp & Đặt tên (Naming Conventions)](#3-quy-chuẩn-cú-pháp--đặt-tên)
4. [Design Patterns: Được Phép Dùng vs BỊ CẤM](#4-design-patterns-được-phép-dùng-vs-bị-cấm)
5. [Quy chuẩn Chuyển đổi Dữ liệu (Object Mapping qua Builder)](#5-quy-chuẩn-chuyển-đổi-dữ-liệu-object-mapping)
6. [Chuẩn hóa Phân trang (PageResponse) & API Response](#6-chuẩn-hóa-phân-trang--api-response)
7. [Ba Thiết luật Giao dịch (@Transactional) & Tối ưu HikariCP](#7-ba-thiết-luật-giao-dịch-transactional)
8. [Quy chuẩn Validation & Bắt lỗi (Exception Handling)](#8-quy-chuẩn-validation--bắt-lỗi)
9. [An toàn Đa luồng & Quản lý Bộ nhớ (Thread-Safety & Memory)](#9-an-toàn-đa-luồng--quản-lý-bộ-nhớ)

---

## 1. KIẾN TRÚC GÓI (PACKAGE-BY-FEATURE)

Dự án tổ chức theo **Package-by-Feature (Modular Monolith)**. Mỗi module nghiệp vụ là một "tiểu vương quốc" khép kín, chứa đầy đủ các tầng từ Controller đến Database:

```
com.carrental.car_rental_backend.<module>/
├── controller/                 # Điểm tiếp nhận HTTP Request
│   └── <Module>Controller.java
├── dto/                        # Hợp đồng dữ liệu (Không để lộ Entity)
│   ├── request/                # Các phiếu gửi lên từ Client
│   │   ├── Create<Module>RequestDTO.java
│   │   └── Update<Module>RequestDTO.java
│   └── response/               # Dữ liệu trả về cho Client
│       ├── <Module>ResponseDTO.java
│       └── <Module>DropdownDTO.java
├── entity/                     # Đối tượng tương ứng bảng trong DB
│   └── <Module>.java
├── repository/                 # Giao tiếp DB qua Spring Data JPA
│   └── <Module>Repository.java
└── service/                    # Bộ não nghiệp vụ
    ├── <Module>Service.java    # Interface (Hợp đồng)
    └── impl/
        └── <Module>ServiceImpl.java # Cài đặt thuật toán thực tế
```

> 💡 **Đối chiếu Frontend:** Giống hệt kiến trúc Feature Folders trong Angular hoặc Next.js (`src/features/branch/{components, services, types}`).

---

## 2. PHÂN TẦNG TRÁCH NHIỆM

### 2.1 Controller (Lễ tân tòa nhà)
- **Chỉ làm đúng 3 việc:**
  1. Đón nhận HTTP Request và kích hoạt `@Valid` để kiểm tra định dạng dữ liệu đầu vào.
  2. Ủy quyền trực tiếp cho Service xử lý: `branchService.createBranch(request)`.
  3. Bọc kết quả vào `ApiResponse.success(result)` và trả về HTTP Status phù hợp (`200 OK`, `201 CREATED`).
- **CẤM TUYỆT ĐỐI:**
  - ⛔ Không viết logic nghiệp vụ, tính toán tiền bạc, kiểm tra điều kiện `if/else` tại Controller.
  - ⛔ Không tiêm (`inject`) hoặc gọi trực tiếp `Repository` tại Controller.

### 2.2 Service (Bộ não nghiệp vụ)
- Nơi thực thi 100% logic kinh doanh: kiểm tra phân quyền, cô lập `tenant_id`, validate trùng lặp, tính tiền, gọi Repository.
- Quản lý ranh giới giao dịch CSDL thông qua `@Transactional`.
- Bắt buộc tách thành **`Interface`** và **`Impl`**:
  - `BranchService.java` (Interface): Định nghĩa hợp đồng.
  - `BranchServiceImpl.java` (Class Impl): Cài đặt chi tiết.

### 2.3 Repository (Thủ kho CSDL)
- Chỉ kế thừa `JpaRepository<Entity, UUID>` để thực hiện các câu lệnh `SELECT`, `INSERT`, `UPDATE`, `DELETE`.
- Không chứa logic nghiệp vụ.

### 2.4 Entity vs DTO
- **Entity (`@Entity`):** Tấm gương phản chiếu bảng CSDL vật lý.
- **DTO (`Data Transfer Object`):** Tờ phiếu trao đổi dữ liệu qua mạng.
- **Ranh giới:** **Entity CẤM TUYỆT ĐỐI đi qua Controller để ra ngoài Client.** Mọi dữ liệu trả ra Frontend bắt buộc phải được map sang `ResponseDTO` để che giấu cấu trúc DB nội bộ và bảo vệ các cột nhạy cảm (`password_hash`, `tenant_id`...).

---

## 3. QUY CHUẨN CÚ PHÁP & ĐẶT TÊN

| Thành phần | Quy tắc | Ví dụ chuẩn |
| :--- | :--- | :--- |
| **Package** | `lowercase`, số ít, viết liền | `branch`, `vehicle`, `booking` |
| **Class / Interface** | `PascalCase` | `BranchController`, `BranchService` |
| **Method / Variable** | `camelCase` | `createBranch()`, `activeBranchId` |
| **Constant** | `UPPER_SNAKE_CASE` | `MAX_HOLD_TIMEOUT_MINUTES` |
| **Request DTO** | `[Hành_Động][Tên_Module]RequestDTO` | `CreateBranchRequestDTO`, `UpdateBranchStatusRequestDTO` |
| **Response DTO** | `[Tên_Module]ResponseDTO` | `BranchResponseDTO`, `BranchDropdownDTO` |
| **Repository Method** | Theo chuẩn Spring Data Derived Query | `existsByTenantIdAndNameIgnoreCase()`, `findByIdAndTenantId()` |

---

## 4. DESIGN PATTERNS: ĐƯỢC PHÉP DÙNG VS BỊ CẤM

### ✅ CÁC PATTERN BẮT BUỘC & KHUYÊN DÙNG
1. **Dependency Inversion Principle (Interface + Impl):**
   - Giúp Spring tạo JDK Dynamic Proxy bọc lấy Service khi dùng `@Transactional`. Dễ dàng Mock khi viết Unit Test.
2. **Constructor Injection (`@RequiredArgsConstructor`):**
   - Luôn khai báo dependency dưới dạng `private final`.
   - Lombok tự tạo constructor nạp bean. Giúp các dependency trở nên **bất biến (Immutable)**.
3. **Builder Pattern (`@Builder`):**
   - Dùng để khởi tạo Entity và DTO trong sáng, tránh nhầm lẫn vị trí tham số.
4. **Guard Clauses (Early Return):**
   - Kiểm tra điều kiện sai $\rightarrow$ `throw new AppException(...)` ngay lập tức ở đầu hàm. Tối đa không lồng quá 3 tầng `if/else`.
5. **Static Factory Method:**
   - Dùng cho các DTO bọc chung như `ApiResponse.success(data)`, `ApiResponse.error(...)`. Không lưu trạng thái (Stateless), an toàn đa luồng 100%.

### ⛔ CÁC ANTI-PATTERNS BỊ CẤM HOÀN TOÀN
1. **⛔ CẤM Field Injection (`@Autowired private Xxx xxx;`):**
   - Làm ẩn giấu sự phụ thuộc, dễ gây `NullPointerException` khi test và vi phạm nguyên tắc đóng gói OOP.
2. **⛔ CẤM Generic Base CRUD ảo ma (`BaseServiceImpl<T, ID>`):**
   - Tránh việc trừu tượng hóa cao siêu làm dev mới học không nhìn thấy được câu truy vấn SQL thực tế và luồng chạy của chương trình.
3. **⛔ CẤM nuốt lỗi (`Exception Swallowing`):**
   - Tuyệt đối không dùng `try { ... } catch (Exception e) {}` rỗng hoặc chỉ `e.printStackTrace()`.

---

## 5. QUY CHUẨN CHUYỂN ĐỔI DỮ LIỆU (OBJECT MAPPING)

Dự án thống nhất **Phương án Manual Mapping (Tự viết tay qua Builder hoặc hàm Mapper tĩnh)**, không dùng MapStruct/ModelMapper:

```java
// ✅ CHUẨN: Viết hàm mapper tường minh (hoặc private method trong Service)
public BranchResponseDTO toDTO(Branch entity) {
    if (entity == null) return null;
    return BranchResponseDTO.builder()
        .id(entity.getId())
        .name(entity.getName())
        .address(entity.getAddress())
        .phone(entity.getPhone())
        .email(entity.getEmail())
        .isActive(entity.isActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
}
```
* **Lợi ích:** Dev tận mắt thấy vùng nhớ cấp phát trên Heap, tự do tùy biến che mờ dữ liệu (data masking), stacktrace chỉ thẳng dòng lỗi nếu có `null`.

---

## 6. CHUẨN HÓA PHÂN TRANG & API RESPONSE

Mọi API của hệ thống đều được bọc trong vỏ bọc chuẩn `ApiResponse<T>`.

### 6.1 DTO Phân trang Chuẩn SaaS: `PageResponse<T>`
Tạo tại `common/dto/PageResponse.java` để thay thế đối tượng `Page<T>` nặng nề của Spring Data JPA:

```java
package com.carrental.car_rental_backend.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PageResponse<T> {
    private List<T> items;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean isLast;

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
            .items(page.getContent())
            .pageNumber(page.getNumber())
            .pageSize(page.getSize())
            .totalElements(page.getTotalElements())
            .totalPages(page.getTotalPages())
            .isLast(page.isLast())
            .build();
    }
}
```

---

## 7. BA THIẾT LUẬT GIAO DỊCH (@TRANSACTIONAL)

### 📌 THIẾT LUẬT 1: Đặt đúng tầng
- `@Transactional` **BẮT BUỘC** đặt trên method cụ thể tại tầng **Service Impl**.
- Cấm đặt trên Controller hoặc Repository.

### 📌 THIẾT LUẬT 2: Phân định Read-Only
- Mọi hàm truy vấn dữ liệu (`get...`, `find...`, `list...`) **BẮT BUỘC** khai báo:
  ```java
  @Transactional(readOnly = true)
  ```
- *Ý nghĩa Java Core / Hibernate:* Tắt chế độ Dirty Checking (theo dõi thay đổi entity), tiết kiệm RAM cho Heap và giải phóng Connection HikariCP cực nhanh.

### 📌 THIẾT LUẬT 3: Cấm ôm Connection "buôn chuyện"
- **TUYỆT ĐỐI KHÔNG** gọi API bên thứ ba (gửi Email, gọi cổng thanh toán, upload S3) bên trong `@Transactional`.
- HikariCP pool chỉ có 10 connection. Nếu một luồng giữ connection để chờ mạng 5-10 giây, toàn bộ hệ thống sẽ bị treo cứng (`ConnectionTimeoutException`).

---

## 8. QUY CHUẨN VALIDATION & BẮT LỖI

```
[ Request từ Client ]
         │
         ▼
[ Controller: @Valid ] ── (Sai format) ──► GlobalExceptionHandler (400 Bad Request)
         │ (Đúng format)
         ▼
[ Service: Guard Clause ] ── (Sai nghiệp vụ) ──► throw new AppException(ErrorCode.XYZ)
```

1. **Validation Định dạng (Format):**
   - Dùng Jakarta Bean Validation trên DTO: `@NotBlank`, `@Size`, `@Email`, `@Pattern`, `@NotNull`, `@Min`.
   - Đặt `@Valid` trước tham số Request DTO ở Controller.
2. **Validation Nghiệp vụ (Business Rule):**
   - Viết Guard Clause ở đầu hàm Service.
   - Khi vi phạm: `throw new AppException(ErrorCode.BRANCH_NAME_ALREADY_EXISTS);`
   - Tuyệt đối không trả về null hoặc boolean `false` để biểu thị lỗi nghiệp vụ.

---

## 9. AN TOÀN ĐA LUỒNG & QUẢN LÝ BỘ NHỚ

1. **Service và Controller là Singleton:**
   - Spring chỉ khởi tạo duy nhất 1 Instance của Controller và Service chạy chung cho hàng triệu request.
   - **CẤM:** Không được khai báo biến có trạng thái (stateful instance variables) trong Service/Controller. Mọi biến xử lý phải là biến cục bộ (Local Variables) nằm trên Stack của từng Thread.
2. **Dọn dẹp `ThreadLocal`:**
   - Khi sử dụng `ThreadLocal` (như `TenantContext`), bắt buộc phải gọi `.remove()` trong khối `finally` của Filter/Interceptor sau khi kết thúc request để chống rò rỉ bộ nhớ (Memory Leak) trong Thread Pool của Tomcat.

