import { AbstractControl, ValidationErrors } from '@angular/forms';

/** Cùng mẫu với backend: chữ thường, số, dấu chấm, gạch ngang; không bắt đầu, kết thúc hoặc lặp dấu. */
const DOMAIN_PATTERN = /^[a-z0-9]+([.-][a-z0-9]+)*$/;

/** Giữ cấu trúc email của Angular, dùng giới hạn 255 ký tự theo DTO backend thay vì 254. */
const EMAIL_PATTERN = /^(?=.{1,255}$)(?=.{1,64}@)[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+)*@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/;

/** Cùng cách chuẩn hóa email với backend: bỏ khoảng trắng hai đầu và đổi chữ thường, để so sánh hai email có trùng nhau không. */
export function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

/** Kiểm email sau khi cắt khoảng trắng; ô rỗng để validator required quyết định. Backend vẫn kiểm tra bằng @Email. */
export function trimmedEmail(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  return !value || EMAIL_PATTERN.test(value) ? null : { email: true };
}

/** Kiểm tra sau khi làm gọn (cắt khoảng trắng, đổi chữ thường), vì lúc gửi đi domain cũng được làm gọn như vậy. */
export function domainValidator(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim().toLowerCase();
  return !value || DOMAIN_PATTERN.test(value) ? null : { domain: true };
}
