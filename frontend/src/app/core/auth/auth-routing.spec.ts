import { AuthSession } from '../models/auth.models';
import { BRANCH_1, TENANT_A, TENANT_B, userInfo } from '../testing/auth-fixtures';
import { nextRouteFor, ROUTES } from './auth-routing';

function sessionOf(user: Partial<ReturnType<typeof userInfo>>, tenantCount = 0): AuthSession {
  return {
    user: userInfo(user),
    availableTenants: [TENANT_A, TENANT_B].slice(0, tenantCount),
    expiresAt: 0,
  };
}

describe('nextRouteFor', () => {
  it('should_go_to_login_when_there_is_no_session', () => {
    expect(nextRouteFor(null)).toBe(ROUTES.login);
  });

  it('should_go_home_when_user_is_super_admin_without_tenant', () => {
    expect(nextRouteFor(sessionOf({ roleCode: 'SUPER_ADMIN' }))).toBe(ROUTES.home);
  });

  it('should_select_tenant_when_user_has_no_tenant_yet', () => {
    expect(nextRouteFor(sessionOf({}, 2))).toBe(ROUTES.selectTenant);
  });

  it('should_select_tenant_even_when_tenant_list_is_empty_so_the_screen_can_explain', () => {
    expect(nextRouteFor(sessionOf({}, 0))).toBe(ROUTES.selectTenant);
  });

  it('should_select_branch_when_tenant_chosen_and_branches_assigned', () => {
    const session = sessionOf({ tenantId: 'tenant-a', assignedBranches: [BRANCH_1] });
    expect(nextRouteFor(session)).toBe(ROUTES.selectBranch);
  });

  it('should_go_home_when_tenant_chosen_but_no_branch_is_assigned', () => {
    const session = sessionOf({ tenantId: 'tenant-a', assignedBranches: [] });
    expect(nextRouteFor(session)).toBe(ROUTES.home);
  });

  it('should_go_home_when_tenant_chosen_and_assigned_branches_is_null', () => {
    const session = sessionOf({ tenantId: 'tenant-a', assignedBranches: null });
    expect(nextRouteFor(session)).toBe(ROUTES.home);
  });

  it('should_go_home_when_branch_already_chosen', () => {
    const session = sessionOf({ tenantId: 'tenant-a', assignedBranches: [BRANCH_1], activeBranchId: 'branch-1' });
    expect(nextRouteFor(session)).toBe(ROUTES.home);
  });
});
