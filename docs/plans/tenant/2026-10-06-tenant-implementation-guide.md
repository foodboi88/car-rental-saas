# Hướng dẫn triển khai module Tenant

> **Ngày:** 06/10/2026.
> **Người đọc** Lập trình viên Frontend đang học Backend Java/Spring và nhận nhiệm vụ làm module Tenant.
> **Công nghệ:** Java 17, Spring Boot 4.0.7, PostgreSQL 15 theo [pom.xml](../../../backend/pom.xml) và [README](../../../README.md).

## 0. Cách dùng tài liệu

Đọc lần lượt, mỗi buổi hoàn thành một chặng. Trước khi gõ, tự nói được đầu vào, đầu ra và trường hợp thất bại của việc sắp làm. Sau khi gõ, kiểm tra tiêu chí của chặng rồi giải thích lại bằng lời của mình.

Tài liệu cung cấp tên lớp, kiểu dữ liệu, hợp đồng hàm và các bước xử lý để bạn tự triển khai. Khi kẹt cú pháp, dùng từ khóa ở cuối chặng để tra tài liệu chính thức. Khi hỏi AI, đưa đoạn bạn đã viết, lỗi gặp phải và dự đoán nguyên nhân. Nhờ AI hỏi từng câu, chờ bạn trả lời rồi chỉnh lại cách hiểu.

| Tài liệu | Mở khi cần |
| --- | --- |
| [Guide này](2026-10-06-tenant-implementation-guide.md) | Biết nghĩ gì, tự viết gì và kiểm tra gì ở từng bước |
| [API spec](2026-10-19-tenant-api-spec.md) | Tra chính xác đường dẫn, payload, phản hồi và mã lỗi |
| [Thiết kế chi tiết — DD](2026-10-19-tenant-detail-design.md) | Tra quy tắc nghiệp vụ và sơ đồ xử lý; đây là nguồn chuẩn của thiết kế Tenant |
| [Quy tắc học trong AGENTS.md](../../../AGENTS.md) | Thống nhất cách AI hướng dẫn và cách bạn tự code |

Lộ trình gồm hiểu nhiệm vụ → đọc dữ liệu → chốt hợp đồng → Entity → DTO → Repository → Service → Controller → kiểm chứng → luyện trình bày. Thứ tự hiểu bài toán khác với thứ tự gõ file. Bạn cần biết API sẽ làm gì trước, dù viết Controller hoàn chỉnh ở gần cuối.

## 1. Nhận nhiệm vụ và xác định đúng bài toán

### 1.1 Người dùng muốn đạt được điều gì?

Hãy tưởng tượng nền tảng là ban quản lý một khu văn phòng. Mỗi nhà xe thuê một phòng làm việc. Khi bàn giao phòng, ban quản lý phải cấp nội quy mặc định, bộ quyền ra vào và người chịu trách nhiệm. Chỉ tạo tên phòng rồi bỏ trống người quản lý thì chưa bàn giao được.

Trong dự án, một nhà xe là một **tenant**. Module Tenant cho Super Admin tạo và quản lý các nhà xe sử dụng nền tảng. Nhà xe mới cần thông tin, cấu hình vận hành, ba vai trò mặc định và ít nhất một chủ có thể đăng nhập để quản lý.

| Khái niệm | Nghĩa trong dự án | Liên hệ với Frontend |
| --- | --- | --- |
| Tenant | Một doanh nghiệp/nhà xe sử dụng phần mềm | Workspace hoặc tổ chức đang sử dụng ứng dụng |
| Branch | Chi nhánh/bãi xe của một nhà xe | Địa điểm tác nghiệp trong workspace |
| User | Tài khoản dùng để đăng nhập toàn hệ thống | Hồ sơ người dùng sau đăng nhập |
| Membership | Quan hệ một tài khoản thuộc một nhà xe | Phân công người dùng vào workspace |
| Role | Nhóm quyền của người đó trong nhà xe | Vai trò dùng để quyết định giao diện được mở |
| Permission | Một hành động được cho phép | Khả năng hiện một nút; backend vẫn phải kiểm tra độc lập |

**Super Admin khác Tenant Admin.** Super Admin được nhận diện bằng `users.is_super_admin` và authority `ROLE_SUPER_ADMIN`. Tenant Admin là người có membership gắn vai trò `TENANT_ADMIN` của một nhà xe. Ba vai trò tạo cho mỗi nhà xe là `TENANT_ADMIN`, `STAFF`, `SALE`; Super Admin không nằm trong bộ ba này.

### 1.2 Chốt phạm vi trước khi viết

| Nhóm | Nhiệm vụ của module |
| --- | --- |
| Khởi tạo | Tạo nhà xe cùng cấu hình mặc định, vai trò, quyền và danh sách chủ |
| Tra cứu | Danh sách có tìm kiếm/lọc/phân trang; chi tiết nhà xe; danh sách chủ |
| Cập nhật | Sửa thông tin nhà xe, khóa/mở khóa và xóa mềm |
| Chủ nhà xe | Thêm chủ, kiểm tra email để hỗ trợ form |

Đăng nhập, chọn tenant và chọn chi nhánh thuộc Auth. Tạo nhân viên thuộc Account. Sửa cấu hình vận hành bởi chủ nhà xe, quản lý gói/quota, gửi email mời, gỡ chủ và đổi vai trò là các việc cần thiết kế riêng khi có yêu cầu. Tạo Tenant không đồng nghĩa tạo sẵn chi nhánh.

Khi được giao việc, tự ghi sáu câu trả lời vào nháp trước khi mở IDE.

1. Ai được gọi? Super Admin.
2. Kết quả thành công gồm gì? Nhà xe và các dữ liệu phụ cần để bắt đầu quản lý.
3. Dữ liệu nào phải duy nhất? Domain toàn hệ thống, email tài khoản, membership theo người và nhà xe, role code trong từng nhà xe.
4. Email đã có tài khoản thì sao? Kiểm tra điều kiện và liên kết tài khoản đó.
5. Một chủ trong danh sách không hợp lệ thì sao? Từ chối cả request tạo nhà xe.
6. Có bước ghi thất bại sau khi đã ghi vài bảng thì sao? Rollback các thay đổi của request đó.

Một request chứa hai chủ khác với hai request độc lập. Tính nguyên tử áp dụng cho từng giao dịch; không tự động làm request khác thất bại theo.

**Đạt khi** Bạn diễn đạt được mục tiêu module trong ba câu, phân biệt được Super Admin, Tenant Admin và nhân viên.

**Câu hỏi tự trả lời** Nếu chỉ lưu một dòng `tenants`, người chủ mới sẽ thiếu những dữ liệu nào để quản lý nhà xe?

**Từ khóa** `SaaS tenant membership RBAC`, `authentication vs authorization`.

## 2. Đọc database để suy ra trách nhiệm của code

### 2.1 Bắt đầu từ quan hệ, chưa bắt đầu từ annotation

Đọc [Database Schema](../../overviews/Database-Schema.md) cùng [migration V1](../../../backend/src/main/resources/db/migration/V1__init_schema.sql), [V5](../../../backend/src/main/resources/db/migration/V5__update_schema_to_latest_spec.sql) và các migration tiếp theo để xác định cấu trúc bảng. Xác định schema sau khi áp dụng V1–V7, không chỉ đọc riêng V1. Ví dụ V1 định nghĩa `user_tenants.role` dạng số, V5 chuyển sang `role_id`.

| Bảng | Lưu điều gì? | Ràng buộc ảnh hưởng tới cách làm |
| --- | --- | --- |
| `tenants` | Thông tin nhà xe | PK `id`; `domain` duy nhất kể cả nhà xe đã khóa |
| `tenant_configs` | Tham số vận hành | `tenant_id` vừa là PK vừa tham chiếu Tenant, mỗi nhà xe tối đa một cấu hình |
| `roles` | Vai trò theo nhà xe | Duy nhất theo `(tenant_id, code)` |
| `permissions` | Danh mục hành động dùng chung | `code` duy nhất; dùng danh mục đã có |
| `role_permissions` | Vai trò có những quyền nào | PK `(role_id, permission_id)` |
| `users` | Tài khoản dùng chung hệ thống | `email` duy nhất |
| `user_tenants` | Người nào thuộc nhà xe nào, có role nào | PK `(user_id, tenant_id)`; `role_id` bắt buộc |

Từ PK của `user_tenants`, suy ra một tài khoản có thể thuộc nhiều nhà xe, nhưng trong một nhà xe chỉ có một membership và một role. Schema không giới hạn mỗi nhà xe chỉ có một chủ. Không cần tạo thêm User khi người đó đã thuộc nhà xe khác.

Với `roles`, hai nhà xe cùng có code `TENANT_ADMIN` nhưng hai bản ghi có ID khác nhau. Luôn tìm role bằng tenant và code. FK của `user_tenants.role_id` chỉ bảo đảm role tồn tại; Service phải chọn role đúng nhà xe, không nhận tùy ý `roleId` từ client.

### 2.2 Suy ra thứ tự ghi

1. Có nhà xe để lấy `tenantId`.
2. Có cấu hình gắn `tenantId`.
3. Có ba role của nhà xe và các liên kết tới permission.
4. Có User cho email mới, hoặc lấy User đã tồn tại.
5. Có membership chứa `userId`, `tenantId`, `roleId` và thời điểm gia nhập.

Tạo role chưa đồng nghĩa role có quyền. Bạn còn phải ghi `role_permissions`. Hãy liên tưởng role là thẻ nhân viên, permission là cửa được phép mở; phát thẻ mà chưa gán cửa thì người đó chưa có quyền đi đâu.

Không có trigger tự sinh cấu hình và vai trò cho mỗi lần tạo Tenant. Dữ liệu mẫu có sẵn là do migration chèn. Service phải làm phần khởi tạo cho nhà xe mới.

### 2.3 Chia việc kiểm tra thành ba tầng

| Tầng | Ví dụ | Vì sao đặt ở đây? |
| --- | --- | --- |
| DTO | Tên trống, domain sai mẫu, danh sách chủ rỗng | Có thể quyết định chỉ từ request |
| Service | Domain đã dùng, email mới thiếu mật khẩu, tài khoản bị khóa | Cần DB hoặc quy tắc có điều kiện |
| DB | UNIQUE, PK, FK | Chặn dữ liệu sai tại thời điểm ghi, kể cả hai request đồng thời |

Kiểm tra `existsByDomain` giúp báo lỗi dễ hiểu, nhưng không giữ chỗ domain. Hai request có thể cùng nhận kết quả “chưa tồn tại”. UNIQUE ở DB vẫn là chốt cuối. `@Transactional` cũng không tự biến bước kiểm tra thành khóa chống trùng.

