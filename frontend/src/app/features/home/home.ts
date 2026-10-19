import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { toApiError } from '../../core/auth/api-error';
import { nextRouteFor } from '../../core/auth/auth-routing';
import { AuthService } from '../../core/auth/auth.service';
import { Icon } from '../../shared/icon/icon';

const CLOCK_TICK_MS = 1000;

@Component({
  selector: 'app-home',
  imports: [DatePipe, Icon],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly user = this.auth.user;
  readonly events = this.auth.events;
  readonly isSuperAdmin = this.auth.isSuperAdmin;

  readonly refreshing = signal(false);
  readonly errorMessage = signal<string | null>(null);
  private readonly now = signal(Date.now());

  readonly activeBranch = computed(() => {
    const user = this.user();
    return user?.assignedBranches?.find((branch) => branch.branchId === user.activeBranchId) ?? null;
  });
  readonly permissions = computed(() => this.user()?.permissions ?? []);

  /** Thời gian còn lại của access token, dạng mm:ss. */
  readonly tokenCountdown = computed(() => {
    const expiresAt = this.auth.session()?.expiresAt ?? 0;
    const remainingSeconds = Math.max(0, Math.floor((expiresAt - this.now()) / 1000));
    const minutes = String(Math.floor(remainingSeconds / 60)).padStart(2, '0');
    const seconds = String(remainingSeconds % 60).padStart(2, '0');
    return `${minutes}:${seconds}`;
  });

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), CLOCK_TICK_MS);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  refreshToken(): void {
    this.errorMessage.set(null);
    this.refreshing.set(true);
    this.auth.refresh().subscribe({
      // Backend giữ nhà xe và chi nhánh đang chọn, nên thường ở lại trang chủ; chỉ điều hướng khi ngữ cảnh đổi.
      next: () => {
        // Super Admin vẫn ở lại trang chủ (đường dẫn không đổi), nên phải tự tắt trạng thái chờ.
        this.refreshing.set(false);
        void this.router.navigateByUrl(nextRouteFor(this.auth.session()));
      },
      error: (error: unknown) => {
        this.refreshing.set(false);
        this.errorMessage.set(toApiError(error).message);
      },
    });
  }
}
