import { Component, ElementRef, HostListener, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { ROUTES } from '../../core/auth/auth-routing';
import { AuthService } from '../../core/auth/auth.service';
import { visibleNavItems } from '../../core/navigation/nav-items';
import { ThemeService } from '../../core/theme/theme.service';
import { Icon } from '../../shared/icon/icon';

/**
 * Khung chung cho mọi trang sau đăng nhập: header trên cùng, menu bên trái (cố định từ 1024px,
 * dạng ngăn kéo trên màn nhỏ) và vùng nội dung. Module mới chỉ cần thêm route con.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.css',
})
export class AppShell {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly routes = ROUTES;
  protected readonly theme = inject(ThemeService);

  readonly user = this.auth.user;
  readonly canChangeBranch = this.auth.canChangeBranch;
  readonly canChangeTenant = this.auth.canChangeTenant;
  readonly navItems = computed(() => visibleNavItems(this.user()));

  readonly navOpen = signal(false);
  readonly menuOpen = signal(false);

  readonly tenantLabel = computed(() =>
    this.auth.isSuperAdmin() ? 'Toàn hệ thống' : (this.user()?.tenantName ?? 'Chưa chọn nhà xe'),
  );
  readonly branchLabel = computed(() => {
    const user = this.user();
    return user?.assignedBranches?.find((branch) => branch.branchId === user.activeBranchId)?.branchName ?? null;
  });
  readonly initials = computed(() => {
    const words = (this.user()?.fullname ?? '').trim().split(/\s+/).filter(Boolean);
    return words
      .slice(-2)
      .map((word) => word[0])
      .join('')
      .toUpperCase();
  });

  constructor() {
    // Chuyển trang xong thì đóng ngăn kéo và menu người dùng.
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.closeAll());
  }

  toggleNav(): void {
    this.menuOpen.set(false);
    this.navOpen.update((open) => !open);
  }

  toggleMenu(): void {
    this.navOpen.set(false);
    this.menuOpen.update((open) => !open);
  }

  closeAll(): void {
    this.navOpen.set(false);
    this.menuOpen.set(false);
  }

  goTo(url: string): void {
    this.closeAll();
    void this.router.navigateByUrl(url);
  }

  logout(): void {
    this.closeAll();
    this.auth.logout().subscribe(() => void this.router.navigateByUrl(ROUTES.login));
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeAll();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const userMenu = this.host.nativeElement.querySelector('.user-menu');
    if (this.menuOpen() && userMenu && !userMenu.contains(event.target as Node)) {
      this.menuOpen.set(false);
    }
  }
}
