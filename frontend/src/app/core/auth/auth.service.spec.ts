import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { authPayload, failure, success, TENANT_A, TENANT_B } from '../testing/auth-fixtures';
import { AUTH_API, AuthService } from './auth.service';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should_store_session_and_token_in_memory_after_login', () => {
    auth.login('user@example.com', 'secret').subscribe();

    const request = http.expectOne(`${AUTH_API}/login`);
    expect(request.request.body).toEqual({ email: 'user@example.com', password: 'secret' });
    request.flush(success(authPayload({ accessToken: 'token-1', user: { fullname: 'An' } })));

    expect(auth.accessToken()).toBe('token-1');
    expect(auth.user()?.fullname).toBe('An');
  });

  it('should_not_expose_the_access_token_through_the_session_signal_or_web_storage', () => {
    auth.login('user@example.com', 'secret').subscribe();
    http.expectOne(`${AUTH_API}/login`).flush(success(authPayload({ accessToken: 'token-1' })));

    expect(JSON.stringify(auth.session())).not.toContain('token-1');
    expect(JSON.stringify(localStorage)).not.toContain('token-1');
    expect(JSON.stringify(sessionStorage)).not.toContain('token-1');
  });

  it('should_compute_expiry_from_expires_in_seconds', () => {
    const before = Date.now();
    auth.login('user@example.com', 'secret').subscribe();
    http.expectOne(`${AUTH_API}/login`).flush(success(authPayload({ expiresIn: 900 })));

    const expiresAt = auth.session()?.expiresAt ?? 0;
    expect(expiresAt).toBeGreaterThanOrEqual(before + 900_000);
    expect(expiresAt).toBeLessThan(before + 900_000 + 5_000);
  });

  it('should_keep_previous_tenant_list_when_response_has_none', () => {
    auth.login('user@example.com', 'secret').subscribe();
    http.expectOne(`${AUTH_API}/login`).flush(success(authPayload({ availableTenants: [TENANT_A, TENANT_B] })));

    auth.selectTenant('tenant-a').subscribe();
    http.expectOne(`${AUTH_API}/select-tenant`).flush(success(authPayload({ availableTenants: null })));

    expect(auth.availableTenants()).toEqual([TENANT_A, TENANT_B]);
  });

  it('should_send_active_branch_id_when_switching_branch', () => {
    auth.switchBranch('branch-1').subscribe();

    const request = http.expectOne(`${AUTH_API}/switch-branch`);
    expect(request.request.body).toEqual({ activeBranchId: 'branch-1' });
    request.flush(success(authPayload()));
  });

  it('should_record_a_failed_event_and_keep_no_session_when_login_is_rejected', () => {
    let failed = false;
    auth.login('user@example.com', 'wrong').subscribe({ error: () => (failed = true) });

    http
      .expectOne(`${AUTH_API}/login`)
      .flush(failure('AUTH_UNAUTHORIZED', 'Sai mật khẩu'), { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(auth.session()).toBeNull();
    expect(auth.events()[0]).toMatchObject({ action: 'Đăng nhập', ok: false });
  });

  it('should_share_one_request_when_refresh_is_called_twice_at_the_same_time', () => {
    const results: string[] = [];
    auth.refresh().subscribe((payload) => results.push(payload.accessToken));
    auth.refresh().subscribe((payload) => results.push(payload.accessToken));

    http.expectOne(`${AUTH_API}/refresh-token`).flush(success(authPayload({ accessToken: 'fresh' })));

    expect(results).toEqual(['fresh', 'fresh']);
  });

  it('should_allow_a_new_refresh_request_after_the_previous_one_finished', () => {
    auth.refresh().subscribe();
    http.expectOne(`${AUTH_API}/refresh-token`).flush(success(authPayload()));

    auth.refresh().subscribe();

    http.expectOne(`${AUTH_API}/refresh-token`).flush(success(authPayload()));
  });

  it('should_restore_session_when_refresh_cookie_is_valid', () => {
    let restored: boolean | undefined;
    auth.restoreSession().subscribe((value) => (restored = value));

    http.expectOne(`${AUTH_API}/refresh-token`).flush(success(authPayload()));

    expect(restored).toBe(true);
    expect(auth.session()).not.toBeNull();
  });

  it('should_report_not_restored_when_refresh_is_rejected', () => {
    let restored: boolean | undefined;
    auth.restoreSession().subscribe((value) => (restored = value));

    http
      .expectOne(`${AUTH_API}/refresh-token`)
      .flush(failure('AUTH_UNAUTHORIZED', 'x'), { status: 401, statusText: 'Unauthorized' });

    expect(restored).toBe(false);
    expect(auth.session()).toBeNull();
  });

  it('should_not_log_an_error_event_when_restoring_a_session_fails_silently', () => {
    auth.restoreSession().subscribe();

    http
      .expectOne(`${AUTH_API}/refresh-token`)
      .flush(failure('AUTH_UNAUTHORIZED', 'x'), { status: 401, statusText: 'Unauthorized' });

    expect(auth.events()).toEqual([]);
  });

  it('should_clear_local_session_after_logout', () => {
    auth.login('user@example.com', 'secret').subscribe();
    http.expectOne(`${AUTH_API}/login`).flush(success(authPayload()));

    auth.logout().subscribe();
    http.expectOne(`${AUTH_API}/logout`).flush({ success: true, data: 'ok' });

    expect(auth.session()).toBeNull();
    expect(auth.accessToken()).toBeNull();
  });

  it('should_clear_local_session_even_when_logout_request_fails', () => {
    auth.login('user@example.com', 'secret').subscribe();
    http.expectOne(`${AUTH_API}/login`).flush(success(authPayload()));

    auth.logout().subscribe();
    http.expectOne(`${AUTH_API}/logout`).flush(null, { status: 500, statusText: 'Server Error' });

    expect(auth.session()).toBeNull();
    expect(auth.accessToken()).toBeNull();
  });

  it('should_drop_the_tenant_list_after_logout', () => {
    auth.login('user@example.com', 'secret').subscribe();
    http.expectOne(`${AUTH_API}/login`).flush(success(authPayload({ availableTenants: [TENANT_A] })));

    auth.logout().subscribe();
    http.expectOne(`${AUTH_API}/logout`).flush({ success: true, data: 'ok' });

    expect(auth.availableTenants()).toEqual([]);
  });
});
