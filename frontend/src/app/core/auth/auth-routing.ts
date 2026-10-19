import { AuthSession, SUPER_ADMIN_ROLE } from '../models/auth.models';

export const ROUTES = {
  login: '/login',
  selectTenant: '/select-tenant',
  selectBranch: '/select-branch',
  home: '/home',
} as const;

/**
 * Màn hình tiếp theo sau mỗi lần gọi API auth thành công.
 *
 * 1. Chưa có phiên: đăng nhập.
 * 2. Super Admin: trang chủ.
 * 3. Chưa có nhà xe: chọn nhà xe.
 * 4. Có nhà xe, chưa có chi nhánh: chọn chi nhánh (trang chủ nếu chưa có chi nhánh nào).
 * 5. Có chi nhánh: trang chủ.
 */
export function nextRouteFor(session: AuthSession | null): string {
  if (!session) return ROUTES.login;

  const { user } = session;
  if (user.roleCode === SUPER_ADMIN_ROLE) return ROUTES.home;
  if (!user.tenantId) return ROUTES.selectTenant;
  if (!user.activeBranchId && (user.assignedBranches?.length ?? 0) > 0) return ROUTES.selectBranch;
  return ROUTES.home;
}
