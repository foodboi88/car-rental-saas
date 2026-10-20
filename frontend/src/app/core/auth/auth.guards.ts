import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';

import { nextRouteFor, ROUTES } from './auth-routing';
import { AuthService } from './auth.service';

/** Chỉ cho vào khi chưa đăng nhập; đã có phiên thì đưa tới màn tiếp theo. */
export const guestGuard: CanActivateFn = () => {
  const session = inject(AuthService).session();
  return session ? inject(Router).parseUrl(nextRouteFor(session)) : true;
};

/** Màn chọn nhà xe: cần phiên, và không phải Super Admin (Super Admin không thuộc nhà xe nào). */
export const selectTenantGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.session()) return router.parseUrl(ROUTES.login);
  return auth.isSuperAdmin() ? router.parseUrl(ROUTES.home) : true;
};

/** Màn chọn chi nhánh: cần đã vào một nhà xe có chi nhánh. */
export const selectBranchGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const user = auth.user();
  if (!user) return router.parseUrl(ROUTES.login);

  const canPickBranch = !!user.tenantId && (user.assignedBranches?.length ?? 0) > 0;
  return canPickBranch ? true : redirectToNextStep(auth, router);
};

/** Trang chỉ dành cho Super Admin (quản lý nhà xe): người khác về trang chủ, chưa đăng nhập thì về màn đăng nhập. */
export const superAdminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.session()) return router.parseUrl(ROUTES.login);
  return auth.isSuperAdmin() ? true : router.parseUrl(ROUTES.home);
};

/** Trang chủ: chỉ vào khi đã hoàn tất các bước chọn nhà xe và chi nhánh. */
export const homeGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.session()) return router.parseUrl(ROUTES.login);
  return nextRouteFor(auth.session()) === ROUTES.home ? true : redirectToNextStep(auth, router);
};

function redirectToNextStep(auth: AuthService, router: Router): UrlTree {
  return router.parseUrl(nextRouteFor(auth.session()));
}
