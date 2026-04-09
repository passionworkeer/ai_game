import { ExecutionContext } from '@nestjs/common';
import { JwtAuthGuard } from '../jwt-auth.guard';
import { AuthGuard } from '@nestjs/passport';

// Mock Passport's AuthGuard
jest.mock('@nestjs/passport', () => ({
  AuthGuard: jest.fn().mockImplementation((strategy: string) => {
    return class MockedAuthGuard {
      canActivate(context: ExecutionContext): boolean | Promise<boolean> {
        const request = context.switchToHttp().getRequest();
        const authHeader = request.headers['authorization'];

        // Simulate scenarios
        if (!authHeader) {
          return Promise.reject({
            statusCode: 401,
            message: 'No authorization header',
          });
        }

        if (!authHeader.startsWith('Bearer ')) {
          return Promise.reject({
            statusCode: 401,
            message: 'Invalid authorization format',
          });
        }

        const token = authHeader.slice(7);

        if (token === 'expired-token') {
          return Promise.reject({
            statusCode: 401,
            message: 'Token expired',
          });
        }

        if (token === 'invalid-signature') {
          return Promise.reject({
            statusCode: 401,
            message: 'invalid signature',
          });
        }

        // Empty or whitespace-only token should fail
        if (!token || token.trim() === '') {
          return Promise.reject({
            statusCode: 401,
            message: 'Empty token',
          });
        }

        // Valid token — inject user
        if (token === 'valid-token') {
          request.user = { userId: 'user-123', deviceId: 'device-abc' };
          return true;
        }

        // Any other non-empty, non-expired token is treated as valid
        return true;
      }
    };
  }),
}));

describe('JwtAuthGuard', () => {
  let guard: JwtAuthGuard;
  let mockContext: ExecutionContext;
  let mockRequest: { headers: Record<string, string>; user?: unknown };

  beforeEach(() => {
    guard = new JwtAuthGuard();
    mockRequest = { headers: {} };
    mockContext = {
      switchToHttp: () => ({
        getRequest: () => mockRequest,
      }),
    } as ExecutionContext;
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('should be defined', () => {
    expect(guard).toBeDefined();
  });

  describe('canActivate — auth failure cases', () => {
    it('should return 401 when Authorization header is missing', async () => {
      mockRequest.headers = {};

      await expect(guard.canActivate(mockContext)).rejects.toMatchObject({
        statusCode: 401,
        message: 'No authorization header',
      });
    });

    it('should return 401 when Authorization format is not Bearer', async () => {
      mockRequest.headers = { authorization: 'Basic dXNlcjpwYXNz' };

      await expect(guard.canActivate(mockContext)).rejects.toMatchObject({
        statusCode: 401,
        message: 'Invalid authorization format',
      });
    });

    it('should return 401 when token is empty Bearer', async () => {
      mockRequest.headers = { authorization: 'Bearer ' };

      // Empty token after Bearer prefix — should also fail
      await expect(guard.canActivate(mockContext)).rejects.toBeDefined();
    });

    it('should return 401 when token is expired', async () => {
      mockRequest.headers = { authorization: 'Bearer expired-token' };

      await expect(guard.canActivate(mockContext)).rejects.toMatchObject({
        statusCode: 401,
        message: 'Token expired',
      });
    });

    it('should return 401 when token has invalid signature', async () => {
      mockRequest.headers = { authorization: 'Bearer invalid-signature' };

      await expect(guard.canActivate(mockContext)).rejects.toMatchObject({
        statusCode: 401,
        message: 'invalid signature',
      });
    });
  });

  describe('canActivate — auth success case', () => {
    it('should return true and inject user for valid token', async () => {
      mockRequest.headers = { authorization: 'Bearer valid-token' };

      const result = await guard.canActivate(mockContext);

      expect(result).toBe(true);
      expect(mockRequest.user).toEqual({
        userId: 'user-123',
        deviceId: 'device-abc',
      });
    });

    it('should allow arbitrary valid token strings', async () => {
      mockRequest.headers = { authorization: 'Bearer abc-def-ghi-jkl' };

      const result = await guard.canActivate(mockContext);

      // Our mock allows any non-empty non-expired token
      expect(result).toBe(true);
    });
  });
});
