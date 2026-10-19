import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AUTH_API, AuthService } from '../../core/auth/auth.service';
import { AuthPayload } from '../../core/models/auth.models';
import { authPayload, BRANCH_1, BRANCH_2, success, TENANT_A, TENANT_B } from '../../core/testing/auth-fixtures';
import { AppShell } from './app-shell';

describe('AppShell', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<AppShell>;
  let root: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [provideRouter([{ path: 'login', children: [] }]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  const settle = () => TestBed.inject(ApplicationRef).whenStable();

  async function renderAfterLogin(payload: AuthPayload): Promise<void> {
    TestBed.inject(AuthService).login('user@example.com', 'secret').subscribe();
    http.expectOne(`${AUTH_API}/login`).flush(success(payload));
    fixture = TestBed.createComponent(AppShell);
    root = fixture.nativeElement as HTMLElement;
    await settle();
  }

  const shell = () => root.querySelector('.shell')!;
  const click = async (selector: string) => {
    root.querySelector<HTMLElement>(selector)!.click();
    await settle();
  };
  const pressEscape = async () => {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    await settle();
  };
  const menuItems = () => Array.from(root.querySelectorAll('.menu-item')).map((item) => item.textContent?.trim());

  const tenantUser = () =>
    authPayload({
      availableTenants: [TENANT_A, TENANT_B],
      user: {
        fullname: 'Nguyễn Quang Huy',
        tenantId: 'tenant-a',
        tenantName: 'RentCar Hà Nội',
        activeBranchId: 'branch-1',
        assignedBranches: [BRANCH_1, BRANCH_2],
        permissions: ['vehicle:read'],
      },
    });

  it('should_show_initials_and_the_tenant_and_branch_in_the_header', async () => {
    await renderAfterLogin(tenantUser());

    expect(root.querySelector('.avatar')?.textContent?.trim()).toBe('QH');
    expect(root.querySelector('.context')?.textContent).toContain('RentCar Hà Nội');
    expect(root.querySelector('.context')?.textContent).toContain('Cầu Giấy');
  });

  it('should_show_whole_system_for_super_admin', async () => {
    await renderAfterLogin(authPayload({ user: { roleCode: 'SUPER_ADMIN', permissions: null } }));

    expect(root.querySelector('.context')?.textContent).toContain('Toàn hệ thống');
  });

  it('should_list_navigation_items_by_permission_and_mark_unbuilt_modules', async () => {
    await renderAfterLogin(tenantUser());

    const links = Array.from(root.querySelectorAll('.nav-link')).map((link) =>
      link.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(links).toEqual(['Tổng quan', 'Xe Sắp có']);
    expect(root.querySelectorAll('a.nav-link')).toHaveLength(1);
  });

  it('should_open_and_close_the_drawer_with_the_menu_button', async () => {
    await renderAfterLogin(tenantUser());
    expect(shell().classList.contains('nav-open')).toBe(false);

    await click('.menu-btn');
    expect(shell().classList.contains('nav-open')).toBe(true);
    expect(root.querySelector('.menu-btn')?.getAttribute('aria-expanded')).toBe('true');

    await click('.menu-btn');
    expect(shell().classList.contains('nav-open')).toBe(false);
  });

  it('should_close_the_drawer_when_the_backdrop_is_tapped', async () => {
    await renderAfterLogin(tenantUser());
    await click('.menu-btn');

    await click('.backdrop');

    expect(shell().classList.contains('nav-open')).toBe(false);
  });

  it('should_close_the_drawer_on_escape', async () => {
    await renderAfterLogin(tenantUser());
    await click('.menu-btn');

    await pressEscape();

    expect(shell().classList.contains('nav-open')).toBe(false);
  });

  it('should_close_the_user_menu_on_escape', async () => {
    await renderAfterLogin(tenantUser());
    await click('.user-btn');

    await pressEscape();

    expect(root.querySelector('.menu')).toBeNull();
  });

  it('should_close_the_user_menu_when_clicking_outside_of_it', async () => {
    await renderAfterLogin(tenantUser());
    await click('.user-btn');
    expect(root.querySelector('.menu')).not.toBeNull();

    document.body.click();
    await settle();

    expect(root.querySelector('.menu')).toBeNull();
  });

  it('should_offer_change_branch_and_change_tenant_when_the_user_has_several', async () => {
    await renderAfterLogin(tenantUser());

    await click('.user-btn');

    expect(menuItems()).toEqual(['Đổi chi nhánh', 'Đổi nhà xe', 'Giao diện tối', 'Đăng xuất']);
  });

  it('should_offer_theme_and_logout_when_there_is_nothing_to_switch', async () => {
    await renderAfterLogin(
      authPayload({
        availableTenants: [TENANT_A],
        user: { tenantId: 'tenant-a', tenantName: 'RentCar', activeBranchId: 'branch-1', assignedBranches: [BRANCH_1] },
      }),
    );

    await click('.user-btn');

    expect(menuItems()).toEqual(['Giao diện tối', 'Đăng xuất']);
  });

  it('should_switch_theme_from_the_menu_and_keep_the_menu_open', async () => {
    await renderAfterLogin(tenantUser());
    await click('.user-btn');
    const before = document.documentElement.dataset['theme'];

    await click('.menu-item[role="menuitemcheckbox"]');

    expect(document.documentElement.dataset['theme']).not.toBe(before);
    expect(root.querySelector('.menu')).not.toBeNull();
    expect(root.querySelector('.menu-item[role="menuitemcheckbox"]')?.getAttribute('aria-checked')).toBe(
      String(before === 'light'),
    );
  });

  it('should_clear_the_session_when_logging_out_from_the_menu', async () => {
    await renderAfterLogin(tenantUser());
    await click('.user-btn');

    const logout = Array.from(root.querySelectorAll<HTMLElement>('.menu-item')).find((item) =>
      item.textContent?.includes('Đăng xuất'),
    )!;
    logout.click();
    http.expectOne(`${AUTH_API}/logout`).flush({ success: true, data: 'ok' });

    expect(TestBed.inject(AuthService).session()).toBeNull();
  });
});
