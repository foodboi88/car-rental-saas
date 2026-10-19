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
});
