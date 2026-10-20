import { AdminLookupResult, Tenant, TenantAdmin, TenantPage } from '../models/tenant.models';

export function tenant(overrides: Partial<Tenant> = {}): Tenant {
  return {
    id: 'tenant-1',
    name: 'Bình An Car',
    domain: 'binhan-car',
    planTier: 'BASIC',
    logoUrl: null,
    contactEmail: 'lienhe@binhan.vn',
    contactPhone: null,
    settings: '{}',
    isActive: true,
    createdAt: '2026-10-19T03:00:00Z',
    updatedAt: '2026-10-19T03:00:00Z',
    ...overrides,
  };
}

export function tenantPage(content: Tenant[], page: Partial<TenantPage['page']> = {}): TenantPage {
  return {
    content,
    page: { size: 10, number: 0, totalElements: content.length, totalPages: content.length ? 1 : 0, ...page },
  };
}

export function tenantAdmin(overrides: Partial<TenantAdmin> = {}): TenantAdmin {
  return {
    userId: 'user-1',
    email: 'chu1@binhan.vn',
    fullName: 'Nguyễn Văn Bình',
    isActive: true,
    joinedAt: '2026-10-19T03:00:00Z',
    ...overrides,
  };
}

/** Bọc `data` đúng cấu trúc `ApiResponse` của backend. */
export function apiOk<T>(data: T, message = 'ok') {
  return { success: true, data, message, timestamp: '2026-10-19T00:00:00Z' };
}

/** Mặc định: email chưa có tài khoản, gán được (sẽ tạo tài khoản mới). */
export function adminLookup(overrides: Partial<AdminLookupResult> = {}): AdminLookupResult {
  return { email: 'chu1@binhan.vn', accountExists: false, fullName: null, canBeAdmin: true, reason: null, ...overrides };
}
