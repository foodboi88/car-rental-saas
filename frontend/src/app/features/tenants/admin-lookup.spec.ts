import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { adminLookup, apiOk } from '../../core/testing/tenant-fixtures';
import { AdminLookup, applyAccountRequirements } from './admin-lookup';
import { TENANTS_API, TenantService } from './tenant.service';
import { FormControl } from '@angular/forms';

describe('AdminLookup', () => {
  const LOOKUP_URL = `${TENANTS_API}/admin-lookup`;
  let http: HttpTestingController;
  let lookup: AdminLookup;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    lookup = new AdminLookup(TestBed.inject(TenantService), 'tenant-1');
  });

  afterEach(() => http.verify());

  it('should_become_new_when_the_email_has_no_account_and_can_be_assigned', () => {
    lookup.check('chu1@binhan.vn').subscribe();

    expect(lookup.status()).toBe('checking');
    http.expectOne(LOOKUP_URL).flush(apiOk(adminLookup()));

    expect(lookup.status()).toBe('new');
    expect(lookup.needsAccountDetails()).toBe(true);
  });

  it('should_become_existing_with_the_name_when_the_account_exists_and_can_be_assigned', () => {
    lookup.check('minh@binhan.vn').subscribe();
    http.expectOne(LOOKUP_URL).flush(apiOk(adminLookup({ accountExists: true, fullName: 'Nguyễn Văn Minh' })));

    expect(lookup.status()).toBe('existing');
    expect(lookup.fullName()).toBe('Nguyễn Văn Minh');
    expect(lookup.needsAccountDetails()).toBe(false);
  });

  it('should_become_blocked_with_the_reason_when_the_email_cannot_be_assigned', () => {
    lookup.check('minh@binhan.vn').subscribe();
    http.expectOne(LOOKUP_URL).flush(apiOk(adminLookup({ accountExists: true, canBeAdmin: false, reason: 'Tài khoản đang bị khóa !' })));

    expect(lookup.status()).toBe('blocked');
    expect(lookup.reason()).toBe('Tài khoản đang bị khóa !');
  });

  it('should_not_call_the_backend_for_an_empty_or_invalid_email', () => {
    lookup.check('   ').subscribe();
    lookup.check('khong-phai-email').subscribe();

    http.expectNone(LOOKUP_URL);
    expect(lookup.status()).toBe('idle');
  });

  it('should_reuse_the_result_instead_of_asking_again_once_the_email_is_settled', () => {
    lookup.check('chu1@binhan.vn').subscribe();
    http.expectOne(LOOKUP_URL).flush(apiOk(adminLookup()));

    lookup.ensureChecked('chu1@binhan.vn').subscribe();

    http.expectNone(LOOKUP_URL);
    expect(lookup.status()).toBe('new');
  });

  it('should_ask_again_after_a_failed_lookup_because_the_error_may_be_temporary', () => {
    lookup.check('chu1@binhan.vn').subscribe();
    http.expectOne(LOOKUP_URL).flush({}, { status: 500, statusText: 'Server Error' });
    expect(lookup.status()).toBe('failed');
    expect(lookup.needsAccountDetails()).toBe(true);

    lookup.ensureChecked('chu1@binhan.vn').subscribe();
    http.expectOne(LOOKUP_URL).flush(apiOk(adminLookup()));

    expect(lookup.status()).toBe('new');
  });

  it('should_ignore_a_late_answer_after_the_email_was_edited', () => {
    lookup.check('chu1@binhan.vn').subscribe();
    const late = http.expectOne(LOOKUP_URL);

    lookup.reset();
    late.flush(apiOk(adminLookup({ accountExists: true, fullName: 'Người cũ' })));

    expect(lookup.status()).toBe('idle');
    expect(lookup.fullName()).toBeNull();
  });

  it('should_send_the_tenant_id_so_the_backend_can_check_the_relation_with_that_tenant', () => {
    lookup.check('chu1@binhan.vn').subscribe();

    const request = http.expectOne(LOOKUP_URL);
    expect(request.request.body).toEqual({ email: 'chu1@binhan.vn', tenantId: 'tenant-1' });
    request.flush(apiOk(adminLookup()));
  });
});

describe('applyAccountRequirements', () => {
  const controls = () => ({ fullName: new FormControl('Đã gõ', { nonNullable: true }), password: new FormControl('Matkhau123', { nonNullable: true }) });

  it('should_require_name_and_password_only_for_a_new_account', () => {
    const fields = controls();
    fields.fullName.setValue('');
    fields.password.setValue('');

    applyAccountRequirements(fields, 'new');

    expect(fields.fullName.hasError('required')).toBe(true);
    expect(fields.password.hasError('required')).toBe(true);
  });

  it('should_clear_what_was_typed_when_the_account_already_exists', () => {
    const fields = controls();

    applyAccountRequirements(fields, 'existing');

    expect(fields.fullName.value).toBe('');
    expect(fields.password.value).toBe('');
    expect(fields.fullName.valid).toBe(true);
  });

  it('should_keep_what_was_typed_but_not_require_it_when_the_lookup_failed', () => {
    const fields = controls();

    applyAccountRequirements(fields, 'failed');

    expect(fields.fullName.value).toBe('Đã gõ');
    expect(fields.password.value).toBe('Matkhau123');
  });
});
