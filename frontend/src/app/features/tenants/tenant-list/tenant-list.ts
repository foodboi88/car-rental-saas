import { DatePipe } from '@angular/common';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { toApiError } from '../../../core/auth/api-error';
import { TenantPage } from '../../../core/models/tenant.models';
import { Icon } from '../../../shared/icon/icon';
import { TenantService } from '../tenant.service';

const PAGE_SIZE = 10;

type StatusFilter = 'all' | 'active' | 'locked';

const FILTER_TO_ACTIVE: Record<StatusFilter, boolean | null> = { all: null, active: true, locked: false };

@Component({
  selector: 'app-tenant-list',
  imports: [DatePipe, FormsModule, Icon, RouterLink],
  templateUrl: './tenant-list.html',
  styleUrl: './tenant-list.css',
})
export class TenantList {
  private readonly tenants = inject(TenantService);
  private request: Subscription | null = null;

  readonly keyword = signal('');
  readonly status = signal<StatusFilter>('all');
  readonly page = signal(0);
  readonly result = signal<TenantPage | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal<string | null>(null);

  /** Tìm kiếm hoặc lọc có hiệu lực, để phân biệt "chưa có nhà xe nào" với "không tìm thấy". */
  readonly filtered = computed(() => this.keyword().trim() !== '' || this.status() !== 'all');
  readonly pageInfo = computed(() => this.result()?.page ?? null);
  readonly hasPrevious = computed(() => this.page() > 0);
  readonly hasNext = computed(() => this.page() + 1 < (this.pageInfo()?.totalPages ?? 0));

  constructor() {
    this.load();
    inject(DestroyRef).onDestroy(() => this.request?.unsubscribe());
  }

  search(): void {
    this.page.set(0);
    this.load();
  }

  goTo(page: number): void {
    this.page.set(page);
    this.load();
  }

  load(): void {
    // Chỉ giữ kết quả của lần gọi mới nhất, tránh trả lời cũ về muộn ghi đè.
    this.request?.unsubscribe();
    this.loading.set(true);
    this.errorMessage.set(null);
    this.request = this.tenants
      .list({ keyword: this.keyword(), isActive: FILTER_TO_ACTIVE[this.status()], page: this.page(), size: PAGE_SIZE })
      .subscribe({
        next: (result) => {
          this.result.set(result);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.errorMessage.set(toApiError(error).message);
          this.loading.set(false);
        },
      });
  }
}
