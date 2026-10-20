# KẾ HOẠCH FRONTEND: MÀN HÌNH AUTH

> **Trạng thái:** đã dựng và chạy thử với backend thật (19/10/2026). Quyết định đã chốt và kết quả chạy thử nằm ở mục 7 và mục 8.
> **Nhánh:** `feature/huynq7-frontend`, tách từ `feature/dynamic-rbac`. **Chưa gộp các nhánh khác:** lần này chỉ kiểm tra phần auth trên nền `dynamic-rbac`.
> **Mục đích:** dựng frontend cho module auth trước để xem luồng đăng nhập có ổn không. Các module khác (nhà xe, đơn thuê...) sẽ nối vào sau để thông luồng.
> **Nguồn:** đọc từ code `AuthController`, `AuthServiceImpl` và các DTO của nhánh `dynamic-rbac`, cùng `docs/overviews/onboarding.md` mục 7.1 (quy chuẩn giao diện).

---

## 1. Phạm vi

**Có làm:** đăng nhập, chọn nhà xe, chọn chi nhánh, làm mới phiên, đăng xuất, và một trang chủ hiển thị thông tin phiên (người dùng, vai trò, quyền, nhà xe, chi nhánh). Kèm khung ứng dụng dùng chung (header, menu bên trái theo quyền) và giao diện sáng/tối. Từ 20/10/2026 có thêm **quản lý nhà xe cho Super Admin** (xem mục 9).

**Chưa làm:** chi nhánh, người dùng, xe, khách, đơn thuê. Module chi nhánh và người dùng có API ở `feature/giangdt`, chưa gộp. Xe, khách, đơn thuê thì backend chưa có API.

## 2. Công nghệ và cấu trúc

| Hạng mục | Lựa chọn | Ghi chú |
| :--- | :--- | :--- |
| Framework | Angular 21 LTS (component độc lập, không dùng `NgModule`, không dùng zone.js), test bằng Vitest | Phiên bản: xem mục 7 |
| Quản lý trạng thái | `signal` | Phiên làm việc nằm trong một service |
| Thư mục | `frontend/` ở gốc repo, cạnh `backend/` và `docs/` | |
| Giao diện | Theo quy chuẩn prototype: xanh `#3B82F6`, hổ phách `#F59E0B`, nền slate `#F8FAFC`, thẻ trắng, bo góc 12px, font Outfit. Mobile-first và responsive | Quy tắc cụ thể ngay bên dưới |

Cấu trúc gợi ý trong `frontend/src/app/`:

| Thư mục | Nội dung |
| :--- | :--- |
| `core/auth/` | `AuthService` (giữ phiên), bộ chặn HTTP, các hàm bảo vệ route |
| `core/navigation/` | Danh sách mục menu theo quyền (`nav-items.ts`) |
| `core/theme/` | `ThemeService`: giao diện sáng/tối, nhớ lựa chọn |
| `layout/app-shell/` | Khung chung sau đăng nhập: header, menu bên trái (ngăn kéo trên màn nhỏ), vùng nội dung |
| `shared/` | Biểu tượng SVG, nút đổi sáng/tối |
| `core/models/` | Kiểu dữ liệu khớp với DTO của backend |
| `features/login/` | Màn đăng nhập |
| `features/select-tenant/` | Màn chọn nhà xe |
| `features/select-branch/` | Màn chọn chi nhánh |
| `features/home/` | Trang chủ hiển thị phiên |

### Giao diện: mobile-first và responsive
Viết kiểu dáng cho **màn hình nhỏ trước**, rồi mới thêm quy tắc cho màn hình lớn (đúng hướng của `onboarding.md` mục 7: tối ưu cho mobile web).

| Hạng mục | Quy tắc |
| :--- | :--- |
| Bố cục | Một cột, thẻ đầy chiều ngang trên điện thoại. Từ khoảng 768px trở lên, thẻ nằm giữa màn hình, rộng tối đa khoảng 420px, nền slate |
| Vùng chạm | Nút và ô nhập cao tối thiểu 44px |
| Chữ | Cỡ chữ ô nhập tối thiểu 16px |
| Ô nhập | `type="email"` cho email, `type="password"` cho mật khẩu, kèm `autocomplete` đúng |
| Khoảng đệm | Lề hai bên tối thiểu 16px, tôn trọng vùng an toàn của điện thoại có tai thỏ (`env(safe-area-inset-*)`) |
| Danh sách chọn nhà xe, chi nhánh | Thẻ lớn bấm được cả khối, không dùng ô chọn nhỏ |
| Kiểm tra | Xem ở chiều rộng 360px, 390px, 768px và máy tính bằng chế độ giả lập thiết bị của Chrome |