**Đạt khi** Bạn tự vẽ được quan hệ của bảy bảng trên giấy và giải thích được thứ tự ghi bằng PK/FK.

**Câu hỏi tự trả lời** User đã là chủ nhà xe X, nay làm chủ nhà xe Y thì cần thêm những bản ghi nào, giữ nguyên những bản ghi nào?

**Từ khóa** `PostgreSQL primary key foreign key unique constraint`, `many to many association with role`, `race condition check then insert`.

## 3. Chốt hợp đồng API và chia lớp trước khi triển khai

### 3.1 Viết danh sách khả năng của module

Mọi đường dẫn dưới đây có tiền tố `/api/v1/tenants`. Tra payload và thông báo chính xác trong API spec.

| Việc | Method và phần đường dẫn | Hàm nghiệp vụ | Thành công |
| --- | --- | --- | --- |
| Tạo nhà xe | `POST /` | `createTenant` | 201 |
| Danh sách | `GET /` | `listTenants` | 200 |
| Chi tiết | `GET /{tenantId}` | `getTenantById` | 200 |
| Sửa thông tin | `PUT /{tenantId}` | `updateTenant` | 200 |
| Khóa/mở | `PATCH /{tenantId}/status` | `updateTenantStatus` | 200 |
| Xóa mềm | `DELETE /{tenantId}` | `deleteTenant` | 200 |
| Danh sách chủ | `GET /{tenantId}/admins` | `listTenantAdmins` | 200 |
| Thêm chủ | `POST /{tenantId}/admins` | `addTenantAdmin` | 201 |
| Tra email | `POST /admin-email-check` | `checkAdminEmail` | 200 |

Ký hiệu `/` ở hai dòng đầu nghĩa là đường dẫn gốc `/api/v1/tenants`, không yêu cầu thêm dấu `/` cuối khi gọi.

### 3.2 Mỗi lớp trả lời một loại câu hỏi

| Thành phần | Trách nhiệm | Liên hệ với Frontend |
| --- | --- | --- |
| Controller | Đọc HTTP, nhận DTO đã validation, lấy người gọi, gọi Service, bọc response | Lớp tiếp nhận sự kiện và chuyển việc xuống service |
| Service | Quy tắc nghiệp vụ, thứ tự kiểm tra/ghi, ranh giới transaction | Angular service chứa logic ứng dụng |
| Repository | Hỏi và ghi DB | Data access service; Spring tạo phần thực thi cho interface |
| Entity | Đối tượng ánh xạ trạng thái trong DB | Model dữ liệu có vòng đời lưu trữ |
| DTO | Hợp đồng trao đổi với client | Request type, view model hoặc schema cho form |

Chia `TenantService` cho nhà xe và `TenantAdminService` cho chủ. Tạo nhà xe gọi sang service chủ để dùng lại quy tắc kiểm tra/liên kết. Tránh đưa logic truy vấn hoặc tạo role vào Controller.

### 3.3 Xác định ranh giới bảo mật

Super Admin quản lý nhiều tenant ở cấp nền tảng. `tenantId` trong URL là **nhà xe mục tiêu**, `actorId` lấy từ principal là **người đang thao tác**. Hai giá trị này có mục đích khác nhau. Không nhận `actorId` từ body.

Khác với API Vehicle cho người trong nhà xe, API quản trị Tenant không bắt buộc lấy tenant mục tiêu từ `TenantContext`. Super Admin có thể đăng nhập với tenant context rỗng. Quyền Super Admin phải được backend kiểm tra trước khi thực hiện nghiệp vụ.

`TenantContext` của hệ thống dùng `ThreadLocal` để giữ ngữ cảnh theo luồng xử lý. Có thể liên hệ với context trong Frontend, nhưng ở backend thread có thể được dùng lại cho request khác nên filter phải dọn context trong `finally`. Không dùng field mutable của singleton Service để giữ tenant hoặc người gọi.

**Đạt khi** Với mỗi API, bạn chỉ được lớp nhận request, service chịu trách nhiệm và các bảng bị đọc/ghi.

**Câu hỏi tự trả lời** Vì sao cho phép Super Admin truyền tenant mục tiêu qua URL lại khác với cho phép STAFF tự truyền tenant bất kỳ qua body?

**Từ khóa** `Spring MVC layered architecture`, `IDOR authorization resource ownership`, `Java ThreadLocal remove thread pool`.

## 4. Chặng 1 — Chuẩn bị môi trường và danh sách file

### 4.1 Chuẩn bị để biết lỗi thuộc phần nào

1. Tạo nhánh làm việc cho module Tenant. Xác định phạm vi file phụ trách và các hợp đồng dùng chung với Auth, Account.
2. Đọc [README](../../../README.md) để chạy PostgreSQL và backend. Từ thư mục `backend`, chạy `mvn clean compile`, sau đó `mvn spring-boot:run`.
3. Xác nhận môi trường và ứng dụng chạy được trước khi viết module. Flyway quản lý schema; `ddl-auto: validate` kiểm tra ánh xạ entity với DB, không tự tạo bảng giúp bạn.
4. Đọc `AuthController`, `ApiResponse`, `AppException`, `UserPrincipal` để theo kiểu phản hồi và cách lấy người gọi của dự án.
5. Chuẩn bị tài khoản Super Admin cho kiểm thử. Với test tích hợp, tạo fixture trong DB test riêng; migration V1–V7 không cấp sẵn Super Admin. Trao đổi với người phụ trách Auth nếu cần tài khoản thử thủ công.

### 4.2 Bản đồ file cần tự xây dựng

Gốc đường dẫn Java bên dưới là `backend/src/main/java/com/carrental/car_rental_backend/tenant/`. Các tên liệt kê cùng một dòng đều nằm trong thư mục tương ứng.

| Đường dẫn dưới gốc | Trách nhiệm cần triển khai | Chặng |
| --- | --- | --- |
| `entity/Tenant.java` | Ánh xạ nhà xe, thống nhất các trường dùng chung với Auth | 2 |
| `entity/TenantConfig.java` | Ánh xạ cấu hình và cách khởi tạo mặc định | 2 |
| `dto/request/CreateTenantRequestDTO.java`, `UpdateTenantRequestDTO.java`, `UpdateTenantStatusRequestDTO.java` | Hợp đồng dữ liệu nhà xe | 3 |
| `dto/request/AdminAssignmentRequestDTO.java`, `AdminEmailCheckRequestDTO.java` | Hợp đồng gán chủ và tra email | 3 |
| `dto/response/TenantResponseDTO.java`, `CreateTenantResponseDTO.java` | Kết quả nhà xe và tạo nhà xe | 3 |
| `dto/response/AdminAssignmentResponseDTO.java`, `TenantAdminResponseDTO.java`, `AdminEmailCheckResponseDTO.java` | Kết quả các thao tác với chủ | 3 |
| `validation/MaxUtf8Bytes.java`, `MaxUtf8BytesValidator.java` | Giới hạn byte mật khẩu | 3 |
| `repository/TenantRepository.java` | Truy vấn nhà xe và hợp đồng tra cứu dùng chung với Auth | 4 |
| `repository/TenantConfigRepository.java` | Lưu cấu hình | 4 |
| `repository/TenantRoleRepository.java`, `TenantUserTenantRepository.java` | Truy vấn đặc thù Tenant trên entity của Account | 4 |
| `constant/DefaultRole.java`, `AdminRejection.java` | Bộ vai trò và lý do từ chối gán chủ | 4–5 |
| `service/TenantAdminService.java`, `service/impl/TenantAdminServiceImpl.java` | Hợp đồng và xử lý chủ; record `ValidatedAdmin` nằm trong interface | 5 |
| `service/TenantService.java`, `service/impl/TenantServiceImpl.java` | Hợp đồng và xử lý nhà xe | 6–7 |
| `controller/TenantController.java` | Kết nối HTTP với nghiệp vụ | 8 |

Tái sử dụng `account/entity/User`, `Role`, `UserTenant`, `UserTenantId`, cùng `UserRepository` và `UserTenantRepository`. Truy vấn chỉ Tenant dùng trên bảng Account đặt ở repository có tiền tố `Tenant`, giúp rõ quyền sở hữu và tránh trùng tên bean. Nếu một hợp đồng dùng chung còn thiếu, thống nhất với người phụ trách trước khi sửa phần đó.

### 4.3 Java Core cần ôn ngay tại chỗ sử dụng

| Cú pháp/khái niệm sẽ gặp | Cách hiểu để tự viết |
| --- | --- |
| Interface và `implements` | Interface công bố hợp đồng, Impl thực hiện hợp đồng đó. Controller phụ thuộc interface để rõ trách nhiệm và thay dependency khi test; Spring vẫn có thể tạo proxy cho class không có interface |
| `JpaRepository<Tenant, UUID>` và `extends` | Generic xác định kiểu entity và ID. Repository interface kế thừa hợp đồng có sẵn; bạn bổ sung câu hỏi dữ liệu riêng |
| `private final` ở dependency | Chỉ dùng trong lớp và không gán lại reference sau khởi tạo; không có nghĩa object được tham chiếu trở thành immutable |
| `List<T>`, `Set<T>`, vòng lặp `for` | Chọn collection theo cần giữ thứ tự hay kiểm trùng; đặt biến vòng lặp theo phần tử như `defaultRole`, `admin` |
| `Optional<T>` | Buộc nghĩ về nhánh thiếu dữ liệu; thiếu Tenant là lỗi, thiếu User khi gán chủ có thể là nhánh tạo mới |
| `.equals` với UUID/String | So giá trị thay vì dùng `==` để so reference; xét khả năng null trước khi gọi |
| `Boolean.TRUE.equals(...)` | So trạng thái Boolean có thể null mà không unbox null gây lỗi |
| `this` và biến cục bộ | `this` là object đang thực thi, không phải object mới cho mỗi request. Biến trong hàm thuộc lần gọi; object mà biến trỏ tới vẫn có thể được chia sẻ |

**Đạt khi** Bạn có baseline chạy được, biết thư mục làm việc và chỉ ra được file nào của mình, file nào là phụ thuộc.

**Câu hỏi tự trả lời** Ứng dụng đã không chạy trước khi bạn sửa thì làm sao phân biệt lỗi nền với lỗi vừa tạo?

**Từ khóa** `Maven clean compile lifecycle`, `Flyway versioned migrations`, `Hibernate ddl-auto validate`.

