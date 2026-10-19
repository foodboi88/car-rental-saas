/** Kiểu dữ liệu khớp với DTO của backend (`/api/v1/auth`). */

export interface ApiSuccess<T> {
  success: true;
  data: T;
  message: string;
  timestamp: string;
}

export interface ApiFailure {
  success: false;
  error: { code: string; message: string };
  /** Với VALIDATION_ERROR: tên trường -> thông báo. */
  data?: Record<string, string>;
  timestamp: string;
}

export interface BranchSummary {
  branchId: string;
  branchName: string;
  address: string | null;
}

export interface TenantSummary {
  tenantId: string;
  tenantName: string;
  domain: string;
  role: string;
  isActive: boolean;
}

export interface UserInfo {
  id: string;
  email: string;
  fullname: string;
  phone: string | null;
  tenantId: string | null;
  tenantName: string | null;
  roleCode: string;
  /** Backend trả null với Super Admin và với token chưa chọn nhà xe. */
  permissions: string[] | null;
  activeBranchId: string | null;
  assignedBranches: BranchSummary[] | null;
}

/** `data` của login, refresh-token, select-tenant, switch-branch. */
export interface AuthPayload {
  accessToken: string;
  /** Thời hạn access token, tính bằng giây. */
  expiresIn: number;
  user: UserInfo;
  availableTenants: TenantSummary[] | null;
}

/** Phần phiên mà giao diện được phép đọc. Access token cố ý không nằm ở đây. */
export interface AuthSession {
  user: UserInfo;
  availableTenants: TenantSummary[];
  /** Thời điểm access token hết hạn (ms từ epoch). */
  expiresAt: number;
}

export const SUPER_ADMIN_ROLE = 'SUPER_ADMIN';

export interface AuthEvent {
  at: number;
  action: string;
  detail: string;
  ok: boolean;
}
