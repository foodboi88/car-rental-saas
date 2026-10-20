import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { apiOk, tenant, tenantPage } from '../../../core/testing/tenant-fixtures';
import { failure } from '../../../core/testing/auth-fixtures';
import { TENANTS_API } from '../tenant.service';
import { TenantList } from './tenant-list';

describe('TenantList', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<TenantList>;
  let root: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TenantList],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  const listRequest = (): TestRequest => http.expectOne((r) => r.url === TENANTS_API);

  async function render(page = tenantPage([tenant()])): Promise<void> {
    fixture = TestBed.createComponent(TenantList);
    root = fixture.nativeElement as HTMLElement;
    listRequest().flush(apiOk(page));
    await fixture.whenStable();
  }

  const button = (label: string) =>
    Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.includes(label))!;

  it('should_list_tenants_with_plan_and_status', async () => {
    await render(tenantPage([tenant(), tenant({ id: 'tenant-2', name: 'Sài Gòn Auto', isActive: false })]));

    const rows = root.querySelectorAll('.row');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Bình An Car');
    expect(rows[0].textContent).toContain('BASIC');
    expect(rows[0].textContent).toContain('Hoạt động');
    expect(rows[1].textContent).toContain('Đã khóa');
  });

  it('should_link_each_row_to_the_tenant_detail', async () => {
    await render();

    expect(root.querySelector('.row')?.getAttribute('href')).toBe('/tenants/tenant-1');
  });

  it('should_tell_there_are_no_tenants_yet_when_nothing_is_filtered', async () => {
    await render(tenantPage([]));

    expect(root.querySelector('.empty')?.textContent).toContain('Chưa có nhà xe nào');
  });

  it('should_filter_by_status_and_go_back_to_the_first_page', async () => {
    await render(tenantPage([tenant()], { number: 0, totalPages: 3, totalElements: 25 }));
    button('Sau').click();
    listRequest().flush(apiOk(tenantPage([tenant()], { number: 1, totalPages: 3, totalElements: 25 })));
    await fixture.whenStable();

    const select = root.querySelector<HTMLSelectElement>('select')!;
    select.value = 'locked';
    select.dispatchEvent(new Event('change'));

    const request = listRequest();
    expect(request.request.params.get('isActive')).toBe('false');
    expect(request.request.params.get('page')).toBe('0');
    request.flush(apiOk(tenantPage([])));
    await fixture.whenStable();
    expect(root.querySelector('.empty')?.textContent).toContain('Không tìm thấy');
  });

  it('should_search_by_keyword_when_the_form_is_submitted', async () => {
    await render();

    const input = root.querySelector<HTMLInputElement>('input[type=search]')!;
    input.value = 'binh';
    input.dispatchEvent(new Event('input'));
    root.querySelector('form')!.dispatchEvent(new Event('submit'));

    const request = listRequest();
    expect(request.request.params.get('keyword')).toBe('binh');
    request.flush(apiOk(tenantPage([tenant()])));
  });

  it('should_move_between_pages_and_disable_the_buttons_at_both_ends', async () => {
    await render(tenantPage([tenant()], { number: 0, totalPages: 2, totalElements: 12 }));
    expect(button('Trước').disabled).toBe(true);
    expect(root.querySelector('.pager')?.textContent).toContain('Trang 1/2');

    button('Sau').click();
    const request = listRequest();
    expect(request.request.params.get('page')).toBe('1');
    request.flush(apiOk(tenantPage([tenant()], { number: 1, totalPages: 2, totalElements: 12 })));
    await fixture.whenStable();

    expect(button('Sau').disabled).toBe(true);
    expect(button('Trước').disabled).toBe(false);
  });

  it('should_show_the_backend_message_when_loading_fails', async () => {
    fixture = TestBed.createComponent(TenantList);
    root = fixture.nativeElement as HTMLElement;
    listRequest().flush(failure('AUTH_FORBIDDEN', 'Bạn không có quyền thực hiện thao tác này'), {
      status: 403,
      statusText: 'Forbidden',
    });
    await fixture.whenStable();

    expect(root.querySelector('.alert-error')?.textContent).toContain('Bạn không có quyền');
  });
});
