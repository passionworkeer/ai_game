import { Test, TestingModule } from '@nestjs/testing';
import { AuthService } from '../auth.service';
import { PrismaService } from '../../prisma/prisma.service';
import { JwtService } from '@nestjs/jwt';
import { DeviceRegisterDto } from '../dto/device-register.dto';
import { UnauthorizedException } from '@nestjs/common';

describe('AuthService', () => {
  let service: AuthService;
  let mockPrisma: {
    user: {
      findUnique: jest.Mock;
      create: jest.Mock;
    };
  };
  let mockJwt: { sign: jest.Mock };

  const validDeviceId = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';
  const validDto: DeviceRegisterDto = {
    deviceId: validDeviceId,
    clientVersion: '1.0.0',
    platform: 'android',
  };

  beforeEach(async () => {
    mockPrisma = {
      user: {
        findUnique: jest.fn(),
        create: jest.fn(),
      },
    };

    mockJwt = {
      sign: jest.fn().mockReturnValue('mock-jwt-token'),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AuthService,
        { provide: PrismaService, useValue: mockPrisma },
        { provide: JwtService, useValue: mockJwt },
      ],
    }).compile();

    service = module.get<AuthService>(AuthService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  // ------------------------------------------------------------------
  // deviceRegister — 新用户注册
  // ------------------------------------------------------------------
  describe('deviceRegister', () => {
    it('should create a new user and return token', async () => {
      const createdUser = {
        id: 'user-123',
        deviceId: validDeviceId,
        nickname: '宝宝',
        profileJson: '{}',
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      mockPrisma.user.findUnique.mockResolvedValue(null);
      mockPrisma.user.create.mockResolvedValue(createdUser);
      mockJwt.sign.mockReturnValue('brand-new-token');

      const result = await service.deviceRegister(validDto);

      expect(result.userId).toBe('user-123');
      expect(result.token).toBe('brand-new-token');
      expect(result.expiresAt).toBeGreaterThan(Math.floor(Date.now() / 1000));
      expect(mockPrisma.user.findUnique).toHaveBeenCalledWith({
        where: { deviceId: validDeviceId },
      });
      expect(mockPrisma.user.create).toHaveBeenCalledWith({
        data: { deviceId: validDeviceId },
      });
    });

    it('should return existing user without creating duplicate', async () => {
      const existingUser = {
        id: 'existing-user-id',
        deviceId: validDeviceId,
        nickname: '小鱼',
        profileJson: '{"likes":["奶茶"]}',
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      mockPrisma.user.findUnique.mockResolvedValue(existingUser);

      const result = await service.deviceRegister(validDto);

      expect(result.userId).toBe('existing-user-id');
      expect(result.token).toBe('mock-jwt-token');
      expect(mockPrisma.user.create).not.toHaveBeenCalled();
    });

    it('should call jwtService.sign with correct payload', async () => {
      const createdUser = {
        id: 'user-456',
        deviceId: validDeviceId,
        nickname: '宝宝',
        profileJson: '{}',
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      mockPrisma.user.findUnique.mockResolvedValue(null);
      mockPrisma.user.create.mockResolvedValue(createdUser);

      await service.deviceRegister(validDto);

      expect(mockJwt.sign).toHaveBeenCalledWith({
        sub: 'user-456',
        deviceId: validDeviceId,
      });
    });

    it('should set expiresAt approximately 365 days from now', async () => {
      const createdUser = {
        id: 'user-exp',
        deviceId: validDeviceId,
        nickname: '宝宝',
        profileJson: '{}',
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      mockPrisma.user.findUnique.mockResolvedValue(null);
      mockPrisma.user.create.mockResolvedValue(createdUser);

      const before = Math.floor(Date.now() / 1000);
      const result = await service.deviceRegister(validDto);
      const after = Math.floor(Date.now() / 1000);

      const expectedMin = before + 365 * 24 * 60 * 60;
      const expectedMax = after + 365 * 24 * 60 * 60;

      expect(result.expiresAt).toBeGreaterThanOrEqual(expectedMin);
      expect(result.expiresAt).toBeLessThanOrEqual(expectedMax);
    });

    it('should handle device without optional fields (clientVersion, platform)', async () => {
      const minimalDto: DeviceRegisterDto = {
        deviceId: validDeviceId,
      };

      const createdUser = {
        id: 'user-min',
        deviceId: validDeviceId,
        nickname: '宝宝',
        profileJson: '{}',
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      mockPrisma.user.findUnique.mockResolvedValue(null);
      mockPrisma.user.create.mockResolvedValue(createdUser);

      const result = await service.deviceRegister(minimalDto);

      expect(result.userId).toBe('user-min');
      expect(mockPrisma.user.create).toHaveBeenCalled();
    });
  });

  // ------------------------------------------------------------------
  // 边界：Prisma 异常
  // ------------------------------------------------------------------
  describe('deviceRegister — error cases', () => {
    it('should propagate Prisma findUnique error', async () => {
      mockPrisma.user.findUnique.mockRejectedValue(
        new Error('Database connection failed'),
      );

      await expect(service.deviceRegister(validDto)).rejects.toThrow(
        'Database connection failed',
      );
    });

    it('should propagate Prisma create error', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(null);
      mockPrisma.user.create.mockRejectedValue(
        new Error('Unique constraint violation'),
      );

      await expect(service.deviceRegister(validDto)).rejects.toThrow(
        'Unique constraint violation',
      );
    });
  });
});