## 3. Hợp đồng API auth (backend `dynamic-rbac`)

> **Lưu ý:** hợp đồng dưới đây viết theo nhánh `dynamic-rbac`. Nhánh `feature/giangdt` có sửa `AuthServiceImpl` và `UserPrincipal` (hai file `dynamic-rbac` cũng sửa), nên **sau khi gộp phải đối chiếu lại**.

Tiền tố `/api/v1/auth`. Mọi phản hồi theo cấu trúc chung: thành công `{ success, data, message, timestamp }`, lỗi `{ success: false, error: { code, message }, timestamp }`. Lỗi kiểm tra dạng dữ liệu trả HTTP 400 mã `VALIDATION_ERROR`, và `data` là bảng tên trường với thông báo.

| API | Cần token | Body | Ghi chú |
| :--- | :-: | :--- | :--- |
| `POST /login` | Không | `{ email, password }` | Cấp phiên mới |
| `POST /refresh-token` | Không (dùng cookie) | không có | Cấp lại token từ cookie `refreshToken` |
| `POST /select-tenant` | Có | `{ tenantId }` | Vào một nhà xe |
| `POST /switch-branch` | Có, token phải có nhà xe | `{ activeBranchId }` | Chọn chi nhánh làm việc |
| `POST /logout` | Có | không có | Kết thúc phiên, xóa cookie |

**Dữ liệu trả về** (`data` của login, refresh, select-tenant, switch-branch):

| Trường | Ý nghĩa |
| :--- | :--- |
| `accessToken` | Token dùng gọi API, sống 15 phút |
| `expiresIn` | Thời hạn của access token, tính bằng giây |
| `user` | `id`, `email`, `fullname`, `phone`, `tenantId`, `tenantName`, `roleCode`, `permissions[]`, `activeBranchId`, `assignedBranches[]` (mỗi phần tử `branchId`, `branchName`, `address`) |
| `availableTenants` | Danh sách nhà xe để chọn (`tenantId`, `tenantName`, `domain`, `role`, `isActive`), có khi người dùng thuộc nhiều nhà xe hoặc sau khi làm mới token |

### Refresh token nằm trong cookie, không nằm trong phản hồi
- Cookie `refreshToken`: `HttpOnly`, `Secure`, `SameSite=Strict`, chỉ gửi tới đường dẫn `/api/v1/auth`, sống 7 ngày.
- Frontend **không đọc được** cookie này. Chỉ cần gọi `refresh-token` với `credentials: 'include'`, trình duyệt tự gửi.
- **Access token chỉ giữ trong bộ nhớ** (biến trong `AuthService`), không lưu `localStorage` hay `sessionStorage`.

### Ba kiểu kết quả của `login`
| Người dùng | Kết quả | Màn tiếp theo |
| :--- | :--- | :--- |
| Super Admin | `roleCode = SUPER_ADMIN`, không có nhà xe | Trang chủ |
| Thuộc đúng 1 nhà xe | Token đã có nhà xe và vai trò, `assignedBranches` có sẵn | Chọn chi nhánh (hoặc trang chủ nếu không có chi nhánh nào) |
| Thuộc nhiều nhà xe | Token chưa có nhà xe, `availableTenants` có danh sách | Chọn nhà xe |

