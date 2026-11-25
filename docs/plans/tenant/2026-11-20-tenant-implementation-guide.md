# Hướng dẫn triển khai module Tenant

Khởi tạo ngày 20/11/2026, cập nhật ngày 25/11/2026. Module Tenant quản lý hồ sơ nhà xe trên Car Rental SaaS. Mục tiêu là tự triển khai từng API và giải thích được luồng HTTP → Service → Repository, phạm vi quyền, dữ liệu được ghi và cách kiểm chứng.

## Tiến độ và lộ trình

| Chức năng | Trạng thái và phạm vi |
|---|---|
| `POST /api/v1/tenants` | Đã triển khai ở commit `fe5f153` trên `feature/huynq7`. Super Admin tạo nhà xe, bộ cấu hình, role, quyền và gán chủ từ các tài khoản có sẵn. |
| `GET /api/v1/tenants` | Đã chọn cách làm, đang ở bước chuẩn bị. Dùng lại `TenantResponseDTO`, Service trả `Page`, Controller chuyển sang `PageResponse`. Bắt đầu bằng `common/dto/PageResponse.java`. |
| `GET /api/v1/tenants/{id}` | Đề xuất cho bước tiếp theo. Trả hồ sơ và `tenantAdmins` gồm `userId`, `fullName`, `email`, `isActive`. Tra chủ theo `user_tenants` của nhà xe đang xem và role `TENANT_ADMIN` thuộc nhà xe đó. |
| `PATCH /api/v1/tenants/{id}` | Đề xuất cập nhật hồ sơ. Cần chốt trường nào được sửa, quy tắc cho giá trị rỗng và cách xử lý domain. |
| `PATCH /api/v1/tenants/{id}/status` | Đề xuất khóa hoặc mở khóa. Cần chốt tác động lên người đang làm việc trong nhà xe và ca kiểm thử với Auth. |
| Gói cước, cấu hình vận hành, role và permission | Cần đặc tả riêng cho từng luồng và quyền gọi. |

Car Rental SaaS là modular monolith cho nhiều nhà xe và chi nhánh. `SUPER_ADMIN` quản trị toàn hệ thống. `TENANT_ADMIN`, `STAFF` và `SALE` có vai trò theo từng nhà xe. Auth phụ trách phiên và Tenant Picker, còn Account phụ trách tài khoản. Luồng Tenant Picker đọc các nhà xe được gán cho người đăng nhập. API danh sách quản trị phục vụ Super Admin trên toàn hệ thống. [Kiến trúc](../../overviews/Architecture-Diagram.md) · [Phân quyền theo tenant và chi nhánh](../../overviews/Multi-Tenant-Multi-Branch.md)

Bản build tại mốc đối chiếu dùng Java 17 và Spring Boot 4.0.7. Flyway quản lý schema và Hibernate dùng `ddl-auto: validate` để kiểm tra mapping khi khởi động. [POM](../../../backend/pom.xml) · [Cấu hình backend](../../../backend/src/main/resources/application.yaml) · [Hướng dẫn chạy và Swagger](../../../README.md)

## Thứ tự đọc và cách học

1. Đọc quy tắc học và review của repo, rồi xem phần quy chuẩn Controller, Service, Repository, DTO, phân trang và transaction. [AGENTS.md](../../../AGENTS.md) · [CLAUDE.md](../../../CLAUDE.md) · [Quy chuẩn Java/Spring](../../tutorials/JAVA_SPRING_CODE_CONVENTIONS.md)
2. Đọc nghiệp vụ từ onboarding, đối chiếu ràng buộc với schema và migration đang dùng. Xem phần transaction, Hibernate write-behind và `Pageable` trong ghi chú kỹ thuật ngày 22/10. [Onboarding](../../overviews/onboarding.md) · [Schema](../../overviews/Database-Schema.md) · [Ghi chú kỹ thuật](../../meeting-note/22-10-2026.md)
3. Đọc phần API đang làm trong guide này, sau đó xem source ở mục review. Nếu có sổ tay cá nhân trong workspace, mục B1.0 của `docs/local/so-tay-du-an.md` ghi tiến độ lần làm lại, mục A3 ghi các bài học Java, debugging và phân trang.

Mỗi lần chọn một bước nhỏ để tự viết, giải thích lý do rồi kiểm chứng. Khi review chéo, giữ review ở chế độ chỉ đọc và để người học tự sửa. Với mỗi phát hiện, nêu `file:dòng`, tình huống cụ thể, hành vi hiện tại, hành vi mong đợi và cách kiểm chứng. Phân biệt kết quả đọc source với giả thiết về chức năng còn ở mức thiết kế.

## Tạo nhà xe

**Bằng chứng hiện có.** Lần thử Swagger ngày 24/11 trả `201`, `planTier` là `FREE` và có hai timestamp. Response khi đó dùng `active`. Thử riêng với Jackson đã xác nhận annotation trên getter cho ra `isActive`. Các ca cần xác nhận trên endpoint sau commit gồm JSON `isActive`, ID lặp `400`, domain trùng `409` và phân quyền `401/403`. Rollback và hai request cùng domain cần kiểm tra với PostgreSQL. Thư mục test hiện chỉ có `CarRentalBackendApplicationTests.java`.

### 🏛️ BỨC TRANH TOÀN CẢNH (MỞ NHÀ XE VÀ CẤP QUYỀN QUẢN LÝ)

Hình dung Super Admin là bộ phận tiếp nhận doanh nghiệp mới vào nền tảng:

- Hồ sơ doanh nghiệp nằm trong `tenants`.
- Nội quy vận hành riêng nằm trong `tenant_configs`.
- Ba nhóm chức vụ của doanh nghiệp nằm trong `roles`.
- Các hành động mỗi nhóm được phép làm nằm trong quan hệ `role_permissions`.
- Danh sách ai thuộc doanh nghiệp, giữ chức vụ nào nằm trong `user_tenants`.

Tài khoản người dùng là hồ sơ định danh chung do **Account** phụ trách. Khi một người làm chủ thêm nhà xe Y, bổ sung quan hệ với Y; không tạo lại tài khoản hay thay đổi quyền của người đó ở nhà xe X.

