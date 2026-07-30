# Test Cases: Account Management, Authentication, and Tenant Selection

Dưới đây là danh sách 25 test case chi tiết bao gồm Happy Paths, Edge Cases và các kịch bản Bảo mật/Phân quyền để bạn có thể sử dụng để kiểm thử toàn diện các luồng nghiệp vụ.

---

## I. Nhóm 1: Tạo tài khoản (Account Creation)

### TC-01: Tạo tài khoản STAFF hợp lệ với 1 chi nhánh
- **Mô tả**: Tenant Admin tạo tài khoản nhân viên mới (Role STAFF = 2) và gán cho 1 chi nhánh hợp lệ thuộc tenant của mình.
- **Dữ liệu đầu vào**: 
  - Email: `staff1@gmail.com` (chưa tồn tại trong hệ thống)
  - Role: `2` (STAFF)
  - BranchIds: `[branch_id_1]` (chi nhánh hợp lệ)
- **Kết quả mong đợi**: HTTP 200 OK. Tài khoản mới được tạo, mật khẩu được băm, liên kết User-Tenant được thiết lập với role STAFF, chi nhánh được gán đúng.

### TC-02: Tạo tài khoản STAFF hợp lệ với nhiều chi nhánh (Deduplicated)
- **Mô tả**: Tenant Admin tạo tài khoản STAFF và gán cho nhiều chi nhánh, trong đó có chi nhánh bị trùng lặp trong request.
- **Dữ liệu đầu vào**:
  - Email: `staff2@gmail.com`
  - Role: `2`
  - BranchIds: `[branch_id_1, branch_id_2, branch_id_1]`
- **Kết quả mong đợi**: HTTP 200 OK. Tài khoản được tạo thành công, các chi nhánh gán cho STAFF được tự động loại bỏ trùng lặp (distinct) còn `[branch_id_1, branch_id_2]`.

### TC-03: Tạo tài khoản SALE hợp lệ (Không có chi nhánh)
- **Mô tả**: Tenant Admin tạo tài khoản cộng tác viên (Role SALE = 3) và không gán cho chi nhánh nào.
- **Dữ liệu đầu vào**:
  - Email: `sale1@gmail.com`
  - Role: `3` (SALE)
  - BranchIds: `[]` hoặc `null`
- **Kết quả mong đợi**: HTTP 200 OK. Tài khoản được tạo thành công với role SALE, không gán chi nhánh.

### TC-04: Thất bại khi tạo tài khoản STAFF không gán chi nhánh
- **Mô tả**: Tạo tài khoản STAFF nhưng không gán chi nhánh nào.
- **Dữ liệu đầu vào**:
  - Email: `staff_fail@gmail.com`, Role: `2`, BranchIds: `[]`
- **Kết quả mong đợi**: HTTP 400 Bad Request. Thông báo lỗi: `"Staff account must be assigned to at least one branch"`.

### TC-05: Thất bại khi tạo tài khoản SALE nhưng lại gán chi nhánh
- **Mô tả**: Tạo tài khoản SALE nhưng lại truyền danh sách chi nhánh trong request.
- **Dữ liệu đầu vào**:
  - Email: `sale_fail@gmail.com`, Role: `3`, BranchIds: `[branch_id_1]`
- **Kết quả mong đợi**: HTTP 400 Bad Request. Thông báo lỗi: `"Sale account must not be assigned to any branch"`.

### TC-06: Thất bại khi email không đúng định dạng
- **Mô tả**: Truyền email sai định dạng RFC.
- **Dữ liệu đầu vào**: `email: "invalid-email"`, các trường khác hợp lệ.
- **Kết quả mong đợi**: HTTP 400 Bad Request. Validation báo lỗi email không hợp lệ.

### TC-07: Thất bại khi mật khẩu yếu
- **Mô tả**: Mật khẩu không chứa cả chữ và số hoặc dưới 8 ký tự.
- **Dữ liệu đầu vào**: `password: "12345678"` (thiếu chữ) hoặc `"abcdefgh"` (thiếu số), hoặc `"abc12"` (ngắn hơn 8 ký tự).
- **Kết quả mong đợi**: HTTP 400 Bad Request. Validation báo lỗi mật khẩu bắt buộc tối thiểu 8 ký tự và chứa cả chữ lẫn số.