### Làm mới token giữ nhà xe và chi nhánh đang chọn
Từ commit `30a97b8` (nhánh dynamic-rbac), refresh token mang theo `tenant_id` và `active_branch_id`, nên `refresh-token` cấp lại access token đúng ngữ cảnh đang làm việc (Super Admin thì mang `SUPER_ADMIN`). Kết quả theo ngữ cảnh: chưa chọn nhà xe thì kèm `availableTenants`; đã chọn nhà xe thì kèm `assignedBranches` và `permissions`; đã chọn cả chi nhánh thì token đầy đủ. Hệ quả cho frontend:
- Tải lại trang: gọi `refresh-token` để khôi phục phiên, người dùng **ở lại đúng chỗ đang làm** (trang chủ nếu đã chọn xong).
- Hết hạn access token giữa chừng (401): bộ chặn HTTP làm mới rồi gửi lại request, không làm gián đoạn form đang nhập. Nếu nhà xe/chi nhánh sau khi làm mới khác trước thì không gửi lại mà đưa về màn chọn tương ứng.
- `availableTenants` chỉ có ở `login` và ở `refresh-token` khi chưa chọn nhà xe. Sau khi tải lại trang ở trạng thái đã chọn nhà xe, frontend không còn danh sách nhà xe, nên nút "Đổi nhà xe" của người thuộc nhiều nhà xe **không hiện** cho đến lần đăng nhập sau (xem mục 8).

### Mỗi tài khoản một phiên
Mỗi lần `login` tạo một mã phiên mới và làm phiên cũ mất hiệu lực. Làm mới token của phiên cũ trả **403**. Khi gặp 401 hoặc 403 mà làm mới không được, frontend xóa phiên cục bộ và đưa về màn đăng nhập kèm lý do. Access token của phiên cũ bị từ chối bằng **403** mã `AUTH_FORBIDDEN` (đã kiểm tra khi chạy thử), thông báo "Tài khoản này hiện đang có người sử dụng. Vui lòng đăng nhập lại !". Vì trùng mã với lỗi thiếu quyền thật nên frontend chỉ hiện thông báo của backend, chưa tự đưa về màn đăng nhập (xem mục 8).

## 4. Luồng màn hình và quy tắc điều hướng

| Màn | Đường dẫn | Điều kiện vào |
| :--- | :--- | :--- |
| Đăng nhập | `/login` | Chưa có phiên |
| Chọn nhà xe | `/select-tenant` | Có phiên, chưa có nhà xe, có `availableTenants` |
| Chọn chi nhánh | `/select-branch` | Có phiên và nhà xe, chưa có chi nhánh |
| Trang chủ | `/home` | Có phiên (Super Admin, hoặc đã chọn nhà xe, và chi nhánh nếu có) |

Quy tắc chọn màn sau mỗi lần gọi API thành công:
1. `roleCode = SUPER_ADMIN`: trang chủ.
2. Chưa có `tenantId` và có `availableTenants`: chọn nhà xe.
3. Có `tenantId`, chưa có `activeBranchId`: nếu `assignedBranches` rỗng thì trang chủ (ví dụ chủ nhà xe chưa có chi nhánh nào), ngược lại chọn chi nhánh.
4. Có `activeBranchId`: trang chủ.

**Bộ chặn HTTP:** tự thêm `Authorization: Bearer <accessToken>`; gặp 401 thì gọi `refresh-token` **một lần** rồi gửi lại; không làm mới lại khi chính API auth bị lỗi (tránh lặp vô hạn).

**Xử lý lỗi:** hiện `error.message` của backend; với `VALIDATION_ERROR` hiện lỗi cạnh từng ô nhập.

## 5. Cách chạy thử

- **Gọi backend qua proxy của máy chủ dev:** frontend gọi cùng gốc `/api`, proxy chuyển sang backend (cổng 8080, đặt trong `frontend/proxy.conf.json`). Backend **chưa cấu hình CORS** nên không gọi trực tiếp từ cổng khác được; dùng proxy thì không phải sửa backend và cookie hoạt động.
- **Trình duyệt:** dùng Chrome hoặc Firefox. Cookie `Secure` trên `http://localhost` được hai trình duyệt này chấp nhận, Safari thì không.
- **Backend:** nhánh này có migration V7 (cột phiên đăng nhập). Chạy với DB hiện tại sẽ **đổi cấu trúc DB đó**, nên nên thử trên một Postgres tạm.
- **Kiểm thử:** test đơn vị (Vitest) cho quy tắc điều hướng, `AuthService`, bộ chặn HTTP, guard, khung ứng dụng, giao diện sáng/tối và màn đăng nhập; chạy luồng thật bằng Chrome.

## 6. Chưa nối (sẽ làm khi gộp các module khác)