## 5. Chặng 2 — Viết Entity từ schema

### 5.1 Chọn kiểu Java có lý do

| Entity và trường | Kiểu Java | Điều cần hiểu |
| --- | --- | --- |
| `Tenant.id` | `UUID` | ID nhà xe, được sinh khi lưu entity mới |
| `name`, `domain`, `logoUrl`, `contactEmail`, `contactPhone` | `String` | Tên cột và độ dài phải đối chiếu schema |
| `planTier` | `PlanTierTenant` | Enum qua converter có sẵn, DB lưu mã số |
| `settings` | `String` | Chuỗi JSON được ánh xạ xuống cột `jsonb` |
| `isActive` | `boolean` | Khi dựng Tenant mới phải gán `true` rõ ràng |
| `createdAt`, `updatedAt` | `Instant` | Thời điểm tuyệt đối, trả ISO-8601 cho client |
| `TenantConfig.tenantId` | `UUID` | Dùng ID của Tenant, không sinh ID mới |
| `holdTimeoutMinutes` | `Integer` | Mặc định 30 phút |
| `lateHourlyPrice` | `BigDecimal` | Mặc định 100000 đồng/giờ |
| `defaultCommissionRate` | `BigDecimal` | Mặc định 5.00 nghĩa là 5%, không phải 0.05 |
| `TenantConfig.createdAt`, `updatedAt` | `Instant` | Thời gian tạo/cập nhật cấu hình |

`BigDecimal` phù hợp với số tiền thập phân, tránh sai số nhị phân của `double`. Tạo hằng số tiền từ chuỗi thập phân. `boolean` có mặc định `false`; mặc định ở DB không tự sửa giá trị sai mà builder đã gửi xuống.

### 5.2 Annotation và bản chất Java Core

Trước mỗi annotation, hỏi công cụ nào đọc metadata đó và đọc vào lúc biên dịch hay lúc chạy.

| Annotation/cơ chế | Dùng ở đâu | Bản chất cần nói được |
| --- | --- | --- |
| `@Entity`, `@Table`, `@Id`, `@Column` | Entity | Hibernate đọc metadata lúc runtime để biết class, field và khóa ánh xạ vào bảng nào |
| `@GeneratedValue` | `Tenant.id` | Hibernate thực hiện chiến lược sinh UUID; `TenantConfig.tenantId` lấy từ Tenant nên không gắn |
| `@JdbcTypeCode(SqlTypes.JSON)` | `Tenant.settings` | Metadata riêng Hibernate chọn cách bind/read JSON cho JDBC |
| `@CreationTimestamp`, `@UpdateTimestamp` | Các trường thời gian | Hibernate quản lý giá trị khi insert/update; thời điểm flush có ý nghĩa khi dựng response |
| `@DynamicUpdate` | `Tenant` | Hibernate so với trạng thái đã đọc để tạo UPDATE chỉ gồm các trường thay đổi; không tự phát hiện xung đột như `@Version` |
| Converter `autoApply` có sẵn | `planTier` | JPA gọi bộ chuyển enum ↔ mã DB; không thêm cách lưu enum khác chồng lên |
| `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder` | Entity | Lombok sinh phương thức/constructor lúc biên dịch, không phải Spring tự tạo chúng bằng AOP |

Entity cần constructor không tham số cho JPA khởi tạo. Tránh `@Data` trên entity vì nó sinh thêm `equals`, `hashCode`, `toString` mà bạn chưa chủ động kiểm soát.

Viết hợp đồng factory `withDefaults(UUID tenantId) → TenantConfig`. Hàm tạo cấu hình mới với ba giá trị mặc định, không truy vấn DB. Có thể liên hệ với hàm dựng giá trị ban đầu cho form. `static` chỉ mô tả cách gọi hàm; độ an toàn đa luồng còn phụ thuộc hàm có đụng trạng thái chung hay không.

**Đạt khi** Biên dịch qua, ứng dụng khởi động qua schema validation và bạn giải thích được vì sao cấu hình dùng chung ID với nhà xe. Schema validation không bảo đảm đã kiểm tra mọi index, constraint hoặc quy tắc nghiệp vụ.

**Câu hỏi tự trả lời** Nếu quên gán `isActive` khi dùng builder, Tenant mới sẽ ở trạng thái nào? Vì sao DEFAULT TRUE trong DB chưa chắc cứu được?

**Từ khóa** `Jakarta Persistence entity no arg constructor`, `Hibernate JSON JdbcTypeCode`, `Java BigDecimal String constructor`, `Lombok Builder default field`.

## 6. Chặng 3 — Viết DTO và validation

### 6.1 Request tạo và sửa nhà xe

| Trường | Kiểu Java | Tạo | Sửa | Ràng buộc |
| --- | --- | --- | --- | --- |
| `name` | `String` | Bắt buộc | Bắt buộc | `@NotBlank`, `@Size(max=255)` |
| `domain` | `String` | Bắt buộc | Bắt buộc | `@NotBlank`, `@Size(max=255)`, `@Pattern` theo mẫu trong spec |
| `planTier` | `PlanTierTenant` | Có thể thiếu, Service chọn FREE | Bắt buộc | `@NotNull` ở DTO sửa |
| `logoUrl` | `String` | Tùy chọn | Tùy chọn | `@Size(max=500)` |
| `contactEmail` | `String` | Tùy chọn | Tùy chọn | `@Email`, `@Size(max=255)` |
| `contactPhone` | `String` | Tùy chọn | Tùy chọn | `@Size(max=20)` |
| `settings` | `String` | Tùy chọn | Tùy chọn | Service đọc JSON để kiểm tra |
| `admins` | `List<AdminAssignmentRequestDTO>` | 1–10 phần tử | Không nằm trong DTO sửa | `@NotEmpty`, `@Size(max=10)`, `@Valid`, phần tử `@NotNull` |

Domain dùng mẫu `^[a-z0-9]+([.-][a-z0-9]+)*$`. Chữ hoa và khoảng trắng bị từ chối theo hợp đồng; không tự thêm bước chuyển domain viết hoa thành viết thường. Service trim `name` trước khi lưu.

### 6.2 Request liên quan tới chủ và trạng thái

| DTO | Trường và kiểu | Validation và trách nhiệm |
| --- | --- | --- |
| `AdminAssignmentRequestDTO` | `email: String` | `@NotBlank`, `@Email`, tối đa 255 |
| Cùng DTO trên | `fullName: String` | Tối đa 255 nếu gửi; Service bắt buộc khi email mới |
| Cùng DTO trên | `password: String` | Nếu gửi phải 8–72 ký tự, có chữ và số, tối đa 72 byte UTF-8; Service bắt buộc khi email mới |
| `UpdateTenantStatusRequestDTO` | `isActive: Boolean` | `@NotNull`, phân biệt không gửi với gửi `false` |
| `AdminEmailCheckRequestDTO` | `email: String`, `tenantId: UUID` | Email không trắng, tối đa 255; tenantId tùy chọn |

Với DTO tra email, dùng ràng buộc không trắng và độ dài; không yêu cầu `@Email` như DTO gán chủ. Hai API có trách nhiệm khác nhau: tra email để gợi ý cho form, gán chủ phải kiểm tra đầy đủ trước khi ghi. Kết quả tra email không thay thế validation của request gán chủ.

Không gắn `@NotBlank` vô điều kiện lên `fullName` và `password`. Nếu email đã có User hợp lệ, request chỉ cần email. Nhưng giá trị được gửi vẫn phải qua validation DTO trước Service. Mật khẩu rỗng/yếu vẫn bị từ chối dù tài khoản đã có; muốn giữ thông tin cũ thì không gửi hoặc gửi `null`.

### 6.3 Tự viết giới hạn byte UTF-8

BCrypt có giới hạn 72 byte đầu vào trong cách dùng của dự án. Một ký tự tiếng Việt có thể tốn nhiều byte UTF-8. Kiểm tra độ dài chuỗi chưa đủ để bảo vệ bước băm.

1. Khai báo annotation `MaxUtf8Bytes` có tham số `value`, thông báo, groups và payload.
2. Dùng metadata `@Constraint` liên kết tới `MaxUtf8BytesValidator`; retention runtime để validation provider đọc được. Target cho field và parameter.
3. Validator nhận giới hạn khi khởi tạo. Với giá trị `null`, cho qua để Service quyết định tính bắt buộc. Với chuỗi có giá trị, so độ dài mảng byte UTF-8 với giới hạn.
4. Gắn giới hạn 72 lên password. Không trim password vì sẽ làm thay đổi mật khẩu người dùng chọn.
5. Loại password khỏi `toString` của DTO bằng `@ToString.Exclude`. Không log nguyên request/body hoặc giá trị password từ lỗi validation.

Bean Validation đọc metadata constraint và gọi validator tương ứng lúc runtime. `@Valid` yêu cầu kiểm tra tiếp đối tượng con, nhưng không tự cấm phần tử `null`; vì vậy danh sách chủ cần cả cascade validation và ràng buộc phần tử. Xem [Spring Bean Validation](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html).

### 6.4 Response chỉ chứa dữ liệu được phép trả

| DTO | Các trường |
| --- | --- |
| `TenantResponseDTO` | `id: UUID`, các chuỗi `name/domain/logoUrl/contactEmail/contactPhone/settings`, `planTier: PlanTierTenant`, `isActive: Boolean`, `createdAt/updatedAt: Instant` |
| `CreateTenantResponseDTO` | `tenant: TenantResponseDTO`, `admins: List<AdminAssignmentResponseDTO>` |
| `AdminAssignmentResponseDTO` | `email: String`, `accountCreated: boolean` |
| `TenantAdminResponseDTO` | `userId: UUID`, `email/fullName: String`, `isActive: Boolean`, `joinedAt: Instant` |
| `AdminEmailCheckResponseDTO` | `email/fullName/reason: String`, `accountExists/canBeAdmin: boolean` |

DTO giống view model và schema form ở Frontend, nhưng validation phía backend là kiểm tra bắt buộc. Không đưa password hoặc password hash vào response.

**Đạt khi** Validator phân biệt được `admins` rỗng, phần tử null, email sai và password Unicode vượt byte. Email có sẵn cho phép thiếu họ tên/mật khẩu; password yếu được gửi lên vẫn báo lỗi.

**Câu hỏi tự trả lời** `Boolean` giải quyết vấn đề gì mà `boolean` không giải quyết được trong request khóa/mở?

**Từ khóa** `Jakarta Validation container element constraints Valid`, `ConstraintValidator null valid`, `Java String UTF-8 bytes length`, `Lombok ToString Exclude`.