```text
Frontend: nhập thông tin nhà xe và chọn các tài khoản chủ có sẵn
       ↓
Spring Security: xác thực người gọi, kiểm tra quyền SUPER_ADMIN
       ↓
TenantController: nhận và kiểm tra request
       ↓
TenantServiceImpl: kiểm tra nghiệp vụ, điều phối một giao dịch
       ↓
Repository → PostgreSQL
  tenants → tenant_configs
          → roles → role_permissions
          → user_tenants dùng role TENANT_ADMIN của nhà xe mới
       ↓
TenantResponseDTO → ApiResponse → Frontend
```

**Liên hệ frontend:** Request DTO giống hợp đồng dữ liệu của form; Service là nơi thực hiện quy tắc nghiệp vụ phía server. Validation trên form không thay thế kiểm tra backend.

#### Các quyết định nghiệp vụ đã chốt

| Nội dung | Quy tắc |
|---|---|
| Người tạo | Chỉ Super Admin đã xác thực |
| Số chủ | Ít nhất một, cho phép nhiều chủ trong cùng request |
| Tài khoản chủ | Đã tồn tại và đang hoạt động; dùng danh sách userId |
| Tạo tài khoản | Account thực hiện độc lập trước khi gọi API Tenant |
| Gói ban đầu | Luôn `FREE` |
| Trạng thái ban đầu | `isActive = true` |
| Settings ban đầu | JSON rỗng `{}` |
| Cấu hình vận hành | Giữ chỗ 30 phút, phạt trả trễ 100.000 đồng/giờ, hoa hồng 5% |
| Role ban đầu | Tạo đủ `TENANT_ADMIN`, `STAFF`, `SALE` riêng cho nhà xe mới |
| Quyền | Liên kết với permission dùng chung đã có; không tạo permission mới |
| Tính toàn vẹn | Tạo Tenant/config/role/quyền/membership thành công cùng nhau hoặc hoàn tác toàn bộ |

Nếu API Account đã tạo tài khoản thành công trước đó nhưng API Tenant thất bại, **giữ tài khoản**. Giao dịch tạo Tenant chỉ hoàn tác dữ liệu do chính thao tác tạo Tenant ghi vào.

---

### 🗺️ MỤC LỤC LỘ TRÌNH 5 GIAI ĐOẠN

1. **DTO Request & Response:** xác định dữ liệu nhận vào và trả ra.
2. **Entity & Repository:** ánh xạ đúng schema, dùng lại dữ liệu phục vụ Auth.
3. **Phân quyền & Mã lỗi:** sử dụng hạ tầng Security và xử lý lỗi hiện có.
4. **Service:** viết thuật toán tạo nhà xe trong một giao dịch.
5. **Controller:** nối hợp đồng HTTP với Service.

Sau đó kiểm tra phối hợp frontend/Account và checklist nghiệm thu. Đây là thứ tự đọc hướng dẫn; khi thực hiện từng hành vi, áp dụng **test thất bại → code đủ để test thành công → refactor**, theo mục 8.2 onboarding.

---

### 📦 GIAI ĐOẠN 1: XÂY DỰNG CÁC DTO NHẬN & TRẢ DỮ LIỆU (`tenant/dto/`)

#### 1. File `CreateTenantRequestDTO.java`

- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/tenant/dto/request/CreateTenantRequestDTO.java`.
- **💡 Vai trò thực tế:** Tờ phiếu tạo nhà xe; chứa dữ liệu client được phép cung cấp.
- **Annotations cho class:** `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder` theo cách trình bày của guide Auth.
- **Bản chất:** Lombok sinh constructor, getter/setter và builder lúc biên dịch. DTO không phải Entity và không tự lưu DB.

| Thuộc tính | Kiểu | Validation và ý nghĩa |
|---|---|---|
| `name` | `String` | `@NotBlank`, `@Size(max = 255)`; tên nhà xe |
| `domain` | `String` | `@NotBlank`, `@Size(max = 255)`; Service kiểm tra trùng trong DB |
| `adminUserIds` | `List<UUID>` | `@NotEmpty`; từng phần tử không được null; Service kiểm tra tài khoản |
| `logoUrl` | `String` | Tùy chọn, `@Size(max = 500)` |
| `contactEmail` | `String` | Tùy chọn, `@Email`, `@Size(max = 255)` |
| `contactPhone` | `String` | Tùy chọn, `@Size(max = 20)` |

- Không thêm `password`, email dùng để tạo tài khoản, `tenantId`, `roleId`, `planTier`, `isActive`, `settings` hoặc ba cấu hình vận hành vào DTO tạo nhà xe.
- Các giới hạn độ dài theo schema. Không tự áp giới hạn 10 chủ của bản cũ khi chưa có yêu cầu.
- Dùng thông báo validation tiếng Việt. Client không cần biết tên bảng hoặc câu SQL để sửa lỗi.
- Với danh sách UUID, ràng buộc phần tử là `@NotNull` ở kiểu phần tử; đây không phải danh sách DTO con cần cascade validation. `@Valid` trên request ở Controller kích hoạt Bean Validation.
- Service từ chối danh sách lặp userId bằng HTTP 400 trước khi tra tài khoản. So kích thước danh sách với tập UUID duy nhất để phát hiện lặp.
- **Cần chốt:** định dạng/chuẩn hóa domain trước khi thêm regex hoặc tự chuyển chữ thường. Onboarding dùng Single Domain + Tenant Picker, không yêu cầu tạo routing subdomain. Không tự đổi dữ liệu tenant có sẵn.

**Từ khóa tra cứu:** `Jakarta Bean Validation container element constraints`, `Spring MVC request body validation`.

#### 2. File `TenantResponseDTO.java`

- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/tenant/dto/response/TenantResponseDTO.java`.
- **💡 Vai trò thực tế:** Bì thư kết quả tạo nhà xe; không trả Entity ra client.
- **Annotations cho class:** `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`.
- **Hợp đồng hiện tại:**

