import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { toApiError } from '../../core/auth/api-error';
import { nextRouteFor, ROUTES } from '../../core/auth/auth-routing';
import { AuthService } from '../../core/auth/auth.service';
import { BranchSummary } from '../../core/models/auth.models';
import { Icon } from '../../shared/icon/icon';
import { ThemeToggle } from '../../shared/theme-toggle/theme-toggle';

@Component({
  selector: 'app-select-branch',
  imports: [Icon, ThemeToggle],
  templateUrl: './select-branch.html',
})
export class SelectBranch {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly user = this.auth.user;
  readonly branches = computed(() => this.user()?.assignedBranches ?? []);
  readonly canChangeTenant = computed(() => this.auth.availableTenants().length > 1);
  readonly pendingBranchId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  choose(branch: BranchSummary): void {
    if (this.pendingBranchId()) return;

    this.errorMessage.set(null);
    this.pendingBranchId.set(branch.branchId);
    this.auth.switchBranch(branch.branchId).subscribe({
      next: () => void this.router.navigateByUrl(nextRouteFor(this.auth.session())),
      error: (error: unknown) => {
        this.pendingBranchId.set(null);
        this.errorMessage.set(toApiError(error).message);
      },
    });
  }

  changeTenant(): void {
    void this.router.navigateByUrl(ROUTES.selectTenant);
  }

  logout(): void {
    this.auth.logout().subscribe(() => void this.router.navigateByUrl(ROUTES.login));
  }
}
