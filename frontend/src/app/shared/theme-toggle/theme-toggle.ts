import { Component, inject } from '@angular/core';

import { ThemeService } from '../../core/theme/theme.service';
import { Icon } from '../icon/icon';

/** Nút tròn đổi sáng/tối. Dùng trên header, và nổi ở góc các màn trước đăng nhập. */
@Component({
  selector: 'app-theme-toggle',
  imports: [Icon],
  template: `
    <button
      type="button"
      class="theme-toggle"
      [attr.aria-label]="theme.isDark() ? 'Chuyển sang giao diện sáng' : 'Chuyển sang giao diện tối'"
      [attr.title]="theme.isDark() ? 'Giao diện sáng' : 'Giao diện tối'"
      (click)="theme.toggle()"
    >
      <app-icon [name]="theme.isDark() ? 'sun' : 'moon'" />
    </button>
  `,
  styles: `
    .theme-toggle {
      width: var(--touch);
      height: var(--touch);
      border: 0;
      border-radius: 50%;
      background: transparent;
      color: var(--text);
      display: grid;
      place-items: center;
      cursor: pointer;
    }
    .theme-toggle:hover {
      background: var(--blue-soft);
      color: var(--blue-text);
    }
  `,
})
export class ThemeToggle {
  protected readonly theme = inject(ThemeService);
}
