import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, map, of, shareReplay, tap, throwError } from 'rxjs';

import { ApiSuccess, AuthEvent, AuthPayload, AuthSession, SUPER_ADMIN_ROLE } from '../models/auth.models';
import { toApiError } from './api-error';

export const AUTH_API = '/api/v1/auth';
const MAX_EVENTS = 30;

/**
 * Giữ phiên đăng nhập. Access token chỉ nằm trong biến của service (bộ nhớ),
 * không ghi `localStorage`/`sessionStorage`. Refresh token nằm trong cookie HttpOnly
 * nên frontend không đọc được, chỉ gọi `refresh-token` để trình duyệt tự gửi kèm.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private accessTokenValue: string | null = null;
  private refreshInFlight$: Observable<AuthPayload> | null = null;

  private readonly sessionState = signal<AuthSession | null>(null);
  private readonly eventState = signal<AuthEvent[]>([]);

  readonly session = this.sessionState.asReadonly();
  readonly events = this.eventState.asReadonly();
  readonly user = computed(() => this.sessionState()?.user ?? null);
  readonly availableTenants = computed(() => this.sessionState()?.availableTenants ?? []);
  readonly isSuperAdmin = computed(() => this.user()?.roleCode === SUPER_ADMIN_ROLE);
  readonly canChangeBranch = computed(() => (this.user()?.assignedBranches?.length ?? 0) > 1);
  readonly canChangeTenant = computed(() => this.availableTenants().length > 1);

  accessToken(): string | null {
    return this.accessTokenValue;
  }

  login(email: string, password: string): Observable<AuthPayload> {
    return this.call('login', { email, password }, 'Đăng nhập');
  }

  selectTenant(tenantId: string): Observable<AuthPayload> {
    return this.call('select-tenant', { tenantId }, 'Chọn nhà xe');
  }

  switchBranch(activeBranchId: string): Observable<AuthPayload> {
    return this.call('switch-branch', { activeBranchId }, 'Chọn chi nhánh');
  }

  /**
   * Cấp lại token từ cookie. Backend giữ nhà xe và chi nhánh đang chọn trong refresh token, nên
   * người dùng ở lại đúng chỗ đang làm (chưa chọn nhà xe thì nhận lại danh sách nhà xe).
   * Nhiều request cùng gọi sẽ dùng chung một lần gọi.
   */
  refresh(label = 'Làm mới token', logFailure = true): Observable<AuthPayload> {
    if (!this.refreshInFlight$) {
      this.refreshInFlight$ = this.call('refresh-token', {}, label, logFailure).pipe(
        finalize(() => (this.refreshInFlight$ = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.refreshInFlight$;
  }

  /** Khôi phục phiên khi tải lại trang. Không có cookie hợp lệ là chuyện bình thường (chưa đăng nhập), nên không ghi lỗi vào nhật ký. */
  restoreSession(): Observable<boolean> {
    return this.refresh('Khôi phục phiên', false).pipe(
      map(() => true),
      catchError(() => of(false)),
    );
  }

  /** Báo backend kết thúc phiên rồi xóa phiên cục bộ, kể cả khi gọi lỗi. */
  logout(): Observable<void> {
    return this.http.post(`${AUTH_API}/logout`, {}).pipe(
      tap(() => this.log('Đăng xuất', 'Backend đã xóa cookie refresh token', true)),
      catchError((error) => {
        this.log('Đăng xuất', toApiError(error).message, false);
        return of(null);
      }),
      map(() => undefined),
      finalize(() => this.clearSession()),
    );
  }

  clearSession(): void {
    this.accessTokenValue = null;
    this.sessionState.set(null);
  }

  private call(path: string, body: object, label: string, logFailure = true): Observable<AuthPayload> {
    return this.http.post<ApiSuccess<AuthPayload>>(`${AUTH_API}/${path}`, body).pipe(
      map((response) => response.data),
      tap((payload) => {
        this.apply(payload);
        this.log(label, describe(payload), true);
      }),
      catchError((error) => {
        if (logFailure) {
          const apiError = toApiError(error);
          this.log(label, `${apiError.status} ${apiError.code}: ${apiError.message}`, false);
        }
        return throwError(() => error);
      }),
    );
  }

  private apply(payload: AuthPayload): void {
    this.accessTokenValue = payload.accessToken;
    this.sessionState.set({
      user: payload.user,
      // Giữ danh sách nhà xe cũ nếu phản hồi này không kèm danh sách, để còn "đổi nhà xe".
      availableTenants: payload.availableTenants?.length ? payload.availableTenants : this.availableTenants(),
      expiresAt: Date.now() + payload.expiresIn * 1000,
    });
  }

  private log(action: string, detail: string, ok: boolean): void {
    this.eventState.update((events) => [{ at: Date.now(), action, detail, ok }, ...events].slice(0, MAX_EVENTS));
  }
}

function describe(payload: AuthPayload): string {
  const { user } = payload;
  const tenant = user.tenantName ?? 'chưa chọn nhà xe';
  const branch = user.activeBranchId ? 'đã chọn chi nhánh' : 'chưa chọn chi nhánh';
  return `${user.roleCode}, ${tenant}, ${branch}`;
}