### TC-08: Thất bại khi số điện thoại không hợp lệ
- **Mô tả**: Số điện thoại không bắt đầu bằng số 0, hoặc không đủ/thừa chữ số (yêu cầu 10-11 chữ số).
- **Dữ liệu đầu vào**: `phone: "1234567890"` (không bắt đầu bằng 0), hoặc `"098765"` (quá ngắn), hoặc `"098765432123"` (quá dài).
- **Kết quả mong đợi**: HTTP 400 Bad Request. Validation báo lỗi định dạng số điện thoại.

### TC-09: Thất bại khi họ tên trống
- **Mô tả**: Bỏ trống trường họ tên (`fullName`).
- **Dữ liệu đầu vào**: `fullName: ""`.
- **Kết quả mong đợi**: HTTP 400 Bad Request. Validation báo lỗi họ tên không được để trống.

### TC-10: Bảo mật - Thất bại khi gán chi nhánh thuộc Tenant khác (IDOR Prevention)
- **Mô tả**: Tenant Admin cố tình truyền vào danh sách chi nhánh thuộc sở hữu của một doanh nghiệp (Tenant) khác.
- **Dữ liệu đầu vào**: `branchIds: [branch_id_of_another_tenant]`.
- **Kết quả mong đợi**: HTTP 403 Forbidden. Thông báo lỗi: `"One or more branches do not belong to this tenant"`.

### TC-11: Liên kết tài khoản (Linking Account) khi email đã tồn tại ở Tenant khác
- **Mô tả**: Tạo tài khoản với email đã tồn tại trên hệ thống (do Tenant khác tạo trước đó), thực hiện ánh xạ tài khoản này vào Tenant hiện tại thay vì tạo bản ghi User mới.
- **Dữ liệu đầu vào**: Email: `shared-user@gmail.com` (đã có trong DB toàn hệ thống nhưng chưa thuộc Tenant này).
- **Kết quả mong đợi**: HTTP 200 OK. Hệ thống bỏ qua việc băm mật khẩu và tạo User mới, chỉ thực hiện liên kết User đã có vào Tenant mới với Role và Branch tương ứng. Thông tin cá nhân (fullName, phone) của User gốc được giữ nguyên không thay đổi.

### TC-12: Trùng lặp - Thất bại khi email đã tồn tại trong chính Tenant hiện tại
- **Mô tả**: Tạo tài khoản mới trùng email với một tài khoản đã là thành viên của doanh nghiệp hiện tại.
- **Dữ liệu đầu vào**: Email trùng với tài khoản STAFF hiện tại của Tenant.
- **Kết quả mong đợi**: HTTP 400 Bad Request. Thông báo lỗi: `"Account with this email already exists in your organization"`.

---

## II. Nhóm 2: Phân công lại Role và Branch (Role & Branch Modification)

### TC-13: Chuyển đổi từ STAFF sang SALE thành công
- **Mô tả**: Tenant Admin chuyển đổi chức vụ của một STAFF hiện tại thành SALE.
- **Dữ liệu đầu vào**: Role mới: `3` (SALE), BranchIds: `[]`.
- **Kết quả mong đợi**: HTTP 200 OK. Role của nhân viên đổi thành SALE và toàn bộ chi nhánh cũ gán cho nhân viên này bị thu hồi (delete khỏi bảng UserBranch).

### TC-14: Chuyển đổi từ SALE sang STAFF thành công
- **Mô tả**: Tenant Admin chuyển đổi chức vụ của một SALE hiện tại thành STAFF và gán chi nhánh mới.
- **Dữ liệu đầu vào**: Role mới: `2` (STAFF), BranchIds: `[branch_id_1]`.
- **Kết quả mong đợi**: HTTP 200 OK. Role đổi thành STAFF, hệ thống tạo bản ghi liên kết chi nhánh mới thành công.

