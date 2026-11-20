import { FormControl } from '@angular/forms';

import { trimmedEmail } from './tenant-validators';

describe('trimmedEmail', () => {
  function emailWithLength(length: number): string {
    return 'a'.repeat(60) + '@' + 'b'.repeat(63) + '.' + 'c'.repeat(63) + '.'
      + 'd'.repeat(63) + '.' + 'e'.repeat(length - 253);
  }

  it.each([[254, true], [255, true], [256, false]] as const)(
    'should_match_the_backend_email_length_boundary_at_%i_characters', (length, valid) => {
      const control = new FormControl(emailWithLength(length), { nonNullable: true });
      expect(trimmedEmail(control) === null).toBe(valid);
    },
  );

  it.each([
    ['', true],
    ['  chu@binhan.vn  ', true],
    ['khong-phai-email', false],
    ['chu@@binhan.vn', false],
  ] as const)('should_validate_email_%s', (email, valid) => {
    const control = new FormControl(email, { nonNullable: true });
    expect(trimmedEmail(control) === null).toBe(valid);
  });
});
