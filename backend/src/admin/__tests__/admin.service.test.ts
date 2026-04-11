import { Test, TestingModule } from '@nestjs/testing';
import { NotFoundException } from '@nestjs/common';
import { AdminService, AdminStats } from '../admin.service';
import { PrismaService } from '../../prisma/prisma.service';
import { CreateCharacterDto } from '../dto/create-character.dto';
import { UpdateCharacterDto } from '../dto/update-character.dto';

describe('AdminService', () => {
  let service: AdminService;

  const mockPrisma = {
    user: {
      count: jest.fn(),
      findUnique: jest.fn(),
      findMany: jest.fn(),
      update: jest.fn(),
    },
    character: {
      findMany: jest.fn(),
      create: jest.fn(),
      findUnique: jest.fn(),
      update: jest.fn(),
      delete: jest.fn(),
    },
    purchase: {
      groupBy: jest.fn(),
      aggregate: jest.fn(),
      findUnique: jest.fn(),
      updateMany: jest.fn(),
      findFirst: jest.fn(),
    },
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        AdminService,
        { provide: PrismaService, useValue: mockPrisma },
      ],
    }).compile();

    service = module.get<AdminService>(AdminService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  // ------------------------------------------------------------------
  // listCharacters
  // ------------------------------------------------------------------
  describe('listCharacters', () => {
    it('should return array of characters ordered by createdAt asc', async () => {
      const mockCharacters = [
        {
          id: 'char-1',
          code: 'GUCHEN',
          name: '顾晨',
          description: '温柔学长',
          price: 600,
          assetsUrl: 'https://cdn.example.com/char1.zip',
          systemPrompt: 'You are Gu Chen...',
          previewUrl: 'https://cdn.example.com/char1.png',
          isActive: true,
          createdAt: new Date('2024-01-01'),
          updatedAt: new Date('2024-01-01'),
        },
        {
          id: 'char-2',
          code: 'LINYI',
          name: '林逸',
          description: '阳光少年',
          price: 800,
          assetsUrl: 'https://cdn.example.com/char2.zip',
          systemPrompt: 'You are Lin Yi...',
          previewUrl: null,
          isActive: true,
          createdAt: new Date('2024-01-15'),
          updatedAt: new Date('2024-01-15'),
        },
      ];

      mockPrisma.character.findMany.mockResolvedValue(mockCharacters);

      const result = await service.listCharacters();

      expect(result).toEqual(mockCharacters);
      expect(mockPrisma.character.findMany).toHaveBeenCalledWith({
        orderBy: { createdAt: 'asc' },
      });
    });

    it('should return empty array when no characters exist', async () => {
      mockPrisma.character.findMany.mockResolvedValue([]);

      const result = await service.listCharacters();

      expect(result).toEqual([]);
      expect(mockPrisma.character.findMany).toHaveBeenCalled();
    });
  });

  // ------------------------------------------------------------------
  // createCharacter
  // ------------------------------------------------------------------
  describe('createCharacter', () => {
    it('should create character with all fields and return it', async () => {
      const dto: CreateCharacterDto = {
        code: 'NEW_CHAR',
        name: '新角色',
        description: '神秘角色',
        price: 1000,
        assetsUrl: 'https://cdn.example.com/new.zip',
        systemPrompt: 'You are a mysterious character...',
        previewUrl: 'https://cdn.example.com/new.png',
        isActive: true,
      };

      const createdCharacter = {
        id: 'new-char-id',
        ...dto,
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      mockPrisma.character.create.mockResolvedValue(createdCharacter);

      const result = await service.createCharacter(dto);

      expect(result).toEqual(createdCharacter);
      expect(mockPrisma.character.create).toHaveBeenCalledWith({
        data: {
          code: 'NEW_CHAR',
          name: '新角色',
          description: '神秘角色',
          price: 1000,
          assetsUrl: 'https://cdn.example.com/new.zip',
          systemPrompt: 'You are a mysterious character...',
          previewUrl: 'https://cdn.example.com/new.png',
          isActive: true,
        },
      });
    });

    it('should use empty string for optional systemPrompt when not provided', async () => {
      const dto: CreateCharacterDto = {
        code: 'MINIMAL_CHAR',
        name: '最小角色',
        description: '最简角色',
        price: 0,
        assetsUrl: 'https://cdn.example.com/minimal.zip',
      };

      const createdCharacter = {
        id: 'minimal-char-id',
        code: 'MINIMAL_CHAR',
        name: '最小角色',
        description: '最简角色',
        price: 0,
        assetsUrl: 'https://cdn.example.com/minimal.zip',
        systemPrompt: '',
        previewUrl: null,
        isActive: true,
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      mockPrisma.character.create.mockResolvedValue(createdCharacter);

      const result = await service.createCharacter(dto);

      expect(result.systemPrompt).toBe('');
      expect(mockPrisma.character.create).toHaveBeenCalledWith({
        data: expect.objectContaining({
          systemPrompt: '',
        }),
      });
    });

    it('should default isActive to true when not provided', async () => {
      const dto: CreateCharacterDto = {
        code: 'INACTIVE_CHAR',
        name: '非活跃角色',
        description: '即将上线',
        price: 0,
        assetsUrl: 'https://cdn.example.com/inactive.zip',
        isActive: false,
      };

      const createdCharacter = {
        id: 'inactive-char-id',
        ...dto,
        systemPrompt: '',
        previewUrl: null,
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      mockPrisma.character.create.mockResolvedValue(createdCharacter);

      await service.createCharacter(dto);

      expect(mockPrisma.character.create).toHaveBeenCalledWith({
        data: expect.objectContaining({
          isActive: false,
        }),
      });
    });
  });

  // ------------------------------------------------------------------
  // updateCharacter
  // ------------------------------------------------------------------
  describe('updateCharacter', () => {
    it('should update character and return updated result', async () => {
      const charId = 'char-123';
      const existingChar = {
        id: charId,
        code: 'OLD_CODE',
        name: '旧名字',
        description: '旧描述',
        price: 500,
        assetsUrl: 'https://cdn.example.com/old.zip',
        systemPrompt: '',
        previewUrl: null,
        isActive: true,
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      const updateDto: UpdateCharacterDto = {
        name: '新名字',
        price: 800,
      };

      const updatedChar = {
        ...existingChar,
        name: '新名字',
        price: 800,
      };

      mockPrisma.character.findUnique.mockResolvedValue(existingChar);
      mockPrisma.character.update.mockResolvedValue(updatedChar);

      const result = await service.updateCharacter(charId, updateDto);

      expect(result).toEqual(updatedChar);
      expect(mockPrisma.character.update).toHaveBeenCalledWith({
        where: { id: charId },
        data: updateDto,
      });
    });

    it('should throw NotFoundException with CHARACTER_NOT_FOUND code when character does not exist', async () => {
      const charId = 'non-existent-id';
      mockPrisma.character.findUnique.mockResolvedValue(null);

      await expect(service.updateCharacter(charId, { name: 'New' })).rejects.toThrow(NotFoundException);
      await expect(service.updateCharacter(charId, { name: 'New' })).rejects.toMatchObject({
        response: expect.objectContaining({
          code: 'CHARACTER_NOT_FOUND',
        }),
      });
    });

    it('should not call update when character not found', async () => {
      mockPrisma.character.findUnique.mockResolvedValue(null);

      await expect(service.updateCharacter('bad-id', { name: 'Test' })).rejects.toThrow();
      expect(mockPrisma.character.update).not.toHaveBeenCalled();
    });
  });

  // ------------------------------------------------------------------
  // deleteCharacter
  // ------------------------------------------------------------------
  describe('deleteCharacter', () => {
    it('should delete character and return {id}', async () => {
      const charId = 'char-to-delete';
      const existingChar = {
        id: charId,
        code: 'DELETE_ME',
        name: '删除角色',
        description: '将被删除',
        price: 0,
        assetsUrl: 'https://cdn.example.com/delete.zip',
        systemPrompt: '',
        previewUrl: null,
        isActive: false,
        createdAt: new Date(),
        updatedAt: new Date(),
      };

      mockPrisma.character.findUnique.mockResolvedValue(existingChar);
      mockPrisma.character.delete.mockResolvedValue(existingChar);

      const result = await service.deleteCharacter(charId);

      expect(result).toEqual({ id: charId });
      expect(mockPrisma.character.delete).toHaveBeenCalledWith({ where: { id: charId } });
    });

    it('should throw NotFoundException with CHARACTER_NOT_FOUND code when character does not exist', async () => {
      mockPrisma.character.findUnique.mockResolvedValue(null);

      await expect(service.deleteCharacter('non-existent')).rejects.toThrow(NotFoundException);
      await expect(service.deleteCharacter('non-existent')).rejects.toMatchObject({
        response: expect.objectContaining({
          code: 'CHARACTER_NOT_FOUND',
        }),
      });
    });

    it('should not call delete when character not found', async () => {
      mockPrisma.character.findUnique.mockResolvedValue(null);

      await expect(service.deleteCharacter('bad-id')).rejects.toThrow();
      expect(mockPrisma.character.delete).not.toHaveBeenCalled();
    });
  });

  // ------------------------------------------------------------------
  // getStats
  // ------------------------------------------------------------------
  describe('getStats', () => {
    it('should return all zeros when no users exist', async () => {
      mockPrisma.user.count.mockResolvedValue(0);
      mockPrisma.purchase.groupBy.mockResolvedValue([]);
      mockPrisma.purchase.aggregate.mockResolvedValue({ _sum: { amount: null } });

      const result = await service.getStats();

      expect(result).toEqual({
        dau: 0,
        mau: 0,
        totalUsers: 0,
        paidUsers: 0,
        revenue: 0,
        paidRate: 0,
      });
    });

    it('should return correct stats with mock data', async () => {
      // Mock DAU: 5 users active today
      mockPrisma.user.count
        .mockResolvedValueOnce(5)   // dau
        .mockResolvedValueOnce(50)  // mau
        .mockResolvedValueOnce(100); // totalUsers

      // Mock paid users: 20 distinct users with completed purchases
      mockPrisma.purchase.groupBy.mockResolvedValue([
        { userId: 'user-1' },
        { userId: 'user-2' },
        { userId: 'user-3' },
        // ... 17 more
      ].concat(Array(17).fill(null).map((_, i) => ({ userId: `user-${i + 4}` }))));

      // Mock revenue: 15000 fen = 150.00 yuan
      mockPrisma.purchase.aggregate.mockResolvedValue({
        _sum: { amount: 15000 },
      });

      const result = await service.getStats();

      expect(result).toEqual({
        dau: 5,
        mau: 50,
        totalUsers: 100,
        paidUsers: 20,
        revenue: 15000,
        paidRate: 20,
      });
    });

    it('should calculate paidRate correctly as percentage', async () => {
      mockPrisma.user.count
        .mockResolvedValueOnce(10)  // dau
        .mockResolvedValueOnce(30)  // mau
        .mockResolvedValueOnce(200); // totalUsers

      mockPrisma.purchase.groupBy.mockResolvedValue([
        { userId: 'user-1' },
        { userId: 'user-2' },
        { userId: 'user-3' },
        { userId: 'user-4' },
      ]);

      mockPrisma.purchase.aggregate.mockResolvedValue({
        _sum: { amount: 5000 },
      });

      const result = await service.getStats();

      // 4 / 200 * 100 = 2
      expect(result.paidRate).toBe(2);
    });

    it('should handle null revenue sum gracefully', async () => {
      mockPrisma.user.count.mockResolvedValue(0);
      mockPrisma.purchase.groupBy.mockResolvedValue([]);
      mockPrisma.purchase.aggregate.mockResolvedValue({ _sum: { amount: null } });

      const result = await service.getStats();

      expect(result.revenue).toBe(0);
    });

    it('should round paidRate to 2 decimal places', async () => {
      mockPrisma.user.count
        .mockResolvedValueOnce(0)
        .mockResolvedValueOnce(0)
        .mockResolvedValueOnce(3);

      mockPrisma.purchase.groupBy.mockResolvedValue([
        { userId: 'user-1' },
      ]);

      mockPrisma.purchase.aggregate.mockResolvedValue({
        _sum: { amount: 1000 },
      });

      const result = await service.getStats();

      // 1 / 3 * 100 = 33.333... -> rounded to 2 decimal places
      expect(result.paidRate).toBeCloseTo(33.33, 2);
    });
  });

  // ------------------------------------------------------------------
  // banDevice
  // ------------------------------------------------------------------
  describe('banDevice', () => {
    it('should ban device and return {deviceId, isBanned: true}', async () => {
      const deviceId = 'device-1234-abcd';
      const existingUser = {
        id: 'user-456',
        deviceId,
        nickname: 'TestUser',
        isBanned: false,
        role: 'user',
        profileJson: '{}',
        createdAt: new Date(),
        updatedAt: new Date(),
        lastActive: new Date(),
      };

      const updatedUser = {
        ...existingUser,
        isBanned: true,
      };

      mockPrisma.user.findUnique.mockResolvedValue(existingUser);
      mockPrisma.user.update.mockResolvedValue(updatedUser);

      const result = await service.banDevice(deviceId);

      expect(result).toEqual({ deviceId, isBanned: true });
      expect(mockPrisma.user.update).toHaveBeenCalledWith({
        where: { deviceId },
        data: { isBanned: true },
      });
    });

    it('should throw NotFoundException with USER_NOT_FOUND code when device not found', async () => {
      const deviceId = 'non-existent-device';
      mockPrisma.user.findUnique.mockResolvedValue(null);

      await expect(service.banDevice(deviceId)).rejects.toThrow(NotFoundException);
      await expect(service.banDevice(deviceId)).rejects.toMatchObject({
        response: expect.objectContaining({
          code: 'USER_NOT_FOUND',
        }),
      });
    });

    it('should not call update when device not found', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(null);

      await expect(service.banDevice('bad-device')).rejects.toThrow();
      expect(mockPrisma.user.update).not.toHaveBeenCalled();
    });
  });

  // ------------------------------------------------------------------
  // unbanDevice
  // ------------------------------------------------------------------
  describe('unbanDevice', () => {
    it('should unban device and return {deviceId, isBanned: false}', async () => {
      const deviceId = 'device-5678-efgh';
      const existingUser = {
        id: 'user-789',
        deviceId,
        nickname: 'BannedUser',
        isBanned: true,
        role: 'user',
        profileJson: '{}',
        createdAt: new Date(),
        updatedAt: new Date(),
        lastActive: new Date(),
      };

      const updatedUser = {
        ...existingUser,
        isBanned: false,
      };

      mockPrisma.user.findUnique.mockResolvedValue(existingUser);
      mockPrisma.user.update.mockResolvedValue(updatedUser);

      const result = await service.unbanDevice(deviceId);

      expect(result).toEqual({ deviceId, isBanned: false });
      expect(mockPrisma.user.update).toHaveBeenCalledWith({
        where: { deviceId },
        data: { isBanned: false },
      });
    });

    it('should throw NotFoundException with USER_NOT_FOUND code when device not found', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(null);

      await expect(service.unbanDevice('non-existent')).rejects.toThrow(NotFoundException);
      await expect(service.unbanDevice('non-existent')).rejects.toMatchObject({
        response: expect.objectContaining({
          code: 'USER_NOT_FOUND',
        }),
      });
    });

    it('should not call update when device not found', async () => {
      mockPrisma.user.findUnique.mockResolvedValue(null);

      await expect(service.unbanDevice('bad-device')).rejects.toThrow();
      expect(mockPrisma.user.update).not.toHaveBeenCalled();
    });
  });

  // ------------------------------------------------------------------
  // listDevices
  // ------------------------------------------------------------------
  describe('listDevices', () => {
    it('should return paginated device list with purchaseCount', async () => {
      const mockUsers = [
        {
          id: 'user-1',
          deviceId: 'device-001',
          nickname: 'Alice',
          isBanned: false,
          role: 'user',
          createdAt: new Date('2024-01-01T10:00:00Z'),
          lastActive: new Date('2024-01-15T15:30:00Z'),
          _count: { purchases: 3 },
        },
        {
          id: 'user-2',
          deviceId: 'device-002',
          nickname: 'Bob',
          isBanned: true,
          role: 'user',
          createdAt: new Date('2024-01-05T08:00:00Z'),
          lastActive: new Date('2024-01-10T12:00:00Z'),
          _count: { purchases: 1 },
        },
      ];

      mockPrisma.user.findMany.mockResolvedValue(mockUsers);
      mockPrisma.user.count.mockResolvedValue(2);

      const result = await service.listDevices(1, 20);

      expect(result).toEqual({
        users: [
          {
            userId: 'user-1',
            deviceId: 'device-001',
            nickname: 'Alice',
            isBanned: false,
            role: 'user',
            createdAt: new Date('2024-01-01T10:00:00Z').getTime(),
            lastActive: new Date('2024-01-15T15:30:00Z').getTime(),
            purchaseCount: 3,
          },
          {
            userId: 'user-2',
            deviceId: 'device-002',
            nickname: 'Bob',
            isBanned: true,
            role: 'user',
            createdAt: new Date('2024-01-05T08:00:00Z').getTime(),
            lastActive: new Date('2024-01-10T12:00:00Z').getTime(),
            purchaseCount: 1,
          },
        ],
        total: 2,
        page: 1,
        limit: 20,
      });
    });

    it('should use correct skip and take for pagination', async () => {
      mockPrisma.user.findMany.mockResolvedValue([]);
      mockPrisma.user.count.mockResolvedValue(0);

      await service.listDevices(3, 10);

      expect(mockPrisma.user.findMany).toHaveBeenCalledWith({
        skip: 20,  // (3-1) * 10
        take: 10,
        orderBy: { createdAt: 'desc' },
        select: expect.any(Object),
      });
    });

    it('should return empty array when no users exist', async () => {
      mockPrisma.user.findMany.mockResolvedValue([]);
      mockPrisma.user.count.mockResolvedValue(0);

      const result = await service.listDevices(1, 20);

      expect(result.users).toEqual([]);
      expect(result.total).toBe(0);
      expect(result.page).toBe(1);
      expect(result.limit).toBe(20);
    });

    it('should handle default pagination values', async () => {
      mockPrisma.user.findMany.mockResolvedValue([]);
      mockPrisma.user.count.mockResolvedValue(0);

      const result = await service.listDevices();

      expect(mockPrisma.user.findMany).toHaveBeenCalledWith({
        skip: 0,   // (1-1) * 20
        take: 20,
        orderBy: { createdAt: 'desc' },
        select: expect.any(Object),
      });
      expect(result.page).toBe(1);
      expect(result.limit).toBe(20);
    });

    it('should correctly map dates to timestamps', async () => {
      const createdAt = new Date('2024-03-01T00:00:00.000Z');
      const lastActive = new Date('2024-03-15T23:59:59.999Z');

      mockPrisma.user.findMany.mockResolvedValue([{
        id: 'user-x',
        deviceId: 'device-x',
        nickname: 'Charlie',
        isBanned: false,
        role: 'user',
        createdAt,
        lastActive,
        _count: { purchases: 0 },
      }]);
      mockPrisma.user.count.mockResolvedValue(1);

      const result = await service.listDevices(1, 1);

      expect(result.users[0].createdAt).toBe(createdAt.getTime());
      expect(result.users[0].lastActive).toBe(lastActive.getTime());
    });
  });
});
