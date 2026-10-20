import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { adminLookup, apiOk, tenant, tenantAdmin, tenantPage } from '../../core/testing/tenant-fixtures';
import { TENANTS_API, TenantService } from './tenant.service';

describe('TenantService', () => {
  let service: TenantService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(TenantService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should_send_only_paging_params_when_there_is_no_keyword_and_no_status_filter', () => {
    service.list({ keyword: '  ', isActive: null, page: 0, size: 10 }).subscribe();

    const request = http.expectOne((r) => r.url === TENANTS_API);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys().sort()).toEqual(['page', 'size']);
    request.flush(apiOk(tenantPage([])));
  });

  it('should_send_trimmed_keyword_and_status_filter_including_false', () => {
    service.list({ keyword: ' binh ', isActive: false, page: 2, size: 10 }).subscribe();

    const request = http.expectOne((r) => r.url === TENANTS_API);
    expect(request.request.params.get('keyword')).toBe('binh');
    expect(request.request.params.get('isActive')).toBe('false');
    expect(request.request.params.get('page')).toBe('2');
    request.flush(apiOk(tenantPage([])));
  });

  it('should_unwrap_data_from_the_api_response', () => {
    let result: unknown;
    service.get('tenant-1').subscribe((value) => (result = value));

    http.expectOne(`${TENANTS_API}/tenant-1`).flush(apiOk(tenant()));

    expect(result).toEqual(tenant());
  });

  it('should_post_the_create_request_to_the_collection', () => {
    const body = { name: 'A', domain: 'a', planTier: 'FREE' as const, admins: [{ email: 'a@a.vn' }] };
    service.create(body).subscribe();

    const request = http.expectOne(TENANTS_API);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush(apiOk({ tenant: tenant(), admins: [] }));
  });

  it('should_put_the_update_request_to_the_tenant', () => {
    service.update('tenant-1', { name: 'A', domain: 'a', planTier: 'PRO' }).subscribe();

    const request = http.expectOne(`${TENANTS_API}/tenant-1`);
    expect(request.request.method).toBe('PUT');
    request.flush(apiOk(tenant()));
  });

  it('should_patch_the_status_with_is_active', () => {
    service.setActive('tenant-1', false).subscribe();

    const request = http.expectOne(`${TENANTS_API}/tenant-1/status`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ isActive: false });
    request.flush(apiOk(tenant({ isActive: false })));
  });

  it('should_delete_the_tenant_and_complete_without_data', () => {
    let done = false;
    service.remove('tenant-1').subscribe(() => (done = true));

    const request = http.expectOne(`${TENANTS_API}/tenant-1`);
    expect(request.request.method).toBe('DELETE');
    request.flush({ success: true, message: 'ok' });

    expect(done).toBe(true);
  });

  it('should_read_the_tenant_admins', () => {
    let admins: unknown;
    service.admins('tenant-1').subscribe((value) => (admins = value));

    http.expectOne(`${TENANTS_API}/tenant-1/admins`).flush(apiOk([tenantAdmin()]));

    expect(admins).toEqual([tenantAdmin()]);
  });

  it('should_post_the_email_in_the_body_when_looking_up_an_admin', () => {
    service.lookupAdminByEmail('  Chu1@binhan.vn ', 'tenant-1').subscribe();

    const request = http.expectOne(`${TENANTS_API}/admin-lookup`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'Chu1@binhan.vn', tenantId: 'tenant-1' });
    expect(request.request.url).not.toContain('@');
    request.flush(apiOk(adminLookup()));
  });

  it('should_leave_out_the_tenant_id_when_looking_up_an_admin_for_a_new_tenant', () => {
    service.lookupAdminByEmail('chu1@binhan.vn').subscribe();

    const request = http.expectOne(`${TENANTS_API}/admin-lookup`);
    expect(request.request.body).toEqual({ email: 'chu1@binhan.vn' });
    request.flush(apiOk(adminLookup()));
  });

  it('should_post_a_new_tenant_admin', () => {
    service.addAdmin('tenant-1', { email: 'b@b.vn' }).subscribe();

    const request = http.expectOne(`${TENANTS_API}/tenant-1/admins`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'b@b.vn' });
    request.flush(apiOk({ email: 'b@b.vn', accountCreated: false }));
  });
});
