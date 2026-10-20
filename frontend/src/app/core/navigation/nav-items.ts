import { SUPER_ADMIN_ROLE, UserInfo } from '../models/auth.models';
import { IconName } from '../../shared/icon/icon-paths';

export interface NavItem {
  label: string;
  icon: IconName;
  /** Chưa có `path` nghĩa là module chưa làm: hiện mờ kèm nhãn "Sắp có". */
  path?: string;
  /** Mã quyền cần có (lấy từ `permissions` của phiên). Không ghi thì ai cũng thấy. */
  permission?: string;
  superAdminOnly?: boolean;
}

/** Thêm module mới: thêm một dòng ở đây và một route con trong `app.routes.ts`. */
export const NAV_ITEMS: readonly NavItem[] = [
  { label: 'Tổng quan', icon: 'home', path: '/home' },
  { label: 'Nhà xe', icon: 'building', path: '/tenants', superAdminOnly: true },
  { label: 'Chi nhánh', icon: 'branch', permission: 'branch:manage' },
  { label: 'Người dùng', icon: 'users', permission: 'account:manage' },
  { label: 'Xe', icon: 'car', permission: 'vehicle:read' },
  { label: 'Khách hàng', icon: 'user', permission: 'booking:read' },
  { label: 'Đơn thuê', icon: 'calendar', permission: 'booking:read' },
  { label: 'Báo cáo', icon: 'chart', permission: 'report:view_revenue' },
];

/** Super Admin chỉ thấy mục dành riêng cho họ; người dùng nhà xe thấy mục khớp với quyền được cấp. */
export function visibleNavItems(user: UserInfo | null): NavItem[] {
  if (!user) return [];

  const isSuperAdmin = user.roleCode === SUPER_ADMIN_ROLE;
  const granted = new Set(user.permissions ?? []);
  return NAV_ITEMS.filter((item) => {
    if (item.superAdminOnly) return isSuperAdmin;
    if (!item.permission) return true;
    return !isSuperAdmin && granted.has(item.permission);
  });
}
