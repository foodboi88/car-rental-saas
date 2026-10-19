import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { authPayload, BRANCH_1, failure, success, TENANT_A } from '../testing/auth-fixtures';
import { ROUTES } from './auth-routing';
import { authInterceptor, SESSION_EXPIRED_REASON } from './auth.interceptor';
import { AUTH_API, AuthService } from './auth.service';

const PROTECTED_URL = '/api/v1/something';
const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };

describe('authInterceptor', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  let client: HttpClient;
  let router: { navigate: ReturnType<typeof vi.fn>; navigateByUrl: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    router = { navigate: vi.fn(), navigateByUrl: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
  });

  afterEach(() => http.verify());

  function loginWith(payload = authPayload({ accessToken: 'old-token' })): void {
    auth.login('user@example.com', 'secret').subscribe();
    http.expectOne(`${AUTH_API}/login`).flush(success(payload));
  }

  it('should_add_bearer_token_to_backend_requests', () => {
    loginWith();

    client.get(PROTECTED_URL).subscribe();

    const request = http.expectOne(PROTECTED_URL);
    expect(request.request.headers.get('Authorization')).toBe('Bearer old-token');
    request.flush({});
  });

  it('should_not_add_bearer_token_to_login_or_refresh_calls', () => {
    loginWith();

    client.post(`${AUTH_API}/refresh-token`, {}).subscribe();

    const request = http.expectOne(`${AUTH_API}/refresh-token`);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(success(authPayload()));
  });

  it('should_not_touch_requests_outside_the_backend_api', () => {
    loginWith();

    client.get('/assets/logo.svg').subscribe();

    const request = http.expectOne('/assets/logo.svg');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush('');
  });

  it('should_refresh_once_and_retry_with_the_new_token_when_context_is_unchanged', () => {
    loginWith(authPayload({ accessToken: 'old-token', user: { roleCode: 'SUPER_ADMIN' } }));
    let body: unknown;
    client.get(PROTECTED_URL).subscribe((value) => (body = value));

    http.expectOne(PROTECTED_URL).flush(failure('AUTH_UNAUTHORIZED', 'x'), UNAUTHORIZED);
    http
      .expectOne(`${AUTH_API}/refresh-token`)
      .flush(success(authPayload({ accessToken: 'new-token', user: { roleCode: 'SUPER_ADMIN' } })));
    const retry = http.expectOne(PROTECTED_URL);
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new-token');
    retry.flush({ ok: true });

    expect(body).toEqual({ ok: true });
  });

  it('should_not_refresh_a_second_time_when_the_retried_request_is_unauthorized_again', () => {
    loginWith();
    let failed = false;
    client.get(PROTECTED_URL).subscribe({ error: () => (failed = true) });

    http.expectOne(PROTECTED_URL).flush(failure('AUTH_UNAUTHORIZED', 'x'), UNAUTHORIZED);
    http.expectOne(`${AUTH_API}/refresh-token`).flush(success(authPayload({ accessToken: 'new-token' })));
    http.expectOne(PROTECTED_URL).flush(failure('AUTH_UNAUTHORIZED', 'x'), UNAUTHORIZED);

    expect(failed).toBe(true);
  });

  it('should_go_back_to_login_and_clear_session_when_refresh_fails', () => {
    loginWith();
    let failed = false;
    client.get(PROTECTED_URL).subscribe({ error: () => (failed = true) });

    http.expectOne(PROTECTED_URL).flush(failure('AUTH_UNAUTHORIZED', 'x'), UNAUTHORIZED);
    http
      .expectOne(`${AUTH_API}/refresh-token`)
      .flush(failure('AUTH_FORBIDDEN', 'Phiên cũ'), { status: 403, statusText: 'Forbidden' });

    expect(failed).toBe(true);
    expect(auth.session()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith([ROUTES.login], { queryParams: { reason: SESSION_EXPIRED_REASON } });
  });

  it('should_not_retry_and_should_route_to_the_next_step_when_refresh_loses_the_chosen_tenant', () => {
    loginWith(
      authPayload({
        accessToken: 'old-token',
        availableTenants: [TENANT_A],
        user: { tenantId: 'tenant-a', activeBranchId: 'branch-1', assignedBranches: [BRANCH_1] },
      }),
    );
    let failed = false;
    client.get(PROTECTED_URL).subscribe({ error: () => (failed = true) });

    http.expectOne(PROTECTED_URL).flush(failure('AUTH_UNAUTHORIZED', 'x'), UNAUTHORIZED);
    http
      .expectOne(`${AUTH_API}/refresh-token`)
      .flush(success(authPayload({ accessToken: 'user-level-token', availableTenants: [TENANT_A] })));

    http.expectNone(PROTECTED_URL);
    expect(failed).toBe(true);
    expect(router.navigateByUrl).toHaveBeenCalledWith(ROUTES.selectTenant);
  });

  it('should_pass_through_errors_other_than_401_without_refreshing', () => {
    loginWith();
    let status = 0;
    client.get(PROTECTED_URL).subscribe({ error: (error) => (status = error.status) });

    http.expectOne(PROTECTED_URL).flush(failure('AUTH_FORBIDDEN', 'x'), { status: 403, statusText: 'Forbidden' });

    http.expectNone(`${AUTH_API}/refresh-token`);
    expect(status).toBe(403);
  });

  it('should_not_refresh_when_login_itself_returns_401', () => {
    let status = 0;
    client.post(`${AUTH_API}/login`, {}).subscribe({ error: (error) => (status = error.status) });

    http.expectOne(`${AUTH_API}/login`).flush(failure('AUTH_UNAUTHORIZED', 'x'), UNAUTHORIZED);

    http.expectNone(`${AUTH_API}/refresh-token`);
    expect(status).toBe(401);
  });
});
