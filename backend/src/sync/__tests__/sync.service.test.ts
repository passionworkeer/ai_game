import { Test, TestingModule } from '@nestjs/testing';
import { SyncService } from '../sync.service';
import { PrismaService } from '../../prisma/prisma.service';
import { SyncProfileDto } from '../dto/sync-profile.dto';
import { CurrentUserPayload } from '../../common/types';
import { ForbiddenException, NotFoundException } from '@nestjs/common';

describe('SyncService', () => {
  let service: SyncService;
  let mockPrisma: { user: { findUnique: jest.Mock; update: jest.Mock } };

  const baseUser = {
    id: 'user-123',
    deviceId: 'device-abc',
    nickname: '小鱼',
    profileJson: '{"likes":["奶茶","猫"]}',
    createdAt: new Date('2024-01-01'),
    updatedAt: new Date('2024-06-01T12:00:00Z'),
  };

  beforeEach(async () => {
    mockPrisma = {
      user: { findUnique: jest.fn(), update: jest.fn() },
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        SyncService,
        { provide: PrismaService, useValue: mockPrisma },
      ],
    }).compile();

    service = module.get<SyncService>(SyncService);
  });

  afterEach(() => jest.clearAllMocks());

  // ==================================================================
  // getProfile — 授权校验
  // ==================================================================
  describe('getProfile — authorization', () => {
    it('should throw FORBIDDEN when JWT userId !== requested userId', async () => {
      const currentUser: CurrentUserPayload = {
        userId: 'attacker-user',
        deviceId: 'attacker-device',
      };

      await expect(service.getProfile('user-123', currentUser)).rejects.toThrow(
        ForbiddenException,
      );
      await expect(service.getProfile('user-123', currentUser)).rejects.toMatchObject({
        response: expect.objectContaining({
          code: 'UNAUTHORIZED',
          message: '无权访问该用户数据',
        }),
      });
      // Prisma should NOT be called when auth fails first
      expect(mockPrisma.user.findUnique).not.toHaveBeenCalled();
    });

    it('should NOT throw when JWT userId === requested userId (same user)', async () => {
      const currentUser: CurrentUserPayload = {
        userId: 'user-123',
        deviceId: 'device-abc',
      };

      mockPrisma.user.findUnique.mockResolvedValue(baseUser);

      await expect(service.getProfile('user-123', currentUser)).resolves.not.toThrow();
    });
  });

  // ==================================================================
  // getProfile — 成功路径
  // ==================================================================
  describe('getProfile — success', () => {
    it('should return parsed profile data when authorized', async () => {
      const currentUser: CurrentUserPayload = {
        userId: 'user-123',
        deviceId: 'device-abc',
      };

      mockPrisma.user.findUnique.mockResolvedValue(baseUser);

      const result = await service.getProfile('user-123', currentUser);

      expect(result.nickname).toBe('小鱼');
      expect(result.profileJson).toEqual({ likes: ['奶茶', '猫'] });
      expect(result.keyEvents).toEqual([]);
      expect(result.updatedAt).toBe(baseUser.updatedAt.getTime());
    });

    it('should handle profileJson as already-parsed object', async () => {
      const currentUser: CurrentUserPayload = {
        userId: 'user-123',
        deviceId: 'device-abc',
      };

      const userWithObjectProfile = {
        ...baseUser,
        profileJson: { dislikes: ['香菜'] }, // not a string
      };

      mockPrisma.user.findUnique.mockResolvedValue(userWithObjectProfile);

      const result = await service.getProfile('user-123', currentUser);

      expect(result.profileJson).toEqual({ dislikes: ['香菜'] });
    });

    it('should return empty object when profileJson is invalid JSON string', async () => {
      const currentUser: CurrentUserPayload = {
        userId: 'user-123',
        deviceId: 'device-abc',
      };

      const userWithInvalidJson = {
        ...baseUser,
        profileJson: 'not-valid-json{{{',
      };

      mockPrisma.user.findUnique.mockResolvedValue(userWithInvalidJson);

      const result = await service.getProfile('user-123', currentUser);

      expect(result.profileJson).toEqual({});
    });

    it('should throw USER_NOT_FOUND when user does not exist', async () => {
      const currentUser: CurrentUserPayload = {
        userId: 'user-123',
        deviceId: 'device-abc',
      };

      mockPrisma.user.findUnique.mockResolvedValue(null);

      await expect(service.getProfile('user-123', currentUser)).rejects.toThrow(
        NotFoundException,
      );
      await expect(service.getProfile('user-123', currentUser)).rejects.toMatchObject({
        response: expect.objectContaining({ code: 'USER_NOT_FOUND' }),
      });
    });
  });

  // ==================================================================
  // updateProfile — 授权校验
  // ==================================================================
  describe('updateProfile — authorization', () => {
    it('should throw FORBIDDEN when JWT userId !== userId param', async () => {
      const currentUser: CurrentUserPayload = {
        userId: 'attacker-user',
        deviceId: 'attacker-device',
      };

      const dto: SyncProfileDto = { nickname: '新昵称' };

      await expect(service.updateProfile('user-123', dto, currentUser)).rejects.toThrow(
        ForbiddenException,
      );
      await expect(service.updateProfile('user-123', dto, currentUser)).rejects.toMatchObject({
        response: expect.objectContaining({
          code: 'UNAUTHORIZED',
          message: '无权修改该用户数据',
        }),
      });
      expect(mockPrisma.user.update).not.toHaveBeenCalled();
    });
  });

  // ==================================================================
  // updateProfile — 成功路径
  // ==================================================================
  describe('updateProfile — success', () => {
    const currentUser: CurrentUserPayload = {
      userId: 'user-123',
      deviceId: 'device-abc',
    };

    it('should update nickname and profileJson', async () => {
      const updatedUser = {
        ...baseUser,
        nickname: '新昵称',
        profileJson: '{"mood":"happy"}',
        updatedAt: new Date('2024-07-01T00:00:00Z'),
      };

      mockPrisma.user.findUnique.mockResolvedValue(baseUser);
      mockPrisma.user.update.mockResolvedValue(updatedUser);

      const dto: SyncProfileDto = {
        nickname: '新昵称',
        profileJson: { mood: 'happy' },
      };

      const result = await service.updateProfile('user-123', dto, currentUser);

      expect(result.updatedAt).toBe(updatedUser.updatedAt.getTime());
      expect(mockPrisma.user.update).toHaveBeenCalledWith({
        where: { id: 'user-123' },
        data: {
          nickname: '新昵称',
          profileJson: '{"mood":"happy"}',
        },
      });
    });

    it('should update only nickname when profileJson is undefined', async () => {
      const updatedUser = {
        ...baseUser,
        nickname: '只改昵称',
        updatedAt: new Date('2024-07-02T00:00:00Z'),
      };

      mockPrisma.user.findUnique.mockResolvedValue(baseUser);
      mockPrisma.user.update.mockResolvedValue(updatedUser);

      const dto: SyncProfileDto = { nickname: '只改昵称' };

      const result = await service.updateProfile('user-123', dto, currentUser);

      expect(mockPrisma.user.update).toHaveBeenCalledWith({
        where: { id: 'user-123' },
        data: { nickname: '只改昵称' },
      });
      expect(result.updatedAt).toBe(updatedUser.updatedAt.getTime());
    });

    it('should update only profileJson when nickname is undefined', async () => {
      const updatedUser = {
        ...baseUser,
        profileJson: '{"theme":"dark"}',
        updatedAt: new Date('2024-07-03T00:00:00Z'),
      };

      mockPrisma.user.findUnique.mockResolvedValue(baseUser);
      mockPrisma.user.update.mockResolvedValue(updatedUser);

      const dto: SyncProfileDto = { profileJson: { theme: 'dark' } };

      const result = await service.updateProfile('user-123', dto, currentUser);

      expect(mockPrisma.user.update).toHaveBeenCalledWith({
        where: { id: 'user-123' },
        data: { profileJson: '{"theme":"dark"}' },
      });
    });

    it('should return existing updatedAt when no fields are provided', async () => {
      const dto: SyncProfileDto = {}; // empty — nothing to update

      mockPrisma.user.findUnique.mockResolvedValue(baseUser);
      // update should NOT be called
      const result = await service.updateProfile('user-123', dto, currentUser);

      expect(mockPrisma.user.update).not.toHaveBeenCalled();
      expect(result.updatedAt).toBe(baseUser.updatedAt.getTime());
    });

    it('should throw USER_NOT_FOUND when user does not exist', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(null);

      const dto: SyncProfileDto = { nickname: '新昵称' };

      await expect(service.updateProfile('user-123', dto, currentUser)).rejects.toThrow(
        NotFoundException,
      );
      await expect(service.updateProfile('user-123', dto, currentUser)).rejects.toMatchObject({
        response: expect.objectContaining({ code: 'USER_NOT_FOUND' }),
      });
    });

    it('should serialize complex profileJson objects', async () => {
      const updatedUser = {
        ...baseUser,
        profileJson: '{"nested":{"deep":{"value":42}}}',
        updatedAt: new Date(),
      };

      mockPrisma.user.findUnique.mockResolvedValue(baseUser);
      mockPrisma.user.update.mockResolvedValue(updatedUser);

      const dto: SyncProfileDto = {
        profileJson: { nested: { deep: { value: 42 } } },
      };

      await service.updateProfile('user-123', dto, currentUser);

      expect(mockPrisma.user.update).toHaveBeenCalledWith({
        where: { id: 'user-123' },
        data: { profileJson: '{"nested":{"deep":{"value":42}}}' },
      });
    });
  });
});
