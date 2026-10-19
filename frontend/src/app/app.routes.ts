import { Routes } from '@angular/router';

import { guestGuard, homeGuard, selectBranchGuard, selectTenantGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  // Màn trước đăng nhập: thẻ nằm giữa màn hình, không có khung ứng dụng.
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login').then((m) => m.Login),
  },
  {
    path: 'select-tenant',
    canActivate: [selectTenantGuard],
    loadComponent: () => import('./features/select-tenant/select-tenant').then((m) => m.SelectTenant),
  },
  {
    path: 'select-branch',
    canActivate: [selectBranchGuard],
    loadComponent: () => import('./features/select-branch/select-branch').then((m) => m.SelectBranch),
  },
  // Các trang sau đăng nhập nằm trong khung chung (header + menu). Module mới: thêm route con ở đây
  // và một dòng trong core/navigation/nav-items.ts.
  {
    path: '',
    canActivate: [homeGuard],
    loadComponent: () => import('./layout/app-shell/app-shell').then((m) => m.AppShell),
    children: [
      { path: 'home', loadComponent: () => import('./features/home/home').then((m) => m.Home) },
      { path: '', pathMatch: 'full', redirectTo: 'home' },
    ],
  },
  { path: '**', redirectTo: 'home' },
];
