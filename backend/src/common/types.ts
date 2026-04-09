// 统一响应格式
export interface ApiResponse<T> {
  success: true;
  data: T;
}

export interface ApiError {
  success: false;
  error: {
    code: string;
    message: string;
  };
}

export type ApiResult<T> = ApiResponse<T> | ApiError;

// 错误码常量
export const ErrorCodes = {
  INVALID_DEVICE_ID: 'INVALID_DEVICE_ID',
  VALIDATION_ERROR: 'VALIDATION_ERROR',
  UNAUTHORIZED: 'UNAUTHORIZED',
  CHARACTER_NOT_FOUND: 'CHARACTER_NOT_FOUND',
  ALREADY_PURCHASED: 'ALREADY_PURCHASED',
  PURCHASE_VERIFY_FAILED: 'PURCHASE_VERIFY_FAILED',
  USER_NOT_FOUND: 'USER_NOT_FOUND',
  INTERNAL_ERROR: 'INTERNAL_ERROR',
} as const;

// JWT payload 结构
export interface JwtPayload {
  sub: string;       // userId
  deviceId: string;  // 脱敏设备标识
  iat?: number;
  exp?: number;
}

// CurrentUser 注入结构
export interface CurrentUserPayload {
  userId: string;
  deviceId: string;
}
