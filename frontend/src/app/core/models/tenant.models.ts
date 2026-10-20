/** Kiểu dữ liệu khớp với DTO của module nhà xe (`/api/v1/tenants`, chỉ Super Admin). */

export const PLAN_TIERS = ['FREE', 'BASIC', 'PRO', 'ENTERPRISE'] as const;
export type PlanTier = (typeof PLAN_TIERS)[number];

export interface Tenant {
  id: string;
  name: string;
  domain: string;
  planTier: PlanTier;
  logoUrl: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  /** Chuỗi JSON cấu hình tự do, mặc định `"{}"`. */
  settings: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface TenantPage {
  content: Tenant[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface TenantListQuery {
  keyword: string;
  /** `null` là không lọc theo trạng thái. */
  isActive: boolean | null;
  /** Bắt đầu từ 0. */
  page: number;
  size: number;
}

export interface TenantAdmin {
  userId: string;
  email: string;
  fullName: string;
  isActive: boolean;
  joinedAt: string;
}

/** Họ tên và mật khẩu chỉ bắt buộc khi email chưa có tài khoản; email đã có thì backend bỏ qua hai trường này. */
export interface AdminInput {
  email: string;
  fullName?: string;
  password?: string;
}

export interface CreateTenantRequest {
  name: string;
  domain: string;
  planTier: PlanTier;
  logoUrl?: string;
  contactEmail?: string;
  contactPhone?: string;
  settings?: string;
  admins: AdminInput[];
}

/**
 * Với 4 trường tùy chọn: bỏ qua hoặc `null` là giữ nguyên, chuỗi rỗng là xóa (riêng `settings` thành `"{}"`).
 */
export interface UpdateTenantRequest {
  name: string;
  domain: string;
  planTier: PlanTier;
  logoUrl?: string;
  contactEmail?: string;
  contactPhone?: string;
  settings?: string;
}

/** Kết quả tra cứu một email trước khi gán làm chủ nhà xe. */
export interface AdminLookupResult {
  email: string;
  accountExists: boolean;
  fullName: string | null;
  canBeAdmin: boolean;
  /** Lý do không gán được, null nếu gán được. */
  reason: string | null;
}

export interface AdminResult {
  email: string;
  accountCreated: boolean;
}

export interface CreateTenantResult {
  tenant: Tenant;
  admins: AdminResult[];
}