## 7. Chặng 4 — Suy ra Repository và bộ vai trò

### 7.1 Đặt câu hỏi dữ liệu trước, đặt tên hàm sau

| Câu hỏi Service cần trả lời | Repository và hợp đồng |
| --- | --- |
| Domain này có ai dùng chưa? | `TenantRepository.existsByDomain(String domain) → boolean` |
| Domain có trùng nhà xe khác khi sửa? | `existsByDomainAndIdNot(String domain, UUID tenantId) → boolean` |
| Nhà xe có tồn tại? | `findById(UUID tenantId) → Optional<Tenant>` kế thừa từ JPA |
| Danh sách theo từ khóa/trạng thái/trang? | `search(String keywordPattern, Boolean isActive, Pageable pageable) → Page<Tenant>` |
| Lưu cấu hình? | `TenantConfigRepository`, dùng thao tác lưu kế thừa |
| Tạo role mặc định? | `TenantRoleRepository.insertSystemRole(tenantId, name, code, description) → void` |
| Gán tất cả quyền cho role? | `grantAllPermissions(UUID tenantId, String roleCode) → void` |
| Gán tập quyền đã chọn? | `grantPermissions(UUID tenantId, String roleCode, Collection<String> permissionCodes) → void` |
| Lấy role đúng nhà xe? | `findByTenantIdAndCode(UUID tenantId, String code) → Optional<Role>` |
| Lấy danh sách chủ? | `TenantUserTenantRepository.findAdmins(UUID tenantId) → List<TenantAdminResponseDTO>` |
| Email có tài khoản? | `UserRepository.findByEmail(String email) → Optional<User>` có sẵn |
| Người đã thuộc nhà xe chưa? | `UserTenantRepository.findByUserIdAndTenantId(userId, tenantId) → Optional<UserTenant>` có sẵn |
| Thêm quan hệ mà không ghi đè quan hệ đã có? | `TenantUserTenantRepository.insertMembershipIfNotExists(UUID userId, UUID tenantId, UUID roleId, Instant joinedAt) → int`: 1 là thêm được, 0 là quan hệ đã tồn tại |

Ba tham số đầu của `insertSystemRole` lần lượt có kiểu `UUID`, `String`, `String`; `description` cũng là `String`. Hai ID của hàm tìm membership đều là `UUID`. Giữ truy vấn `findAllAssignedTenantsByUserId` đang phục vụ Auth trong `TenantRepository`.

Spring Data tạo proxy thực thi repository interface. Với derived query, framework phân tích tên thuộc tính như `DomainAndIdNot`. Với `@Query`, framework đọc JPQL/SQL bạn khai báo. JPQL dùng tên entity/field Java, SQL thuần dùng tên bảng/cột DB. Xem [Spring Data query methods](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html).

### 7.2 Thuật toán truy vấn cần tự viết

**Tìm nhà xe** So tên hoặc domain viết thường với `keywordPattern`; trạng thái null nghĩa là không lọc. Nhận `Pageable` để phân trang ở DB. Không tải hết bảng rồi cắt danh sách trong Java.

**Tạo role** Chèn role có tenant mục tiêu, tên/code/mô tả và `is_system_default=true`. SQL dùng DB sinh ID và các mặc định phù hợp của bảng.

**Thêm thành viên** Chèn vào `user_tenants` với bốn giá trị `userId`, `tenantId`, `roleId`, `joinedAt`. Dùng `ON CONFLICT DO NOTHING`, trả số dòng được thêm. Nếu quan hệ đã có thì không đổi role hay thời điểm gia nhập. Kiểm tra trước giúp báo lỗi sớm; thao tác insert này vẫn cần thiết vì request khác có thể tạo quan hệ sau bước kiểm tra.

**Cấp quyền** Chọn đúng role theo cả `tenant_id` và `code`, ghép với danh mục permission, rồi chèn các cặp ID vào `role_permissions`. TENANT_ADMIN ghép toàn bộ danh mục; STAFF/SALE chỉ ghép các code được liệt kê. Không tạo lại bảng quyền hay nhân bản danh mục permission cho từng tenant.

**Danh sách chủ** Nối `UserTenant` với `User` bằng user ID và `Role` bằng role ID, lọc tenant mục tiêu và code `TENANT_ADMIN`, sắp `joinedAt` tăng dần, trả thẳng DTO với constructor expression. Kiểm tra thứ tự/kiểu tham số constructor DTO. Với các entity Account lưu ID thay vì quan hệ đối tượng, chỉ rõ điều kiện `JOIN ... ON`.

`@Modifying` thông báo cho Spring Data thực thi query khai báo theo nhánh ghi thay vì lấy kết quả đọc. Với insert thành viên, `flushAutomatically = true` đẩy các thay đổi JPA đang chờ xuống DB trước khi chạy SQL, để User/Tenant/role mới đã có khi kiểm tra khóa ngoại. Flush không phải commit; Service bên ngoài vẫn quản lý giao dịch và rollback.

### 7.3 Định nghĩa `DefaultRole`

Enum giữ `code`, `name`, `description` kiểu `String` và `permissionCodes: List<String>`. Hàm `grantsAllPermissions() → boolean` nhận biết trường hợp cấp toàn bộ quyền theo quy ước danh sách rỗng.

| Role | Danh mục quyền |
| --- | --- |
| `TENANT_ADMIN` | Toàn bộ permission có trong danh mục tại thời điểm tạo |
| `STAFF` | `booking:read`, `booking:create`, `booking:confirm_deposit`, `booking:handover`, `booking:return`, `booking:cancel`, `vehicle:read` |
| `SALE` | `booking:read`, `booking:create`, `booking:cancel`, `vehicle:read` |

Với danh mục của seed V5, số liên kết là 11 + 7 + 4 = 22. Số lượng phụ thuộc danh mục quyền, không phải giới hạn cố định. Việc thêm permission sau này không tự cấp ngược cho role của các tenant tạo trước đó.

**Đạt khi** Các query khởi động/thực thi được trên PostgreSQL và kiểm tra được đúng tập permission của từng role, không chỉ đếm số dòng. SQL cấp quyền chỉ tác động tenant mục tiêu.

**Câu hỏi tự trả lời** Bỏ điều kiện `tenant_id` trong SQL cấp quyền cho STAFF thì những nhà xe nào có thể bị ảnh hưởng?

**Từ khóa** `Spring Data JPA derived query existsBy`, `JPQL constructor expression unrelated entity join`, `Spring Data JPA Modifying native query`, `PostgreSQL insert select cross join`.

## 8. Chặng 5 — Xử lý chủ nhà xe trước khi ghép luồng tạo Tenant

### 8.1 Tách kiểm tra và ghi vì lý do gì?

Giống kiểm đủ giấy tờ của tất cả người nhận phòng trước khi bàn giao chìa khóa, hãy kiểm toàn bộ chủ trước khi bắt đầu ghi Tenant. Transaction vẫn cần thiết để xử lý lỗi phát sinh tại DB trong bước ghi.

