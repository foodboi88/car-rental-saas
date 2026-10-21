import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Login } from './login';

describe('Login', () => {
  let root: HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(Login);
    root = fixture.nativeElement as HTMLElement;
    await TestBed.inject(ApplicationRef).whenStable();
  });

  const passwordInput = () => root.querySelector<HTMLInputElement>('#password')!;
  const toggle = () => root.querySelector<HTMLButtonElement>('.input-toggle')!;

  it('should_hide_the_password_and_offer_to_show_it_by_default', () => {
    expect(passwordInput().type).toBe('password');
    expect(toggle().getAttribute('aria-label')).toBe('Hiện mật khẩu');
    expect(toggle().querySelector('svg')).not.toBeNull();
  });

  it('should_show_the_password_when_the_eye_button_is_pressed', async () => {
    toggle().click();
    await TestBed.inject(ApplicationRef).whenStable();

    expect(passwordInput().type).toBe('text');
    expect(toggle().getAttribute('aria-label')).toBe('Ẩn mật khẩu');
  });

  it('should_hide_the_password_again_when_the_eye_button_is_pressed_twice', async () => {
    toggle().click();
    await TestBed.inject(ApplicationRef).whenStable();
    toggle().click();
    await TestBed.inject(ApplicationRef).whenStable();

    expect(passwordInput().type).toBe('password');
  });

  describe('demo accounts', () => {
    const settle = () => TestBed.inject(ApplicationRef).whenStable();
    const emailInput = () => root.querySelector<HTMLInputElement>('#email')!;
    const demoButton = (label: string) =>
      Array.from(root.querySelectorAll<HTMLButtonElement>('.demo-list button')).find((b) => b.textContent?.trim() === label)!;

    it('should_offer_one_account_for_each_role_and_each_login_situation', () => {
      const labels = Array.from(root.querySelectorAll('.demo-list button')).map((b) => b.textContent?.trim());

      expect(labels).toEqual([
        'Super Admin',
        'Chủ nhà xe Hà Nội',
        'Chủ nhà xe Sài Gòn',
        'Chủ nhà xe (chưa có chi nhánh)',
        'Nhân viên Hà Nội',
        'Nhân viên Sài Gòn',
        'Nhân viên (2 nhà xe)',
        'Sale Hà Nội',
        'Sale Sài Gòn',
        'Tài khoản bị khóa',
        'Nhà xe bị khóa',
      ]);
    });

    it('should_fill_both_the_email_and_the_shared_seed_password', async () => {
      demoButton('Super Admin').click();
      await settle();

      expect(emailInput().value).toBe('superadmin@carrental.vn');
      expect(passwordInput().value).toBe('Hieudvt@123');
    });

    it('should_replace_the_previous_email_when_another_account_is_chosen', async () => {
      demoButton('Sale Hà Nội').click();
      await settle();
      demoButton('Tài khoản bị khóa').click();
      await settle();

      expect(emailInput().value).toBe('locked.user@rentcarhanoi.vn');
      expect(passwordInput().value).toBe('Hieudvt@123');
    });
  });
});
