import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { failure } from '../../../core/testing/auth-fixtures';
import { adminLookup, apiOk, tenant } from '../../../core/testing/tenant-fixtures';
import { AdminLookupResult } from '../../../core/models/tenant.models';
import { TENANTS_API } from '../tenant.service';
import { TenantForm } from './tenant-form';

describe('TenantForm', () => {
  let http: HttpTestingController;
  let router: Router;
  let fixture: ComponentFixture<TenantForm>;
  let root: HTMLElement;

  function setup(tenantId: string | null): void {
    const params = tenantId ? { tenantId } : {};
    TestBed.configureTestingModule({
      imports: [TenantForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(params) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(TenantForm);
    root = fixture.nativeElement as HTMLElement;
  }

  const component = () => fixture.componentInstance;
  const errors = () => Array.from(root.querySelectorAll('.field-error')).map((e) => e.textContent?.trim());
  const submit = () => root.querySelector('form')!.dispatchEvent(new Event('submit'));
  const LOOKUP_URL = `${TENANTS_API}/admin-lookup`;

  /** Trả lời yêu cầu tra cứu email đang chờ. Mặc định: email chưa có tài khoản. */
  function answerLookup(result: Partial<AdminLookupResult> = {}) {
    const request = http.expectOne((r) => r.url === LOOKUP_URL);
    request.flush(apiOk(adminLookup({ email: request.request.body.email, ...result })));
    return request;
  }

  function fillValidCreateForm(): void {
    component().form.patchValue({ name: ' Bình An Car ', domain: ' Binhan-Car ' });
    component().admins.at(0).patchValue({ email: ' chu1@binhan.vn ', fullName: 'Nguyễn Văn Bình', password: 'Matkhau123' });
  }

  describe('create', () => {
    beforeEach(async () => {
      setup(null);
      await fixture.whenStable();
    });

    it('should_not_call_the_backend_and_should_show_errors_when_required_fields_are_empty', async () => {
      submit();
      await fixture.whenStable();

      http.expectNone(TENANTS_API);
      expect(errors()).toContain('Không được để trống');
    });

    it('should_reject_a_domain_that_is_not_lowercase_letters_digits_dots_and_dashes', async () => {
      fillValidCreateForm();
      component().form.patchValue({ domain: 'binh an!' });
      submit();
      await fixture.whenStable();

      http.expectNone(TENANTS_API);
      expect(errors()).toContain('Domain chỉ gồm chữ thường, số, dấu chấm và gạch ngang');
    });

    it('should_leave_the_password_strength_check_to_the_backend_and_show_its_message_under_the_field', async () => {
      fillValidCreateForm();
      component().admins.at(0).patchValue({ password: 'abcdefgh' });
      submit();
      answerLookup();

      http.expectOne(TENANTS_API).flush(
        failure('VALIDATION_ERROR', 'Dữ liệu đầu vào không hợp lệ', {
          'admins[0].password': 'Mật khẩu ít nhất 8 ký tự, gồm chữ và số',
        }),
        { status: 400, statusText: 'Bad Request' },
      );
      await fixture.whenStable();

      expect(errors()).toContain('Mật khẩu ít nhất 8 ký tự, gồm chữ và số');
    });

    it('should_send_trimmed_lowercase_domain_and_leave_out_blank_optional_fields', () => {
      fillValidCreateForm();
      submit();
      answerLookup();

      const request = http.expectOne(TENANTS_API);
      expect(request.request.body).toEqual({
        name: 'Bình An Car',
        domain: 'binhan-car',
        planTier: 'FREE',
        admins: [{ email: 'chu1@binhan.vn', fullName: 'Nguyễn Văn Bình', password: 'Matkhau123' }],
      });
      request.flush(apiOk({ tenant: tenant(), admins: [] }));
    });

    it('should_send_only_the_email_for_an_admin_who_already_has_an_account_even_if_a_name_and_password_were_typed', () => {
      fillValidCreateForm();
      submit();
      answerLookup({ accountExists: true, fullName: 'Có Sẵn' });

      const request = http.expectOne(TENANTS_API);
      expect(request.request.body.admins).toEqual([{ email: 'chu1@binhan.vn' }]);
      request.flush(apiOk({ tenant: tenant(), admins: [] }));
    });

    it('should_go_to_the_new_tenant_with_a_notice_about_the_admin_accounts', () => {
      fillValidCreateForm();
      submit();
      answerLookup();

      http.expectOne(TENANTS_API).flush(
        apiOk({
          tenant: tenant({ id: 'new-id' }),
          admins: [
            { email: 'chu1@binhan.vn', accountCreated: true },
            { email: 'minh.admin@rentcarhanoi.vn', accountCreated: false },
          ],
        }),
      );

      expect(router.navigate).toHaveBeenCalledWith(['/tenants', 'new-id'], {
        state: {
          notice: 'Đã tạo nhà xe. Tài khoản mới: chu1@binhan.vn. Gán tài khoản có sẵn: minh.admin@rentcarhanoi.vn.',
        },
      });
    });

    it('should_show_the_domain_conflict_next_to_the_domain_field', async () => {
      fillValidCreateForm();
      submit();
      answerLookup();

      http
        .expectOne(TENANTS_API)
        .flush(failure('TENANT_DOMAIN_ALREADY_EXISTS', 'Domain đã được sử dụng'), { status: 409, statusText: 'Conflict' });
      await fixture.whenStable();

      expect(errors()).toContain('Domain đã được sử dụng');
      expect(root.querySelector('.alert-error')).toBeNull();
      expect(component().submitting()).toBe(false);
    });

    it('should_show_backend_validation_errors_by_field_including_admin_list_paths', async () => {
      fillValidCreateForm();
      submit();
      answerLookup();

      http
        .expectOne(TENANTS_API)
        .flush(failure('VALIDATION_ERROR', 'Dữ liệu đầu vào không hợp lệ', { 'admins[0].email': 'Email đã dùng' }), {
          status: 400,
          statusText: 'Bad Request',
        });
      await fixture.whenStable();

      expect(errors()).toContain('Email đã dùng');
    });

    it('should_show_a_general_message_for_business_errors_without_a_field', async () => {
      fillValidCreateForm();
      submit();
      answerLookup();

      http
        .expectOne(TENANTS_API)
        .flush(failure('ERR_400', 'Tài khoản chu1@binhan.vn đang bị khóa !'), { status: 400, statusText: 'Bad Request' });
      await fixture.whenStable();

      expect(root.querySelector('.alert-error')?.textContent).toContain('đang bị khóa');
    });

    it('should_add_admins_and_never_remove_the_last_one', async () => {
      component().addAdmin();
      await fixture.whenStable();
      expect(root.querySelectorAll('.admin').length).toBe(2);

      component().removeAdmin(1);
      component().removeAdmin(0);
      await fixture.whenStable();

      expect(root.querySelectorAll('.admin').length).toBe(1);
    });
  

    it('should_hide_name_and_password_until_the_email_is_checked_then_show_them_for_a_new_email', async () => {
      component().admins.at(0).patchValue({ email: 'chu1@binhan.vn' });
      await fixture.whenStable();
      expect(root.querySelector('#admin-name-0')).toBeNull();
      expect(root.querySelector('#admin-password-0')).toBeNull();

      root.querySelector<HTMLInputElement>('#admin-email-0')!.dispatchEvent(new Event('blur'));
      answerLookup();
      await fixture.whenStable();

      expect(root.querySelector('#admin-name-0')).not.toBeNull();
      expect(root.querySelector('#admin-password-0')).not.toBeNull();
      expect(root.textContent).toContain('Email chưa có tài khoản');
    });

    it('should_keep_name_and_password_hidden_and_name_the_account_when_the_email_already_exists', async () => {
      component().admins.at(0).patchValue({ email: 'minh.admin@rentcarhanoi.vn' });
      root.querySelector<HTMLInputElement>('#admin-email-0')!.dispatchEvent(new Event('blur'));
      answerLookup({ accountExists: true, fullName: 'Nguyễn Văn Minh' });
      await fixture.whenStable();

      expect(root.querySelector('#admin-name-0')).toBeNull();
      expect(root.querySelector('#admin-password-0')).toBeNull();
      expect(root.textContent).toContain('Đã có tài khoản Nguyễn Văn Minh');
    });

    it('should_flag_the_second_row_when_two_owners_use_the_same_email_ignoring_case_and_spaces', async () => {
      component().addAdmin();
      component().admins.at(0).patchValue({ email: 'chu1@binhan.vn' });
      component().admins.at(1).patchValue({ email: '  CHU1@binhan.vn ' });
      await fixture.whenStable();

      expect(component().duplicateOf(component().admins.at(0))).toBeNull();
      expect(component().duplicateOf(component().admins.at(1))).toBe(1);
      expect(root.querySelectorAll('.admin')[1].textContent).toContain('Email này đã nhập ở chủ nhà xe số 1');
      expect(root.querySelectorAll('.admin')[0].textContent).not.toContain('đã nhập ở chủ nhà xe');
    });

    it('should_not_submit_nor_call_the_backend_while_two_owners_share_an_email', async () => {
      fillValidCreateForm();
      component().addAdmin();
      component().admins.at(1).patchValue({ email: 'CHU1@binhan.vn', fullName: 'Hai', password: 'Matkhau123' });
      submit();
      await fixture.whenStable();

      http.expectNone(TENANTS_API);
      http.expectNone((r) => r.url === LOOKUP_URL);
    });

    it('should_not_ask_the_backend_again_when_leaving_an_email_that_was_already_checked', async () => {
      component().admins.at(0).patchValue({ email: 'chu1@binhan.vn' });
      const input = root.querySelector<HTMLInputElement>('#admin-email-0')!;
      input.dispatchEvent(new Event('blur'));
      answerLookup();
      await fixture.whenStable();

      input.dispatchEvent(new Event('blur'));
      await fixture.whenStable();

      http.expectNone((r) => r.url === LOOKUP_URL);
      expect(root.querySelector('#admin-name-0')).not.toBeNull();
    });

    it('should_hide_the_fields_again_and_forget_the_result_when_the_email_is_edited', async () => {
      component().admins.at(0).patchValue({ email: 'chu1@binhan.vn' });
      const input = root.querySelector<HTMLInputElement>('#admin-email-0')!;
      input.dispatchEvent(new Event('blur'));
      answerLookup();
      await fixture.whenStable();
      expect(root.querySelector('#admin-name-0')).not.toBeNull();

      input.dispatchEvent(new Event('input'));
      await fixture.whenStable();

      expect(root.querySelector('#admin-name-0')).toBeNull();
      expect(component().lookupOf(component().admins.at(0)).status()).toBe('idle');
    });

    it('should_show_the_reason_and_not_submit_when_the_account_cannot_be_an_owner', async () => {
      fillValidCreateForm();
      submit();
      answerLookup({ accountExists: true, canBeAdmin: false, reason: 'Tài khoản chu1@binhan.vn đang bị khóa !' });
      await fixture.whenStable();

      http.expectNone(TENANTS_API);
      expect(root.querySelector('.admin .field-error')?.textContent).toContain('đang bị khóa');
      expect(component().submitting()).toBe(false);
    });

    it('should_ask_for_name_and_password_and_not_submit_when_a_new_account_has_none', async () => {
      component().form.patchValue({ name: 'Bình An Car', domain: 'binhan-car' });
      component().admins.at(0).patchValue({ email: 'chu1@binhan.vn' });
      submit();
      answerLookup();
      await fixture.whenStable();

      http.expectNone(TENANTS_API);
      expect(errors()).toContain('Không được để trống');
      expect(root.querySelector('#admin-name-0')).not.toBeNull();
    });

    it('should_show_all_fields_and_let_the_backend_decide_when_the_lookup_fails', async () => {
      fillValidCreateForm();
      submit();
      http
        .expectOne((r) => r.url === LOOKUP_URL)
        .flush(failure('ERR_500', 'Lỗi hệ thống'), { status: 500, statusText: 'Server Error' });
      await fixture.whenStable();

      expect(http.expectOne(TENANTS_API).request.method).toBe('POST');
      expect(root.querySelector('#admin-name-0')).not.toBeNull();
    });
  });

  describe('edit', () => {
    beforeEach(async () => {
      setup('tenant-1');
      http
        .expectOne(`${TENANTS_API}/tenant-1`)
        .flush(apiOk(tenant({ contactEmail: 'lienhe@binhan.vn', contactPhone: '0900000001' })));
      await fixture.whenStable();
    });

    it('should_fill_the_form_from_the_tenant_and_hide_the_admin_section', () => {
      expect(component().form.controls.name.value).toBe('Bình An Car');
      expect(component().form.controls.contactPhone.value).toBe('0900000001');
      expect(root.textContent).toContain('Sửa nhà xe');
      expect(root.querySelector('.admin')).toBeNull();
    });

    it('should_send_empty_strings_for_cleared_optional_fields_and_never_send_settings', () => {
      component().form.patchValue({ contactEmail: '', contactPhone: '' });
      submit();

      const request = http.expectOne(`${TENANTS_API}/tenant-1`);
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({
        name: 'Bình An Car',
        domain: 'binhan-car',
        planTier: 'BASIC',
        contactEmail: '',
        contactPhone: '',
        logoUrl: '',
      });
      request.flush(apiOk(tenant()));
      expect(router.navigate).toHaveBeenCalledWith(['/tenants', 'tenant-1'], { state: { notice: 'Đã lưu thay đổi.' } });
    });
  });

  it('should_show_the_backend_message_when_the_tenant_to_edit_is_missing', async () => {
    setup('missing');
    http
      .expectOne(`${TENANTS_API}/missing`)
      .flush(failure('ERR_404', 'Không tìm thấy nhà xe !'), { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(root.querySelector('.alert-error')?.textContent).toContain('Không tìm thấy nhà xe');
    expect(root.querySelector('form')).toBeNull();
  });
});
