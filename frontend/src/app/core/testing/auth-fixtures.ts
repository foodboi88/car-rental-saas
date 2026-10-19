import { AuthPayload, TenantSummary, UserInfo } from '../models/auth.models';

export const TENANT_A: TenantSummary = {
  tenantId: 'tenant-a',
  tenantName: 'RentCar Hà Nội',
  domain: 'hanoi.carrental.local',
  role: 'STAFF',
  isActive: true,
};

export const TENANT_B: TenantSummary = {
  tenantId: 'tenant-b',
  tenantName: 'Sài Gòn Auto',
  domain: 'saigon.carrental.local',
  role: 'STAFF',
  isActive: true,
};

export const BRANCH_1 = { branchId: 'branch-1', branchName: 'Cầu Giấy', address: '1 Cầu Giấy' };
export const BRANCH_2 = { branchId: 'branch-2', branchName: 'Hoàn Kiếm', address: null };

export function userInfo(overrides: Partial<UserInfo> = {}): UserInfo {
  return {
    id: 'user-1',
    email: 'user@example.com',
    fullname: 'Người Dùng',
    phone: null,
    tenantId: null,
    tenantName: null,
    roleCode: 'STAFF',
    permissions: [],
    activeBranchId: null,
    assignedBranches: null,
    ...overrides,
  };
}

export function authPayload(overrides: Omit<Partial<AuthPayload>, 'user'> & { user?: Partial<UserInfo> } = {}): AuthPayload {
  const { user, ...rest } = overrides;
  return {
    accessToken: 'access-token',
    expiresIn: 900,
    user: userInfo(user),
    availableTenants: [],
    ...rest,
  };
}

export function success(data: AuthPayload) {
  return { success: true, data, message: 'ok', timestamp: '2026-10-19T00:00:00' };
}

export function failure(code: string, message: string, data?: Record<string, string>) {
  return { success: false, error: { code, message }, data, timestamp: '2026-10-19T00:00:00' };
}
