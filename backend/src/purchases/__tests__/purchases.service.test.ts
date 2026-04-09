import { Test, TestingModule } from '@nestjs/testing';
import { PurchasesService } from '../purchases.service';
import { PrismaService } from '../../prisma/prisma.service';
import { VerifyPurchaseDto } from '../dto/verify-purchase.dto';
import { CurrentUserPayload } from '../../common/types';
import {
  NotFoundException,
  ConflictException,
  UnprocessableEntityException,
} from '@nestjs/common';

describe('PurchasesService', () => {
  let service: PurchasesService;
  let mockPrisma: {
    character: { findUnique: jest.Mock };
    purchase: { findUnique: jest.Mock; findMany: jest.Mock; upsert: jest.Mock };
    user: { findUnique: jest.Mock };
  };

  const mockUser: CurrentUserPayload = {
    userId: 'user-123',
    deviceId: 'device-abc',
  };

  const baseCharacter = {
    id: 'char-1',
    code: 'gu_chen',
    name: '顾晨',
    description: '温柔学长',
    price: 5800, // 58元 = 5800分
    previewUrl: 'https://cdn.example.com/preview/gu_chen.png',
    assetsUrl: 'https://cdn.example.com/models/gu_chen.gguf',
    systemPrompt: 'You are Gu Chen...',
    isActive: true,
    createdAt: new Date('2024-01-01'),
  };

  beforeEach(async () => {
    mockPrisma = {
      character: { findUnique: jest.fn() },
      purchase: { findUnique: jest.fn(), findMany: jest.fn(), upsert: jest.fn() },
      user: { findUnique: jest.fn() },
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        PurchasesService,
        { provide: PrismaService, useValue: mockPrisma },
      ],
    }).compile();

    service = module.get<PurchasesService>(PurchasesService);
  });

  afterEach(() => jest.clearAllMocks());

  // ==================================================================
  // verifyPurchase — 错误路径
  // ==================================================================
  describe('verifyPurchase — error cases', () => {
    it('should throw CHARACTER_NOT_FOUND when character does not exist', async () => {
      mockPrisma.character.findUnique.mockResolvedValue(null);

      const dto: VerifyPurchaseDto = {
        characterId: 'nonexistent-char',
        channel: 'alipay',
        paidAmount: 5800,
        paidAt: Date.now(),
      };

      await expect(service.verifyPurchase(dto, mockUser)).rejects.toThrow(NotFoundException);
      await expect(service.verifyPurchase(dto, mockUser)).rejects.toMatchObject({
        response: expect.objectContaining({ code: 'CHARACTER_NOT_FOUND' }),
      });
    });

    it('should throw ALREADY_PURCHASED when character already purchased', async () => {
      mockPrisma.character.findUnique.mockResolvedValue(baseCharacter);
      mockPrisma.purchase.findUnique.mockResolvedValue({
        id: 'purchase-existing',
        userId: 'user-123',
        characterId: 'char-1',
        status: 'completed',
      });

      const dto: VerifyPurchaseDto = {
        characterId: 'char-1',
        channel: 'alipay',
        paidAmount: 5800,
        paidAt: Date.now(),
      };

      await expect(service.verifyPurchase(dto, mockUser)).rejects.toThrow(ConflictException);
      await expect(service.verifyPurchase(dto, mockUser)).rejects.toMatchObject({
        response: expect.objectContaining({ code: 'ALREADY_PURCHASED' }),
      });
    });

    it('should throw PURCHASE_VERIFY_FAILED when paidAmount is insufficient', async () => {
      mockPrisma.character.findUnique.mockResolvedValue(baseCharacter);
      mockPrisma.purchase.findUnique.mockResolvedValue(null); // no existing purchase

      const dto: VerifyPurchaseDto = {
        characterId: 'char-1',
        channel: 'alipay',
        paidAmount: 1000, // 10元 < 58元 (5800分)
        paidAt: Date.now(),
      };

      await expect(service.verifyPurchase(dto, mockUser)).rejects.toThrow(
        UnprocessableEntityException,
      );
      await expect(service.verifyPurchase(dto, mockUser)).rejects.toMatchObject({
        response: expect.objectContaining({ code: 'PURCHASE_VERIFY_FAILED' }),
      });
    });

    it('should throw PURCHASE_VERIFY_FAILED when paidAmount is exactly zero', async () => {
      mockPrisma.character.findUnique.mockResolvedValue(baseCharacter);
      mockPrisma.purchase.findUnique.mockResolvedValue(null);

      const dto: VerifyPurchaseDto = {
        characterId: 'char-1',
        channel: 'alipay',
        paidAmount: 0,
        paidAt: Date.now(),
      };

      await expect(service.verifyPurchase(dto, mockUser)).rejects.toThrow(
        UnprocessableEntityException,
      );
    });
  });

  // ==================================================================
  // verifyPurchase — 成功路径
  // ==================================================================
  describe('verifyPurchase — success path', () => {
    it('should create purchase record when all validations pass (new purchase)', async () => {
      const upsertedPurchase = {
        id: 'purchase-new-1',
        userId: 'user-123',
        characterId: 'char-1',
        channel: 'alipay',
        channelOrderId: 'ali-order-001',
        amount: 5800,
        status: 'completed',
        paidAt: new Date(),
        createdAt: new Date(),
      };

      mockPrisma.character.findUnique.mockResolvedValue(baseCharacter);
      mockPrisma.purchase.findUnique.mockResolvedValue(null); // no prior purchase
      mockPrisma.purchase.upsert.mockResolvedValue(upsertedPurchase);

      const dto: VerifyPurchaseDto = {
        characterId: 'char-1',
        channel: 'alipay',
        channelOrderId: 'ali-order-001',
        paidAmount: 5800,
        paidAt: Date.now(),
      };

      const result = await service.verifyPurchase(dto, mockUser);

      expect(result.purchaseId).toBe('purchase-new-1');
      expect(result.status).toBe('completed');
      expect(result.modelUrl).toBe(baseCharacter.assetsUrl);
      expect(mockPrisma.purchase.upsert).toHaveBeenCalledWith({
        where: { userId_characterId: { userId: 'user-123', characterId: 'char-1' } },
        update: expect.objectContaining({ status: 'completed', amount: 5800 }),
        create: expect.objectContaining({ userId: 'user-123', characterId: 'char-1', status: 'completed' }),
      });
    });

    it('should update existing pending purchase to completed', async () => {
      const existingPending = {
        id: 'purchase-pending-1',
        userId: 'user-123',
        characterId: 'char-1',
        status: 'pending',
      };

      const upsertedPurchase = {
        id: 'purchase-pending-1',
        userId: 'user-123',
        characterId: 'char-1',
        channel: 'wechat',
        channelOrderId: 'wx-order-001',
        amount: 5800,
        status: 'completed',
        paidAt: new Date(),
        createdAt: new Date(),
      };

      mockPrisma.character.findUnique.mockResolvedValue(baseCharacter);
      mockPrisma.purchase.findUnique.mockResolvedValue(existingPending);
      mockPrisma.purchase.upsert.mockResolvedValue(upsertedPurchase);

      const dto: VerifyPurchaseDto = {
        characterId: 'char-1',
        channel: 'wechat',
        channelOrderId: 'wx-order-001',
        paidAmount: 5800,
        paidAt: Date.now(),
      };

      const result = await service.verifyPurchase(dto, mockUser);

      expect(result.status).toBe('completed');
      // upsert should have been called with update path
      expect(mockPrisma.purchase.upsert).toHaveBeenCalled();
    });

    it('should accept paidAmount greater than character price', async () => {
      const upsertedPurchase = {
        id: 'purchase-bonus-1',
        userId: 'user-123',
        characterId: 'char-1',
        channel: 'alipay',
        channelOrderId: null,
        amount: 6000, // overpaid
        status: 'completed',
        paidAt: new Date(),
        createdAt: new Date(),
      };

      mockPrisma.character.findUnique.mockResolvedValue(baseCharacter);
      mockPrisma.purchase.findUnique.mockResolvedValue(null);
      mockPrisma.purchase.upsert.mockResolvedValue(upsertedPurchase);

      const dto: VerifyPurchaseDto = {
        characterId: 'char-1',
        channel: 'alipay',
        paidAmount: 6000,
        paidAt: Date.now(),
      };

      const result = await service.verifyPurchase(dto, mockUser);

      expect(result.status).toBe('completed');
    });

    it('should handle purchase with no channelOrderId (optional field)', async () => {
      const upsertedPurchase = {
        id: 'purchase-no-order',
        userId: 'user-123',
        characterId: 'char-1',
        channel: 'apple',
        channelOrderId: null,
        amount: 5800,
        status: 'completed',
        paidAt: new Date(),
        createdAt: new Date(),
      };

      mockPrisma.character.findUnique.mockResolvedValue(baseCharacter);
      mockPrisma.purchase.findUnique.mockResolvedValue(null);
      mockPrisma.purchase.upsert.mockResolvedValue(upsertedPurchase);

      const dto: VerifyPurchaseDto = {
        characterId: 'char-1',
        channel: 'apple',
        paidAmount: 5800,
        paidAt: Date.now(),
        // channelOrderId intentionally omitted
      };

      const result = await service.verifyPurchase(dto, mockUser);

      expect(result.purchaseId).toBe('purchase-no-order');
    });

    it('should accept signature field (Phase 1 skip validation)', async () => {
      const upsertedPurchase = {
        id: 'purchase-sig',
        userId: 'user-123',
        characterId: 'char-1',
        channel: 'alipay',
        channelOrderId: null,
        amount: 5800,
        status: 'completed',
        paidAt: new Date(),
        createdAt: new Date(),
      };

      mockPrisma.character.findUnique.mockResolvedValue(baseCharacter);
      mockPrisma.purchase.findUnique.mockResolvedValue(null);
      mockPrisma.purchase.upsert.mockResolvedValue(upsertedPurchase);

      const dto: VerifyPurchaseDto = {
        characterId: 'char-1',
        channel: 'alipay',
        paidAmount: 5800,
        paidAt: Date.now(),
        signature: 'mock-signature-xyz',
      };

      const result = await service.verifyPurchase(dto, mockUser);

      expect(result.status).toBe('completed');
    });
  });

  // ==================================================================
  // findUserPurchases
  // ==================================================================
  describe('findUserPurchases', () => {
    it('should return empty array when user has no purchases', async () => {
      mockPrisma.purchase.findMany.mockResolvedValue([]);

      const result = await service.findUserPurchases(mockUser);

      expect(result.purchases).toEqual([]);
      expect(mockPrisma.purchase.findMany).toHaveBeenCalledWith({
        where: { userId: 'user-123', status: 'completed' },
        include: { character: { select: { id: true, code: true, name: true } } },
        orderBy: { paidAt: 'desc' },
      });
    });

    it('should return purchase records with character info', async () => {
      const purchaseRecords = [
        {
          id: 'p1',
          characterId: 'char-1',
          paidAt: new Date('2024-06-01T10:00:00Z'),
          character: { id: 'char-1', code: 'gu_chen', name: '顾晨' },
        },
        {
          id: 'p2',
          characterId: 'char-2',
          paidAt: new Date('2024-07-01T10:00:00Z'),
          character: { id: 'char-2', code: 'lin_xin', name: '林欣' },
        },
      ];

      mockPrisma.purchase.findMany.mockResolvedValue(purchaseRecords);

      const result = await service.findUserPurchases(mockUser);

      expect(result.purchases).toHaveLength(2);
      expect(result.purchases[0]).toEqual({
        characterId: 'char-1',
        characterCode: 'gu_chen',
        characterName: '顾晨',
        paidAt: '2024-06-01T10:00:00.000Z',
      });
      expect(result.purchases[1]).toEqual({
        characterId: 'char-2',
        characterCode: 'lin_xin',
        characterName: '林欣',
        paidAt: '2024-07-01T10:00:00.000Z',
      });
    });

    it('should order purchases by paidAt descending', async () => {
      const oldPurchase = {
        id: 'p-old',
        characterId: 'char-old',
        paidAt: new Date('2024-01-01'),
        character: { id: 'char-old', code: 'old', name: '旧角色' },
      };
      const newPurchase = {
        id: 'p-new',
        characterId: 'char-new',
        paidAt: new Date('2024-12-01'),
        character: { id: 'char-new', code: 'new', name: '新角色' },
      };

      // DB returns newest first (descending), matching orderBy
      mockPrisma.purchase.findMany.mockResolvedValue([newPurchase, oldPurchase]);

      const result = await service.findUserPurchases(mockUser);

      expect(result.purchases[0].characterId).toBe('char-new');
      expect(result.purchases[1].characterId).toBe('char-old');
    });

    it('should handle null paidAt gracefully', async () => {
      const purchaseWithNullPaidAt = {
        id: 'p-null',
        characterId: 'char-null',
        paidAt: null,
        character: { id: 'char-null', code: 'null', name: '空角色' },
      };

      mockPrisma.purchase.findMany.mockResolvedValue([purchaseWithNullPaidAt]);

      const result = await service.findUserPurchases(mockUser);

      expect(result.purchases[0].paidAt).toBe('');
    });
  });
});
