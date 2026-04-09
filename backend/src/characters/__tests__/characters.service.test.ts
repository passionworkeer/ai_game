import { Test, TestingModule } from '@nestjs/testing';
import { CharactersService, CharacterResponse } from '../characters.service';
import { PrismaService } from '../../prisma/prisma.service';
import { CurrentUserPayload } from '../../common/types';

describe('CharactersService', () => {
  let service: CharactersService;
  let mockPrisma: {
    character: { findMany: jest.Mock; findUnique: jest.Mock };
    purchase: { findMany: jest.Mock };
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
    price: 5800,
    previewUrl: 'https://cdn.example.com/preview/gu_chen.png',
    assetsUrl: 'https://cdn.example.com/models/gu_chen.gguf',
    systemPrompt: 'You are Gu Chen...',
    isActive: true,
    createdAt: new Date('2024-01-01'),
  };

  beforeEach(async () => {
    mockPrisma = {
      character: {
        findMany: jest.fn(),
        findUnique: jest.fn(),
      },
      purchase: {
        findMany: jest.fn(),
      },
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        CharactersService,
        { provide: PrismaService, useValue: mockPrisma },
      ],
    }).compile();

    service = module.get<CharactersService>(CharactersService);
  });

  afterEach(() => jest.clearAllMocks());

  // ------------------------------------------------------------------
  // findAllActive
  // ------------------------------------------------------------------
  describe('findAllActive', () => {
    it('should return all active characters with isOwned=false when no purchases', async () => {
      mockPrisma.character.findMany.mockResolvedValue([baseCharacter]);
      mockPrisma.purchase.findMany.mockResolvedValue([]);

      const result = await service.findAllActive(mockUser);

      expect(result.characters).toHaveLength(1);
      expect(result.characters[0]).toMatchObject({
        id: 'char-1',
        code: 'gu_chen',
        name: '顾晨',
        isOwned: false,
      });
      expect(mockPrisma.character.findMany).toHaveBeenCalledWith({
        where: { isActive: true },
        orderBy: { createdAt: 'asc' },
      });
    });

    it('should return isOwned=true for purchased characters', async () => {
      const character2 = { ...baseCharacter, id: 'char-2', code: 'lin_xin', name: '林欣' };
      mockPrisma.character.findMany.mockResolvedValue([baseCharacter, character2]);
      mockPrisma.purchase.findMany.mockResolvedValue([
        { characterId: 'char-1' }, // user owns char-1
      ]);

      const result = await service.findAllActive(mockUser);

      const char1 = result.characters.find((c) => c.id === 'char-1');
      const char2 = result.characters.find((c) => c.id === 'char-2');

      expect(char1?.isOwned).toBe(true);
      expect(char2?.isOwned).toBe(false);
    });

    it('should return empty list when no active characters', async () => {
      mockPrisma.character.findMany.mockResolvedValue([]);
      mockPrisma.purchase.findMany.mockResolvedValue([]);

      const result = await service.findAllActive(mockUser);

      expect(result.characters).toHaveLength(0);
    });

    it('should order characters by createdAt ascending', async () => {
      const charA = { ...baseCharacter, id: 'char-a', code: 'a', name: 'A', createdAt: new Date('2024-01-01') };
      const charB = { ...baseCharacter, id: 'char-b', code: 'b', name: 'B', createdAt: new Date('2024-02-01') };

      // Prisma mock returns unsorted data (simulating DB); Prisma applies orderBy internally
      // We set up the mock to return already-sorted data to match Prisma's behavior
      mockPrisma.character.findMany.mockResolvedValue([charA, charB]);
      mockPrisma.purchase.findMany.mockResolvedValue([]);

      const result = await service.findAllActive(mockUser);

      expect(result.characters[0].id).toBe('char-a');
      expect(result.characters[1].id).toBe('char-b');
      // Verify Prisma was called with correct orderBy parameter
      expect(mockPrisma.character.findMany).toHaveBeenCalledWith({
        where: { isActive: true },
        orderBy: { createdAt: 'asc' },
      });
    });

    it('should return characters with all required fields', async () => {
      mockPrisma.character.findMany.mockResolvedValue([baseCharacter]);
      mockPrisma.purchase.findMany.mockResolvedValue([]);

      const result = await service.findAllActive(mockUser);
      const char = result.characters[0];

      expect(char).toHaveProperty('id');
      expect(char).toHaveProperty('code');
      expect(char).toHaveProperty('name');
      expect(char).toHaveProperty('description');
      expect(char).toHaveProperty('price');
      expect(char).toHaveProperty('previewUrl');
      expect(char).toHaveProperty('assetsUrl');
      expect(char).toHaveProperty('isOwned');
    });

    it('should handle null previewUrl', async () => {
      const charWithNullPreview = { ...baseCharacter, previewUrl: null };
      mockPrisma.character.findMany.mockResolvedValue([charWithNullPreview]);
      mockPrisma.purchase.findMany.mockResolvedValue([]);

      const result = await service.findAllActive(mockUser);

      expect(result.characters[0].previewUrl).toBeNull();
    });
  });

  // ------------------------------------------------------------------
  // findById
  // ------------------------------------------------------------------
  describe('findById', () => {
    it('should return character when found', async () => {
      mockPrisma.character.findUnique.mockResolvedValue(baseCharacter);

      const result = await service.findById('char-1');

      expect(result).toEqual(baseCharacter);
      expect(mockPrisma.character.findUnique).toHaveBeenCalledWith({ where: { id: 'char-1' } });
    });

    it('should return null when character not found', async () => {
      mockPrisma.character.findUnique.mockResolvedValue(null);

      const result = await service.findById('nonexistent');

      expect(result).toBeNull();
    });
  });
});