### TC-15: Thất bại khi đổi thông tin của Tenant Administrator
- **Mô tả**: Tenant Admin cố gắng thay đổi vai trò hoặc chi nhánh của tài khoản Tenant Admin khác (hoặc chính mình).
- **Dữ liệu đầu vào**: Đích danh `userId` của Tenant Admin.
- **Kết quả mong đợi**: HTTP 400 Bad Request. Thông báo lỗi: `"Cannot modify role or branch assignment of a Tenant Administrator"`.

### TC-16: Bảo mật - Thất bại khi cập nhật chi nhánh của Tenant khác
- **Mô tả**: Khi chuyển vai trò sang STAFF, truyền chi nhánh thuộc Tenant khác.
- **Dữ liệu đầu vào**: `branchIds` chứa ID chi nhánh của Tenant khác.
- **Kết quả mong đợi**: HTTP 403 Forbidden. Thông báo lỗi: `"One or more branches do not belong to this tenant"`.

---

## III. Nhóm 3: Lấy danh sách tài khoản (Get Account List)

### TC-17: Tenant Admin lấy danh sách tài khoản thành công
- **Mô tả**: Người dùng có role `TENANT_ADMIN` gửi yêu cầu xem danh sách tài khoản của doanh nghiệp mình.
- **Kết quả mong đợi**: HTTP 200 OK. Trả về đúng danh sách các tài khoản thuộc Tenant của Admin đó (không bị lẫn dữ liệu của Tenant khác). Mỗi tài khoản trả về đầy đủ: userId, email, fullName, phone, role, active, và list branchIds tương ứng.

### TC-18: Bảo mật - Phân quyền lấy danh sách tài khoản
- **Mô tả**: Nhân viên thông thường (STAFF hoặc SALE) cố gắng truy cập API lấy danh sách tài khoản.
- **Kết quả mong đợi**: HTTP 403 Forbidden. Từ chối truy cập.

---

## IV. Nhóm 4: Đăng nhập & Chọn Tenant (Login & Tenant Selection)

### TC-19: Đăng nhập thành công với thông tin chính xác
- **Mô tả**: Người dùng đăng nhập bằng Email và Mật khẩu chính xác.
- **Kết quả mong đợi**: HTTP 200 OK. Trả về JWT Access Token hợp lệ, chứa các claims như userId, email, và danh sách các tenant được liên kết.

### TC-20: Đăng nhập thất bại khi sai thông tin credentials
- **Mô tả**: Đăng nhập bằng email không tồn tại hoặc mật khẩu sai.
- **Kết quả mong đợi**: HTTP 401 Unauthorized. Thông báo lỗi sai thông tin đăng nhập.

### TC-21: Đăng nhập thất bại đối với tài khoản bị khóa (Inactive)
- **Mô tả**: Một tài khoản đã bị Tenant Admin khóa (`active = false`) cố gắng đăng nhập.
- **Kết quả mong đợi**: HTTP 401 Unauthorized. Thông báo tài khoản đã bị khóa.

### TC-22: Đăng nhập và tự động gán Tenant Context (Người dùng thuộc 1 Tenant)
- **Mô tả**: Người dùng chỉ thuộc về duy nhất một công ty/doanh nghiệp tiến hành đăng nhập và gọi API.
- **Kết quả mong đợi**: JWT token trả về tự động đính kèm `tenantId` tương ứng. Khi gọi các API nghiệp vụ, TenantContext tự động nhận dạng doanh nghiệp này mà không cần người dùng chọn thủ công.

### TC-23: Lựa chọn Tenant đối với tài khoản thuộc nhiều doanh nghiệp (Multi-tenant User)
- **Mô tả**: Người dùng được liên kết vào nhiều Tenant khác nhau (sau khi được link ở TC-11) tiến hành lựa chọn Tenant muốn làm việc khi đăng nhập.
- **Dữ liệu đầu vào**: Header hoặc query parameter chọn `tenantId` cụ thể trong danh sách liên kết của mình.
- **Kết quả mong đợi**: HTTP 200 OK. JWT Token mới được sinh ra chứa đúng `tenantId` đã chọn. Mọi truy vấn dữ liệu sau đó sẽ bị giới hạn cô lập trong Tenant này.

