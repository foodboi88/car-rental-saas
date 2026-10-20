import { computed, signal } from '@angular/core';
import { AbstractControl, Validators } from '@angular/forms';
import { Observable, catchError, map, of, tap } from 'rxjs';

import { TenantService } from './tenant.service';
import { trimmedEmail } from './tenant-validators';

/**
 * - `idle`: chưa tra cứu (chưa nhập email, hoặc vừa sửa email).
 * - `checking`: đang hỏi backend.
 * - `new`: email chưa có tài khoản, cần họ tên và mật khẩu để tạo mới.
 * - `existing`: email đã có tài khoản dùng được, chỉ cần gán, không cần họ tên và mật khẩu.
 * - `blocked`: không gán được (tài khoản bị khóa, là Super Admin, đã là chủ hoặc nhân viên của nhà xe, nhà xe bị khóa).
 * - `failed`: không tra cứu được (lỗi mạng...), đành hiện đủ ô và để backend quyết khi gửi.
 */
export type LookupStatus = 'idle' | 'checking' | 'new' | 'existing' | 'blocked' | 'failed';

// `failed` cố ý không nằm trong đây: lỗi tra cứu có thể chỉ thoáng qua, nên lần rời ô email sau sẽ thử lại
const SETTLED: readonly LookupStatus[] = ['new', 'existing', 'blocked'];
const MAX_NAME = 255;

/**
 * Trạng thái tra cứu email của MỘT chủ nhà xe, để biết có cần hiện ô họ tên và mật khẩu hay không.
 * Mỗi dòng nhập chủ nhà xe giữ một đối tượng riêng.
 */
export class AdminLookup {
  readonly status = signal<LookupStatus>('idle');
  readonly fullName = signal<string | null>(null);
  readonly reason = signal<string | null>(null);
  /** Chỉ hiện họ tên, mật khẩu khi cần tạo tài khoản mới (hoặc khi không tra cứu được). */
  readonly needsAccountDetails = computed(() => this.status() === 'new' || this.status() === 'failed');

  /** Mỗi lần tra cứu hoặc đặt lại tăng một số, kết quả về muộn của lần cũ sẽ bị bỏ qua. */
  private ticket = 0;

  constructor(
    private readonly tenants: TenantService,
    /** Có thì backend kiểm tra thêm quan hệ với nhà xe này (đã là chủ, đang là nhân viên, nhà xe bị khóa). */
    private readonly tenantId?: string,
  ) {}

  reset(): void {
    this.ticket++;
    this.status.set('idle');
    this.fullName.set(null);
    this.reason.set(null);
  }

  /** Tra cứu lại nếu chưa có kết quả cho email hiện tại, ngược lại dùng kết quả đã có. */
  ensureChecked(email: string): Observable<LookupStatus> {
    return SETTLED.includes(this.status()) ? of(this.status()) : this.check(email);
  }

  check(email: string): Observable<LookupStatus> {
    const value = email.trim();
    if (!value || trimmedEmail({ value } as AbstractControl<string>)) {
      this.reset();
      return of('idle');
    }

    const ticket = ++this.ticket;
    this.status.set('checking');
    return this.tenants.lookupAdminByEmail(value, this.tenantId).pipe(
      tap((result) => {
        if (ticket !== this.ticket) return;
        this.fullName.set(result.fullName);
        this.reason.set(result.reason);
        this.status.set(!result.canBeAdmin ? 'blocked' : result.accountExists ? 'existing' : 'new');
      }),
      catchError(() => {
        if (ticket === this.ticket) this.status.set('failed');
        return of(null);
      }),
      map(() => this.status()),
    );
  }
}

/**
 * Chỉ bắt buộc họ tên và mật khẩu khi email chưa có tài khoản. Các trường hợp còn lại xóa giá trị đã nhập
 * và bỏ ràng buộc, để không gửi dữ liệu thừa.
 */
export function applyAccountRequirements(
  controls: { fullName: AbstractControl<string>; password: AbstractControl<string> },
  status: LookupStatus,
): void {
  const needsDetails = status === 'new';
  controls.fullName.setValidators(needsDetails ? [Validators.required, Validators.maxLength(MAX_NAME)] : [Validators.maxLength(MAX_NAME)]);
  controls.password.setValidators(needsDetails ? [Validators.required] : []);
  if (status !== 'new' && status !== 'failed') {
    controls.fullName.setValue('');
    controls.password.setValue('');
  }
  controls.fullName.updateValueAndValidity();
  controls.password.updateValueAndValidity();
}