| Phần | Lý do chưa có |
| :--- | :--- |
| Quản lý chi nhánh, quản lý người dùng | Đã có API ở nhánh `feature/giangdt`, chưa gộp (gộp có thể xung đột ở `AuthServiceImpl` và `UserPrincipal`) |
| Xe, khách, đơn thuê | Backend chưa có API |
| Gọi API nghiệp vụ có kiểm tra quyền | Chưa có API để gọi |

Khi gộp thêm module, migration mới phải đánh số từ **V8** (V7 đã dùng cho phiên đăng nhập).

## 7. Việc chưa chốt

| # | Điểm | Đang nghiêng về |
| :-: | :--- | :--- |
| 1 | Ai chạy lệnh dựng khung Angular | **Đã chốt:** Claude chạy (`npx @angular/cli@21 new frontend --routing --style=css --skip-git --ssr=false --ai-config=none --defaults`) |
| 2 | Phiên bản Angular | **Đã chốt:** 21 LTS (bản mới nhất lúc đó là 22) |
| 3 | Cách gọi backend khi chạy thử | **Đã chốt:** proxy của máy chủ dev tới cổng 8080 (không sửa backend) |
| 4 | Chạy backend để thử | **Đã chốt:** Postgres tạm, không động tới DB dev |
| 5 | Chế độ tối | **Đã chốt (đổi ý 19/10):** có cả sáng và tối, lần đầu theo hệ điều hành, nhớ lựa chọn |
| 6 | Bảng ghi sự kiện auth trên trang chủ (để demo) | **Đã chốt:** có ("Nhật ký các lần gọi auth") |
| 7 | Thứ tự gộp các nhánh (`dynamic-rbac`, `huynq7-tenant`, `giangdt`) | **Đã chốt: không gộp trước**, chỉ kiểm tra auth. Khi gộp sau cần đối chiếu lại hợp đồng auth, và thống nhất API gán chủ nhà xe (`/api/v1/tenants/{id}/admins` so với `/api/v1/admin/users/tenant-admin` của `giangdt`) để không có hai cách làm |

## 8. Kết quả chạy thử (19/10/2026)

**Cách thử:** Postgres tạm (`postgres:15-alpine`, cổng 5433), backend nhánh này chạy ở cổng 8090, `ng serve` với proxy trỏ cổng 8090, ba tài khoản bổ sung tạo bằng SQL trong README, điều khiển Chrome thật (chế độ giả lập thiết bị). Mật khẩu dùng chung của dữ liệu mẫu.

| Kịch bản | Kết quả |
| :--- | :--- |
| Chưa đăng nhập vào `/home` | Về `/login` |
| Gửi form trống | Lỗi cạnh từng ô |
| Sai mật khẩu, tài khoản bị khóa | Hiện đúng thông báo của backend, ở lại `/login` |
| `minh.admin` (1 nhà xe, 3 chi nhánh) | `/select-branch`, chọn chi nhánh, `/home` đúng vai trò, quyền, nhà xe |
| `multi.user` (2 nhà xe) | `/select-tenant` có 2 thẻ, chọn một nhà xe thì đi tiếp |
| `superadmin` | Thẳng `/home`, vai trò `SUPER_ADMIN` |
| Tải lại trang | Khôi phục phiên bằng cookie, ở lại đúng màn đang làm (đã chọn xong thì trang chủ, đúng với hành vi backend mới: refresh token giữ nhà xe và chi nhánh) |
| Đăng xuất rồi tải lại | Ở `/login`, cookie đã bị xóa |
| Đăng nhập cùng tài khoản ở trình duyệt khác, rồi thao tác ở phiên cũ | Backend trả 403 `AUTH_FORBIDDEN`, giao diện hiện thông báo của backend |
| Cuộn ngang ở 360, 390, 768, 1024, 1280, 1920px | Không có |

Cookie `Secure` trên `http://localhost` hoạt động với Chrome. Có 76 test đơn vị (điều hướng, `AuthService`, bộ chặn HTTP, guard, khung ứng dụng, menu theo quyền, sáng/tối, màn đăng nhập, trang chủ) và 20 bước kiểm tra trên trình duyệt thật đều đạt.