### TC-24: Thất bại khi chọn Tenant mà mình không phải là thành viên
- **Mô tả**: Người dùng cố tình truyền lên `tenantId` của một công ty khác mà tài khoản của họ không liên kết.
- **Kết quả mong đợi**: HTTP 403 Forbidden hoặc HTTP 401 Unauthorized. Hệ thống từ chối cấp token/context làm việc đối với tenant đó.

### TC-25: Xác thực Tenant Context trên mọi API (Tenant Isolation Enforcement)
- **Mô tả**: Gửi một request API nghiệp vụ (ví dụ: tạo tài khoản) mà không kèm JWT token hoặc token không có claim `tenant_id`.
- **Kết quả mong đợi**: HTTP 401 Unauthorized hoặc HTTP 400 Bad Request. Báo lỗi `"Tenant context is required"`.

---

## V. Nhóm 5: Các kịch bản nâng cao & Biên giới hạn bảo mật (Advanced & Boundary Edge Cases)

### TC-26: Concurrency - Hai Tenant Admins liên kết cùng một Email đồng thời (Race Condition)
- **Mô tả**: Hai Tenant Admins thuộc Tenant A và Tenant B cố gắng liên kết/tạo tài khoản cho cùng một Email nhân viên chưa tồn tại trong hệ thống (`new-staff@gmail.com`) tại cùng một thời điểm.
- **Kịch bản chi tiết**:
  1. Yêu cầu tạo/liên kết của Tenant A và Tenant B đến Backend đồng thời.
  2. Cả hai luồng xử lý (Thread A và Thread B) đều kiểm tra xem User có email `new-staff@gmail.com` đã tồn tại trong DB toàn cục chưa và nhận kết quả là "Chưa tồn tại".
  3. Cả hai luồng cùng cố gắng insert một bản ghi `User` mới vào bảng `users`.
- **Kết quả mong đợi**: Hệ thống xử lý đồng thời tốt:
  - Một luồng insert thành công và tạo liên kết `UserTenant` tương ứng.
  - Luồng còn lại bị Database chặn bằng lỗi trùng khóa unique (`Duplicate key value violates unique constraint on email`).
  - Backend bắt được ngoại lệ (DataIntegrityViolationException), tự động chuyển sang luồng "Liên kết tài khoản có sẵn" (giống TC-11) để liên kết User vừa được insert ở luồng kia vào Tenant còn lại một cách mượt mà mà không ném lỗi HTTP 500 ra Client.

### TC-27: Bảo mật - Cố ý gán chi nhánh chéo Tenant khi liên kết tài khoản (IDOR & Privilege Escalation)
- **Mô tả**: Tenant Admin A muốn liên kết Email của một nhân viên đã có sẵn tài khoản ở Tenant B. Tuy nhiên, trong request tạo của Tenant Admin A, tham số `branchIds` lại chứa ID chi nhánh thuộc sở hữu của Tenant B (hòng chiếm quyền quản trị chi nhánh đó).
- **Dữ liệu đầu vào**:
  - Người thực hiện: Tenant Admin A.
  - Email: `user-of-tenant-b@gmail.com` (Đã tồn tại ở Tenant B).
  - BranchIds gửi lên: `[branch_id_of_tenant_b]`.
- **Kết quả mong đợi**: HTTP 403 Forbidden. Mặc dù User này thuộc Tenant B và có quyền làm việc tại chi nhánh đó ở Tenant B, Tenant Admin A (thuộc Tenant A) hoàn toàn không có quyền hạn gì đối với các chi nhánh của Tenant B. Hệ thống phải chặn và báo lỗi `"One or more branches do not belong to this tenant"`.

### TC-28: Cô lập vai trò giữa các Tenant khác nhau (Role Isolation)
- **Mô tả**: Tài khoản `admin@tenant-a.com` là **Tenant Admin** của Tenant A, nhưng đồng thời được liên kết làm **STAFF** (nhân viên) tại Tenant B. Đảm bảo vai trò không bị lẫn lộn giữa các môi trường làm việc.
- **Kịch bản chi tiết**:
  1. User đăng nhập và chọn Tenant A $\rightarrow$ Lấy JWT Token A. Gọi API lấy danh sách tài khoản $\rightarrow$ Thành công (do có quyền Tenant Admin ở Tenant A).
  2. User chuyển đổi (Switch Tenant) sang Tenant B $\rightarrow$ Lấy JWT Token B. Gọi API lấy danh sách tài khoản của Tenant B $\rightarrow$ Thất bại (HTTP 403 Forbidden do ở Tenant B user này chỉ có quyền STAFF).
