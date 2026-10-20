import { Component, computed, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormArray,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, forkJoin, tap } from 'rxjs';

import { toApiError } from '../../../core/auth/api-error';
import { CreateTenantResult, PLAN_TIERS, PlanTier, Tenant } from '../../../core/models/tenant.models';
import { Icon } from '../../../shared/icon/icon';
import { AdminLookup, applyAccountRequirements } from '../admin-lookup';
import { AdminLookupNote } from '../admin-lookup-note/admin-lookup-note';
import { TenantService } from '../tenant.service';
import { domainValidator, normalizeEmail, trimmedEmail } from '../tenant-validators';

const MAX_TEXT = 255;
const MAX_URL = 500;
const MAX_PHONE = 20;

const DOMAIN_TAKEN_CODE = 'TENANT_DOMAIN_ALREADY_EXISTS';

/** Tạo nhà xe (kèm chủ) hoặc sửa nhà xe có sẵn, tùy đường dẫn có `:tenantId` hay không. */
@Component({
  selector: 'app-tenant-form',
  imports: [ReactiveFormsModule, RouterLink, Icon, AdminLookupNote],
  templateUrl: './tenant-form.html',
  styleUrl: './tenant-form.css',
})
export class TenantForm {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly tenants = inject(TenantService);
  private readonly router = inject(Router);

  readonly tenantId = inject(ActivatedRoute).snapshot.paramMap.get('tenantId');
  readonly isEdit = this.tenantId !== null;
  readonly title = this.isEdit ? 'Sửa nhà xe' : 'Thêm nhà xe';
  readonly planTiers = PLAN_TIERS;

  /** Mỗi dòng chủ nhà xe giữ một đối tượng tra cứu email riêng, khóa theo FormGroup của dòng đó. */
  private readonly lookups = new Map<AbstractControl, AdminLookup>();

  readonly form = this.formBuilder.group({
    name: ['', [Validators.required, Validators.maxLength(MAX_TEXT)]],
    domain: ['', [Validators.required, Validators.maxLength(MAX_TEXT), domainValidator]],
    planTier: ['FREE' as PlanTier, [Validators.required]],
    contactEmail: ['', [trimmedEmail]],
    contactPhone: ['', [Validators.maxLength(MAX_PHONE)]],
    logoUrl: ['', [Validators.maxLength(MAX_URL)]],
    admins: this.formBuilder.array([this.newAdmin()]),
  });

  readonly loading = signal(this.isEdit);
  readonly loadFailed = signal(false);
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  private readonly serverErrors = signal<Record<string, string>>({});
  private readonly adminCount = signal(1);
  /** Chỉ cho xóa chủ khi còn hơn một người: backend yêu cầu ít nhất một chủ. */
  readonly canRemoveAdmin = computed(() => this.adminCount() > 1);

  constructor() {
    if (!this.isEdit) return;

    // Sửa nhà xe không đụng tới chủ nhà xe (có màn riêng ở trang chi tiết).
    this.admins.clear();
    this.tenants.get(this.tenantId!).subscribe({
      next: (tenant) => {
        this.fillFrom(tenant);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(toApiError(error).message);
        this.loadFailed.set(true);
        this.loading.set(false);
      },
    });
  }

  get admins(): FormArray {
    return this.form.controls.admins;
  }

  addAdmin(): void {
    this.admins.push(this.newAdmin());
    this.adminCount.set(this.admins.length);
  }

  removeAdmin(index: number): void {
    if (!this.canRemoveAdmin()) return;
    this.admins.removeAt(index);
    this.adminCount.set(this.admins.length);
  }

  /** Lỗi của một ô: ưu tiên lỗi nhập sai tại chỗ, sau đó là lỗi backend trả theo tên trường. */
  error(path: string, control: AbstractControl): string | null {
    if (control.touched && control.invalid) return messageFor(control);
    return this.serverErrors()[path] ?? null;
  }

  /**
   * Số thứ tự (từ 1) của dòng chủ nhà xe phía trên đã dùng cùng email này, hoặc null nếu không trùng.
   * Chỉ báo ở các dòng sau, để dòng đầu tiên dùng email đó vẫn bình thường. Backend vẫn kiểm tra trùng như chốt cuối.
   */
  duplicateOf(group: AbstractControl): number | null {
    const email = normalizeEmail(group.value.email);
    if (!email) return null;

    const own = this.admins.controls.indexOf(group);
    const first = this.admins.controls.findIndex((other) => normalizeEmail(other.value.email) === email);
    return first >= 0 && first < own ? first + 1 : null;
  }

  lookupOf(group: AbstractControl): AdminLookup {
    return this.lookups.get(group)!;
  }

  /** Sửa email thì kết quả tra cứu cũ hết giá trị: ẩn họ tên, mật khẩu cho tới khi tra cứu lại. */
  onAdminEmailInput(group: AbstractControl): void {
    this.lookupOf(group).reset();
    this.applyRequirements(group);
  }

  /** Rời ô email thì hỏi backend email đã có tài khoản chưa, để biết có cần hiện họ tên và mật khẩu. */
  checkAdmin(group: AbstractControl): void {
    // ensureChecked: đã có kết quả cho email này (sửa email thì kết quả đã bị bỏ) thì không hỏi lại, tránh gọi thừa và nhấp nháy
    this.lookupOf(group)
      .ensureChecked(group.value.email)
      .subscribe(() => this.applyRequirements(group));
  }