**Điều rút ra, cần bàn với người làm backend:**
1. **Đã xử lý ở backend:** `refresh-token` nay giữ nhà xe và chi nhánh đang chọn (commit `30a97b8`), nên không còn phải chọn lại sau 15 phút hay khi tải lại trang. Còn một khoảng trống nhỏ: khi đã chọn nhà xe, `refresh-token` trả `availableTenants = null`, nên sau khi tải lại trang người thuộc nhiều nhà xe mất nút "Đổi nhà xe". Đề nghị backend trả `availableTenants` trong `refresh-token` cho người không phải Super Admin, hoặc thêm API lấy danh sách nhà xe.
2. **Access token của phiên bị thay thế trả 403 `AUTH_FORBIDDEN`**, trùng mã với lỗi thiếu quyền. Nên trả 401 với mã riêng (ví dụ `SESSION_REPLACED`) để frontend phân biệt và đưa về màn đăng nhập.

### Khung ứng dụng và giao diện sáng/tối (19/10/2026)
- Trang sau đăng nhập nằm trong khung chung: **header** (logo, nhà xe/chi nhánh đang làm việc, nút sáng/tối, menu người dùng) và **menu bên trái** cố định từ 1024px; dưới 1024px menu là ngăn kéo mở bằng nút ba gạch. Màn trước đăng nhập (đăng nhập, chọn nhà xe, chọn chi nhánh) giữ thẻ giữa màn hình.
- Mục menu hiện theo quyền trong phiên (Super Admin chỉ thấy "Nhà xe"); module chưa làm hiện mờ kèm nhãn "Sắp có". Thêm module mới: một route con trong `app.routes.ts` và một dòng trong `nav-items.ts`.
- Giao diện sáng/tối đổi bằng token màu (`:root[data-theme='dark']` trong `styles.css`), nên component không cần biết đang ở chế độ nào.

## 9. Module Nhà xe cho Super Admin (20/10/2026)

Backend `feature/huynq7-tenant` đã gộp vào nhánh này, nên frontend nối đủ 9 API ở [đặc tả API tenant](../tenant/2026-10-19-tenant-api-spec.md). Chỉ Super Admin thấy và vào được; người dùng nhà xe mở `/tenants` bị đưa về `/home` (`superAdminGuard`).

| Đường dẫn | Màn hình | Việc làm được |
| :--- | :--- | :--- |
| `/tenants` | Danh sách | Tìm theo tên hoặc domain, lọc đang hoạt động hoặc đã khóa, phân trang 10 dòng, mới nhất trước |
| `/tenants/new` | Tạo nhà xe | Nhập thông tin và một hoặc nhiều chủ nhà xe. Cấu hình vận hành dùng giá trị mặc định, không cho chỉnh lúc tạo |
| `/tenants/:tenantId` | Chi tiết | Xem thông tin và danh sách chủ, khóa, mở khóa, xóa mềm, thêm chủ |
| `/tenants/:tenantId/edit` | Sửa | Sửa tên, domain, gói và các trường liên hệ |

