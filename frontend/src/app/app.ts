import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { ThemeService } from './core/theme/theme.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {
  // Tạo sớm để giao diện sáng/tối được áp dụng ngay từ màn đăng nhập.
  constructor() {
    inject(ThemeService);
  }
}
