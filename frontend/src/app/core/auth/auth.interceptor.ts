import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { nextRouteFor, ROUTES } from './auth-routing';
import { AUTH_API, AuthService } from './auth.service';

/** Hai API này không cần token, và nếu chúng lỗi thì không được làm mới lại (tránh lặp vô hạn). */
const PUBLIC_AUTH_CALLS = [`${AUTH_API}/login`, `${AUTH_API}/refresh-token`];

export const SESSION_EXPIRED_REASON = 'expired';

/**
 * Gắn `Authorization: Bearer` cho API của backend. Gặp 401 thì làm mới token đúng một lần:
 * - làm mới lỗi: xóa phiên, về màn đăng nhập;
 * - làm mới được và nhà xe/chi nhánh vẫn như cũ (trường hợp bình thường, backend giữ ngữ cảnh
 *   trong refresh token): gửi lại request với token mới;
 * - làm mới được nhưng nhà xe/chi nhánh đã khác (ví dụ nhà xe bị đổi): không gửi lại
 *   (sẽ bị từ chối), đưa người dùng về màn chọn tương ứng.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!request.url.startsWith('/api/') || PUBLIC_AUTH_CALLS.some((url) => request.url.startsWith(url))) {
    return next(request);
  }

  return next(withToken(request, auth.accessToken())).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401) {
        return throwError(() => error);
      }

      const before = auth.user();
      return auth.refresh().pipe(
        catchError((refreshError: unknown) => {
          auth.clearSession();
          void router.navigate([ROUTES.login], { queryParams: { reason: SESSION_EXPIRED_REASON } });
          return throwError(() => refreshError);
        }),
        switchMap((payload) => {
          const keptContext =
            payload.user.tenantId === (before?.tenantId ?? null) &&
            payload.user.activeBranchId === (before?.activeBranchId ?? null);
          if (!keptContext) {
            void router.navigateByUrl(nextRouteFor(auth.session()));
            return throwError(() => error);
          }
          return next(withToken(request, payload.accessToken));
        }),
      );
    }),
  );
};

function withToken(request: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  return token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
}
