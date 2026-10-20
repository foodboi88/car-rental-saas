import { userInfo } from '../testing/auth-fixtures';
import { visibleNavItems } from './nav-items';

const labels = (user: Parameters<typeof visibleNavItems>[0]) => visibleNavItems(user).map((item) => item.label);

describe('visibleNavItems', () => {
  it('should_return_nothing_when_there_is_no_user', () => {
    expect(visibleNavItems(null)).toEqual([]);
  });

  it('should_show_only_overview_and_tenant_management_to_super_admin', () => {
    expect(labels(userInfo({ roleCode: 'SUPER_ADMIN', permissions: null }))).toEqual(['Tổng quan', 'Nhà xe']);
  });

  it('should_link_tenant_management_to_the_tenants_page', () => {
    const user = userInfo({ roleCode: 'SUPER_ADMIN', permissions: null });
    const item = visibleNavItems(user).find((entry) => entry.label === 'Nhà xe');

    expect(item?.path).toBe('/tenants');
  });

  it('should_hide_tenant_management_from_tenant_users', () => {
    expect(labels(userInfo({ permissions: ['booking:read'] }))).not.toContain('Nhà xe');
  });

  it('should_show_only_items_matching_granted_permissions', () => {
    const user = userInfo({ permissions: ['vehicle:read', 'booking:read'] });

    expect(labels(user)).toEqual(['Tổng quan', 'Xe', 'Khách hàng', 'Đơn thuê']);
  });

  it('should_show_admin_items_when_admin_permissions_are_granted', () => {
    const user = userInfo({ permissions: ['branch:manage', 'account:manage', 'report:view_revenue'] });

    expect(labels(user)).toEqual(['Tổng quan', 'Chi nhánh', 'Người dùng', 'Báo cáo']);
  });

  it('should_show_only_overview_when_permissions_are_null_for_a_tenant_user', () => {
    expect(labels(userInfo({ permissions: null }))).toEqual(['Tổng quan']);
  });
});
