import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AUTH_API, AuthService } from '../../core/auth/auth.service';
import { authPayload, BRANCH_1, success } from '../../core/testing/auth-fixtures';
import { AuthPayload } from '../../core/models/auth.models';
import { Home } from './home';

describe('Home', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Home],
      providers: [provideRouter([{ path: 'home', component: Home }]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function renderAfterLogin(payload: AuthPayload): Promise<HTMLElement> {
    TestBed.inject(AuthService).login('user@example.com', 'secret').subscribe();
    http.expectOne(`${AUTH_API}/login`).flush(success(payload));
    const fixture = TestBed.createComponent(Home);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  const refreshButton = (root: HTMLElement) =>
    root.querySelector<HTMLButtonElement>('button.btn-primary')!;

  const buttonLabels = (root: HTMLElement) =>
    Array.from(root.querySelectorAll('button')).map((button) => button.textContent?.trim());

  it('should_render_the_whole_page_for_super_admin_whose_permissions_are_null', async () => {
    const root = await renderAfterLogin(authPayload({ user: { roleCode: 'SUPER_ADMIN', permissions: null } }));

    expect(root.textContent).toContain('SUPER_ADMIN');
    expect(root.textContent).toContain('Quyền (0)');
    expect(buttonLabels(root)).toContain('Làm mới token');
  });

  it('should_stop_showing_the_waiting_state_when_a_super_admin_refresh_succeeds_and_stays_on_home', async () => {
    const superAdmin = authPayload({ user: { roleCode: 'SUPER_ADMIN', permissions: null } });
    const root = await renderAfterLogin(superAdmin);

    refreshButton(root).click();
    await TestBed.inject(ApplicationRef).whenStable();
    expect(refreshButton(root).disabled).toBe(true);
    http.expectOne(`${AUTH_API}/refresh-token`).flush(success(superAdmin));
    await TestBed.inject(ApplicationRef).whenStable();

    expect(refreshButton(root).disabled).toBe(false);
    expect(refreshButton(root).textContent?.trim()).toBe('Làm mới token');
  });

  it('should_list_permissions_when_the_backend_returns_them', async () => {
    const root = await renderAfterLogin(
      authPayload({
        user: {
          tenantId: 'tenant-a',
          tenantName: 'RentCar',
          activeBranchId: 'branch-1',
          assignedBranches: [BRANCH_1],
          permissions: ['booking:read', 'vehicle:read'],
        },
      }),
    );

    expect(root.textContent).toContain('Quyền (2)');
    expect(root.textContent).toContain('booking:read');
    expect(root.textContent).toContain('Cầu Giấy');
  });
});
