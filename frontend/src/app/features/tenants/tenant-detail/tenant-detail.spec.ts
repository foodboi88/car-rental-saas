import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { failure } from '../../../core/testing/auth-fixtures';
import { Tenant, TenantAdmin } from '../../../core/models/tenant.models';
import { adminLookup, apiOk, tenant, tenantAdmin } from '../../../core/testing/tenant-fixtures';
import { AdminLookupResult } from '../../../core/models/tenant.models';
import { TENANTS_API } from '../tenant.service';
import { TenantDetail } from './tenant-detail';

describe('TenantDetail', () => {
  const TENANT_URL = `${TENANTS_API}/tenant-1`;
  let http: HttpTestingController;
  let fixture: ComponentFixture<TenantDetail>;
  let root: HTMLElement;

  async function render(options: { tenant?: Tenant; admins?: TenantAdmin[]; state?: object | null } = {}): Promise<void> {
    TestBed.configureTestingModule({
      imports: [TenantDetail],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ tenantId: 'tenant-1' }) } } },
        { provide: Location, useValue: { getState: () => options.state ?? null } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TenantDetail);
    root = fixture.nativeElement as HTMLElement;
    http.expectOne(TENANT_URL).flush(apiOk(options.tenant ?? tenant()));
    http.expectOne(`${TENANT_URL}/admins`).flush(apiOk(options.admins ?? [tenantAdmin()]));
    await fixture.whenStable();
  }

  const button = (label: string) =>
    Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.includes(label)) as HTMLButtonElement;
  const text = () => root.textContent ?? '';
  const submitAdminForm = () => root.querySelector('.admin-form')!.dispatchEvent(new Event('submit'));

  /** Trả lời yêu cầu tra cứu email đang chờ, kiểm tra luôn là có kèm mã nhà xe. Mặc định: email chưa có tài khoản. */
  function answerLookup(result: Partial<AdminLookupResult> = {}): void {
    const request = http.expectOne((r) => r.url === `${TENANTS_API}/admin-lookup`);
    expect(request.request.body.tenantId).toBe('tenant-1');
    request.flush(apiOk(adminLookup({ email: request.request.body.email, ...result })));
  }

  it('should_show_the_tenant_and_its_admins', async () => {
    await render();

    expect(text()).toContain('Bình An Car');
    expect(text()).toContain('binhan-car');
    expect(text()).toContain('BASIC');
    expect(text()).toContain('lienhe@binhan.vn');
    expect(root.querySelector('.admins')?.textContent).toContain('Nguyễn Văn Bình');
    expect(root.querySelector('.admins')?.textContent).toContain('chu1@binhan.vn');
  });

  it('should_show_the_notice_passed_from_the_previous_page', async () => {
    await render({ state: { notice: 'Đã tạo nhà xe.' } });

    expect(root.querySelector('.alert-success')?.textContent).toContain('Đã tạo nhà xe.');
  });

  it('should_ask_for_confirmation_before_locking_and_do_nothing_when_cancelled', async () => {
    await render();

    button('Khóa').click();
    await fixture.whenStable();
    expect(root.querySelector('.confirm')?.textContent).toContain('Khóa nhà xe');

    button('Hủy').click();
    await fixture.whenStable();

    http.expectNone(`${TENANT_URL}/status`);
    expect(root.querySelector('.confirm')).toBeNull();
  });

  it('should_lock_the_tenant_after_confirmation_and_reload_it', async () => {
    await render();
    button('Khóa').click();
    await fixture.whenStable();

    button('Xác nhận').click();
    const patch = http.expectOne(`${TENANT_URL}/status`);
    expect(patch.request.method).toBe('PATCH');
    expect(patch.request.body).toEqual({ isActive: false });
    patch.flush(apiOk(tenant({ isActive: false })));
    http.expectOne(TENANT_URL).flush(apiOk(tenant({ isActive: false })));
    await fixture.whenStable();

    expect(text()).toContain('Đã khóa');
    expect(root.querySelector('.alert-success')?.textContent).toContain('Đã khóa nhà xe.');
    expect(button('Mở khóa')).toBeTruthy();
  });

  it('should_explain_soft_delete_and_call_delete_after_confirmation', async () => {
    await render();
    button('Xóa').click();
    await fixture.whenStable();
    expect(root.querySelector('.confirm')?.textContent).toContain('xóa mềm');

    button('Xác nhận').click();
    const request = http.expectOne(TENANT_URL);
    expect(request.request.method).toBe('DELETE');
    request.flush({ success: true, message: 'ok' });
    http.expectOne(TENANT_URL).flush(apiOk(tenant({ isActive: false })));
    await fixture.whenStable();

    expect(root.querySelector('.alert-success')?.textContent).toContain('xóa mềm');
  });

  it('should_show_the_backend_message_when_the_action_fails', async () => {
    await render();
    button('Khóa').click();
    await fixture.whenStable();

    button('Xác nhận').click();
    http
      .expectOne(`${TENANT_URL}/status`)
      .flush(failure('ERR_404', 'Không tìm thấy nhà xe !'), { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(root.querySelector('.alert-error')?.textContent).toContain('Không tìm thấy nhà xe');
    expect(root.querySelector('.confirm')).toBeNull();
  });

  it('should_offer_unlock_and_block_adding_admins_while_the_tenant_is_locked', async () => {
    await render({ tenant: tenant({ isActive: false }) });

    expect(button('Mở khóa')).toBeTruthy();
    expect(button('Khóa')).toBeUndefined();
    expect(button('Thêm chủ').disabled).toBe(true);
    expect(text()).toContain('mở khóa trước khi thêm chủ nhà xe');
  });

  it('should_add_an_admin_then_reload_the_admin_list_and_report_the_account_state', async () => {
    await render();
    button('Thêm chủ').click();
    await fixture.whenStable();

    fixture.componentInstance.adminForm.patchValue({ email: ' chu2@binhan.vn ', fullName: 'Trần Thị Hai', password: 'Matkhau456' });
    submitAdminForm();
    answerLookup();
    const post = http.expectOne(`${TENANT_URL}/admins`);
    expect(post.request.body).toEqual({ email: 'chu2@binhan.vn', fullName: 'Trần Thị Hai', password: 'Matkhau456' });
    post.flush(apiOk({ email: 'chu2@binhan.vn', accountCreated: true }));
    http.expectOne(`${TENANT_URL}/admins`).flush(apiOk([tenantAdmin(), tenantAdmin({ userId: 'user-2', email: 'chu2@binhan.vn' })]));
    await fixture.whenStable();

    expect(root.querySelectorAll('.admins li').length).toBe(2);
    expect(root.querySelector('.alert-success')?.textContent).toContain('Đã tạo tài khoản mới');
    expect(root.querySelector('.admin-form')).toBeNull();
  });

  it('should_send_only_the_email_when_linking_an_existing_account_even_if_a_name_and_password_were_typed', async () => {
    await render();
    button('Thêm chủ').click();
    await fixture.whenStable();

    fixture.componentInstance.adminForm.patchValue({ email: 'minh.admin@rentcarhanoi.vn', fullName: 'Gõ thừa', password: 'GoThua123' });
    submitAdminForm();
    answerLookup({ accountExists: true, fullName: 'Nguyễn Văn Minh' });

    expect(http.expectOne(`${TENANT_URL}/admins`).request.body).toEqual({ email: 'minh.admin@rentcarhanoi.vn' });
  });

  it('should_not_call_the_backend_when_the_admin_email_is_missing', async () => {
    await render();
    button('Thêm chủ').click();
    await fixture.whenStable();

    root.querySelector('.admin-form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    http.expectNone(`${TENANT_URL}/admins`);
    expect(root.querySelector('.field-error')?.textContent).toContain('Không được để trống');
  });

  it('should_show_the_backend_password_message_under_the_password_field', async () => {
    await render();
    button('Thêm chủ').click();
    await fixture.whenStable();

    fixture.componentInstance.adminForm.patchValue({ email: 'chu3@binhan.vn', fullName: 'Ba', password: 'abcdefgh' });
    submitAdminForm();
    answerLookup();
    http.expectOne(`${TENANT_URL}/admins`).flush(
      failure('VALIDATION_ERROR', 'Dữ liệu đầu vào không hợp lệ', { password: 'Mật khẩu ít nhất 8 ký tự, gồm chữ và số' }),
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(root.querySelector('.admin-form .field-error')?.textContent).toContain('Mật khẩu ít nhất 8 ký tự');
    expect(root.querySelector('.admin-form .alert-error')).toBeNull();
  });

  it('should_show_the_backend_message_when_adding_an_admin_fails', async () => {
    await render();
    button('Thêm chủ').click();
    await fixture.whenStable();

    fixture.componentInstance.adminForm.patchValue({ email: 'chu1@binhan.vn' });
    submitAdminForm();
    answerLookup({ accountExists: true, fullName: 'Nguyễn Văn Bình' });
    http
      .expectOne(`${TENANT_URL}/admins`)
      .flush(failure('TENANT_ADMIN_ALREADY_ASSIGNED', 'Người này đã là chủ nhà xe của nhà xe này: chu1@binhan.vn'), {
        status: 409,
        statusText: 'Conflict',
      });
    await fixture.whenStable();

    expect(root.querySelector('.admin-form .alert-error')?.textContent).toContain('đã là chủ nhà xe');
  });

  it('should_hide_name_and_password_for_an_existing_account_and_show_them_for_a_new_one', async () => {
    await render();
    button('Thêm chủ').click();
    await fixture.whenStable();
    expect(root.querySelector('#add-name')).toBeNull();
    const email = root.querySelector<HTMLInputElement>('#add-email')!;

    fixture.componentInstance.adminForm.patchValue({ email: 'minh.admin@rentcarhanoi.vn' });
    email.dispatchEvent(new Event('blur'));
    answerLookup({ accountExists: true, fullName: 'Nguyễn Văn Minh' });
    await fixture.whenStable();
    expect(root.querySelector('#add-name')).toBeNull();
    expect(text()).toContain('Đã có tài khoản Nguyễn Văn Minh');

    fixture.componentInstance.adminForm.patchValue({ email: 'moi@binhan.vn' });
    email.dispatchEvent(new Event('input'));
    email.dispatchEvent(new Event('blur'));
    answerLookup();
    await fixture.whenStable();
    expect(root.querySelector('#add-name')).not.toBeNull();
    expect(root.querySelector('#add-password')).not.toBeNull();
  });

  it('should_show_the_reason_and_not_submit_when_the_account_is_already_an_owner_of_this_tenant', async () => {
    await render();
    button('Thêm chủ').click();
    await fixture.whenStable();

    fixture.componentInstance.adminForm.patchValue({ email: 'chu1@binhan.vn' });
    submitAdminForm();
    answerLookup({
      accountExists: true,
      canBeAdmin: false,
      reason: 'Người này đã là chủ nhà xe của nhà xe này: chu1@binhan.vn',
    });
    await fixture.whenStable();

    http.expectNone(`${TENANT_URL}/admins`);
    expect(root.querySelector('.admin-form .field-error')?.textContent).toContain('đã là chủ nhà xe');
    expect(fixture.componentInstance.addingAdmin()).toBe(false);
  });

  it('should_require_name_and_password_for_a_new_account_before_submitting', async () => {
    await render();
    button('Thêm chủ').click();
    await fixture.whenStable();

    fixture.componentInstance.adminForm.patchValue({ email: 'moi@binhan.vn' });
    submitAdminForm();
    answerLookup();
    await fixture.whenStable();

    http.expectNone(`${TENANT_URL}/admins`);
    expect(root.querySelector('.admin-form .field-error')?.textContent).toContain('Không được để trống');
  });
});
