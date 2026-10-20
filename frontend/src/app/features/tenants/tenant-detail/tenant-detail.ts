import { DatePipe, Location } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { toApiError } from '../../../core/auth/api-error';
import { Tenant, TenantAdmin } from '../../../core/models/tenant.models';
import { Icon } from '../../../shared/icon/icon';
import { AdminLookup, applyAccountRequirements } from '../admin-lookup';
import { AdminLookupNote } from '../admin-lookup-note/admin-lookup-note';
import { TenantService } from '../tenant.service';
import { trimmedEmail } from '../tenant-validators';

/** Hành động cần người dùng xác nhận trước khi gọi backend. */
type PendingAction = 'lock' | 'unlock' | 'remove';

@Component({
  selector: 'app-tenant-detail',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, Icon, AdminLookupNote],
  templateUrl: './tenant-detail.html',
  styleUrl: './tenant-detail.css',
})
export class TenantDetail {
  private readonly tenants = inject(TenantService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  readonly tenantId = inject(ActivatedRoute).snapshot.paramMap.get('tenantId')!;

  /** Tra cứu email chủ mới, kèm mã nhà xe để biết trước nếu người đó đã là chủ hoặc nhân viên của nhà xe này. */
  readonly lookup = new AdminLookup(this.tenants, this.tenantId);

  readonly tenant = signal<Tenant | null>(null);
  readonly admins = signal<TenantAdmin[] | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly pending = signal<PendingAction | null>(null);
  readonly showAdminForm = signal(false);
  readonly addingAdmin = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly adminError = signal<string | null>(null);
  private readonly adminServerErrors = signal<Record<string, string>>({});
  readonly adminsError = signal<string | null>(null);
  /** Thông báo từ màn trước (vừa tạo, vừa sửa) hoặc từ thao tác vừa làm xong. */
  readonly notice = signal<string | null>((inject(Location).getState() as { notice?: string } | null)?.notice ?? null);

  readonly adminForm = this.formBuilder.group({
    email: ['', [Validators.required, trimmedEmail, Validators.maxLength(255)]],
    fullName: ['', [Validators.maxLength(255)]],
    // Độ mạnh mật khẩu do backend kiểm tra, câu báo lỗi hiện dưới ô theo `adminServerErrors`.
    password: [''],
  });

  constructor() {
    this.loadTenant();
    this.loadAdmins();
  }

  ask(action: PendingAction): void {
    this.pending.set(action);
    this.notice.set(null);
  }

  cancel(): void {
    this.pending.set(null);
  }

  confirm(): void {
    const action = this.pending();
    if (!action || this.busy()) return;

    this.busy.set(true);
    this.errorMessage.set(null);
    const request$: Observable<unknown> =
      action === 'remove' ? this.tenants.remove(this.tenantId) : this.tenants.setActive(this.tenantId, action === 'unlock');
    request$.subscribe({
      next: () => {
        this.busy.set(false);
        this.pending.set(null);
        // Báo thành công sau khi đã tải lại nhà xe, để thông báo và trạng thái hiện cùng lúc.
        this.loadTenant(() => this.notice.set(NOTICE_AFTER[action]));
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.pending.set(null);
        this.errorMessage.set(toApiError(error).message);
      },
    });
  }

  toggleAdminForm(): void {
    this.showAdminForm.update((open) => !open);
    this.adminError.set(null);
  }

  adminFieldError(name: 'email' | 'fullName' | 'password'): string | null {
    const control = this.adminForm.controls[name];
    if (control.touched && control.hasError('required')) return 'Không được để trống';
    if (control.touched && control.hasError('email')) return 'Email không đúng định dạng';
    if (control.touched && control.hasError('maxlength')) return 'Tối đa 255 ký tự';
    return this.adminServerErrors()[name] ?? null;
  }

  /** Sửa email thì kết quả tra cứu cũ hết giá trị: ẩn họ tên, mật khẩu cho tới khi tra cứu lại. */
  onEmailInput(): void {
    this.lookup.reset();
    this.applyRequirements();
  }

  /** Rời ô email thì hỏi backend email đã có tài khoản chưa, để biết có cần hiện họ tên và mật khẩu. */
  checkEmail(): void {
    // ensureChecked: đã có kết quả cho email này thì không hỏi lại, tránh gọi thừa và nhấp nháy
    this.lookup.ensureChecked(this.adminForm.controls.email.value).subscribe(() => this.applyRequirements());
  }

  addAdmin(): void {
    this.adminError.set(null);
    this.adminServerErrors.set({});
    if (this.adminForm.invalid) {
      this.adminForm.markAllAsTouched();
      return;
    }

    // Người dùng có thể bấm gửi ngay khi vừa nhập email, nên tra cứu nếu chưa có kết quả
    this.addingAdmin.set(true);
    this.lookup.ensureChecked(this.adminForm.controls.email.value).subscribe((status) => {
      this.applyRequirements();
      if (status === 'blocked' || this.adminForm.invalid) {
        this.adminForm.markAllAsTouched();
        this.addingAdmin.set(false);
        return;
      }
      this.sendAdmin();
    });
  }

  private applyRequirements(): void {
    applyAccountRequirements(this.adminForm.controls, this.lookup.status());
  }

  private sendAdmin(): void {
    const { email, fullName, password } = this.adminForm.getRawValue();
    this.tenants
      .addAdmin(this.tenantId, {
        email: email.trim(),
        // Email đã có tài khoản thì backend bỏ qua họ tên và mật khẩu, nên chỉ gửi khi có nhập.
        ...(fullName.trim() ? { fullName: fullName.trim() } : {}),
        ...(password ? { password } : {}),
      })
      .subscribe({
        next: (result) => {
          this.addingAdmin.set(false);
          this.adminForm.reset();
          this.lookup.reset();
          this.applyRequirements();
          this.showAdminForm.set(false);
          const message = result.accountCreated
            ? `Đã tạo tài khoản mới và gán ${result.email} làm chủ nhà xe.`
            : `Đã gán tài khoản có sẵn ${result.email} làm chủ nhà xe.`;
          this.loadAdmins(() => this.notice.set(message));
        },
        error: (error: unknown) => {
          const apiError = toApiError(error);
          this.addingAdmin.set(false);
          this.adminServerErrors.set(apiError.fields);
          // Lỗi theo ô hiện cạnh ô nhập; chỉ hiện thông báo chung khi không có lỗi theo ô.
          this.adminError.set(Object.keys(apiError.fields).length ? null : apiError.message);
        },
      });
  }

  private loadTenant(onLoaded?: () => void): void {
    this.tenants.get(this.tenantId).subscribe({
      next: (tenant) => {
        this.tenant.set(tenant);
        this.loading.set(false);
        onLoaded?.();
      },
      error: (error: unknown) => {
        this.errorMessage.set(toApiError(error).message);
        this.loading.set(false);
      },
    });
  }

  private loadAdmins(onLoaded?: () => void): void {
    this.adminsError.set(null);
    this.tenants.admins(this.tenantId).subscribe({
      next: (admins) => {
        this.admins.set(admins);
        onLoaded?.();
      },
      error: (error: unknown) => this.adminsError.set(toApiError(error).message),
    });
  }
}

const NOTICE_AFTER: Record<PendingAction, string> = {
  lock: 'Đã khóa nhà xe.',
  unlock: 'Đã mở khóa nhà xe.',
  remove: 'Đã xóa nhà xe (xóa mềm): nhà xe bị khóa, dữ liệu vẫn được giữ.',
};