- **Kết quả mong đợi**: Hệ thống phân quyền động dựa trên context `tenantId` đang hoạt động. Quyền lợi cao nhất ở Tenant A không được "bắc cầu" sang Tenant B.

### TC-29: Ngăn ngừa lỗi tự khóa hệ thống (Self-Demotion & Orphaned Tenant Prevention)
- **Mô tả**: Một Tenant Admin cố tình tự thay đổi vai trò của mình thành STAFF, hoặc tự khóa tài khoản của mình (`active = false`) khi họ là **Quản trị viên duy nhất còn hoạt động** của doanh nghiệp đó.
- **Dữ liệu đầu vào**:
  - Người thực hiện: Tenant Admin duy nhất của Tenant A.
  - Request: Đổi role chính mình thành STAFF (`2`) hoặc gọi API cập nhật trạng thái `isActive = false`.
- **Kết quả mong đợi**: HTTP 400 Bad Request. Thông báo lỗi: `"Cannot modify or deactivate the only Tenant Administrator of this organization"` để tránh việc Tenant bị mồ côi (không ai quản trị).

### TC-30: Kiểm chứng đồng bộ Token khi thu hồi quyền (Token & Permission Sync Check)
- **Mô tả**: Một STAFF đang đăng nhập và có JWT Access Token hợp lệ (hạn 15 phút) đang hoạt động trên Chi nhánh 1. Giữa chừng, Tenant Admin thực hiện thu hồi tài khoản (`isActive = false`) hoặc xóa quyền truy cập Chi nhánh 1 của nhân viên này.
- **Kiểm thử**: STAFF gửi tiếp một API nghiệp vụ liên quan đến Chi nhánh 1 (ví dụ: bàn giao xe).
- **Kết quả mong đợi**: HTTP 403 Forbidden hoặc HTTP 401 Unauthorized. Hệ thống không được chỉ tin cậy hoàn toàn vào JWT (stateless) mà đối với các ghi nhận nghiệp vụ quan trọng (Write operations), bộ lọc security hoặc service bắt buộc phải kiểm tra lại trạng thái User/Branch thực tế trong DB để chặn truy cập ngay lập tức khi có thay đổi nhân sự đột xuất.

### TC-31: Super Admin bypass cơ chế RLS (Super Admin Scope Validation)
- **Mô tả**: Tài khoản có cờ `is_super_admin = true` thực hiện lấy danh sách tài khoản mà không chọn Tenant (hoặc chọn Tenant bất kỳ).
- **Kết quả mong đợi**: HTTP 200 OK. Hệ thống cho phép Super Admin bypass qua chính sách Row-Level Security của PostgreSQL để xem danh sách tài khoản của mọi Tenant trong hệ thống mà không vấp phải lỗi `app.current_tenant` bị trống hay null. Dữ liệu trả về hiển thị đầy đủ tài khoản toàn hệ thống.

---

## VI. Nhóm 6: Các kịch bản nghiệp vụ sâu & Ràng buộc dữ liệu (Advanced Domain & Database Constraints)

### TC-32: Thất bại khi gán tọa độ Chi nhánh ngoài phạm vi địa lý (Latitude/Longitude Boundary Validation)
- **Mô tả**: Khi Tenant Admin tạo hoặc cập nhật Chi nhánh (`branches`), truyền vào tọa độ vĩ độ (Latitude) hoặc kinh độ (Longitude) không nằm trong dải hợp lý toàn cầu.
- **Dữ liệu đầu vào**: 
  - Latitude: `95.123456` (vượt quá 90) hoặc `-91.0`
  - Longitude: `185.123456` (vượt quá 180) hoặc `-181.0`
