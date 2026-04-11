import { ExecutionContext, ForbiddenException } from '@nestjs/common';
import { AdminGuard } from '../guards/admin.guard';
import { CurrentUserPayload } from '../../common/types';

function createMockContext(user: CurrentUserPayload | undefined | null): ExecutionContext {
  return {
    switchToHttp: () => ({
      getRequest: () => ({ user }),
    }),
  } as ExecutionContext;
}

describe('AdminGuard', () => {
  let guard: AdminGuard;

  beforeEach(() => {
    guard = new AdminGuard();
  });

  it('role: admin -> returns true', () => {
    const user: CurrentUserPayload = {
      userId: 'user-123',
      deviceId: 'device-456',
      role: 'admin',
    };
    const context = createMockContext(user);

    expect(guard.canActivate(context)).toBe(true);
  });

  it('role: user -> throws ForbiddenException', () => {
    const user: CurrentUserPayload = {
      userId: 'user-123',
      deviceId: 'device-456',
      role: 'user',
    };
    const context = createMockContext(user);

    expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
  });

  it('role: vip -> throws ForbiddenException', () => {
    const user: CurrentUserPayload = {
      userId: 'user-123',
      deviceId: 'device-456',
      role: 'vip',
    };
    const context = createMockContext(user);

    expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
  });

  it('user: undefined -> throws ForbiddenException', () => {
    const context = createMockContext(undefined);

    expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
  });

  it('user: null -> throws ForbiddenException', () => {
    const context = createMockContext(null);

    expect(() => guard.canActivate(context)).toThrow(ForbiddenException);
  });

  it('throws ForbiddenException with correct response body', () => {
    const user: CurrentUserPayload = {
      userId: 'user-123',
      deviceId: 'device-456',
      role: 'user',
    };
    const context = createMockContext(user);

    expect(() => guard.canActivate(context)).toThrow();
    try {
      guard.canActivate(context);
    } catch (error) {
      expect(error).toBeInstanceOf(ForbiddenException);
      expect((error as ForbiddenException).getResponse()).toEqual({
        code: 'FORBIDDEN',
        message: '需要管理员权限',
      });
    }
  });
});
