import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

export const THEME_STORAGE_KEY = 'car-rental-theme';
const DARK_SCHEME_QUERY = '(prefers-color-scheme: dark)';
const THEME_COLORS: Record<Theme, string> = { light: '#3B82F6', dark: '#0B1220' };

/**
 * Giao diện sáng/tối. Lần đầu theo cài đặt của hệ điều hành, sau đó nhớ lựa chọn của người dùng
 * (chỉ là tùy chọn hiển thị nên lưu `localStorage`; không có cũng không sao). Script nhỏ trong
 * `index.html` đặt sẵn `data-theme` trước khi Angular chạy để màn hình không nháy sáng.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);

  readonly theme = signal<Theme>(this.initialTheme());
  readonly isDark = computed(() => this.theme() === 'dark');

  constructor() {
    effect(() => this.apply(this.theme()));
  }

  toggle(): void {
    this.set(this.isDark() ? 'light' : 'dark');
  }

  set(theme: Theme): void {
    this.theme.set(theme);
    try {
      localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch {
      // Trình duyệt chặn lưu trữ (chế độ riêng tư...): vẫn đổi giao diện cho phiên này.
    }
  }

  private initialTheme(): Theme {
    try {
      const saved = localStorage.getItem(THEME_STORAGE_KEY);
      if (saved === 'light' || saved === 'dark') return saved;
    } catch {
      // bỏ qua, dùng cài đặt hệ thống
    }
    return this.document.defaultView?.matchMedia?.(DARK_SCHEME_QUERY).matches ? 'dark' : 'light';
  }

  private apply(theme: Theme): void {
    this.document.documentElement.dataset['theme'] = theme;
    this.document.querySelector('meta[name="theme-color"]')?.setAttribute('content', THEME_COLORS[theme]);
  }
}
