import { HttpErrorResponse } from '@angular/common/http';

import { ApiFailure } from '../models/auth.models';

export interface ApiError {
  status: number;
  code: string;
  message: string;
  /** Lỗi theo từng ô nhập (chỉ có với VALIDATION_ERROR). */
  fields: Record<string, string>;
}

const NETWORK_ERROR_MESSAGE = 'Không kết nối được máy chủ. Hãy kiểm tra backend đã chạy chưa.';
const UNKNOWN_ERROR_MESSAGE = 'Có lỗi xảy ra, vui lòng thử lại.';

export function toApiError(error: unknown): ApiError {
  if (!(error instanceof HttpErrorResponse)) {
    return { status: -1, code: 'UNKNOWN', message: UNKNOWN_ERROR_MESSAGE, fields: {} };
  }
  if (error.status === 0) {
    return { status: 0, code: 'NETWORK', message: NETWORK_ERROR_MESSAGE, fields: {} };
  }
  const body = error.error as Partial<ApiFailure> | null;
  return {
    status: error.status,
    code: body?.error?.code ?? `HTTP_${error.status}`,
    message: body?.error?.message ?? UNKNOWN_ERROR_MESSAGE,
    fields: body?.error?.code === 'VALIDATION_ERROR' ? (body.data ?? {}) : {},
  };
}
