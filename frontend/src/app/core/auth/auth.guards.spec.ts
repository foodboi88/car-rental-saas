import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, provideRouter, RouterStateSnapshot, UrlTree } from '@angular/router';

import { AuthPayload } from '../models/auth.models';
import { authPayload, BRANCH_1, success, TENANT_A } from '../testing/auth-fixtures';
import { ROUTES } from './auth-routing';
import { guestGuard, homeGuard, selectBranchGuard, selectTenantGuard } from './auth.guards';
import { AUTH_API, AuthService } from './auth.service';

describe('auth guards', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  function loginAs(payload: AuthPayload): void {
    auth.login('user@example.com', 'secret').subscribe();
    http.expectOne(`${AUTH_API}/login`).flush(success(payload));
  }

  function run(guard: CanActivateFn): boolean | string {
    const result = TestBed.runInInjectionContext(() =>
      guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    return result instanceof UrlTree ? result.toString() : (result as boolean);
  }

  const multiTenantPayload = () => authPayload({ availableTenants: [TENANT_A] });
  const inTenantPayload = () =>
    authPayload({ user: { tenantId: 'tenant-a', assignedBranches: [BRANCH_1] }, availableTenants: [TENANT_A] });
  const fullSessionPayload = () =>
    authPayload({
      user: { tenantId: 'tenant-a', assignedBranches: [BRANCH_1], activeBranchId: 'branch-1' },
      availableTenants: [TENANT_A],
    });

  it('should_let_guests_open_login', () => {
    expect(run(guestGuard)).toBe(true);
  });

  it('should_send_a_logged_in_user_from_login_to_the_next_step', () => {
    loginAs(multiTenantPayload());

    expect(run(guestGuard)).toBe(ROUTES.selectTenant);
  });

  it.each([
    ['select-tenant', selectTenantGuard],
    ['select-branch', selectBranchGuard],
    ['home', homeGuard],
  ])('should_send_guests_from_%s_to_login', (_name, guard) => {
    expect(run(guard)).toBe(ROUTES.login);
  });

  it('should_let_a_user_without_tenant_open_select_tenant', () => {
    loginAs(multiTenantPayload());

    expect(run(selectTenantGuard)).toBe(true);
  });

  it('should_send_super_admin_away_from_select_tenant', () => {
    loginAs(authPayload({ user: { roleCode: 'SUPER_ADMIN' } }));

    expect(run(selectTenantGuard)).toBe(ROUTES.home);
  });

  it('should_keep_a_user_without_tenant_out_of_select_branch', () => {
    loginAs(multiTenantPayload());

    expect(run(selectBranchGuard)).toBe(ROUTES.selectTenant);
  });

  it('should_let_a_user_inside_a_tenant_with_branches_open_select_branch', () => {
    loginAs(inTenantPayload());

    expect(run(selectBranchGuard)).toBe(true);
  });

  it('should_let_a_user_who_already_has_a_branch_change_branch', () => {
    loginAs(fullSessionPayload());

    expect(run(selectBranchGuard)).toBe(true);
  });

  it('should_hold_home_back_until_a_branch_is_chosen', () => {
    loginAs(inTenantPayload());

    expect(run(homeGuard)).toBe(ROUTES.selectBranch);
  });

  it('should_open_home_after_tenant_and_branch_are_chosen', () => {
    loginAs(fullSessionPayload());

    expect(run(homeGuard)).toBe(true);
  });

  it('should_open_home_for_super_admin', () => {
    loginAs(authPayload({ user: { roleCode: 'SUPER_ADMIN' } }));

    expect(run(homeGuard)).toBe(true);
  });
});
