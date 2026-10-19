import { Component, computed, inject, isDevMode, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { toApiError } from '../../core/auth/api-error';
import { nextRouteFor } from '../../core/auth/auth-routing';
import { AuthService } from '../../core/auth/auth.service';
import { SESSION_EXPIRED_REASON } from '../../core/auth/auth.interceptor';
import { Icon } from '../../shared/icon/icon';
import { ThemeToggle } from '../../shared/theme-toggle/theme-toggle';

/** Email tài khoản mẫu (chỉ hiện khi chạy dev) để bấm điền nhanh. Mật khẩu vẫn phải tự nhập. */
const DEMO_ACCOUNTS = [
  { label: 'Chủ nhà xe Hà Nội', email: 'minh.admin@rentcarhanoi.vn' },
  { label: 'Nhân viên Hà Nội', email: 'lan.staff@rentcarhanoi.vn' },
  { label: 'Chủ nhà xe Sài Gòn', email: 'mai.admin@saigonauto.vn' },
  { label: 'Thuộc 2 nhà xe', email: 'multi.user@carrental.vn' },
  { label: 'Super Admin', email: 'superadmin@carrental.vn' },
  { label: 'Tài khoản bị khóa', email: 'locked.user@rentcarhanoi.vn' },
];

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, Icon, ThemeToggle],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly form = this.formBuilder.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  readonly submitting = signal(false);
  readonly showPassword = signal(false);
  readonly errorMessage = signal<string | null>(null);
  private readonly serverFieldErrors = signal<Record<string, string>>({});

  readonly demoAccounts = isDevMode() ? DEMO_ACCOUNTS : [];
  readonly notice = computed(() =>
    this.route.snapshot.queryParamMap.get('reason') === SESSION_EXPIRED_REASON
      ? 'Phiên đăng nhập đã hết hạn hoặc tài khoản vừa đăng nhập ở nơi khác. Vui lòng đăng nhập lại.'
      : null,
  );

  fillEmail(email: string): void {
    this.form.controls.email.setValue(email);
    this.errorMessage.set(null);
  }

  emailError(): string | null {
    return this.fieldError('email', 'Vui lòng nhập email', 'Email không đúng định dạng');
  }

  passwordError(): string | null {
    return this.fieldError('password', 'Vui lòng nhập mật khẩu');
  }

  submit(): void {
    this.errorMessage.set(null);
    this.serverFieldErrors.set({});
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { email, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.auth.login(email.trim(), password).subscribe({
      next: () => void this.router.navigateByUrl(nextRouteFor(this.auth.session())),
      error: (error: unknown) => {
        const apiError = toApiError(error);
        this.submitting.set(false);
        this.serverFieldErrors.set(apiError.fields);
        // Lỗi theo ô đã hiện cạnh ô nhập, chỉ hiện thông báo chung khi không có lỗi theo ô.
        this.errorMessage.set(Object.keys(apiError.fields).length ? null : apiError.message);
      },
    });
  }

  private fieldError(name: 'email' | 'password', requiredMessage: string, invalidMessage?: string): string | null {
    const control = this.form.controls[name];
    if (control.touched && control.hasError('required')) return requiredMessage;
    if (control.touched && invalidMessage && control.invalid) return invalidMessage;
    return this.serverFieldErrors()[name] ?? null;
  }
}