| Thuộc tính | Kiểu Java |
|---|---|
| `id` | `UUID` |
| `name`, `domain`, `logoUrl`, `contactEmail`, `contactPhone` | `String` |
| `planTier` | `PlanTierTenant` |
| `isActive` | `boolean` |
| `createdAt`, `updatedAt` | `Instant` |

Map thủ công qua builder hoặc hàm chuyển đổi nhỏ theo quy chuẩn. Jackson dùng `@JsonProperty("isActive")` trên getter để trả đúng tên JSON `isActive`. Enum và timestamp theo cấu hình Jackson của dự án.

**Từ khóa tra cứu:** `Java DTO manual mapping builder`, `Jackson enum serialization`.

---

### 🗄️ GIAI ĐOẠN 2: ENTITY & REPOSITORY (SỔ SÁCH DỮ LIỆU)

#### 1. Entity `Tenant.java` và `TenantRepository.java`

- **Đường dẫn Entity:** `backend/src/main/java/com/carrental/car_rental_backend/tenant/entity/Tenant.java`.
- **Đường dẫn Repository:** `backend/src/main/java/com/carrental/car_rental_backend/tenant/repository/TenantRepository.java`.
- Dùng lại hai file nền đang phục vụ Auth; giữ truy vấn lấy danh sách nhà xe được phân công.
- Đối chiếu từng cột với V1: UUID tự sinh, chiều dài chuỗi, `plan_tier` qua converter, JSONB cho `settings`, trạng thái và timestamp.
- `@Entity`, `@Table`, `@Id`, `@Column` cung cấp metadata để JPA ánh xạ đối tượng Java vào bảng/cột. `@GeneratedValue` khai báo cách cấp ID; `@JdbcTypeCode(SqlTypes.JSON)` chỉ định cách Hibernate làm việc với cột JSON.
- `ddl-auto: validate` không tạo/sửa bảng; annotation Entity không thay thế constraint trong migration.
- **Timestamp:** lựa chọn phù hợp với User là `@CreationTimestamp` và `@UpdateTimestamp` do Hibernate cấp khi thực hiện INSERT/UPDATE. Hiểu thời điểm giá trị được gán trước khi đưa chúng vào response.
- **Truy vấn cần bổ sung:** `existsByDomain(String domain)` trả boolean để kiểm tra trùng sớm. Spring Data phân tích tên method để tạo truy vấn; không cần tự viết SQL cho kiểm tra này.
- Khi tạo, chủ động gán `FREE`, `true`, `{}`. DEFAULT của DB chỉ có tác dụng khi câu INSERT dùng default hoặc bỏ cột đó, không tự sửa NULL/false do ORM gửi xuống.

**Từ khóa tra cứu:** `Spring Data JPA derived query existsBy`, `Hibernate CreationTimestamp UpdateTimestamp`, `JPA flush vs commit`.

#### 2. Entity `TenantConfig.java`

- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/tenant/entity/TenantConfig.java`.
- **💡 Vai trò thực tế:** Nội quy vận hành riêng của nhà xe.
- **Annotations:** `@Entity`, `@Table(name = "tenant_configs")`, `@Id`, `@Column`, `@Getter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`; dùng timestamp theo cùng chiến lược với Tenant.

| Thuộc tính | Kiểu Java | Ý nghĩa |
|---|---|---|
| `tenantId` | `UUID` | Vừa PK, vừa FK đến tenants; lấy ID nhà xe mới, không tự sinh ID khác |
| `holdTimeoutMinutes` | `Integer` | Khởi tạo 30 phút |
| `lateHourlyPrice` | `BigDecimal` | Khởi tạo 100.000 đồng; precision 12, scale 2 |
| `defaultCommissionRate` | `BigDecimal` | Khởi tạo 5%; precision 5, scale 2 |
| `createdAt`, `updatedAt` | `Instant` | Thời điểm tạo/cập nhật |

Để bắt đầu, ánh xạ FK bằng UUID tường minh; chưa cần thêm quan hệ `@OneToOne` nếu luồng không dùng điều hướng đối tượng. DB bảo vệ PK/FK, thời gian giữ chỗ lớn hơn 0, giá không âm và hoa hồng từ 0 đến 100.

**Repository:** `tenant/repository/TenantConfigRepository.java`, quản lý `TenantConfig` với khóa `UUID`. Không cần repository tùy biến cho thao tác lưu thông thường.

**Từ khóa tra cứu:** `JPA shared primary key UUID column`, `Java BigDecimal monetary values`.

#### 3. Dùng lại dữ liệu `account`

- `UserRepository`: dùng `findAllById(...)` để đọc nhóm tài khoản; không đọc từng chủ bằng một query nếu không cần.
- `RoleRepository`: lưu ba role của nhà xe mới. Giữ các đối tượng/ID vừa tạo để gán membership và permission.
- `PermissionRepository`: bổ sung truy vấn theo danh sách code nếu cần; kiểm tra đủ các permission dự kiến.
- `UserTenantRepository`: lưu membership với `tenantId`, `userId`, `roleId`, `joinedAt`. Dùng lại Entity `UserTenant` và khóa `UserTenantId` hiện có.
- Không tạo thêm Entity User hoặc bộ repository thứ hai cho cùng bảng chỉ để mang tên Tenant. Việc bổ sung hợp đồng dùng chung cần phối hợp người phụ trách Account.
- Không dùng `RoleRepository.findByCode("TENANT_ADMIN")` để lấy role của nhà xe mới: nhiều tenant có cùng code. Dùng role vừa tạo, hoặc truy vấn có cả tenantId và code.
- **Phạm vi mã role.** DB ràng buộc duy nhất theo `(tenant_id, code)`. Entity hiện phản ánh phạm vi này bằng cách bỏ `unique=true` ở cột `code`.

`UserTenant` dùng `@IdClass`: JPA ghép `userId` và `tenantId` thành định danh. Một người có thể thuộc nhiều nhà xe nhưng mỗi cặp người–nhà xe chỉ có một membership, giữ một role tại một thời điểm. FK `(role_id, tenant_id)` buộc role thuộc đúng nhà xe.

#### 4. Ánh xạ `role_permissions`

Mapping và repository hiện có trong Account gồm các file sau.

- `account/entity/RolePermissionId.java`: hai trường UUID `roleId`, `permissionId`; khóa phải có constructor phù hợp, `equals/hashCode` và đáp ứng yêu cầu khóa ghép của JPA, tương tự `UserTenantId`.
- `account/entity/RolePermission.java`: ánh xạ bảng `role_permissions`, dùng `@IdClass`, hai trường `@Id` tương ứng. Không sinh một ID thứ ba.
- `account/repository/RolePermissionRepository.java`: thao tác lưu các liên kết bằng khóa ghép đó.

Chọn cách ánh xạ tường minh để thấy rõ dữ liệu cần ghi, chưa cần thêm quan hệ `@ManyToMany` hoặc native INSERT. `saveAll` không đồng nghĩa một câu INSERT duy nhất; kiểm chứng SQL thực tế khi học đến phần này.

**Từ khóa tra cứu:** `JPA IdClass composite primary key`, `Spring Data JPA saveAll SQL statements`.

---

### 🛡️ GIAI ĐOẠN 3: PHÂN QUYỀN & HỢP ĐỒNG LỖI

#### 1. Dùng hạ tầng Security hiện có

- JWT filter xác thực và nạp `UserPrincipal`/authorities vào SecurityContext.
- Dùng `@PreAuthorize` để yêu cầu role `SUPER_ADMIN` tại điểm vào nghiệp vụ phù hợp, nhất quán với conventions.
- `@PreAuthorize` được thực hiện qua method interceptor/proxy của Spring Security, kiểm tra người gọi trước khi chạy method. Tên class hay tham số client không tự chứng minh quyền.
- Không lấy tenantId từ TenantContext làm đích tạo: Super Admin có thể chưa chọn tenant; ID đích là ID nhà xe vừa tạo.
- Không viết lại SecurityConfig/JWT chỉ để thêm chức năng này. Kiểm chứng 401/403 bằng request thật hoặc kiểm thử Security.

**Từ khóa tra cứu:** `Spring Security method security PreAuthorize hasRole`.

#### 2. Các lỗi cần thể hiện

| Tình huống | HTTP | Mã lỗi nghiệp vụ |
|---|---|---|
| Chưa xác thực | 401 | Dùng `UNAUTHORIZED` hiện có |
| Không phải Super Admin | 403 | Dùng `FORBIDDEN` hiện có |
| Request sai định dạng/thiếu trường | 400 | Dùng validation/JSON handler hiện có |
| Danh sách có userId lặp | 400 | `TENANT_ADMIN_IDS_DUPLICATED` |
| Chủ không tồn tại | 404 | `TENANT_ADMIN_NOT_FOUND` |
| Chủ bị khóa | 400 | `TENANT_ADMIN_INACTIVE` |
| Domain đã tồn tại | 409 | `TENANT_DOMAIN_ALREADY_EXISTS` |
| Thiếu permission để khởi tạo | 500 | `TENANT_DEFAULT_PERMISSION_MISSING` |

Các mã lỗi nghiệp vụ trong bảng đã được khai báo trong ErrorCode. Trạng thái HTTP được GlobalExceptionHandler lấy từ AppException.

Không sửa GlobalExceptionHandler trong phạm vi này. Handler hiện có xử lý DataIntegrityViolationException bằng 409 và thông báo chung; lỗi UNIQUE khi hai request cùng domain có thể đi qua nhánh đó. Không cam kết mọi lỗi 409 đều có mã Tenant riêng nếu chưa thiết kế việc chuyển đổi lỗi cụ thể.

---

### 💼 GIAI ĐOẠN 4: LOGIC NGHIỆP VỤ (`tenant/service/`)

#### 1. File `TenantService.java` & `TenantServiceImpl.java`

- **Đường dẫn Interface:** `backend/src/main/java/com/carrental/car_rental_backend/tenant/service/TenantService.java`.
- **Đường dẫn Implementation:** `backend/src/main/java/com/carrental/car_rental_backend/tenant/service/impl/TenantServiceImpl.java`.
- **Hợp đồng:** `createTenant` nhận `CreateTenantRequestDTO`, trả `TenantResponseDTO`.
- **Annotations cho class Impl:** `@Service`, `@RequiredArgsConstructor`.
- **Dependencies:** các repository của Tenant, TenantConfig, User, Role, Permission, RolePermission và UserTenant đã xác định ở giai đoạn 2.
- Khai báo dependency `private final`; Lombok sinh constructor, Spring cung cấp bean qua constructor. `final` giữ tham chiếu không bị gán lại, không tự làm đối tượng được tham chiếu bất biến.
- Chỉ dùng biến cục bộ cho dữ liệu của một request; không giữ danh sách chủ hoặc tenant đang tạo trong field của Service.

#### 2. Quy trình xử lý `createTenant`

1. **Kiểm tra danh sách chủ:** So kích thước danh sách với tập UUID duy nhất. Nếu có ID lặp, trả lỗi TENANT_ADMIN_IDS_DUPLICATED.
2. **Kiểm tra domain:** gọi truy vấn kiểm tra trùng để trả thông báo dễ hiểu. UNIQUE của DB vẫn là lớp bảo vệ cuối cùng.
3. **Kiểm tra tài khoản:** lấy nhóm User theo ID; xác định ID không tìm thấy và tài khoản không hoạt động. Có bất kỳ tài khoản không hợp lệ thì dừng cả thao tác.
4. **Kiểm tra bộ permission khởi tạo:** tra permission từ danh mục chung; thiếu mã cần thiết thì báo lỗi hệ thống, không tạo role với bộ quyền thiếu.
5. **Tạo Tenant:** gán `name`, `domain`, `logoUrl`, `contactEmail`, `contactPhone` và các mặc định `FREE`, `true`, JSON `{}`. Lưu để lấy ID dùng cho dữ liệu phụ thuộc. `adminUserIds` được dùng ở bước gán chủ.
6. **Tạo TenantConfig:** dùng đúng ID đó, gán 30 phút, 100.000 đồng và 5%.
7. **Tạo ba role:** cùng tenantId mới, code TENANT_ADMIN/STAFF/SALE; đánh dấu role mặc định theo thiết kế, giữ lại ID từng role.
8. **Tạo role_permissions:** ghép ID role mới với ID permission đã có theo bộ quyền tương ứng.
9. **Gán chủ:** với từng tài khoản hợp lệ, tạo UserTenant dùng tenantId mới và ID role TENANT_ADMIN vừa tạo; gán joinedAt. Không thay đổi User hoặc membership của người đó ở tenant khác.
10. **Đồng bộ và trả kết quả:** nếu dùng timestamp do Hibernate sinh, flush trước khi map các giá trị đó vào DTO. Map tường minh và trả TenantResponseDTO; transaction proxy hoàn tất commit trước khi kết quả đi tiếp tới Controller.

Hai request cùng domain có thể cùng vượt qua bước 2; một request phải bị DB từ chối. Không bắt lỗi ghi rồi tiếp tục lưu để cố trả thành công.

Không cần logic “đã thuộc nhà xe thì đổi role” trong luồng này: tenant là bản ghi mới, danh sách chủ phải hợp lệ và không lặp. Lưu membership tường minh trước khi cân nhắc native SQL/ON CONFLICT cho một bài toán khác.

#### 3. Transaction và Persistence Context cần hiểu

- Đặt `@Transactional` trên method ghi cụ thể của Service Impl theo conventions, không đặt ở Controller.
- Khi gọi qua bean được Spring quản lý, transaction interceptor/proxy mở hoặc tham gia giao dịch, gọi method, rồi commit/rollback theo kết quả. Các repository tham gia cùng giao dịch khi dùng chung transaction manager.
- AppException hiện kế thừa RuntimeException, phù hợp rollback mặc định. Không nuốt exception; nếu chọn checked exception phải hiểu và khai báo quy tắc rollback tương ứng.
- Persistence Context quản lý các Entity trong giao dịch; Hibernate có thể trì hoãn SQL tới lúc flush. UUID đã được cấp không tự chứng minh INSERT đã chạy hoặc giao dịch đã commit.
- Flush đồng bộ thay đổi đang chờ xuống DB; commit kết thúc giao dịch. Sau flush, lỗi ở bước sau vẫn có thể làm toàn bộ giao dịch rollback.
- Không thêm flushAutomatically/clearAutomatically khi đang dùng các thao tác JPA thông thường và chưa có bài toán bulk query. Cần giải thích được hành vi trước khi chọn kỹ thuật.
- Không gọi API Account, email hoặc dịch vụ ngoài trong giao dịch tạo Tenant. Việc tạo tài khoản là request riêng trước đó.
- Giao dịch không tự chứng minh tài khoản không thể bị khóa đồng thời sau bước kiểm tra. Nếu yêu cầu phải bảo đảm trạng thái chủ tới đúng thời điểm commit, cần phân tích concurrency cùng luồng khóa tài khoản; chưa tự thêm cơ chế khóa khi chưa có yêu cầu.

**Liên hệ frontend:** frontend gọi hai API không tạo thành một giao dịch DB chung; nếu API thứ hai thất bại thì dữ liệu API thứ nhất không tự biến mất.

**Từ khóa tra cứu:** `Spring transaction proxy self invocation`, `Hibernate persistence context flush commit`, `Spring transaction rollback RuntimeException`.

#### 4. Bộ permission khởi tạo

Dùng bộ quyền trong V2 làm mẫu nghiệp vụ, không copy role đã được tenant khác tùy chỉnh:

- **TENANT_ADMIN:** đủ 11 permission nghiệp vụ hiện tại trong V2.
- **STAFF:** booking:read, booking:create, booking:confirm_deposit, booking:handover, booking:return, booking:cancel, vehicle:read.
- **SALE:** booking:read, booking:create, booking:cancel, vehicle:read.

Không hardcode UUID role/permission của dữ liệu demo. Mã quyền ban đầu dùng để khởi tạo liên kết DB; khi phân quyền request phải đọc quyền thực tế, không dùng bộ mặc định để ghi đè tùy chỉnh của nhà xe. Khi danh mục thêm quyền mới, rà soát bộ khởi tạo thay vì mặc nhiên cấp quyền mới cho mọi role. Chốt bộ quyền chi tiết trước khi viết phần này.

---

### 🌐 GIAI ĐOẠN 5: REST API CONTROLLER (`tenant/controller/`)

#### File `TenantController.java`

- **Đường dẫn:** `backend/src/main/java/com/carrental/car_rental_backend/tenant/controller/TenantController.java`.
- **Annotations:** `@RestController`, `@RequestMapping`, `@RequiredArgsConstructor`; endpoint dùng `@PostMapping`, `@RequestBody`, `@Valid` và kiểm tra quyền đã xác định.
- **Endpoint:** `POST /api/v1/tenants`.
- **Đầu vào:** CreateTenantRequestDTO.
- **Đầu ra:** `201 Created`, dữ liệu bọc trong `ApiResponse<TenantResponseDTO>`.
- Controller chỉ nhận request, kích hoạt validation, gọi Service và bọc response. Không inject repository, không viết vòng lặp tạo role/gán chủ tại Controller.

`@RestController` giúp Spring MVC đăng ký handler HTTP và chuyển kết quả thành response body. `@RequestBody` dùng bộ chuyển đổi JSON để đọc request; `@Valid` kích hoạt Bean Validation. Sai UUID có thể bị từ chối khi đọc JSON trước khi Service được gọi.

---

### 📱 PHỐI HỢP FRONTEND VÀ MODULE ACCOUNT

1. Nhập thông tin nhà xe và tìm/chọn các tài khoản làm chủ.
2. Nếu chưa có tài khoản, tạo qua API do Account cung cấp, nhận userId rồi bổ sung vào danh sách chọn.
3. Gọi API Tenant với sáu trường đã thống nhất; không gửi thông tin tạo tài khoản.
4. Nếu tạo Tenant thất bại, giữ thông tin form và các tài khoản đã tạo để thử lại; không tạo lại tài khoản chỉ vì Tenant chưa được tạo.

**Phụ thuộc cần phối hợp:** mục 9.2 onboarding hiện mô tả API Account cho TENANT_ADMIN tạo/gán STAFF hoặc SALE trong tenant hiện tại. Chưa có đặc tả API Super Admin tạo tài khoản độc lập trước khi có tenant. Không dùng nguyên API đó để chuẩn bị chủ nhà xe; thống nhất hợp đồng tìm/tạo tài khoản với người phụ trách Account trước khi tích hợp. Chưa có API frontend không ngăn việc viết và kiểm thử Tenant với userId của các tài khoản có sẵn.

---

### Kiểm thử API tạo nhà xe

#### 1. Kiểm thử theo từng hành vi

Viết test mong đợi trước phần code tương ứng. Dùng Arrange–Act–Assert: chuẩn bị dữ liệu, thực hiện thao tác, kiểm tra kết quả. Không đánh dấu đã đạt chỉ vì code biên dịch thành công.

| Ca kiểm thử | Kết quả cần chứng minh |
|---|---|
| Một hoặc nhiều chủ hợp lệ | 201; một Tenant, một config, ba role, đúng quyền và membership |
| Thiếu tên/domain/danh sách chủ; phần tử chủ null; chuỗi quá dài; email sai | 400; không tạo dữ liệu |
| userId sai định dạng UUID | 400; không chạy nghiệp vụ tạo |
| userId lặp | 400 TENANT_ADMIN_IDS_DUPLICATED; không ghi dữ liệu |
| Một userId không tồn tại hoặc đang bị khóa | Từ chối toàn bộ; phản hồi xác định được tài khoản gặp vấn đề |
| Domain đã tồn tại | 409; không thêm nhà xe |
| Không có xác thực hoặc sai quyền | 401/403; không có dữ liệu mới |
| Thiếu permission khởi tạo | Báo lỗi cấu hình; không lưu nhà xe với bộ quyền thiếu |
| Ghi config/role/permission/membership thất bại | Không còn dữ liệu của thao tác tạo Tenant trong cả năm bảng |
| User đang thuộc tenant X được gán vào Y | Giữ nguyên User và membership ở X; thêm đúng membership ở Y |
| Hai request đồng thời cùng domain | Một thành công, một xung đột; chỉ một nhà xe và bộ dữ liệu phụ thuộc được lưu |
| Tạo Account trước, sau đó Tenant thất bại | Tài khoản được giữ; chỉ dữ liệu tạo Tenant bị rollback |

**Nhóm file test dự kiến:** TenantServiceImplTest cho logic; TenantControllerTest cho hợp đồng HTTP/validation/phân quyền; TenantCreationIntegrationTest với PostgreSQL cho ràng buộc, rollback và concurrency. Đặt dưới `backend/src/test/java/com/carrental/car_rental_backend/tenant/` theo tầng phù hợp.

Mock không chứng minh transaction hoặc FK/UNIQUE thật. Integration test rollback cần kiểm tra dữ liệu sau khi giao dịch nghiệp vụ đã kết thúc, không chỉ dựa vào việc transaction của test tự rollback. Không dùng tài khoản hay DB thật ngoài môi trường kiểm thử để thử lỗi ghi.

#### 2. Tự giải thích trước khi báo cáo

- Vì sao Tenant chỉ nhận userId, không nhận mật khẩu?
- Vì sao tạo role và gán role_permissions là hai việc khác nhau?
- Vì sao nhiều chủ cùng dùng một role TENANT_ADMIN của nhà xe?
- Vì sao tenantId phải là ID nhà xe mới, không lấy từ ngữ cảnh Super Admin?
- Vì sao kiểm tra domain trước vẫn cần UNIQUE ở DB?
- Vì sao có UUID chưa chứng minh dữ liệu đã commit? Flush khác commit thế nào?
- Khi gán một chủ thất bại, dữ liệu nào rollback và dữ liệu nào phải giữ?
- Unit test và integration test chứng minh được những điều gì khác nhau?

#### 3. Các điểm cần chốt khi mở rộng

- Định dạng/chuẩn hóa domain, cách xử lý chuỗi tùy chọn rỗng.
- Cách cập nhật permission mặc định cho các nhà xe đã tồn tại nếu danh mục quyền thay đổi.
- API Account phục vụ Super Admin chuẩn bị/chọn tài khoản chủ.

---

### 📚 TÀI LIỆU ĐỐI CHIẾU

- [AGENTS.md](../../../AGENTS.md), [CLAUDE.md](../../../CLAUDE.md) và [quy chuẩn Java/Spring](../../tutorials/JAVA_SPRING_CODE_CONVENTIONS.md).
- [Onboarding](../../overviews/onboarding.md): nghiệp vụ tập trung, TDD, phân quyền, Account và Single Domain/Tenant Picker.
- [Database Schema](../../overviews/Database-Schema.md): cột, kiểu, default, index và composite FK.
- [Multi-Tenant / Multi-Branch](../../overviews/Multi-Tenant-Multi-Branch.md): User dùng chung và role theo tenant.
- [V1 — schema](../../../backend/src/main/resources/db/migration/V1__init_schema.sql), [V2 — seed](../../../backend/src/main/resources/db/migration/V2__seed_system_data.sql).
- [Guide Auth làm mẫu trình bày](../auth-login/2026-08-28-auth-jwt-implementation-guide.md).

## Danh sách nhà xe

Phương án đã chọn là dùng lại `TenantResponseDTO`, giữ `Page` ở Service và chuyển sang `PageResponse` tại Controller. Các giá trị mặc định và hành vi phân trang trong phần này là đề xuất cho bản đầu.

### Chốt màn hình và hợp đồng HTTP

Hình dung bảng quản trị giống màn hình xem sổ đăng ký nhà xe. Mỗi lần mở, lễ tân xem một trang, thấy tên, domain, gói và trạng thái, rồi dùng ID để mở hồ sơ chi tiết. Cả nhà xe đang hoạt động và nhà xe đã khóa đều có mặt trong sổ.

| Nội dung | Đề xuất cho bản đầu |
|---|---|
| Quyền truy cập | `SUPER_ADMIN`, xem nhà xe toàn hệ thống |
| Query params | `page` mặc định `0`, `size` mặc định `20` |
| Giới hạn | `page >= 0`, `1 <= size <= 100`. Sai kiểu hoặc ngoài giới hạn trả `400` |
| Thứ tự | `createdAt` giảm dần, sau đó `id` giảm dần để thứ tự ổn định khi thời điểm tạo giống nhau |
| Dữ liệu mỗi dòng | Dùng `TenantResponseDTO` hiện có. Bảng hiển thị `id`, `name`, `domain`, `planTier`, `isActive`. API cũng trả các trường logo, liên hệ và thời điểm tạo/cập nhật của DTO |
| Thành công | `200 OK`, dữ liệu nằm trong `ApiResponse<PageResponse<TenantResponseDTO>>` |
| Trang không có bản ghi | `200 OK`, `data.items` là mảng rỗng, metadata vẫn phản ánh tổng theo truy vấn |
| Lỗi quyền | `401` khi chưa xác thực, `403` khi tài khoản đã xác thực thiếu quyền Super Admin |

Ví dụ `GET /api/v1/tenants?page=1&size=10` lấy trang thứ hai. Với 25 nhà xe, trang đó có 10 dòng, `totalElements` là 25 và `totalPages` là 3. Trang có chỉ số 2 có 5 dòng, `pageSize` vẫn là 10, `isLast` là `true`.

Luồng chi tiết `GET /api/v1/tenants/{id}` là nơi có thể đưa thêm danh sách `tenantAdmins` theo đề xuất đã thảo luận. Mỗi dòng của trang danh sách dùng dữ liệu từ `tenants`.

### Tự viết theo thứ tự file

#### Bước 1. `common/dto/PageResponse.java`

Tạo lớp generic dùng chung theo mục 6.1 của quy chuẩn. `T` là kiểu của từng phần tử trong `items`. Ở API này, dùng lại [TenantResponseDTO](../../../backend/src/main/java/com/carrental/car_rental_backend/tenant/dto/response/TenantResponseDTO.java), gồm cả cách cố định tên JSON `isActive` trên getter.

| Thuộc tính | Kiểu | Lấy từ `Page<T>` |
|---|---|---|
| `items` | `List<T>` | `getContent()` |
| `pageNumber` | `int` | `getNumber()` |
| `pageSize` | `int` | `getSize()` |
| `totalElements` | `long` | `getTotalElements()` |
| `totalPages` | `int` | `getTotalPages()` |
| `isLast` | `boolean` | `isLast()` |

Method `from` nhận `Page<T>` và tạo `PageResponse<T>` từ sáu giá trị trên. Có thể hình dung `PageResponse<T>` giống kiểu generic của frontend chứa một mảng dữ liệu và metadata cho nút chuyển trang. Kiểm tra tên JSON của `isLast` giống bài học về `isActive` và khai báo tên tường minh trên getter nếu cần giữ đúng hợp đồng.

#### Bước 2. `TenantService` và `TenantServiceImpl`

Khai báo method `listTenants` nhận `page` và `size` kiểu `int`, trả `Page<TenantResponseDTO>`. Trong implementation, tự triển khai theo thứ tự sau.

1. Kiểm tra giới hạn `page` và `size`. Nếu sai, dùng `AppException` và `ErrorCode.BAD_REQUEST` có sẵn, kèm thông báo chỉ rõ tham số cần sửa. Kiểm tra trước khi tạo `PageRequest` để API dùng hợp đồng lỗi `400` của dự án.
2. Tạo `PageRequest` từ `page`, `size` và `Sort` theo hai thuộc tính Java `createdAt`, `id`. `PageRequest` là một implementation của `Pageable`, mang số trang, kích thước và thứ tự cho Spring Data.
3. Gọi `tenantRepository.findAll(pageable)`, nhận `Page<Tenant>`. `TenantRepository` đã kế thừa method này từ `JpaRepository`. Spring Data truyền phần sắp xếp và giới hạn trang xuống DB. Khi cần tính tổng, nó thực hiện thêm truy vấn đếm.
4. Dùng `Page.map` để chuyển từng `Tenant` thành `TenantResponseDTO`, rồi trả trang DTO. Cách nghĩ giống `Array.map` trong TypeScript, đồng thời `Page.map` giữ metadata của trang. Có thể đưa phần mapping builder hiện có vào một method `toTenantResponse` để `createTenant` và `listTenants` dùng chung.

Đặt `@Transactional(readOnly = true)` trực tiếp trên method đọc của `TenantServiceImpl`. File hiện import bản Jakarta. Để dùng thuộc tính `readOnly`, chuyển import sang `org.springframework.transaction.annotation.Transactional`. Annotation được Spring đọc để thiết lập interceptor quanh lời gọi qua bean, tạo ranh giới giao dịch và cung cấp gợi ý read-only cho tầng JPA/Hibernate. Method `createTenant` tiếp tục dùng giao dịch ghi mặc định.

#### Bước 3. `TenantController.java`

Thêm một method GET vào Controller đang có base path `/api/v1/tenants`.

1. Dùng `@GetMapping` để Spring MVC đăng ký method cho HTTP GET tại base path.
2. Dùng `@RequestParam` để Spring MVC đọc `page`, `size` từ query string, áp dụng giá trị mặc định và chuyển sang `int`. Lỗi chuyển kiểu đi qua handler `MethodArgumentTypeMismatchException` hiện có.
3. Áp dụng `@PreAuthorize` với điều kiện Super Admin như `createTenant`. Spring Security dùng method interceptor để kiểm tra quyền trước khi gọi method. Phạm vi toàn hệ thống đến từ quyền của người gọi đã xác thực.
4. Gọi Service để nhận `Page<TenantResponseDTO>`, chuyển trang sang `PageResponse<TenantResponseDTO>` bằng `PageResponse.from`, rồi bọc bằng `ApiResponse.success` và trả `200 OK`. Bước chuyển đổi này đặt danh sách tại `data.items` theo hợp đồng JSON của dự án.

Nguồn đối chiếu gồm [TenantController](../../../backend/src/main/java/com/carrental/car_rental_backend/tenant/controller/TenantController.java), [TenantRepository](../../../backend/src/main/java/com/carrental/car_rental_backend/tenant/repository/TenantRepository.java) và [GlobalExceptionHandler](../../../backend/src/main/java/com/carrental/car_rental_backend/common/exception/GlobalExceptionHandler.java).

### Tự kiểm chứng

| Ca thử | Kết quả cần kiểm tra |
|---|---|
| Super Admin gọi GET thiếu query params | `200`, `pageNumber=0`, `pageSize=20`, tên JSON là `isActive` và `isLast` |
| Có ít nhất hai nhà xe, gọi trang 0 và trang 1 với `size=1` | Mỗi trang có một nhà xe, ID khác nhau, tổng bản ghi giống nhau khi dữ liệu đứng yên |
| Trang cuối chỉ có một phần dữ liệu | `pageSize` bằng kích thước yêu cầu, `items` có số dòng thực tế, `isLast=true` |
| Trang nằm ngoài dữ liệu | `200`, `items=[]`, giữ tổng bản ghi và tổng trang của truy vấn |
| `page=-1`, `size=0`, `size=101` hoặc `page=abc` | `400`, lỗi chỉ ra tham số cần sửa |
| Chưa xác thực hoặc dùng tài khoản `TENANT_ADMIN` | `401` hoặc `403` tương ứng |

Ca test đầu theo Arrange–Act–Assert có thể chuẩn bị hai nhà xe trong một `Page` với metadata đã biết, gọi Service, rồi kiểm tra trường của từng DTO cùng số trang và tổng bản ghi. Ca phân quyền thực hiện ở lớp kiểm thử HTTP hoặc qua Swagger.

**Câu tự kiểm tra:** Vì sao trang cuối có thể chỉ có 5 `items` mà `pageSize` vẫn bằng 10? Khi frontend cần biết có bao nhiêu trang, con số nào cho biết tổng số dòng trong DB?

Từ khóa tra cứu gồm `Spring Data JpaRepository findAll Pageable`, `Spring Data Page map preserve pagination`, `Spring MVC RequestParam defaultValue` và `Spring transactional readOnly`.

## Review chéo với Claude Code

### Điểm vào source

| Nhóm | File và mục đích |
|---|---|
| HTTP và hợp đồng | [TenantController](../../../backend/src/main/java/com/carrental/car_rental_backend/tenant/controller/TenantController.java), [CreateTenantRequestDTO](../../../backend/src/main/java/com/carrental/car_rental_backend/tenant/dto/request/CreateTenantRequestDTO.java), [TenantResponseDTO](../../../backend/src/main/java/com/carrental/car_rental_backend/tenant/dto/response/TenantResponseDTO.java) |
| Service và dữ liệu | [TenantService](../../../backend/src/main/java/com/carrental/car_rental_backend/tenant/service/TenantService.java), [TenantServiceImpl](../../../backend/src/main/java/com/carrental/car_rental_backend/tenant/service/impl/TenantServiceImpl.java), [TenantRepository](../../../backend/src/main/java/com/carrental/car_rental_backend/tenant/repository/TenantRepository.java), [DefaultRole](../../../backend/src/main/java/com/carrental/car_rental_backend/tenant/constant/DefaultRole.java) |
| Quyền và ngữ cảnh | [SecurityConfig](../../../backend/src/main/java/com/carrental/car_rental_backend/security/config/SecurityConfig.java), [JwtAuthenticationFilter](../../../backend/src/main/java/com/carrental/car_rental_backend/security/jwt/JwtAuthenticationFilter.java), [CustomUserDetailsServiceImpl](../../../backend/src/main/java/com/carrental/car_rental_backend/auth/service/impl/CustomUserDetailsServiceImpl.java), [UserPrincipal](../../../backend/src/main/java/com/carrental/car_rental_backend/security/principal/UserPrincipal.java), [AuthController](../../../backend/src/main/java/com/carrental/car_rental_backend/auth/controller/AuthController.java) |
| JSON và lỗi | [ApiResponse](../../../backend/src/main/java/com/carrental/car_rental_backend/common/dto/ApiResponse.java), [ErrorCode](../../../backend/src/main/java/com/carrental/car_rental_backend/common/exception/ErrorCode.java), [GlobalExceptionHandler](../../../backend/src/main/java/com/carrental/car_rental_backend/common/exception/GlobalExceptionHandler.java) |

`TenantRepository` cũng phục vụ Auth bằng truy vấn lấy nhà xe mà người dùng được phân công. Lúc review, kiểm tra mỗi luồng giữ đúng phạm vi. Tenant Picker lấy danh sách theo membership của người đăng nhập. Màn quản trị Super Admin lấy danh sách toàn hệ thống. [TenantRepository](../../../backend/src/main/java/com/carrental/car_rental_backend/tenant/repository/TenantRepository.java) · [AuthServiceImpl](../../../backend/src/main/java/com/carrental/car_rental_backend/auth/service/impl/AuthServiceImpl.java)

### Câu hỏi review

1. `createTenant` hiện có lỗi thực tế nào trong xác thực quyền, validation, ghi đủ năm bảng hoặc xử lý lỗi DB không? Phát hiện nào có thể xác nhận bằng test nhỏ nhất?
2. Thiết kế GET có trả đúng JSON và thực thi phân trang ở repository không? Việc chuẩn hóa định dạng ở Controller có giữ `Page` làm kiểu nội bộ của Service không?
3. Việc dùng chung `TenantResponseDTO` có giữ hợp đồng POST và chỉ trả các trường được phép cho Super Admin không? Có ca nào cần kiểm tra riêng khi tách đoạn mapping dùng chung?
4. Sau khi review, đề xuất thứ tự ưu tiên và một việc nhỏ để tôi tự làm tiếp. Nếu code vẫn ở trạng thái ghi trên, việc đó là khai báo `PageResponse<T>` và tự giải thích `pageSize` khác số phần tử thực tế ở trang cuối như thế nào.