- **Kết quả mong đợi**: HTTP 400 Bad Request. Hệ thống chặn đầu vào ở tầng Validation (hoặc DB Check Constraint) và thông báo lỗi tọa độ không hợp lệ thay vì lưu dữ liệu lỗi vào DB.

### TC-33: Tránh trùng biển số xe trong cùng một Tenant dạng ẩn (License Plate Normalization & Case Insensitivity)
- **Mô tả**: Đảm bảo index `idx_vehicles_tenant_license` (unique trên `tenant_id` + `license_plate`) hoạt động đúng để tránh trường hợp nhập trùng biển số xe bằng cách chèn khoảng trắng hoặc thay đổi chữ hoa/thường.
- **Kịch bản**: 
  - Xe đã có trong DB của Tenant A: `30A-123.45`
  - Tenant Admin của Tenant A cố gắng tạo thêm xe mới với biển số: `30a-123.45` (chữ thường) hoặc `30A - 123.45` (thêm khoảng trắng).
- **Kết quả mong đợi**: HTTP 400 Bad Request. Hệ thống phải thực hiện tiền xử lý (Normalization: chuyển thành chữ in hoa và loại bỏ khoảng trắng, ký tự đặc biệt không cần thiết) trước khi kiểm tra trùng lặp và insert.

### TC-34: Ràng buộc giao dịch thanh toán không dùng tiền mặt (Database Constraint - Payment Transaction ID check)
- **Mô tả**: Bảng `payments` có ràng buộc kiểm tra `chk_transaction_id`: Nếu `method` khác 1 (BANK_TRANSFER = 2 hoặc E_WALLET = 3), trường `transaction_id` không được để trống hoặc null.
- **Dữ liệu đầu vào**:
  - Ghi nhận thanh toán cọc cho đơn thuê: `method = 2` (Chuyển khoản), `amount = 500,000`, `transaction_id = null` hoặc `""` (chuỗi trống).
- **Kết quả mong đợi**: HTTP 400 Bad Request. Hệ thống bắt buộc phải kiểm tra điều kiện này ở tầng code để trả về lỗi validation rõ ràng, không để trôi xuống DB gây ra lỗi vi phạm ràng buộc DB (`chk_transaction_id`) và trả về HTTP 500.

### TC-35: Concurrency - Đặt trùng một xe cho khoảng thời gian chồng lấn (Overlapping Booking Prevention)
- **Mô tả**: Hai khách hàng (hoặc nhân viên tạo hộ) cùng thực hiện đặt giữ chỗ cho cùng một xe `Vehicle X` trong hai khung thời gian bị trùng lặp (chồng lấn) tại cùng một thời điểm.
- **Dữ liệu đầu vào**:
  - Yêu cầu A: Đặt xe từ ngày `2026-08-01` đến `2026-08-05`
  - Yêu cầu B: Đặt xe từ ngày `2026-08-03` đến `2026-08-07`
- **Kết quả mong đợi**: Chỉ có duy nhất một yêu cầu thành công tạo đơn giữ chỗ tạm (Hold Engine). Yêu cầu còn lại phải bị từ chối với thông báo HTTP 400 hoặc 409: `"Vehicle is not available for the selected period"`. Hệ thống cần đảm bảo dùng cơ chế khóa (`SELECT FOR UPDATE` hoặc optimistic lock) để ngăn chặn hoàn toàn race condition tạo hai đơn đè lịch lên cùng một xe.

### TC-36: Phân quyền tác nghiệp giao/nhận xe giữa STAFF và SALE (Operational Role Restrictions)
- **Mô tả**: Kiểm tra tính độc lập nghiệp vụ của tài khoản có role `SALE` (Cộng tác viên kinh doanh). Theo quy chuẩn, SALE chỉ có quyền tạo đơn đặt xe, không được phép thực hiện bàn giao xe vật lý hay nhận lại xe.
- **Kịch bản**: 
  - Tài khoản SALE đăng nhập và gửi request `POST /api/bookings/{bookingId}/handover` (Bàn giao xe) hoặc `POST /api/bookings/{bookingId}/return` (Nhận lại xe).