Tạo hợp đồng trong `TenantAdminService` trước, rồi triển khai `TenantAdminServiceImpl`. Service được Spring quản lý bằng `@Service`. `@RequiredArgsConstructor` của Lombok sinh constructor cho các dependency `final` lúc compile; Spring dùng constructor đó để cung cấp bean khi khởi tạo. Đây là hai việc khác nhau. Xem [Lombok constructor](https://projectlombok.org/features/constructor).

| Hàm | Đầu vào | Đầu ra và bảo đảm |
| --- | --- | --- |
| `validateAdminsForNewTenant` | `List<AdminAssignmentRequestDTO> requests` | `List<ValidatedAdmin>`; kiểm tra trong ngữ cảnh chưa có Tenant, không ghi DB |
| `validateAdminRequests` | Danh sách request, `Tenant tenant` | Danh sách đã kiểm tra; tenant là null khi tạo mới |
| `addAdminsToTenant` | `UUID tenantId`, `UUID adminRoleId`, `List<ValidatedAdmin> validatedAdmins` | `List<AdminAssignmentResponseDTO>`; ghi các chủ đã kiểm tra |
| `addTenantAdmin` | `UUID tenantId`, `AdminAssignmentRequestDTO request`, `UUID actorId` | `AdminAssignmentResponseDTO`; điều phối thêm một chủ |
| `listTenantAdmins` | `UUID tenantId` | `List<TenantAdminResponseDTO>`; 404 nếu không có nhà xe |
| `checkAdminEmail` | `String email`, `UUID tenantId` tùy chọn | `AdminEmailCheckResponseDTO`; chỉ đọc |

`ValidatedAdmin` là record chứa `email/fullName/password: String` và `existingUser: User`. Nó giữ kết quả kiểm tra để bước ghi biết cần tạo tài khoản hay dùng tài khoản có sẵn. Hàm `isNewAccount() → boolean` căn cứ vào `existingUser` vắng hay có.

Record có các component reference không đổi sau khởi tạo, nhưng đối tượng `User` bên trong vẫn có thể mutable. Record cũng tự sinh `toString` bao gồm các component; tự kiểm soát `toString` của record này để không lộ password. Chỉ dùng nó bên trong service, không trả cho client và không lưu trong field singleton.

### 8.2 Dùng một bảng quyết định cho mọi API liên quan

Tạo enum `AdminRejection` giữ `errorCode: ErrorCode`, `message: String` và hợp đồng `toException() → AppException`. Helper `findRejection(Tenant tenant, User user) → Optional<AdminRejection>` kiểm tra theo thứ tự sau; không cần role ID vì đã thuộc nhà xe với vai trò nào cũng bị từ chối thêm.

| Ưu tiên | Điều kiện | Lý do | Khi API ghi dữ liệu |
| --- | --- | --- | --- |
| 1 | Có tenant và tenant đang khóa | `TENANT_LOCKED` | 400 `ERR_400` |
| 2 | Có User nhưng tài khoản không hoạt động | `ACCOUNT_LOCKED` | 400 `ERR_400` |
| 3 | User là Super Admin | `SUPER_ADMIN` | 400 `ERR_400` |
| 4 | Đã có membership trong tenant này, bất kể vai trò | `ALREADY_MEMBER` | 409 `TENANT_MEMBER_ALREADY_EXISTS` |

Không có User thì không có lỗi tài khoản/membership, nhưng tenant đang khóa vẫn bị từ chối trước. Khi chưa có tenant, chỉ xét lỗi tài khoản. Thông báo thành viên đã tồn tại là "Người dùng đã thuộc nhà xe này"; các thông báo khác lấy đúng từ DD. API tra email vẫn trả 200, dùng `canBeAdmin=false` và cùng thông báo trong `reason`.

### 8.3 Tự viết thuật toán kiểm tra danh sách

1. Tạo `emailsInRequest: Set<String>` và `validatedAdmins: List<ValidatedAdmin>` là biến cục bộ của lần gọi.
2. Với mỗi request, trim và chuyển email về chữ thường bằng `Locale.ROOT`. Nếu email đã có trong Set thì ném 400, kể cả khác nhau ở hoa/thường.
3. Tìm User theo email đã chuẩn hóa. Gọi helper tìm lý do từ chối; nếu có thì ném exception tương ứng.
4. Nếu User hợp lệ đã tồn tại, tạo kết quả kiểm tra với `existingUser`; không lấy họ tên/mật khẩu mới để cập nhật người đó.
5. Nếu email mới, bắt buộc họ tên và mật khẩu không trắng. Trim họ tên, giữ nguyên password, tạo kết quả kiểm tra dành cho tài khoản mới.
6. Giữ thứ tự danh sách. Chỉ trả kết quả khi toàn bộ phần tử qua kiểm tra.

`Set` tương đương JavaScript Set để kiểm tra trùng. `List` tương đương mảng giữ thứ tự. `Optional<User>` diễn đạt có thể không có kết quả, gần với `User | null` trong TypeScript nhưng có API xử lý riêng. Khi không có dữ liệu, cần quyết định rõ đó là kết quả hợp lệ hay một exception.

### 8.4 Tự viết thuật toán ghi chủ

1. Nhận tenant ID và admin role ID do service điều phối đã xác định, cùng danh sách đã kiểm tra.
2. Với mỗi phần tử mới, băm password qua `PasswordEncoder`, tạo User có email chuẩn hóa, họ tên, `isActive=true`, `isSuperAdmin=false`.
3. Với tài khoản có sẵn, dùng User đã tìm; giữ password hash và họ tên cũ.
4. Gọi `insertMembershipIfNotExists` với `userId`, `tenantId`, `roleId`, `joinedAt=Instant.now()`; nhận số dòng được thêm.
5. Nếu kết quả là 0, ném `AdminRejection.ALREADY_MEMBER.toException()`: trả 409 và rollback giao dịch. Không truy vấn lại để phân biệt role, không đổi vai trò hiện tại. Nếu là 1, tiếp tục xử lý.
6. Trả email và `accountCreated`; không trả password. Kết quả cùng thứ tự đầu vào.

Không dùng `UserTenantRepository.save` cho bước thêm quan hệ này: với entity có khóa đã gán, thao tác merge có thể cập nhật bản ghi mà request khác vừa tạo. Mục tiêu ở đây là chỉ thêm mới. Đổi vai trò thành viên là nghiệp vụ riêng, nằm ngoài phạm vi module.

Hàm ghi này là phần hỗ trợ cho luồng đã validation, không phải một endpoint công khai cho client truyền role bất kỳ. Tái sử dụng lõi trong module Tenant trước; việc chia sẻ với module Account sau này cần thống nhất hợp đồng giữa hai bên.

### 8.5 Ghép thêm chủ, danh sách chủ và tra email

**Thêm chủ** Tìm Tenant, không có thì 404. Lấy role `TENANT_ADMIN` của Tenant, thiếu role là lỗi dữ liệu hệ thống 500. Kiểm tra request trong ngữ cảnh đó, rồi ghi bằng lõi dùng chung và log actor/tenant/kết quả tạo tài khoản. Tìm role trước bước kiểm tra lý do từ chối: nếu Tenant vừa thiếu role vừa bị khóa thì phải báo lỗi thiếu role trước; nếu role đầy đủ thì áp dụng bảng từ chối ở mục 8.2.

**Danh sách chủ** Xác nhận nhà xe tồn tại rồi gọi repository trả DTO. Nhà xe tồn tại nhưng không có kết quả thì trả danh sách rỗng, không suy ra 404.

**Tra email** Nếu có tenant ID, tìm Tenant; thiếu Tenant trả 404. Chuẩn hóa email, tìm User, gọi cùng helper tìm lý do từ chối, rồi dựng các trường `accountExists`, `fullName`, `canBeAdmin`, `reason`. Có lý do từ chối thì vẫn trả 200 với `canBeAdmin=false`. Không tìm role chủ ở API này; khi thêm chủ, bước ghi phải tìm role để lấy ID gán quan hệ. API tra email không tạo User, không lưu Tenant và không giữ chỗ email.

`canBeAdmin=true` chỉ có nghĩa chưa gặp lý do từ chối ở thời điểm kiểm tra. API tạo/thêm vẫn kiểm tra lại. Email mới còn cần họ tên và password hợp lệ khi gửi thật. Dùng POST để email nằm trong body thay vì URL, giúp hạn chế việc email xuất hiện trong access log đường dẫn; vẫn cần tránh log body nhạy cảm.

**Đạt khi** Bạn kiểm được cả User mới/có sẵn, trùng email trong request, tài khoản khóa, Super Admin, đã là chủ và có role khác. Kết quả tra email và kết quả từ chối gán dùng cùng quy tắc.

**Câu hỏi tự trả lời** Email của chủ nhà xe X được nhập khi tạo nhà xe Y. Vì sao sửa password của User đó theo request mới sẽ gây nguy hiểm?

**Từ khóa** `Java record generated toString sensitive data`, `Java Optional isEmpty`, `HashSet add duplicate`, `Spring Security PasswordEncoder BCrypt`.

## 9. Chặng 6 — Ghép luồng tạo nhà xe và hiểu transaction

### 9.1 Hợp đồng cần có trước thân hàm

`TenantService.createTenant(CreateTenantRequestDTO request, UUID actorId) → CreateTenantResponseDTO`.

Trước điều phối, request đã qua validation DTO và người gọi đã được kiểm quyền. Thành công phải có nhà xe hoạt động, cấu hình mặc định, ba role và tập quyền tương ứng, cùng toàn bộ chủ trong request. Thất bại trong ghi DB phải rollback toàn bộ phần ghi của lần tạo này.

### 9.2 Viết thuật toán thành hai phần

**Phần kiểm tra, chưa ghi dữ liệu**

1. `checkDomainNotTaken(String domain)` hỏi repository, đã tồn tại thì 409 `TENANT_DOMAIN_ALREADY_EXISTS`.
2. `normalizeSettings(String settings) → String` đưa null/trắng về chuỗi `{}`; giá trị có nội dung phải đọc được bằng ObjectMapper, sai thì 400 `ERR_400`.
3. Gọi `validateAdminsForNewTenant` để kiểm toàn bộ chủ. Lưu kết quả vào `validatedAdmins`.

`settings` là chuỗi chứa JSON, khác với object JSON trực tiếp trong request. Dùng `ObjectMapper.readTree` để kiểm tra cú pháp; không áp dụng schema nội dung riêng chỉ cho phép một loại object cụ thể. Lưu ý giới hạn của bộ đọc với nội dung dư ở mục 14.

**Phần ghi, tất cả trong cùng giao dịch**

1. `saveTenant(request, settings) → Tenant` gán tên đã trim, domain hợp lệ, gói gửi lên hoặc FREE, các thông tin liên hệ, settings và `isActive=true`, rồi lưu.
2. Dùng ID nhà xe gọi `TenantConfig.withDefaults`, lưu cấu hình.
3. `createDefaultRoles(UUID tenantId) → void` lặp từng `defaultRole`, chèn role và cấp quyền theo nhánh toàn bộ/tập code.
4. `findAdminRoleOrThrow(UUID tenantId) → Role` lấy đúng role chủ vừa tạo; giữ ID trong biến `adminRoleId`.
5. Gọi `addAdminsToTenant` với danh sách đã kiểm tra. Đầu ra là kết quả từng chủ.
6. Map nhà xe thành `TenantResponseDTO`, ghép vào `CreateTenantResponseDTO` và log actor ID, tenant ID, số chủ.

Không đưa password vào log, kể cả log danh sách `ValidatedAdmin`. Giới hạn 10 chủ là giới hạn cho **một request tạo**, không phải nhà xe chỉ được có tối đa 10 chủ trong suốt vòng đời.

### 9.3 Transaction có tác dụng ở đâu?

Gắn `@Transactional` của Spring ở cấp lớp cho hai Service Impl. Annotation là metadata; transaction interceptor trên proxy đọc nó để bao quanh lời gọi vào Service. Phần quản lý DB do transaction manager phối hợp với JPA/JDBC thực hiện. Xem [Spring transactional annotations](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html).

Luồng cần hình dung là Controller → proxy TenantService → các thao tác DB và TenantAdminService → commit khi toàn bộ thành công. Như một lần bàn giao trọn bộ, chưa thể chốt nhận phòng nếu chưa cấp xong người quản lý.

`REQUIRED` mặc định nghĩa là có giao dịch bên ngoài thì tham gia, chưa có thì mở giao dịch. Do đó gọi từ TenantService sang TenantAdminService qua dependency do Spring cấp vẫn dùng chung giao dịch khi cùng transaction manager và luồng. Không dùng `REQUIRES_NEW` để ghi chủ, vì giao dịch độc lập sẽ phá mục tiêu cùng thành công/cùng thất bại. Xem [Spring propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html).

`AppException` của dự án kế thừa `RuntimeException`. Với cấu hình mặc định, RuntimeException/Error thoát ra khỏi phương thức giao dịch gây rollback; checked exception cần xem quy tắc riêng. Không nuốt lỗi rồi trả thành công. Xem [Transactional Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Transactional.html).

| Khái niệm | Hiểu trong luồng này |
| --- | --- |
| Persistence Context | Hibernate theo dõi entity và thay đổi trong phạm vi làm việc |
| `save` | Đưa entity vào thao tác persist/merge thích hợp, không có nghĩa đã commit |
| `flush` | Đồng bộ thay đổi đang chờ thành SQL gửi DB; vẫn có thể rollback |
| `commit` | Chốt giao dịch, không có lỗi mới xác nhận toàn bộ thành công |

Hai lời gọi `save` viết nối tiếp chạy tuần tự trong luồng này. Hibernate có thể trì hoãn SQL và flush khi cần hoặc lúc commit. Hãy liên tưởng ghi món vào phiếu rồi chuyển phiếu xuống bếp; chuyển phiếu không phải đã thanh toán xong. Native SQL tạo role cần nhìn thấy Tenant trước để thỏa FK, nên kiểm chứng thứ tự SQL trên PostgreSQL. Nếu sửa cách truy vấn, phải đánh giá lại nhu cầu flush.

### 9.4 Hai chỗ dễ trình bày sai

**Gọi hàm nội bộ** `this.updateTenantStatus(...)` không đi qua proxy thêm lần nữa. Tuy vậy `deleteTenant` được gọi từ Controller qua proxy và đã có giao dịch, nên hàm nội bộ tiếp tục chạy trong giao dịch đó. Vấn đề là annotation riêng của lời gọi nội bộ không được proxy áp dụng lại, không phải cứ thấy `this` là mất mọi transaction. Xem [Spring AOP proxying](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html).

**Chỉ đọc** Các hàm tra cứu dùng `@Transactional(readOnly=true)`. Đây là khai báo ý định và gợi ý tối ưu cho hạ tầng, không thay thế kiểm quyền và không nên dùng như bảo đảm tuyệt đối “không thể ghi”. Khi tham gia giao dịch bên ngoài, thuộc tính của giao dịch ngoài có vai trò quyết định. Xem [Spring Data transactionality](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html).

Service mặc định là singleton. Các biến `tenant`, `validatedAdmins`, `actorId` phải thuộc lần gọi, không lưu vào field dùng chung. Việc hai request chạy đồng thời cũng có nghĩa transaction của mỗi request độc lập. Tránh gọi email/OCR/thanh toán bên ngoài trong giao dịch khởi tạo này; giao dịch dài có thể giữ tài nguyên DB lâu. Băm nhiều mật khẩu cũng làm tăng thời gian xử lý, dù không phải gọi mạng.

**Đạt khi** Bạn giải thích được khác nhau giữa “kiểm hết trước khi ghi” và “rollback nếu bước ghi lỗi”, đồng thời chỉ được điểm bắt đầu/kết thúc giao dịch.

**Câu hỏi tự trả lời** Chủ thứ hai lỗi khi ghi, sau khi đã có Tenant và chủ thứ nhất. Những dữ liệu mới nào phải biến mất? User đã tồn tại từ trước có bị xóa không?

**Từ khóa** `Spring Transactional REQUIRED self invocation`, `Hibernate persistence context flush commit`, `Spring singleton service thread safety`, `transaction rollback runtime exception`.

## 10. Chặng 7 — Bổ sung các thao tác nhà xe còn lại

### 10.1 Chốt hợp đồng của `TenantService`

| Hàm | Đầu vào | Đầu ra |
| --- | --- | --- |
| `getTenantById` | `UUID tenantId` | `TenantResponseDTO` |
| `listTenants` | `String keyword`, `Boolean isActive`, `Pageable pageable` | `Page<TenantResponseDTO>` |
| `updateTenant` | `UUID tenantId`, `UpdateTenantRequestDTO request`, `UUID actorId` | `TenantResponseDTO` |
| `updateTenantStatus` | `UUID tenantId`, `boolean isActive`, `UUID actorId` | `TenantResponseDTO` |
| `deleteTenant` | `UUID tenantId`, `UUID actorId` | `void` |

Helper `findTenantOrThrow(UUID tenantId) → Tenant` tìm bằng ID và ném 404 nếu thiếu. Helper `toTenantResponse(Tenant tenant) → TenantResponseDTO` map tường minh các trường được phép trả. Viết helper khi đã thấy nhu cầu dùng lại, chưa cần thêm mapper framework.

### 10.2 Chi tiết và danh sách

**Chi tiết** Tìm nhà xe, thiếu thì 404, có thì map DTO. Super Admin vẫn xem được nhà xe khóa.

**Danh sách** Null/trắng keyword thành `%`; có nội dung thì trim, chuyển chữ thường với `Locale.ROOT`, bọc `%` hai đầu. Đặt tên biến `keywordPattern` vì đây đã là mẫu LIKE. Luôn truyền chuỗi vào query này. Gọi repository rồi dùng `Page.map` đổi entity sang DTO, giữ metadata phân trang.

`Page.map` gần với `Array.map` ở JavaScript nhưng giữ thêm thông tin trang. `Page` thường cần truy vấn đếm để biết tổng; Spring Data có thể bỏ qua count khi suy ra được tổng, nên không nói mọi lần đều chắc chắn đúng hai câu SQL. Mẫu tìm có `%` đầu và `LOWER` có thể hạn chế hiệu quả B-tree; chưa tự thêm index khi chưa đo query thực tế.

Không có kết quả tìm kiếm trả 200 với content rỗng. 404 dùng khi tìm **một tài nguyên cụ thể** không tồn tại. Theo quy tắc tìm kiếm này, `%`/`_` trong keyword có ý nghĩa wildcard của LIKE; nếu cần tìm literal thì phải thiết kế thêm cách escape và cập nhật hợp đồng.

### 10.3 Cập nhật và phân biệt null/rỗng

1. Tìm Tenant mục tiêu, thiếu thì 404.
2. Nếu domain khác hiện tại, kiểm trùng bằng `existsByDomainAndIdNot`; trùng nhà xe khác thì 409. Domain không đổi không tự đụng chính nó.
3. Ghi đè `name` đã trim, `domain` và `planTier` theo request hợp lệ.
4. Với `logoUrl`, `contactEmail`, `contactPhone`, không gửi/null thì giữ nguyên; gửi chuỗi trắng/rỗng thì xóa thành null; có nội dung thì trim và thay giá trị.
5. Với `settings`, không gửi/null thì giữ nguyên; trắng/rỗng thành chuỗi `{}`; có giá trị thì kiểm JSON rồi thay.
6. Dùng `saveAndFlush` rồi dựng DTO để giá trị thời gian cập nhật do Hibernate sinh đã phản ánh trong response khi có thay đổi được ghi. Không sửa `tenant_configs` ở API này.

`saveAndFlush` lưu và đẩy thay đổi xuống DB ngay trong giao dịch, không commit riêng. Xem [JpaRepository](https://docs.spring.io/spring-data/jpa/reference/api/java/org/springframework/data/jpa/repository/JpaRepository.html).

`Tenant` dùng `@DynamicUpdate` để chỉ ghi các trường đã thay đổi. Ví dụ request sửa tên đọc được `isActive=true`, sau đó request khác khóa nhà xe và commit. Khi request sửa tên tiếp tục lưu, nó không ghi lại cột `is_active` vì không thay đổi trường đó, nên nhà xe vẫn bị khóa. Đây không phải khóa chống mọi cập nhật đồng thời: hai request cùng sửa tên vẫn có thể ghi đè nhau.

Đây là hợp đồng cập nhật cụ thể của dự án dù endpoint dùng PUT. Đừng tự suy ra mọi field bị bỏ qua đều phải xóa chỉ từ tên HTTP method. Trong Frontend, cần phân biệt không gửi trường với gửi chuỗi rỗng khi muốn xóa.

### 10.4 Khóa, mở và xóa mềm

1. Tìm Tenant, thiếu thì 404.
2. Nếu trạng thái đã đúng với yêu cầu, trả dữ liệu hiện tại, không ghi thêm.
3. Nếu khác, đổi `isActive`, lưu/flush, log người thao tác và trả DTO.
4. `deleteTenant` tái sử dụng đổi trạng thái về false; không gọi xóa vật lý. Controller trả thành công không có data.

Xóa mềm và khóa dùng chung cờ `is_active` ở thiết kế này, chưa có trạng thái deleted riêng. Dữ liệu con và domain vẫn được giữ, có thể mở lại. Dùng xóa vật lý có thể kéo theo cascade xóa dữ liệu liên quan.

Phần Auth/Security kiểm trạng thái Tenant ở request mới, nên khóa có thể chặn cả token còn hạn của thành viên. Module Tenant chỉ đổi trạng thái, không tự sửa filter hoặc thu hồi từng token. Request đã qua kiểm tra trước thời điểm khóa không bị hủy ngược. Super Admin và tenant khác vẫn có ngữ cảnh riêng.

**Đạt khi** Kiểm được ba trạng thái không gửi/null/rỗng của trường tùy chọn, domain giữ nguyên, danh sách rỗng và khóa lặp cùng trạng thái.

**Câu hỏi tự trả lời** Tại sao xóa mềm xong không được tạo nhà xe khác dùng lại domain đó? Khi gửi `contactPhone=null`, số cũ phải còn hay mất?

**Từ khóa** `Spring Data Page map Pageable`, `SQL LIKE wildcard index`, `JPA saveAndFlush dirty checking`, `soft delete unique constraint idempotent`.

## 11. Chặng 8 — Viết Controller mỏng và nối toàn bộ luồng

### 11.1 Những annotation này được ai xử lý?

| Annotation | Ý nghĩa và cơ chế |
| --- | --- |
| `@RestController`, `@RequestMapping` | Spring MVC đọc metadata để đăng ký handler; giá trị trả được chuyển thành HTTP body |
| `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping` | Metadata ánh xạ method/path vào phương thức Java |
| `@RequestBody`, `@PathVariable`, `@RequestParam` | Argument resolver và bộ chuyển đổi dựng tham số Java từ JSON, URL, query |
| `@Valid` | Kích hoạt validation request trước khi gọi thân Controller, rồi kiểm tiếp object con theo metadata |
| `@AuthenticationPrincipal` | Resolver lấy principal từ ngữ cảnh xác thực, không lấy user ID do client khai |
| `@PreAuthorize` | Method Security kiểm biểu thức qua interceptor trước khi thực thi method được bảo vệ |
| `@RequiredArgsConstructor` | Lombok sinh constructor dependency lúc compile; Spring inject qua constructor lúc runtime |

`@PreAuthorize("hasRole('SUPER_ADMIN')")` đặt ở cấp lớp để áp dụng cho các endpoint. `hasRole` dùng tiền tố `ROLE_` theo cấu hình mặc định, tương ứng authority đã được Security dựng. Dự án đã bật Method Security trong `SecurityConfig`; không cần tạo hệ phân quyền riêng cho Tenant.

### 11.2 Cách nối từng endpoint

1. Dùng DTO cụ thể và ID kiểu `UUID` cho path, không nhận entity làm request.
2. Gọi đúng service trong bảng chín API ở mục 3. Lấy `actorId` từ `UserPrincipal` cho thao tác ghi cần log.
3. Dựng `PageRequest` ở API danh sách. Page mặc định 0, âm thành 0; size mặc định 10, kẹp 1–100; sort cố định `createdAt` giảm dần rồi `id` giảm dần. ID duy nhất xác định thứ tự khi trùng thời điểm tạo, không dùng để suy ra thời gian tạo. Cách này giữ thứ tự ổn định khi dữ liệu không đổi; thêm/xóa giữa hai lần tải vẫn có thể làm dịch trang dùng offset.
4. Service trả `Page<TenantResponseDTO>`, Controller bọc thành `PagedModel<TenantResponseDTO>` rồi đặt vào `ApiResponse`, để JSON có content và metadata trang.
5. Bọc kết quả bằng `ApiResponse.success`, dùng 201 cho tạo nhà xe/thêm chủ, 200 cho các việc còn lại, thông báo theo spec.
6. Không băm mật khẩu, nối SQL, kiểm domain hoặc điều phối role trong Controller.

`ApiResponse.success` là static factory dựng response. Hiểu nó như hàm tạo một object kết quả trong TypeScript; mỗi lần tạo response mới không có nghĩa mọi helper static đều an toàn với trạng thái dùng chung.

Với token hợp lệ, Spring MVC có thể phát hiện body sai khi resolve tham số trước khi tới kiểm quyền method. Vì vậy người không phải Super Admin gửi body sai có thể nhận 400 trước 403 trong luồng này; request hợp lệ của họ vẫn phải bị 403. Không có token hợp lệ bị chặn ở tầng Security trước đó. Kiểm chứng thứ tự bằng HTTP thay vì suy đoán từ vị trí annotation.

**Đạt khi** Cả chín endpoint có đúng status/response; không có token nhận 401, người của nhà xe gửi request hợp lệ nhận 403, Super Admin thao tác được.

**Câu hỏi tự trả lời** Ẩn nút “Tạo nhà xe” trên Frontend đã đủ ngăn Tenant Admin gọi API này chưa?

**Từ khóa** `Spring MVC argument resolvers RequestBody Valid`, `Spring Security method security PreAuthorize hasRole`, `Spring Data web PagedModel`.

## 12. Chặng 9 — Kiểm chứng bằng kết quả quan sát được

### 12.1 Kiểm từng chặng, rồi kiểm toàn luồng

Với mỗi hành vi, ghi rõ input, kết quả mong đợi và dữ liệu DB phải còn/mất. JUnit 5 tổ chức test; Mockito giả dependency để kiểm logic Service. Một test mock ném lỗi không chứng minh được rollback PostgreSQL.

Theo AAA, **Arrange** chuẩn bị dữ liệu và dependency; **Act** gọi đúng một hành vi; **Assert** kiểm kết quả và tác động. Ví dụ domain trùng cần kiểm exception và không có bước tạo Tenant/cấu hình/chủ được gọi. Ví dụ rollback phải kiểm bằng giao dịch thật, sau lỗi không còn dữ liệu mới.

Gốc test là `backend/src/test/java/com/carrental/car_rental_backend/tenant/`.

| File/nhóm test cần tự viết | Điều phải chứng minh |
| --- | --- |
| `dto/CreateTenantRequestDTOTest.java`, `dto/AdminAssignmentRequestDTOTest.java`, `dto/TenantContactEmailValidationTest.java` | Validation danh sách/field/độ dài và Unicode byte |
| `service/ValidatedAdminTest.java` | Không lộ password qua `toString`, phân biệt tài khoản mới/có sẵn |
| `service/impl/TenantAdminServiceImplTest.java` | Bảng từ chối, dùng lại User và không đổi thông tin cũ |
| `service/impl/TenantServiceImplTest.java` | Kiểm tra trước ghi, mặc định, phối hợp role/chủ, update/null/rỗng |
| `TenantApiIntegrationTest.java` | HTTP tạo, DB phụ đầy đủ, đăng nhập chủ và rollback sau lỗi ghi |
| `TenantAdminIntegrationTest.java` | Thêm/xem/tra email chủ, bảo mật dữ liệu trả về |
| `TenantMembershipConcurrencyIntegrationTest.java` | Thành viên tồn tại, quan hệ xuất hiện sau validation, hai request cùng thêm một người và rollback khi ghi nhiều chủ |
| `TenantUpdateAndListIntegrationTest.java` | Tìm/lọc/phân trang, update, status, xóa mềm |
| `TenantAccessControlIntegrationTest.java` | Không token, sai quyền và người thuộc nhà xe thử khóa nhà xe khác |

### 12.2 Bảng nghiệm thu tối thiểu

| Tình huống | Kỳ vọng cần quan sát |
| --- | --- |
| Tạo với một chủ mới | 201; có Tenant active, một config, ba role, quyền đúng tập, một User mới và membership đúng role |
| Tạo với chủ đã có tài khoản ở nhà xe khác | 201; thêm membership, giữ nguyên họ tên và hash cũ |
| Danh sách có chủ thứ hai không hợp lệ | 400/409 theo nguyên nhân; chưa ghi dữ liệu của request tạo |
| DB từ chối khi đang ghi chủ thứ hai | Lỗi; rollback Tenant/config/role/quyền/User/membership mới; dữ liệu có từ trước vẫn còn |
| Domain đã có được phát hiện trước ghi | 409 `TENANT_DOMAIN_ALREADY_EXISTS` |
| Hai email chỉ khác hoa/thường trong một request | 400, không tạo một phần |
| Password Unicode vượt 72 byte | 400 `VALIDATION_ERROR` trước bước băm; field lỗi đúng vị trí |
| Email có sẵn và gửi thêm password yếu | 400 validation, không đổi tài khoản |
| Tenant không tồn tại | 404 cho thao tác nhắm vào tenant đó |
| Tenant tồn tại nhưng danh sách/trang không có kết quả | 200 và danh sách/content rỗng |
| Thêm chủ đã thuộc nhà xe với role chủ, STAFF hoặc SALE | Đều trả 409 `TENANT_MEMBER_ALREADY_EXISTS`; role hiện tại giữ nguyên |
| Quan hệ xuất hiện sau validation, trước insert | Insert trả 0; Service báo 409, giữ nguyên role và `joinedAt` của quan hệ có sẵn |
| Hai request cùng thêm một người có tài khoản vào cùng nhà xe | Một request trả 201, một request trả 409; chỉ có một quan hệ |
| Ghi chủ mới đầu tiên rồi xung đột quan hệ của chủ tiếp theo | Rollback cả User và quan hệ vừa tạo ở bước trước; giữ dữ liệu do giao dịch khác đã commit |
| Tra email chưa có User | 200, `accountExists=false`; vẫn cần họ tên/password khi tạo thật |
| Tra email với tenant khóa | 200, `canBeAdmin=false`, kể cả email mới |
| Trường tùy chọn không gửi/null/rỗng khi sửa | Giữ/giữ/xóa theo quy tắc; settings rỗng thành `{}` |
| Sửa thông tin bằng dữ liệu đã đọc trước khi request khác khóa Tenant | Thông tin được cập nhật, `is_active` vẫn false sau khi lưu |
| Nhiều Tenant trùng `createdAt`, tải các trang khi dữ liệu không đổi | Thứ tự `createdAt DESC, id DESC`; không trùng hoặc mất bản ghi giữa các trang |
| Gọi khóa hai lần | Cùng trạng thái cuối; lần thứ hai không ghi đổi trạng thái |
| Xóa mềm | Dòng Tenant và dữ liệu liên quan còn; `is_active=false` |
| Thành viên dùng token cũ sau khi Tenant bị khóa | Request mới bị Security từ chối; kiểm riêng tài khoản Super Admin và tenant khác |
| Không token / sai quyền với request hợp lệ | 401 / 403 |
| JSON/UUID/enum sai kiểu | 400, phân biệt mã lỗi theo API spec |
| Hai request tranh chấp UNIQUE domain/email lúc ghi | DB không lưu trùng; transaction thất bại phải rollback; lỗi ghi thuộc xử lý exception chung và có thể là 500. Trùng quan hệ thành viên phải được xử lý riêng và trả 409 |

Đối với SQL cấp quyền, chuẩn bị hai tenant rồi kiểm thao tác trên Y không thay đổi role/quyền của X. Đây là kiểm tra cô lập dữ liệu, dù người gọi là Super Admin.

Kiểm riêng các giá trị biên để tránh chỉ thử trường hợp vượt giới hạn. Danh sách chủ chấp nhận 1 và 10 phần tử, từ chối 0 và 11; phần tử null phải báo đúng field `admins[0]`. Password Unicode hợp lệ đúng 72 byte phải tạo tài khoản và đăng nhập được, 73 byte phải bị chặn trước ghi qua cả API tạo nhà xe và thêm chủ. Email liên hệ hợp lệ dài 255 ký tự được chấp nhận, 256 ký tự bị chặn ở cả tạo/sửa và không làm thay đổi dữ liệu. Kiểm `toString` của cả DTO chủ, request tạo nhà xe và record nội bộ không chứa password.

Với bảng từ chối, thử cả STAFF và SALE có sẵn trong tenant; thử tenant khóa đồng thời tài khoản khóa để xác nhận ưu tiên `TENANT_LOCKED`. Danh sách chủ chỉ chứa TENANT_ADMIN và sắp theo ngày tham gia. Gọi xóa mềm lần hai vẫn thành công với trạng thái cuối không đổi.

### 12.3 Cách chạy và giới hạn kết luận

Từ `backend`, chạy `mvn clean test` khi nghiệm thu. Docker cần hoạt động cho nhóm integration test dùng PostgreSQL Testcontainers. Tạo lớp nền `support/ApiIntegrationTestSupport` để khởi tạo DB test riêng và fixture Super Admin, cung cấp hàm gọi HTTP rồi kiểm DB. Dữ liệu thử phải độc lập với DB phát triển cá nhân.

Khi chỉ kiểm một lớp, dùng tùy chọn Maven `-Dtest` cho lớp đó. Chỉ ghi nhận kết quả kiểm thử dựa trên lần chạy thực tế. Phân biệt lỗi Docker/môi trường với assertion fail. Không đổi migration chung hoặc xóa dữ liệu DB cá nhân để ép test xanh.

Để kiểm tra rollback, gây lỗi ghi có kiểm soát trong DB test cô lập, rồi kiểm từ kết nối ngoài request sau khi request kết thúc. Không tạo trigger phá dữ liệu trên DB dùng chung. Chuẩn bị ca kiểm thử từ đầu vào, kết quả mong đợi và dữ liệu cần kiểm tra, sau đó triển khai theo nhóm file ở mục 12.1.

**Đạt khi** Có bằng chứng từ test, HTTP và DB cho các bảo đảm chính; bạn nói rõ phần đã kiểm và phần mới chỉ suy luận.

**Câu hỏi tự trả lời** Test có hai chủ nhưng thất bại ở bước validation có chứng minh được rollback sau khi đã ghi User thứ nhất không?

**Từ khóa** `JUnit 5 Mockito Arrange Act Assert`, `Spring Boot Testcontainers PostgreSQL ServiceConnection`, `integration test transaction rollback HTTP request`.

## 13. Những quyết định cần giải thích được khi trình bày

| Quyết định | Lý do | Phương án khác và điều phải cân nhắc |
| --- | --- | --- |
| Tạo Tenant cùng ít nhất một chủ | Nhà xe mới có người quản lý ngay | Tạo rỗng rồi gán sau cần trạng thái nháp và luồng hoàn tất riêng |
| Tái sử dụng User | Tài khoản đăng nhập dùng chung nhiều tenant | Tạo User trùng email vừa sai ràng buộc vừa tách danh tính |
| Role riêng cho mỗi Tenant | Dữ liệu quyền thuộc từng nhà xe | Role dùng chung toàn hệ thống làm thay đổi mô hình quản trị quyền |
| Kiểm hết chủ rồi ghi | Phát hiện lỗi nghiệp vụ sớm và tránh ghi không cần thiết | Ghi xen kẽ vẫn cần rollback nhưng khó theo dõi hơn |
| Một transaction cho khởi tạo | Tránh Tenant thiếu config/role/chủ khi một bước ghi lỗi | Các giao dịch độc lập cần cơ chế bù và thiết kế phức tạp hơn |
| Enum lý do từ chối dùng chung | API tra email và gán chủ theo cùng quy tắc | Copy điều kiện nhiều nơi dễ lệch lý do/thứ tự ưu tiên |
| Insert thành viên nếu chưa có, báo 409 nếu đã tồn tại | Tránh ghi đè role do request khác tạo sau validation | Dùng `save` có thể merge quan hệ đã có; đổi role cần nghiệp vụ riêng |
| `@DynamicUpdate` trên Tenant | Tránh ghi lại trạng thái cũ khi request chỉ sửa thông tin | Không thay thế `@Version` để phát hiện hai request cùng sửa một trường |
| Thêm `id` vào thứ tự phân trang | Phân định các Tenant có cùng thời điểm tạo | Chỉ sắp theo `createdAt` chưa xác định thứ tự khi giá trị trùng nhau |
| Xóa mềm dùng cờ active | Giữ dữ liệu liên quan và tránh cascade xóa vật lý | Xóa vật lý hoặc trạng thái deleted riêng cần thiết kế vòng đời khác |
| Map DTO tường minh | Dễ nhìn trường được phép trả và thuận tiện học | Mapper thư viện cần thêm dependency/quy ước chung |
| Tách Repository đặc thù Tenant | Giảm va chạm với Account và tránh trùng tên bean | Thêm vào repository chung cần thống nhất quyền sở hữu |
| Giữ Controller mỏng | Quy tắc nghiệp vụ dùng lại và kiểm thử được | Logic dồn vào HTTP handler khó tái sử dụng và khó trình bày |

## 14. Giới hạn thiết kế và phụ thuộc

| Vấn đề | Giới hạn cần lưu ý | Hướng mở rộng hoặc phối hợp |
| --- | --- | --- |
| UNIQUE domain/email bị đụng khi hai request ghi đồng thời | Precheck không giữ chỗ dữ liệu; lỗi UNIQUE tại bước ghi thuộc xử lý exception chung và có thể trả 500. Trùng thành viên phải trả 409 bằng insert-if-not-exists | Thống nhất ánh xạ 409 với người phụ trách phần chung nếu cần |
| Hai người cùng sửa một trường của Tenant | `@DynamicUpdate` chỉ tránh ghi lại trường không đổi; phát hiện xung đột theo version nằm ngoài phạm vi | Thiết kế `@Version` kèm migration và hợp đồng xung đột khi cần |
| Chuỗi `settings` có nội dung dư như `"{} {}"` | `ObjectMapper.readTree` có thể bỏ qua phần dư, khiến DB từ chối và trả 500 | Kiểm thử dữ liệu có phần dư; thống nhất yêu cầu kiểm tra toàn bộ JSON khi mở rộng validation |
| FK membership → role chỉ tham chiếu role ID | Service phải chọn role theo tenant; một FK tới role ID không đủ chặn mọi cách gán chéo tenant | Đánh giá composite FK cùng người phụ trách DB |
| Khóa và xóa mềm cùng cờ | Một giá trị false biểu diễn cả “đã khóa” và “đã xóa” | Chốt vòng đời nếu sản phẩm cần phân biệt |
| Tài khoản mới do Super Admin đặt password | Luồng tạo tài khoản không bao gồm mời người dùng hoặc ép đổi password lần đầu | Thống nhất yêu cầu với Auth nếu mở rộng |
| Danh mục permission thay đổi sau khi Tenant được tạo | Role đã tạo không tự nhận bản ghi permission mới | Thiết kế cập nhật quyền có kiểm soát |
| Giới hạn 10 chủ trong request | Không phải quota tổng số chủ của Tenant | Nếu cần quota phải bổ sung quy tắc nghiệp vụ riêng |

`GlobalExceptionHandler` và `ErrorCode` thuộc phần dùng chung. Dùng các mã trong hợp đồng API, gồm `TENANT_MEMBER_ALREADY_EXISTS`; nếu thiếu mã hoặc cần thay đổi cách ánh xạ lỗi, thống nhất với người phụ trách phần chung. Không mở rộng handler trong phạm vi triển khai Tenant.

Vai trò BRANCH_MANAGER, quota gói, chống trùng giờ booking và thay đổi schema toàn hệ thống cần được xác định trong tài liệu module tương ứng; không đưa vào phạm vi Tenant khi hợp đồng chưa yêu cầu.

## 15. Tự đánh giá và luyện báo cáo

### 15.1 Ba mức hiểu

Mức 1 là nhận diện và nói đúng thuật ngữ. Mức 2 là tự mô tả luồng, viết được và kiểm tra kết quả. Mức 3 là giải thích được “vì sao”, dự đoán lỗi và nêu giới hạn. Chỉ tự đánh dấu sau khi đã trả lời bằng lời hoặc có bằng chứng.

| Chủ đề | Mức 1/2/3 tự ghi | Bằng chứng bạn cần đưa ra |
| --- | --- | --- |
| Tenant/User/Role/Permission | — | Giải thích User thuộc hai nhà xe, role khác ID |
| DTO và Service validation | — | Phân loại ít nhất năm ca lỗi đúng tầng |
| Entity và schema | — | Giải thích PK config, converter và mặc định |
| Luồng tạo nhà xe | — | Mô tả thứ tự các bước và bảng cần ghi |
| Transaction/flush/commit | — | Phân tích lỗi ở chủ thứ hai và cách kiểm tra rollback |
| Quyền Super Admin | — | Chỉ ra nguồn authority và điểm kiểm quyền |
| Update/null/rỗng/xóa mềm | — | Dự đoán chính xác dữ liệu sau request |
| Query, pagination và cô lập tenant | — | Đọc được điều kiện JOIN/WHERE và kết quả trang |
| Bảo vệ password | — | Phân biệt byte/ký tự, hash và log |
| Giới hạn thiết kế | — | Nói đúng race condition và phụ thuộc module chung |

### 15.2 Thứ tự tự trả lời câu hỏi

1. Vì sao module Tenant không chỉ CRUD một bảng?
2. Từ schema, làm sao suy ra phải có role trước membership?
3. Vì sao User có sẵn chỉ được liên kết, không đổi thông tin theo form?
4. Kiểm tra nào ở DTO, kiểm tra nào ở Service, chốt nào ở DB?
5. Kiểm toàn bộ request trước ghi rồi còn cần transaction làm gì?
6. `saveAndFlush` khác commit thế nào?
7. Tại sao `this.updateTenantStatus` trong delete vẫn có thể nằm trong transaction?
8. Super Admin chưa chọn tenant có gọi module này được không?
9. Vì sao email check thành công chưa bảo đảm tạo nhà xe thành công?
10. Mỗi bảo đảm cần được kiểm thử bằng cách nào, và điểm nào cần thống nhất với module khác?

### 15.3 Khung trình bày một phút

Tự điền bằng lời của bạn theo năm ý. Mục tiêu module và người dùng → dữ liệu cần có khi tạo → thứ tự kiểm tra/ghi → transaction và kiểm quyền → cách kiểm thử và giới hạn thiết kế. Khi bị hỏi sâu, chọn một request cụ thể rồi đi từ Controller xuống DB, nêu cả nhánh thất bại.

Khi báo cáo tiến độ, nêu bốn ý: hạng mục thực hiện, bằng chứng kiểm thử, tình huống còn vướng cùng dự đoán nguyên nhân, và bước tiếp theo để xác nhận. Gắn kết quả với hành vi cụ thể, tránh chỉ nói chung chung “xong Service”.

## 16. Tài liệu tham khảo và vị trí triển khai

| Tài liệu hoặc đường dẫn | Mục đích |
| --- | --- |
| [DD Tenant](2026-10-19-tenant-detail-design.md), [API spec Tenant](2026-10-19-tenant-api-spec.md) | Quy tắc R1–R9, hợp đồng chín API, thứ tự từ chối và giới hạn |
| [Onboarding](../../overviews/onboarding.md) | Kiến trúc, quy ước và quan hệ giữa Auth/Account/Tenant |
| [Architecture](../../overviews/Architecture-Diagram.md), [Multi-Tenant/Multi-Branch](../../overviews/Multi-Tenant-Multi-Branch.md) | Phân biệt quản trị nền tảng với tác nghiệp của nhà xe |
| [Database Schema](../../overviews/Database-Schema.md), [migration](../../../backend/src/main/resources/db/migration) | Bảng, khóa và tiến trình thay đổi schema |
| [Meeting 22/10](../../meeting-note/22-10-2026.md) | Bối cảnh thảo luận proxy, persistence context, transaction và pagination; đọc cùng nguồn chính thức để tránh khái quát quá mức |
| [Thư mục Java của Tenant](../../../backend/src/main/java/com/carrental/car_rental_backend/tenant) | Vị trí đặt Entity, DTO, Repository, Service và Controller theo mục 4.2 |
| [Thư mục test Tenant](../../../backend/src/test/java/com/carrental/car_rental_backend/tenant), [test support](../../../backend/src/test/java/com/carrental/car_rental_backend/support/ApiIntegrationTestSupport.java) | Vị trí tổ chức kiểm thử logic, HTTP, DB và rollback theo mục 12 |
