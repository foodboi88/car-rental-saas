import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ApiSuccess } from '../../core/models/auth.models';
import {
  AdminInput,
  AdminLookupResult,
  AdminResult,
  CreateTenantRequest,
  CreateTenantResult,
  Tenant,
  TenantAdmin,
  TenantListQuery,
  TenantPage,
  UpdateTenantRequest,
} from '../../core/models/tenant.models';

export const TENANTS_API = '/api/v1/tenants';

/** Gọi 8 API quản lý nhà xe. Mọi hàm trả về `data` đã bóc khỏi `ApiResponse`. */
@Injectable({ providedIn: 'root' })
export class TenantService {
  private readonly http = inject(HttpClient);

  list(query: TenantListQuery): Observable<TenantPage> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    const keyword = query.keyword.trim();
    if (keyword) params = params.set('keyword', keyword);
    if (query.isActive !== null) params = params.set('isActive', query.isActive);
    return this.http.get<ApiSuccess<TenantPage>>(TENANTS_API, { params }).pipe(map((response) => response.data));
  }

  get(tenantId: string): Observable<Tenant> {
    return this.http.get<ApiSuccess<Tenant>>(`${TENANTS_API}/${tenantId}`).pipe(map((response) => response.data));
  }

  create(request: CreateTenantRequest): Observable<CreateTenantResult> {
    return this.http.post<ApiSuccess<CreateTenantResult>>(TENANTS_API, request).pipe(map((response) => response.data));
  }

  update(tenantId: string, request: UpdateTenantRequest): Observable<Tenant> {
    return this.http.put<ApiSuccess<Tenant>>(`${TENANTS_API}/${tenantId}`, request).pipe(map((response) => response.data));
  }

  setActive(tenantId: string, isActive: boolean): Observable<Tenant> {
    return this.http
      .patch<ApiSuccess<Tenant>>(`${TENANTS_API}/${tenantId}/status`, { isActive })
      .pipe(map((response) => response.data));
  }

  /** Xóa mềm: backend chỉ đặt `isActive = false`, không xóa dòng nào. */
  remove(tenantId: string): Observable<void> {
    return this.http.delete(`${TENANTS_API}/${tenantId}`).pipe(map(() => undefined));
  }

  admins(tenantId: string): Observable<TenantAdmin[]> {
    return this.http
      .get<ApiSuccess<TenantAdmin[]>>(`${TENANTS_API}/${tenantId}/admins`)
      .pipe(map((response) => response.data));
  }

  /**
   * Hỏi trước email đã có tài khoản chưa. Có `tenantId` thì backend kiểm tra thêm quan hệ với nhà xe đó.
   * Dùng POST để email nằm trong body, không lọt vào log truy cập như khi nằm trên đường dẫn.
   */
  lookupAdminByEmail(email: string, tenantId?: string): Observable<AdminLookupResult> {
    const body = { email: email.trim(), ...(tenantId ? { tenantId } : {}) };
    return this.http
      .post<ApiSuccess<AdminLookupResult>>(`${TENANTS_API}/admin-lookup`, body)
      .pipe(map((response) => response.data));
  }

  addAdmin(tenantId: string, request: AdminInput): Observable<AdminResult> {
    return this.http
      .post<ApiSuccess<AdminResult>>(`${TENANTS_API}/${tenantId}/admins`, request)
      .pipe(map((response) => response.data));
  }
}
