import { Component, input } from '@angular/core';

import { AdminEmailCheck } from '../admin-email-check';

/**
 * Dòng phản hồi dưới ô email chủ nhà xe, theo kết quả kiểm tra. Dùng chung cho form tạo nhà xe và form thêm chủ.
 * Lớp `hint`, `email-check-ok` và `field-error` là lớp chung ở styles.css.
 */
@Component({
  selector: 'app-admin-email-check-note',
  template: `
    @switch (emailCheck().status()) {
      @case ('checking') {
        <span class="hint" role="status">Đang kiểm tra email...</span>
      }
      @case ('existing') {
        <span class="email-check-ok" role="status">
          Đã có tài khoản {{ emailCheck().fullName() }}. Hệ thống sẽ gán tài khoản này làm chủ nhà xe.
        </span>
      }
      @case ('new') {
        <span class="hint" role="status">Email chưa có tài khoản. Nhập họ tên và mật khẩu để tạo mới.</span>
      }
      @case ('blocked') {
        <span class="field-error" role="alert">{{ emailCheck().reason() }}</span>
      }
    }
  `,
  // Không tạo thêm khối bao quanh, để dòng chữ nằm trong `.field` như một phần tử con trực tiếp
  styles: `
    :host {
      display: contents;
    }
  `,
})
export class AdminEmailCheckNote {
  readonly emailCheck = input.required<AdminEmailCheck>();
}