**Quy tắc giao diện đáng chú ý**
- Khóa và xóa luôn hỏi xác nhận ngay trên trang (không dùng hộp thoại của trình duyệt). Xóa là **xóa mềm**: nhà xe bị khóa, dữ liệu giữ nguyên, mở khóa lại được. Lời nhắc ghi rõ điều này.
- Domain được cắt khoảng trắng và đổi chữ thường trước khi kiểm tra và gửi (backend chỉ nhận chữ thường). Email cũng cắt khoảng trắng thừa, vì người dùng hay dán thừa dấu cách.
- Chủ nhà xe: giao diện **hỏi trước** email đã có tài khoản chưa (API tra cứu, gọi khi rời ô email hoặc khi bấm gửi) rồi mới hiện đúng ô cần nhập: email mới thì hiện họ tên và mật khẩu (bắt buộc); email có sẵn thì chỉ báo "Đã có tài khoản {họ tên}" và không hiện hai ô đó; email không gán được (tài khoản bị khóa, là Super Admin, đã là chủ hoặc nhân viên của nhà xe, nhà xe bị khóa) thì báo lý do ngay và không cho gửi. Sửa email thì kết quả cũ bị bỏ. Hai dòng chủ cùng email (không phân biệt hoa thường, bỏ khoảng trắng) thì dòng sau báo ngay "Email này đã nhập ở chủ nhà xe số N" và không cho gửi; backend vẫn kiểm tra trùng như chốt cuối. Nếu không tra cứu được (lỗi mạng...) thì hiện đủ ô và để backend quyết khi gửi; lần rời ô email sau sẽ thử tra cứu lại. Độ mạnh mật khẩu do **backend** kiểm tra, giao diện không chặn trước và không hiện dòng gợi ý; câu báo lỗi của backend hiện dưới ô mật khẩu.
- Khi **tạo**, ô tùy chọn để trống thì không gửi để backend dùng mặc định. Khi **sửa**, ô để trống được gửi dưới dạng chuỗi rỗng, nghĩa là xóa giá trị cũ (đúng quy tắc "không gửi là giữ, rỗng là xóa" của API 4).
- Trường `settings` (JSON tự do) **không có ô nhập** ở cả tạo và sửa, và không bao giờ được gửi, nên backend giữ nguyên hoặc dùng `{}`. Trang chi tiết chỉ hiện nó (chỉ xem) khi khác `{}`. Lý do: chưa có tính năng nào đọc `settings`, và cấu hình vận hành đã chốt dùng giá trị mặc định lúc tạo.
- Lỗi từng ô lấy từ `VALIDATION_ERROR` (kể cả đường dẫn kiểu `admins[0].email`); domain trùng (409) hiện ngay dưới ô domain; các lỗi nghiệp vụ khác hiện thành thông báo chung.
- Tạo xong hoặc sửa xong chuyển sang trang chi tiết kèm thông báo (tài khoản nào được tạo mới, tài khoản nào chỉ được gán). Thông báo đi theo trạng thái điều hướng, không nằm trên địa chỉ.
- Nhà xe đang khóa: nút "Thêm chủ" bị tắt kèm lời giải thích (backend từ chối thêm chủ cho nhà xe đã khóa).
- Danh sách chỉ giữ kết quả của lần gọi mới nhất, tránh câu trả lời cũ về muộn ghi đè.
- Điện thoại (dưới 640px): nút ở đầu trang (Thêm nhà xe, Sửa, Khóa, Xóa) chiếm hết bề ngang, chia đều và cao 44px để dễ chạm. Khung "Thông tin" tự chia cột theo bề ngang (1 cột trên điện thoại, 3 cột ở 768px). Cột nhãn trong danh sách rộng cố định để các cột thẳng hàng giữa các dòng. Đã kiểm tra ở 360, 390, 480, 768 và 1280px, không cuộn ngang.

**Cấu trúc:** `features/tenants/` gồm `tenant.service.ts` (9 API), `admin-lookup.ts` (tra cứu email chủ), `admin-lookup-note/` (dòng phản hồi dưới ô email, dùng chung), `tenant-validators.ts` (domain, email), `tenant-list`, `tenant-form` (dùng chung cho tạo và sửa), `tenant-detail`; kiểu dữ liệu ở `core/models/tenant.models.ts`.

**Kết quả kiểm tra (20/10/2026):** thêm 70 test đơn vị, tổng **146 test** đạt (backend: thêm 22 test cho API tra cứu, thứ tự ưu tiên lý do và nhà xe thiếu nhóm quyền chủ, tổng 109). Chạy thật với backend đã gộp (PostgreSQL riêng, cổng 8090) bằng Chrome: **40 bước đạt**, gồm tìm kiếm và lọc, tạo nhà xe với hai chủ (một mới, một tài khoản có sẵn, ô họ tên và mật khẩu hiện hoặc ẩn theo kết quả tra cứu), email Super Admin bị từ chối ngay, hai chủ trùng email bị chặn ngay, trùng domain, thiếu họ tên, sửa và xóa giá trị, khóa, mở khóa, thêm chủ (kể cả chủ đã có của nhà xe bị từ chối ngay), xóa mềm, tải lại trang ở trang chi tiết, người dùng nhà xe bị chặn, và không cuộn ngang ở 390px.

**Khóa nhà xe có hiệu lực ngay:** theo code của `dynamic-rbac` (`CustomUserDetailsServiceImpl`), người của nhà xe bị khóa nhận 403 "Nhà xe của bạn đang chọn đã bị khóa" ở mọi request, kể cả khi còn access token (đọc từ code, chưa chạy thử).

**Giới hạn còn lại (thuộc backend):** mã nhà xe sai dạng UUID trên đường dẫn trả 500 thay vì 400; chưa có API gỡ hoặc đổi chủ nhà xe nên màn chi tiết chỉ xem và thêm chủ.
