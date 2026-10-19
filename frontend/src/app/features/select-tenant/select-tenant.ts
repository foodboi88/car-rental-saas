import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { toApiError } from '../../core/auth/api-error';
import { nextRouteFor, ROUTES } from '../../core/auth/auth-routing';
import { AuthService } from '../../core/auth/auth.service';
import { TenantSummary } from '../../core/models/auth.models';
import { Icon } from '../../shared/icon/icon';
import { ThemeToggle } from '../../shared/theme-toggle/theme-toggle';

@Component({
  selector: 'app-select-tenant',
  imports: [Icon, ThemeToggle],
  templateUrl: './select-tenant.html',
})
export class SelectTenant {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly user = this.auth.user;
  readonly tenants = this.auth.availableTenants;
  readonly pendingTenantId = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  choose(tenant: TenantSummary): void {
    if (!tenant.isActive || this.pendingTenantId()) return;

    this.errorMessage.set(null);
    this.pendingTenantId.set(tenant.tenantId);
    this.auth.selectTenant(tenant.tenantId).subscribe({
      next: () => void this.router.navigateByUrl(nextRouteFor(this.auth.session())),
      error: (error: unknown) => {
        this.pendingTenantId.set(null);
        this.errorMessage.set(toApiError(error).message);
      },
    });
  }

  logout(): void {
    this.auth.logout().subscribe(() => void this.router.navigateByUrl(ROUTES.login));
  }
}