  submit(): void {
    this.errorMessage.set(null);
    this.serverErrors.set({});
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.isEdit) {
      this.submitting.set(true);
      this.send();
      return;
    }
    if (this.admins.controls.some((group) => this.duplicateOf(group) !== null)) {
      return;
    }

    // Trước khi gửi phải biết mỗi chủ có cần họ tên, mật khẩu hay không (người dùng có thể bấm gửi ngay khi vừa nhập email)
    this.submitting.set(true);
    const checks = this.admins.controls.map((group) =>
      this.lookupOf(group)
        .ensureChecked(group.value.email)
        .pipe(tap(() => this.applyRequirements(group))),
    );
    forkJoin(checks).subscribe((statuses) => {
      if (statuses.includes('blocked') || this.form.invalid) {
        this.form.markAllAsTouched();
        this.submitting.set(false);
        return;
      }
      this.send();
    });
  }

  private send(): void {
    const request$: Observable<Tenant | CreateTenantResult> = this.isEdit
      ? this.tenants.update(this.tenantId!, this.tenantBody())
      : this.tenants.create({ ...withoutBlanks(this.tenantBody()), admins: this.adminBodies() });
    request$.subscribe({
      next: (result) => {
        const tenant = 'tenant' in result ? result.tenant : result;
        void this.router.navigate(['/tenants', tenant.id], { state: { notice: noticeFor(result) } });
      },
      error: (error: unknown) => {
        const apiError = toApiError(error);
        this.submitting.set(false);
        const fields = apiError.code === DOMAIN_TAKEN_CODE ? { domain: apiError.message } : apiError.fields;
        this.serverErrors.set(fields);
        // Lỗi theo ô hiện cạnh ô nhập; chỉ hiện thông báo chung khi không có lỗi theo ô.
        this.errorMessage.set(Object.keys(fields).length ? null : apiError.message);
      },
    });
  }

  private newAdmin() {
    const group = this.formBuilder.group({
      email: ['', [Validators.required, trimmedEmail, Validators.maxLength(MAX_TEXT)]],
      fullName: ['', [Validators.maxLength(MAX_TEXT)]],
      // Độ mạnh mật khẩu do backend kiểm tra, câu báo lỗi hiện dưới ô theo `serverErrors`.
      password: [''],
    });
    this.lookups.set(group, new AdminLookup(this.tenants));
    return group;
  }

  private applyRequirements(group: AbstractControl): void {
    const controls = (group as FormGroup).controls;
    applyAccountRequirements(
      { fullName: controls['fullName'] as AbstractControl<string>, password: controls['password'] as AbstractControl<string> },
      this.lookupOf(group).status(),
    );
  }

  private fillFrom(tenant: Tenant): void {
    this.form.patchValue({
      name: tenant.name,
      domain: tenant.domain,
      planTier: tenant.planTier,
      contactEmail: tenant.contactEmail ?? '',
      contactPhone: tenant.contactPhone ?? '',
      logoUrl: tenant.logoUrl ?? '',
    });
  }

  /**
   * Khi sửa, gửi cả ô đã để trống: chuỗi rỗng nghĩa là xóa giá trị cũ.
   * Khi tạo, ô trống được bỏ đi (xem `withoutBlanks`).
   */
  private tenantBody() {
    const value = this.form.getRawValue();
    return {
      name: value.name.trim(),
      domain: value.domain.trim().toLowerCase(),
      planTier: value.planTier,
      contactEmail: value.contactEmail.trim(),
      contactPhone: value.contactPhone.trim(),
      logoUrl: value.logoUrl.trim(),
    };
  }

  private adminBodies() {
    return this.form.getRawValue().admins.map((admin) => {
      const fullName = admin.fullName.trim();
      return {
        email: admin.email.trim(),
        // Email đã có tài khoản thì backend bỏ qua họ tên và mật khẩu, nên chỉ gửi khi có nhập.
        ...(fullName ? { fullName } : {}),
        ...(admin.password ? { password: admin.password } : {}),
      };
    });
  }
}

/** Khi tạo mới, trường tùy chọn để trống thì không gửi, để backend dùng giá trị mặc định. */
function withoutBlanks<T extends object>(body: T): T {
  return Object.fromEntries(Object.entries(body).filter(([, value]) => value !== '')) as T;
}

function noticeFor(result: Tenant | CreateTenantResult): string {
  if (!('tenant' in result)) return 'Đã lưu thay đổi.';

  const created = result.admins.filter((admin) => admin.accountCreated).map((admin) => admin.email);
  const linked = result.admins.filter((admin) => !admin.accountCreated).map((admin) => admin.email);
  const parts = ['Đã tạo nhà xe.'];
  if (created.length) parts.push(`Tài khoản mới: ${created.join(', ')}.`);
  if (linked.length) parts.push(`Gán tài khoản có sẵn: ${linked.join(', ')}.`);
  return parts.join(' ');
}

function messageFor(control: AbstractControl): string {
  if (control.hasError('required')) return 'Không được để trống';
  if (control.hasError('email')) return 'Email không đúng định dạng';
  if (control.hasError('domain')) return 'Domain chỉ gồm chữ thường, số, dấu chấm và gạch ngang';
  if (control.hasError('maxlength')) return `Tối đa ${control.getError('maxlength').requiredLength} ký tự`;
  return 'Giá trị không hợp lệ';
}
