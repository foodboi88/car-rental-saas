import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { adminEmailCheck, apiOk } from '../../core/testing/tenant-fixtures';
import { AdminEmailCheck, applyAccountRequirements } from './admin-email-check';
import { TENANTS_API, TenantService } from './tenant.service';
import { FormControl } from '@angular/forms';

describe('AdminEmailCheck', () => {
  const EMAIL_CHECK_URL = `${TENANTS_API}/admin-email-check`;
  let http: HttpTestingController;
  let emailCheck: AdminEmailCheck;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    emailCheck = new AdminEmailCheck(TestBed.inject(TenantService), 'tenant-1');
  });

  afterEach(() => http.verify());

  it('should_become_new_when_the_email_has_no_account_and_can_be_assigned', () => {
    emailCheck.check('chu1@binhan.vn').subscribe();

    expect(emailCheck.status()).toBe('checking');
    http.expectOne(EMAIL_CHECK_URL).flush(apiOk(adminEmailCheck()));

    expect(emailCheck.status()).toBe('new');
    expect(emailCheck.needsAccountDetails()).toBe(true);
  });

  it('should_become_existing_with_the_name_when_the_account_exists_and_can_be_assigned', () => {
    emailCheck.check('minh@binhan.vn').subscribe();
    http.expectOne(EMAIL_CHECK_URL).flush(apiOk(adminEmailCheck({ accountExists: true, fullName: 'Nguyễn Văn Minh' })));

    expect(emailCheck.status()).toBe('existing');
    expect(emailCheck.fullName()).toBe('Nguyễn Văn Minh');
    expect(emailCheck.needsAccountDetails()).toBe(false);
  });

  it('should_become_blocked_with_the_reason_when_the_email_cannot_be_assigned', () => {
    emailCheck.check('minh@binhan.vn').subscribe();
    http.expectOne(EMAIL_CHECK_URL).flush(apiOk(adminEmailCheck({ accountExists: true, canBeAdmin: false, reason: 'Tài khoản đang bị khóa !' })));

    expect(emailCheck.status()).toBe('blocked');
    expect(emailCheck.reason()).toBe('Tài khoản đang bị khóa !');
  });

  it('should_not_call_the_backend_for_an_empty_or_invalid_email', () => {
    emailCheck.check('   ').subscribe();
    emailCheck.check('khong-phai-email').subscribe();

    http.expectNone(EMAIL_CHECK_URL);
    expect(emailCheck.status()).toBe('idle');
  });

  it('should_reuse_the_result_instead_of_asking_again_once_the_email_is_settled', () => {
    emailCheck.check('chu1@binhan.vn').subscribe();
    http.expectOne(EMAIL_CHECK_URL).flush(apiOk(adminEmailCheck()));

    emailCheck.ensureChecked('chu1@binhan.vn').subscribe();

    http.expectNone(EMAIL_CHECK_URL);
    expect(emailCheck.status()).toBe('new');
  });

  it('should_ask_again_after_a_failed_check_because_the_error_may_be_temporary', () => {
    emailCheck.check('chu1@binhan.vn').subscribe();
    http.expectOne(EMAIL_CHECK_URL).flush({}, { status: 500, statusText: 'Server Error' });
    expect(emailCheck.status()).toBe('failed');
    expect(emailCheck.needsAccountDetails()).toBe(true);

    emailCheck.ensureChecked('chu1@binhan.vn').subscribe();
    http.expectOne(EMAIL_CHECK_URL).flush(apiOk(adminEmailCheck()));

    expect(emailCheck.status()).toBe('new');
  });

  it('should_ignore_a_late_answer_after_the_email_was_edited', () => {
    emailCheck.check('chu1@binhan.vn').subscribe();
    const late = http.expectOne(EMAIL_CHECK_URL);

    emailCheck.reset();
    late.flush(apiOk(adminEmailCheck({ accountExists: true, fullName: 'Người cũ' })));

    expect(emailCheck.status()).toBe('idle');
    expect(emailCheck.fullName()).toBeNull();
  });

  it('should_send_the_tenant_id_so_the_backend_can_check_the_relation_with_that_tenant', () => {
    emailCheck.check('chu1@binhan.vn').subscribe();

    const request = http.expectOne(EMAIL_CHECK_URL);
    expect(request.request.body).toEqual({ email: 'chu1@binhan.vn', tenantId: 'tenant-1' });
    request.flush(apiOk(adminEmailCheck()));
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

  it('should_keep_what_was_typed_but_not_require_it_when_the_check_failed', () => {
    const fields = controls();

    applyAccountRequirements(fields, 'failed');

    expect(fields.fullName.value).toBe('Đã gõ');
    expect(fields.password.value).toBe('Matkhau123');
  });
});
