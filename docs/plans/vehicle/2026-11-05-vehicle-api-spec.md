# 📋 ĐẶC TẢ API: MODULE LOẠI XE VÀ XE

> **Phạm vi:** 6 API loại xe dưới `/api/v1/vehicle-types` và 6 API xe dưới `/api/v1/vehicles`.
> **Cách dùng:** tra cứu đầu vào, đầu ra, lỗi của từng API. Quy tắc nghiệp vụ, lý do thiết kế và sơ đồ luồng xem [Thiết kế chi tiết](2026-11-05-vehicle-detail-design.md).
> **Nguồn:** viết từ thiết kế, **chưa có code**. Tên biến, thông báo và mã lỗi phải đối chiếu lại với phản hồi thật sau khi triển khai, như đã làm với module tenant.
> **Tra nhanh:** [Loại xe](#2-loại-xe) · [Xe](#3-xe) · Phụ lục: [A. Mã lỗi](#a-mã-lỗi) · [B. Kiểu dữ liệu](#b-kiểu-dữ-liệu) · [C. Đối tượng VehicleType](#c-đối-tượng-vehicletype) · [D. Đối tượng Vehicle](#d-đối-tượng-vehicle)

---

> **Nguồn chuẩn:** hợp đồng API và quy tắc nghiệp vụ theo [Thiết kế chi tiết](2026-11-05-vehicle-detail-design.md); khi các tài liệu khác nhau thì theo file đó.

## 1. TỔNG QUAN

### 1.1 Tổng quan 12 API

| # | Việc | Method | Đường dẫn | Ai gọi | Mã thành công |
| :-: | :--- | :---: | :--- | :--- | :---: |
| LX1 | Tạo loại xe | `POST` | `/api/v1/vehicle-types` | Super Admin | 201 |
| LX2 | Danh sách loại xe | `GET` | `/api/v1/vehicle-types` | Super Admin, có `vehicle:read` | 200 |
| LX3 | Xem một loại xe | `GET` | `/api/v1/vehicle-types/{vehicleTypeId}` | Super Admin, có `vehicle:read` | 200 |
| LX4 | Sửa loại xe | `PUT` | `/api/v1/vehicle-types/{vehicleTypeId}` | Super Admin | 200 |
| LX5 | Bật hoặc tắt loại xe | `PATCH` | `/api/v1/vehicle-types/{vehicleTypeId}/status` | Super Admin | 200 |
| LX6 | Xóa loại xe chưa dùng | `DELETE` | `/api/v1/vehicle-types/{vehicleTypeId}` | Super Admin | 200 |
| XE1 | Thêm xe | `POST` | `/api/v1/vehicles` | Có `vehicle:manage` | 201 |
| XE2 | Danh sách xe | `GET` | `/api/v1/vehicles` | Có `vehicle:read` | 200 |
| XE3 | Xem một xe | `GET` | `/api/v1/vehicles/{vehicleId}` | Có `vehicle:read` | 200 |
| XE4 | Sửa xe | `PUT` | `/api/v1/vehicles/{vehicleId}` | Có `vehicle:manage` | 200 |
| XE5 | Đổi trạng thái xe | `PATCH` | `/api/v1/vehicles/{vehicleId}/status` | Có `vehicle:manage` | 200 |
| XE6 | Xóa mềm xe | `DELETE` | `/api/v1/vehicles/{vehicleId}` | Có `vehicle:manage` | 200 |

### 1.2 Xác thực và phân quyền

- Mọi API cần header `Authorization: Bearer <accessToken>`. Body gửi lên dạng `Content-Type: application/json`.
- Quyền lấy từ vai trò trong nhà xe (bảng `role_permissions`). Với seed hiện tại:

| Vai trò | Loại xe | Xe |
| :--- | :--- | :--- |
| Super Admin | Đọc và ghi | Không gọi được (không thuộc nhà xe, không có mã quyền `vehicle:*`) |
| TENANT_ADMIN | Chỉ đọc loại đang dùng | Đọc và ghi mọi xe của nhà xe |
| STAFF | Chỉ đọc loại đang dùng | Chỉ đọc xe ở chi nhánh đang làm việc |
| SALE | Chỉ đọc loại đang dùng | Chỉ đọc mọi xe của nhà xe (trừ xe đã xóa mềm) |

- API xe cần token **đã chọn nhà xe** (`select-tenant`). Token tạm sau đăng nhập (người thuộc nhiều nhà xe, chưa chọn) nhận 403.
- STAFF phải chọn chi nhánh (`switch-branch`) trước khi xem xe, ngược lại nhận 400.
- Với cả XE2 và XE3, mỗi request của STAFF kiểm tra lại DB theo chi nhánh trong token, tenant và người đã xác thực. Chi nhánh cần thuộc tenant và đang hoạt động, phân công trong `user_branches` cần còn ACTIVE. Chi nhánh bị khóa/xóa hoặc phân công bị gỡ, SUSPENDED, RESIGNED thì trả 403 `AUTH_FORBIDDEN` ngay ở request tiếp theo sau khi thay đổi đã commit, kể cả dùng access token cũ còn hạn. Chi tiết theo XE-R11 trong thiết kế.

### 1.3 Mỗi API đọc và ghi bảng nào

| API | `vehicle_types` | `vehicles` | `branches` | `user_branches` | `bookings` |
| :--- | :--- | :--- | :--- | :--- | :--- |
| LX1. Tạo loại xe | đọc và ghi | | | | |
| LX2, LX3. Xem loại xe | đọc | | | | |
| LX4. Sửa loại xe | đọc và ghi | | | | |
| LX5. Bật hoặc tắt | đọc, ghi nếu đổi | | | | |
| LX6. Xóa loại xe | đọc và xóa | đọc (đếm) | | | |
| XE1. Thêm xe | đọc | đọc và ghi | đọc | | |
| XE2, XE3. Xem xe | đọc | đọc | đọc | đọc nếu STAFF | |
| XE4. Sửa xe | đọc | đọc và ghi | đọc | | |
| XE5. Đổi trạng thái | | đọc, ghi nếu đổi | | | đọc nếu đích là `INACTIVE` |
| XE6. Xóa mềm | | đọc, ghi nếu đổi | | | đọc |

Ô trống nghĩa là API không chạm bảng đó. Các bảng `branches`, `user_branches` và `bookings` được module này đọc để kiểm tra và dựng phản hồi.

### 1.4 Luồng chung của một request

Giống module tenant. Sơ đồ từng API trong [Thiết kế chi tiết, mục 5](2026-11-05-vehicle-detail-design.md#5-đặc-tả-api) chỉ vẽ phần từ Controller trở đi.

```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant F as Security Filter
  participant K as Controller
  participant S as Service
  participant H as GlobalExceptionHandler
  C->>F: request kèm Bearer token
  alt không có token, token sai hoặc hết hạn
    F-->>C: 401 AUTH_UNAUTHORIZED
  end
  F->>F: nạp người dùng, vai trò, mã quyền; nhà xe bị khóa thì 403
  F->>K: chuyển request
  K->>K: kiểm tra định dạng body bằng @Valid
  alt sai định dạng
    K->>H: MethodArgumentNotValidException
    H-->>C: 400 VALIDATION_ERROR kèm lỗi từng trường
  end
  K->>K: @PreAuthorize theo vai trò hoặc mã quyền
  alt không đủ quyền
    K->>H: AccessDeniedException
    H-->>C: 403 AUTH_FORBIDDEN
  end
  K->>S: gọi hàm nghiệp vụ
  alt vi phạm luật nghiệp vụ
    S->>H: AppException kèm ErrorCode
    H-->>C: 400, 404 hoặc 409 theo ErrorCode
  else thành công
    S-->>K: kết quả
    K-->>C: 200 hoặc 201 theo cấu trúc ApiResponse
  end
```

> **Chú ý thứ tự:** kiểm tra định dạng body chạy **trước** kiểm tra quyền, nên người không có quyền gửi body sai nhận 400 chứ chưa phải 403.

---

## 2. LOẠI XE

## LX1. Tạo loại xe: `POST /api/v1/vehicle-types`

**Mô tả:** thêm một loại xe vào danh mục dùng chung. Loại mới ở trạng thái đang dùng. Sơ đồ: [DD, LX1](2026-11-05-vehicle-detail-design.md#lx1-tạo-loại-xe-post-apiv1vehicle-types--201).

### Đầu vào (body)

| Tên biến | Kiểu | Bắt buộc | Ràng buộc | Mô tả |
| :--- | :--- | :-: | :--- | :--- |
| `name` | string | Có | Không rỗng, tối đa 50 ký tự | Tên loại xe. Bỏ khoảng trắng hai đầu trước khi lưu. Không trùng loại khác, không phân biệt hoa thường |
| `description` | string | Không | Tối đa 1000 ký tự | Mô tả |

Ví dụ: `{ "name": "SUV 7 chỗ", "description": "Xe gầm cao 7 chỗ" }`

LX1 và LX4 yêu cầu migration tạo unique index `idx_vehicle_types_normalized_name` trên `UPPER(BTRIM(name))` được áp dụng trước khi triển khai. Index bảo đảm tên duy nhất cả khi hai request ghi đồng thời, theo mục 2 của thiết kế chi tiết.

### Đầu ra: `201 Created`
`data` là đối tượng **VehicleType** (Phụ lục C), `isActive = true`. `message`: "Tạo loại xe thành công !"

### Lỗi
| HTTP | `error.code` | Khi nào | Thông báo |
| :-: | :--- | :--- | :--- |
| 400 | `VALIDATION_ERROR` | Thiếu tên, tên quá dài, mô tả quá dài | Theo từng trường |
| 403 | `AUTH_FORBIDDEN` | Không phải Super Admin | |
| 409 | `VEHICLE_TYPE_NAME_ALREADY_EXISTS` | Đã có loại xe cùng tên (kể cả khác hoa thường) | "Tên loại xe đã tồn tại" |

---

## LX2. Danh sách loại xe: `GET /api/v1/vehicle-types`

**Mô tả:** liệt kê loại xe, có tìm kiếm và phân trang. Giao diện dùng làm danh sách chọn khi thêm xe. Sắp xếp theo **tên tăng dần**, không cho chọn cách sắp xếp.

### Đầu vào (query string)

| Tên biến | Kiểu | Bắt buộc | Mặc định | Mô tả |
| :--- | :--- | :-: | :--- | :--- |
| `keyword` | string | Không | (tìm tất cả) | Tìm trong tên, không phân biệt hoa thường |
| `isActive` | boolean | Không | (không lọc) | Chỉ có tác dụng với Super Admin. Người của nhà xe luôn chỉ nhận loại đang dùng |
| `page` | integer | Không | `0` | Số trang, bắt đầu từ 0. Âm thì đổi thành 0 |
| `size` | integer | Không | `20` | Số dòng mỗi trang, bị kẹp trong khoảng 1 đến 100 |

Ví dụ: `GET /api/v1/vehicle-types?keyword=suv&page=0&size=20`

### Đầu ra: `200 OK`

| Tên biến | Kiểu | Mô tả |
| :--- | :--- | :--- |
| `data.content` | array of VehicleType | Các loại xe của trang hiện tại |
| `data.page.size`, `data.page.number`, `data.page.totalElements`, `data.page.totalPages` | integer | Như module tenant |

`message`: "Lấy danh sách loại xe thành công !"

### Lỗi
401, 403 (Phụ lục A). Tham số query sai kiểu (ví dụ `isActive=abc`) trả 400 `ERR_400`.

---

## LX3. Xem một loại xe: `GET /api/v1/vehicle-types/{vehicleTypeId}`

### Đầu vào (đường dẫn)
| Tên biến | Kiểu | Bắt buộc | Mô tả |
| :--- | :--- | :-: | :--- |
| `vehicleTypeId` | uuid | Có | Mã loại xe |

### Đầu ra: `200 OK`
`data` là **VehicleType**. `message`: "Lấy thông tin loại xe thành công !"

### Lỗi
| HTTP | `error.code` | Khi nào |
| :-: | :--- | :--- |
| 404 | `ERR_404` | Không có loại xe, hoặc người của nhà xe xem loại đã tắt |
| 400 | `ERR_400` | `vehicleTypeId` không phải UUID |

---

## LX4. Sửa loại xe: `PUT /api/v1/vehicle-types/{vehicleTypeId}`

**Mô tả:** đổi tên hoặc mô tả. Không đổi trạng thái (dùng LX5). Sơ đồ: [DD, LX4](2026-11-05-vehicle-detail-design.md#lx4-sửa-loại-xe-put-apiv1vehicle-typesvehicletypeid--200).

### Đầu vào (đường dẫn và body)

| Tên biến | Kiểu | Bắt buộc | Ràng buộc | Mô tả |
| :--- | :--- | :-: | :--- | :--- |
| `vehicleTypeId` (đường dẫn) | uuid | Có | | Mã loại xe |
| `name` | string | Có | Như LX1 | Luôn ghi đè. Không trùng loại **khác** |
| `description` | string | Không | Tối đa 1000 ký tự | Không gửi hoặc `null`: giữ nguyên · chuỗi rỗng: xóa · có giá trị: ghi đè |

### Đầu ra: `200 OK`
`data` là **VehicleType** sau khi sửa. `message`: "Cập nhật loại xe thành công !"

### Lỗi
| HTTP | `error.code` | Khi nào | Thông báo |
| :-: | :--- | :--- | :--- |
| 400 | `VALIDATION_ERROR` | Dữ liệu không hợp lệ | Theo từng trường |
| 404 | `ERR_404` | Không có loại xe | "Không tìm thấy loại xe !" |
| 409 | `VEHICLE_TYPE_NAME_ALREADY_EXISTS` | Tên trùng loại xe khác | "Tên loại xe đã tồn tại" |

---

## LX5. Bật hoặc tắt loại xe: `PATCH /api/v1/vehicle-types/{vehicleTypeId}/status`

**Mô tả:** tắt để không cho chọn loại này khi thêm xe hoặc đổi loại xe. Xe đang dùng loại này **không bị ảnh hưởng**.

### Đầu vào
| Tên biến | Kiểu | Bắt buộc | Mô tả |
| :--- | :--- | :-: | :--- |
| `vehicleTypeId` (đường dẫn) | uuid | Có | Mã loại xe |
| `isActive` (body) | boolean | Có | `false` để tắt, `true` để bật |

Ví dụ: `{ "isActive": false }`

### Đầu ra: `200 OK`
`data` là **VehicleType** với `isActive` mới. `message`: "Tắt loại xe thành công !" hoặc "Bật loại xe thành công !". **Gọi lại cùng giá trị vẫn thành công**, không đổi `updatedAt`.

### Lỗi
| HTTP | `error.code` | Khi nào |
| :-: | :--- | :--- |
| 400 | `VALIDATION_ERROR` | Thiếu `isActive` |
| 404 | `ERR_404` | Không có loại xe |

---

## LX6. Xóa loại xe: `DELETE /api/v1/vehicle-types/{vehicleTypeId}`

**Mô tả:** **xóa thật**, chỉ khi chưa có xe nào dùng loại này ở bất kỳ nhà xe nào (kể cả xe đã xóa mềm). Loại đã có xe thì tắt bằng LX5. Sơ đồ: [DD, LX6](2026-11-05-vehicle-detail-design.md#lx6-xóa-loại-xe-delete-apiv1vehicle-typesvehicletypeid--200).

### Đầu ra: `200 OK`
Không có `data`. `message`: "Xóa loại xe thành công !"

### Lỗi
| HTTP | `error.code` | Khi nào | Thông báo |
| :-: | :--- | :--- | :--- |
| 404 | `ERR_404` | Không có loại xe (kể cả đã xóa trước đó) | "Không tìm thấy loại xe !" |
| 409 | `VEHICLE_TYPE_IN_USE` | Còn xe dùng loại này | "Loại xe đang được {n} xe sử dụng, không thể xóa. Hãy tắt loại xe thay vì xóa !" |

Khác LX5 và XE6: xóa lần hai trả **404**, vì dòng đã bị xóa thật.

---

## 3. XE

## XE1. Thêm xe: `POST /api/v1/vehicles`

**Mô tả:** khai báo một xe mới cho nhà xe của người gọi. Nhà xe lấy từ token, **không** gửi trong body. Xe mới luôn ở `AVAILABLE`. Sơ đồ: [DD, XE1](2026-11-05-vehicle-detail-design.md#xe1-thêm-xe-post-apiv1vehicles--201).

### Đầu vào (body)

| Tên biến | Kiểu | Bắt buộc | Ràng buộc | Mô tả |
| :--- | :--- | :-: | :--- | :--- |
| `licensePlate` | string | Có | Không rỗng, tối đa 20 ký tự; chỉ chữ, số, `.`, `-`, khoảng trắng | Biển số. Lưu dạng chữ in hoa, bỏ khoảng trắng hai đầu. Không trùng xe khác **trong cùng nhà xe** |
| `vehicleTypeId` | uuid | Có | Loại xe tồn tại và đang dùng | Loại xe |
| `branchId` | uuid | Có | Chi nhánh thuộc nhà xe và đang hoạt động | Chi nhánh quản lý xe |
| `pricePerDay` | money | Có | Lớn hơn 0, tối đa 10 chữ số phần nguyên, 2 chữ số thập phân | Giá thuê ngày thường (đồng/ngày) |
| `weekendPricePerDay` | money | Không | Như `pricePerDay` | Giá thuê cuối tuần |
| `model` | string | Không | Tối đa 100 ký tự | Dòng xe, ví dụ `Toyota Vios` |
| `color` | string | Không | Tối đa 30 ký tự | Màu sơn |
| `year` | integer | Không | Từ 1990 đến năm hiện tại + 1 | Năm sản xuất |
| `parkingLocation` | string | Không | Tối đa 500 ký tự | Vị trí đỗ thực tế |
| `inspectionExpiryDate` | date | Không | `YYYY-MM-DD` | Hạn đăng kiểm |
| `insuranceExpiryDate` | date | Không | `YYYY-MM-DD` | Hạn bảo hiểm |
| `description` | string | Không | Tối đa 2000 ký tự | Mô tả tình trạng |
| `images` | array of string | Không | Tối đa 5 phần tử; mỗi phần tử không rỗng, tối đa 500 ký tự, bắt đầu bằng `http://` hoặc `https://` | Địa chỉ ảnh đã tải lên nơi khác |

**Ví dụ yêu cầu:**
```json
{
  "licensePlate": "30f-123.45",
  "vehicleTypeId": "316f33f4-01c0-4fec-95c1-052421cece79",
  "branchId": "33b5b9fe-c6bc-42fa-978e-8e0ea092dc6a",
  "pricePerDay": 800000,
  "weekendPricePerDay": 950000,
  "model": "Mazda 3",
  "color": "Đỏ",
  "year": 2023,
  "inspectionExpiryDate": "2027-06-30",
  "images": ["https://s3.example.com/xe/mazda3-1.jpg"]
}
```

### Đầu ra: `201 Created`
`data` là đối tượng **Vehicle** (Phụ lục D), `status = AVAILABLE`, `licensePlate = "30F-123.45"`. `message`: "Thêm xe thành công !"

### Lỗi
| HTTP | `error.code` | Khi nào | Thông báo |
| :-: | :--- | :--- | :--- |
| 400 | `VALIDATION_ERROR` | Thiếu trường bắt buộc, sai độ dài, giá ≤ 0, quá 5 ảnh, URL ảnh sai | Theo từng trường; phần tử ảnh có dạng `images[0]` |
| 400 | `MALFORMED_JSON` | Ngày sai định dạng, UUID sai | "Dữ liệu JSON gửi lên không đúng định dạng" |
| 400 | `ERR_400` | Năm sản xuất ngoài khoảng | "Năm sản xuất không hợp lệ !" |
| 400 | `ERR_400` | Chi nhánh không có hoặc của nhà xe khác | "Không tìm thấy chi nhánh hoặc chi nhánh không thuộc nhà xe !" |
| 400 | `ERR_400` | Chi nhánh đang tạm ngưng | "Chi nhánh đang tạm ngưng hoạt động !" |
| 400 | `ERR_400` | Loại xe không có | "Không tìm thấy loại xe !" |
| 400 | `ERR_400` | Loại xe đã tắt | "Loại xe đang ngừng sử dụng !" |
| 403 | `AUTH_FORBIDDEN` | Không có quyền `vehicle:manage` | |
| 409 | `VEHICLE_LICENSE_PLATE_ALREADY_EXISTS` | Biển số đã có trong nhà xe (kể cả xe đã xóa mềm) | "Biển số đã tồn tại trong nhà xe" |

Chi nhánh và loại xe không có trả **400** chứ không phải 404, vì đây là giá trị tham chiếu trong body, không phải tài nguyên trên đường dẫn.

### Dữ liệu ghi
`vehicles` (1 dòng).

---

## XE2. Danh sách xe: `GET /api/v1/vehicles`

**Mô tả:** liệt kê xe trong phạm vi của người gọi (mục 1.2), có tìm kiếm, lọc và phân trang. Sắp xếp theo **biển số tăng dần**, không cho chọn cách sắp xếp. Sơ đồ: [DD, XE2](2026-11-05-vehicle-detail-design.md#xe2-danh-sách-xe-get-apiv1vehicles--200).

### Đầu vào (query string)

| Tên biến | Kiểu | Bắt buộc | Mặc định | Mô tả |
| :--- | :--- | :-: | :--- | :--- |
| `keyword` | string | Không | (tìm tất cả) | Tìm trong biển số hoặc dòng xe, không phân biệt hoa thường |
| `status` | VehicleStatus | Không | (không lọc) | Lọc một trạng thái. Người không phải chủ lọc `INACTIVE` nhận trang rỗng |
| `branchId` | uuid | Không | (không lọc) | Lọc theo chi nhánh. STAFF bị bỏ qua, luôn là chi nhánh đang làm việc |
| `vehicleTypeId` | uuid | Không | (không lọc) | Lọc theo loại xe |
| `page` | integer | Không | `0` | Âm thì đổi thành 0 |
| `size` | integer | Không | `10` | Bị kẹp trong khoảng 1 đến 100 |

Ví dụ: `GET /api/v1/vehicles?keyword=vios&status=AVAILABLE&page=0&size=10`

**Mặc định không lọc trạng thái:** chủ nhà xe thấy cả xe đã xóa mềm; STAFF, SALE không thấy.

### Đầu ra: `200 OK`

| Tên biến | Kiểu | Mô tả |
| :--- | :--- | :--- |
| `data.content` | array of Vehicle | Các xe của trang hiện tại |
| `data.page.*` | integer | Như module tenant |

`message`: "Lấy danh sách xe thành công !"

### Lỗi
| HTTP | `error.code` | Khi nào | Thông báo |
| :-: | :--- | :--- | :--- |
| 400 | `ERR_400` | Tham số sai kiểu (`status=GOLD`, `branchId=abc`) | Thông báo chung về kiểu tham số |
| 400 | `ERR_400` | STAFF chưa chọn chi nhánh làm việc | "Vui lòng chọn chi nhánh làm việc trước !" |
| 403 | `AUTH_FORBIDDEN` | Không có `vehicle:read`, hoặc token chưa chọn nhà xe | |
| 403 | `AUTH_FORBIDDEN` | STAFF dùng token có chi nhánh đã mất, thuộc tenant khác hoặc đang bị khóa | "Chi nhánh của bạn không còn tồn tại hoặc đã bị ngưng hoạt động" |
| 403 | `AUTH_FORBIDDEN` | Phân công STAFF trong chi nhánh đã bị gỡ | "Tài khoản của bạn không được phân công vào chi nhánh này !" |
| 403 | `AUTH_FORBIDDEN` | Phân công STAFF ở trạng thái SUSPENDED, RESIGNED hoặc null | "Tài khoản của bạn đã bị thu hồi quyền nhân viên ở chi nhánh này" |

---

## XE3. Xem một xe: `GET /api/v1/vehicles/{vehicleId}`

### Đầu vào (đường dẫn)
| Tên biến | Kiểu | Bắt buộc | Mô tả |
| :--- | :--- | :-: | :--- |
| `vehicleId` | uuid | Có | Mã xe |

### Đầu ra: `200 OK`
`data` là **Vehicle**. `message`: "Lấy thông tin xe thành công !"

### Lỗi
| HTTP | `error.code` | Khi nào |
| :-: | :--- | :--- |
| 404 | `ERR_404` | Không có xe, xe của nhà xe khác, xe ở chi nhánh khác (STAFF), xe đã xóa mềm (người không phải chủ). Cùng một câu "Không tìm thấy xe !" để không lộ xe có tồn tại hay không |
| 400 | `ERR_400` | `vehicleId` không phải UUID; STAFF chưa chọn chi nhánh |
| 403 | `AUTH_FORBIDDEN` | STAFF dùng token có chi nhánh hoặc phân công hiện tại không còn hợp lệ theo XE-R11. Điều kiện và thông báo giống XE2 |

XE3 kiểm tra quyền chi nhánh hiện tại trước khi truy vấn xe. Khi guard đạt, xe nằm ngoài phạm vi tiếp tục nhận 404.

---

## XE4. Sửa xe: `PUT /api/v1/vehicles/{vehicleId}`

**Mô tả:** sửa thông tin xe. **Ghi đè toàn bộ form**: trường tùy chọn không gửi hoặc `null` sẽ thành rỗng. Không đổi trạng thái (dùng XE5). Sơ đồ: [DD, XE4](2026-11-05-vehicle-detail-design.md#xe4-sửa-xe-put-apiv1vehiclesvehicleid--200).

> **Khác module tenant:** sửa nhà xe giữ nguyên trường `null`. Sửa xe thì không, vì form gửi đủ mọi trường và nhiều trường không phải chuỗi (giá, ngày, năm) nên không dùng được quy ước "chuỗi rỗng để xóa".

### Đầu vào (đường dẫn và body)
`vehicleId` (đường dẫn, uuid). Body giống XE1, cùng ràng buộc.

| Trường | Kiểm tra thêm khi sửa |
| :--- | :--- |
| `licensePlate` | Đổi thì không được trùng xe **khác** trong nhà xe. Giữ nguyên thì không kiểm tra |
| `branchId` | Đổi thì chi nhánh mới phải thuộc nhà xe và đang hoạt động; xe đang `RENTED` không đổi được chi nhánh |
| `vehicleTypeId` | Đổi thì loại mới phải đang dùng. Giữ nguyên loại đã tắt thì vẫn sửa được |
| `images` | Không gửi thì thành mảng rỗng |

### Đầu ra: `200 OK`
`data` là **Vehicle** sau khi sửa. `createdAt` giữ nguyên, `updatedAt` đổi. `message`: "Cập nhật xe thành công !"

### Lỗi
Các lỗi của XE1, cộng:

| HTTP | `error.code` | Khi nào | Thông báo |
| :-: | :--- | :--- | :--- |
| 404 | `ERR_404` | Không có xe trong nhà xe | "Không tìm thấy xe !" |
| 409 | `VEHICLE_IN_USE` | Đổi chi nhánh khi xe đang thuê | "Xe đang được thuê hoặc còn đơn đặt chưa hoàn tất !" |

### Dữ liệu ghi
`vehicles` (1 dòng).

---

## XE5. Đổi trạng thái xe: `PATCH /api/v1/vehicles/{vehicleId}/status`

**Mô tả:** chuyển trạng thái bằng tay theo bảng dưới (bảng đầy đủ ở [DD, mục 3.2](2026-11-05-vehicle-detail-design.md#32-chuyển-trạng-thái-bằng-tay-xe-r7)). Trạng thái `RENTED` do module booking đặt và gỡ.

| Từ \ Sang | `AVAILABLE` | `RENTED` | `MAINTENANCE` | `TRANSFERRED` | `INACTIVE` |
| :--- | :-: | :-: | :-: | :-: | :-: |
| `AVAILABLE`, `MAINTENANCE`, `TRANSFERRED` | ✓ | 400 | ✓ | ✓ | ✓ nếu không còn đơn chưa xong |
| `INACTIVE` | ✓ | 400 | ✓ | ✓ | (giữ nguyên) |
| `RENTED` | 409 | (giữ nguyên) | 409 | 409 | 409 |

Gửi lại trạng thái đang có được xử lý trước mọi kiểm tra chuyển trạng thái, trả 200 và giữ `updatedAt`, gồm cả `RENTED → RENTED`. Từ trạng thái khác sang RENTED trả 400. Từ RENTED sang trạng thái khác trả 409. Trường hợp giữ nguyên trả ngay trước bước đếm booking và ghi dữ liệu.

### Đầu vào
| Tên biến | Kiểu | Bắt buộc | Mô tả |
| :--- | :--- | :-: | :--- |
| `vehicleId` (đường dẫn) | uuid | Có | Mã xe |
| `status` (body) | VehicleStatus | Có | Trạng thái mới |

Ví dụ: `{ "status": "MAINTENANCE" }`

### Đầu ra: `200 OK`
`data` là **Vehicle** với trạng thái mới. `message`: "Cập nhật trạng thái xe thành công !". **Gửi lại trạng thái đang có vẫn thành công**, không đổi `updatedAt`.

### Lỗi
| HTTP | `error.code` | Khi nào | Thông báo |
| :-: | :--- | :--- | :--- |
| 400 | `VALIDATION_ERROR` | Thiếu `status` | Theo trường |
| 400 | `MALFORMED_JSON` | `status` ngoài 5 giá trị | |
| 400 | `ERR_400` | Đích là `RENTED` và trạng thái hiện tại khác `RENTED` | "Trạng thái Đang thuê do đơn thuê cập nhật !" |
| 404 | `ERR_404` | Không có xe trong nhà xe | "Không tìm thấy xe !" |
| 409 | `VEHICLE_IN_USE` | Xe đang `RENTED` và đích khác `RENTED`; hoặc thực sự chuyển sang `INACTIVE` khi còn đơn HOLD, CONFIRMED, HANDED_OVER | "Xe đang được thuê hoặc còn đơn đặt chưa hoàn tất !" |

---

## XE6. Xóa mềm xe: `DELETE /api/v1/vehicles/{vehicleId}`

**Mô tả:** **không xóa dòng nào**, chỉ chuyển `status = INACTIVE` (giống XE5 với `INACTIVE`). Lý do: `bookings.vehicle_id` có `ON DELETE SET NULL`, xóa thật sẽ làm đơn cũ mất thông tin xe.

### Đầu vào
| Tên biến | Kiểu | Bắt buộc | Mô tả |
| :--- | :--- | :-: | :--- |
| `vehicleId` (đường dẫn) | uuid | Có | Mã xe |

### Đầu ra: `200 OK`
Không có `data`. `message`: "Đã ngừng khai thác xe (xóa mềm) thành công !". Gọi lần hai vẫn thành công. Mở lại bằng XE5 với `AVAILABLE`.

### Lỗi
| HTTP | `error.code` | Khi nào |
| :-: | :--- | :--- |
| 404 | `ERR_404` | Không có xe trong nhà xe |
| 409 | `VEHICLE_IN_USE` | Xe đang thuê hoặc còn đơn chưa xong |

---

## 4. GIỚI HẠN ĐÃ BIẾT

- Tên loại xe được bảo vệ bởi unique index `UPPER(BTRIM(name))` của migration mới, bao gồm các tên chỉ khác hoa thường hoặc khoảng trắng hai đầu. Biển số có UNIQUE theo tenant. Trùng được Service phát hiện sớm trả 409; khi hai request đã cùng qua bước kiểm tra, lỗi UNIQUE ở bước ghi rollback thao tác thất bại và hiện trả 500 `ERR_500`, như module tenant.
- Biển số chỉ kiểm tra ký tự cho phép, chưa kiểm tra mẫu biển số Việt Nam: `29-101.85` và `29-10185` là hai biển khác nhau.
- Chưa có quota số xe, upload ảnh, số xe theo loại, thống kê theo trạng thái. Xem [DD, mục 7](2026-11-05-vehicle-detail-design.md#7-giới-hạn-đã-biết-và-việc-cần-quyết-định).

---

## PHỤ LỤC

Cấu trúc phản hồi chung (`success`, `data`, `message`, `error`, `timestamp`) giống [Phụ lục A của đặc tả API tenant](../tenant/2026-10-19-tenant-api-spec.md#a-cấu-trúc-phản-hồi-chung).

### A. Mã lỗi

| HTTP | `error.code` | Khi nào | Ghi chú |
| :-: | :--- | :--- | :--- |
| 401 | `AUTH_UNAUTHORIZED` | Không có token, token sai hoặc hết hạn | |
| 403 | `AUTH_FORBIDDEN` | Không đủ quyền; token chưa chọn nhà xe; nhà xe bị khóa; STAFF xem xe với chi nhánh hoặc phân công không còn hợp lệ | |
| 400 | `VALIDATION_ERROR` | Body không hợp lệ | `data` là `{ tên trường: thông báo }` |
| 400 | `MALFORMED_JSON` | Body không phải JSON; enum, ngày hoặc UUID sai trong body | |
| 400 | `ERR_400` | Vi phạm luật nghiệp vụ; tham số đường dẫn/query sai kiểu | Thông báo ghi ở từng API |
| 404 | `ERR_404` | Không có loại xe hoặc xe | |
| 409 | `VEHICLE_TYPE_NAME_ALREADY_EXISTS` | Trùng tên loại xe | **Mã mới** |
| 409 | `VEHICLE_TYPE_IN_USE` | Xóa loại xe đang có xe dùng | **Mã mới** |
| 409 | `VEHICLE_LICENSE_PLATE_ALREADY_EXISTS` | Trùng biển số trong nhà xe | **Mã mới** |
| 409 | `VEHICLE_IN_USE` | Xe đang thuê hoặc còn đơn chưa xong | **Mã mới** |
| 500 | `ERR_500` | Lỗi hệ thống, gồm UNIQUE khi request đồng thời | |

### B. Kiểu dữ liệu

| Kiểu | Định dạng | Ví dụ |
| :--- | :--- | :--- |
| `uuid` | Chuỗi UUID | `"316f33f4-01c0-4fec-95c1-052421cece79"` |
| `instant` | ISO-8601, giờ UTC | `"2026-11-05T03:00:00.123456Z"` |
| `date` | `YYYY-MM-DD` | `"2027-06-30"` |
| `money` | Số, tối đa 2 chữ số thập phân | `800000` |
| `VehicleStatus` | Một trong: `AVAILABLE`, `RENTED`, `MAINTENANCE`, `TRANSFERRED`, `INACTIVE` | `"AVAILABLE"` |

### C. Đối tượng `VehicleType`

| Tên biến | Kiểu | Mô tả |
| :--- | :--- | :--- |
| `id` | uuid | Mã loại xe |
| `name` | string | Tên loại xe |
| `description` | string hoặc null | Mô tả |
| `isActive` | boolean | `true` đang dùng, `false` đã tắt |
| `createdAt`, `updatedAt` | instant | |

```json
{ "id": "316f33f4-01c0-4fec-95c1-052421cece79", "name": "Sedan", "description": "Xe 4 chỗ", "isActive": true, "createdAt": "2026-11-05T03:00:00.123456Z", "updatedAt": "2026-11-05T03:00:00.123456Z" }
```

### D. Đối tượng `Vehicle`

| Tên biến | Kiểu | Mô tả |
| :--- | :--- | :--- |
| `id` | uuid | Mã xe |
| `licensePlate` | string | Biển số, chữ in hoa |
| `status` | VehicleStatus | Trạng thái |
| `vehicleTypeId` | uuid | Mã loại xe |
| `vehicleTypeName` | string | Tên loại xe |
| `branchId` | uuid hoặc null | Mã chi nhánh; `null` khi chi nhánh đã bị xóa |
| `branchName` | string hoặc null | Tên chi nhánh |
| `model` | string hoặc null | Dòng xe |
| `color` | string hoặc null | Màu |
| `year` | integer hoặc null | Năm sản xuất |
| `pricePerDay` | money | Giá ngày thường |
| `weekendPricePerDay` | money hoặc null | Giá cuối tuần |
| `parkingLocation` | string hoặc null | Vị trí đỗ |
| `inspectionExpiryDate` | date hoặc null | Hạn đăng kiểm |
| `insuranceExpiryDate` | date hoặc null | Hạn bảo hiểm |
| `description` | string hoặc null | Mô tả tình trạng |
| `images` | array of string | Địa chỉ ảnh, mảng rỗng nếu chưa có |
| `createdAt`, `updatedAt` | instant | |

```json
{
  "id": "83b96f8d-aba4-4efa-b388-7a35d36afb0d",
  "licensePlate": "29-101.85",
  "status": "AVAILABLE",
  "vehicleTypeId": "316f33f4-01c0-4fec-95c1-052421cece79",
  "vehicleTypeName": "Sedan",
  "branchId": "33b5b9fe-c6bc-42fa-978e-8e0ea092dc6a",
  "branchName": "Central Hà Nội",
  "model": "Toyota Vios",
  "color": "Đen",
  "year": 2021,
  "pricePerDay": 0,
  "weekendPricePerDay": null,
  "parkingLocation": null,
  "inspectionExpiryDate": null,
  "insuranceExpiryDate": null,
  "description": "Xe demo cho môi trường phát triển",
  "images": ["https://s3.example.com/.../vehicle_1_1.jpg"],
  "createdAt": "2026-11-05T03:00:00.123456Z",
  "updatedAt": "2026-11-05T03:00:00.123456Z"
}
```

Ví dụ trên là xe seed V2. `pricePerDay` của 60 xe seed đang là `0` vì V4 thêm cột với DEFAULT 0; XE1 và XE4 chỉ chặn giá ≤ 0 khi ghi mới, xe seed vẫn đọc được.
