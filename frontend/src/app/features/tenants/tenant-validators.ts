import { AbstractControl, ValidationErrors, Validators } from '@angular/forms';

/** Cùng mẫu với backend: chữ thường, số, dấu chấm, gạch ngang; không bắt đầu, kết thúc hoặc lặp dấu. */
const DOMAIN_PATTERN = /^[a-z0-9]+([.-][a-z0-9]+)*$/;

/** Cùng cách chuẩn hóa email với backend: bỏ khoảng trắng hai đầu và đổi chữ thường, để so sánh hai email có trùng nhau không. */
export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

/** Như `Validators.email` nhưng bỏ qua khoảng trắng thừa ở hai đầu (hay gặp khi dán), vì lúc gửi đi email cũng được cắt gọn. */
export function trimmedEmail(control: AbstractControl<string>): ValidationErrors | null {
  return Validators.email({ value: control.value.trim() } as AbstractControl);
}

/** Kiểm tra sau khi làm gọn (cắt khoảng trắng, đổi chữ thường), vì lúc gửi đi domain cũng được làm gọn như vậy. */
export function domainValidator(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim().toLowerCase();
  return !value || DOMAIN_PATTERN.test(value) ? null : { domain: true };
}