- **Kết quả mong đợi**: HTTP 403 Forbidden. Từ chối thực hiện hành động. Chỉ có `STAFF` hoặc `TENANT_ADMIN` được cấp quyền gọi các API thay đổi trạng thái này.

### TC-37: Xóa/Ẩn danh dữ liệu khách hàng tuân thủ Nghị định 13/2023/NĐ-CP (Customer Anonymization & FK Integrity)
- **Mô tả**: Khách hàng yêu cầu rút lại sự đồng ý và xóa thông tin cá nhân. Do bảng `bookings` có khóa ngoại `fk_booking_customer_tenant` liên kết chặt chẽ đến bảng `customers` (không được cấu hình ON DELETE SET NULL để đảm bảo lịch sử doanh thu), việc xóa cứng dòng dữ liệu khách hàng sẽ lỗi khóa ngoại. Hệ thống cần có cơ chế ẩn danh hóa (Anonymization).
- **Kiểm thử**: Gửi yêu cầu xóa/ẩn danh dữ liệu khách hàng ID `customer_id_1` đã có 10 đơn đặt xe trong lịch sử.
- **Kết quả mong đợi**: HTTP 200 OK. Thông tin nhạy cảm của khách hàng trong bảng `customers` (fullName, phone, id_card, driver_license, address) được cập nhật thành các giá trị mã hóa/ẩn danh (ví dụ: `CUSTOMER_ANONYMOUS_123`, `0000000000`), trường `is_active` đổi thành `false`. Dữ liệu các đơn đặt xe cũ vẫn được giữ nguyên để làm báo cáo tài chính mà không vi phạm tính toàn vẹn của database và vẫn tuân thủ pháp luật.

### TC-38: Giới hạn số lượng tài nguyên theo gói dịch vụ Tenant (SaaS Tenant Plan Tier Enforcement)
- **Mô tả**: Mỗi Tenant có cấu hình gói dịch vụ `plan_tier` (1: FREE, 2: BASIC, 3: PRO, 4: ENTERPRISE). Gói FREE có giới hạn nghiêm ngặt (tối đa 1 chi nhánh, 5 đầu xe, 2 tài khoản nhân viên).
- **Kiểm thử**: Tenant A đang ở gói FREE (đã có 1 chi nhánh). Tenant Admin cố gắng gọi API tạo chi nhánh thứ 2.
- **Kết quả mong đợi**: HTTP 403 Forbidden. Thông báo lỗi: `"Your current plan tier limit exceeded. Please upgrade to create more branches."`

### TC-39: Kiểm soát đặt xe nằm ngoài Chi nhánh được gán của Nhân viên (Cross-Branch Operational Boundary)
- **Mô tả**: Nhân viên `STAFF A` được gán làm việc tại Chi nhánh 1, không được gán tại Chi nhánh 2.
- **Kiểm thử**: `STAFF A` cố gắng gọi API tạo Booking cho một khách hàng, nhưng truyền vào `branch_id = [branch_2_id]` (Chi nhánh 2).
- **Kết quả mong đợi**: HTTP 403 Forbidden. Mặc dù `STAFF A` thuộc cùng Tenant, họ chỉ được phép thực hiện giao dịch và quản lý xe trong phạm vi chi nhánh được phân công hoạt động (`user_branches` mapping). Hệ thống phải chặn thao tác chéo chi nhánh này.

### TC-40: Thất bại khi gửi JSON bị lỗi cú pháp hoặc định dạng không hợp lệ (Malformed JSON Syntax Validation)
- **Mô tả**: Khi client gửi lên request body bị lỗi cú pháp JSON (ví dụ dư thừa dấu phẩy ở cuối phần tử, mất cân bằng dấu đóng mở ngoặc nhọn/ngoặc vuông, hoặc sai kiểu dữ liệu trường).
- **Dữ liệu đầu vào**: Request chứa `branchIds: [branch_id_1, ]` (thừa dấu phẩy) hoặc gửi chuỗi văn bản trần không đúng chuẩn JSON.
- **Kết quả mong đợi**: HTTP 400 Bad Request. Thông báo lỗi trả về thân thiện và rõ ràng chỉ ra lỗi cú pháp định dạng JSON, không trả về mã lỗi hệ thống HTTP 500.

