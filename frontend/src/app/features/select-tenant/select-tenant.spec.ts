import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AUTH_API, AuthService } from '../../core/auth/auth.service';
import { TenantSummary } from '../../core/models/auth.models';
import { authPayload, success, TENANT_A, TENANT_B } from '../../core/testing/auth-fixtures';
import { SelectTenant } from './select-tenant';

describe('SelectTenant', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SelectTenant],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  /** Backend chỉ trả các nhà xe đang hoạt động trong `availableTenants`. */
  async function renderWith(availableTenants: TenantSummary[]): Promise<HTMLElement> {
    TestBed.inject(AuthService).login('user@example.com', 'secret').subscribe();
    http.expectOne(`${AUTH_API}/login`).flush(success(authPayload({ availableTenants, user: { fullname: 'Lan' } })));
    const fixture = TestBed.createComponent(SelectTenant);
    await TestBed.inject(ApplicationRef).whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  const subtitle = (root: HTMLElement) => root.querySelector('.card-sub')?.textContent?.trim();

  it('should_say_the_user_belongs_to_several_tenants_when_there_are_two_or_more_to_choose_from', async () => {
    const root = await renderWith([TENANT_A, TENANT_B]);

    expect(root.querySelectorAll('.choice').length).toBe(2);
    expect(subtitle(root)).toContain('Bạn thuộc nhiều nhà xe');
  });

  it('should_not_claim_several_tenants_when_only_one_is_left_to_choose_from', async () => {
    const root = await renderWith([TENANT_A]);

    expect(root.querySelectorAll('.choice').length).toBe(1);
    expect(subtitle(root)).not.toContain('Bạn thuộc nhiều nhà xe');
    expect(subtitle(root)).toContain('đang tạm ngưng');
  });

  it('should_explain_that_the_tenants_are_suspended_when_none_is_left_to_choose_from', async () => {
    const root = await renderWith([]);

    expect(root.querySelectorAll('.choice').length).toBe(0);
    expect(root.querySelector('.alert-info')?.textContent).toContain('đang tạm ngưng');
    expect(root.querySelector('.alert-info')?.textContent).not.toContain('chưa thuộc nhà xe nào');
  });
});
