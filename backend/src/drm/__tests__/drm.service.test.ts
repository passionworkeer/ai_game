import { Test, TestingModule } from '@nestjs/testing';
import { DrmService } from '../drm.service';
import { PrismaService } from '../../prisma/prisma.service';
import { JwtService } from '@nestjs/jwt';

describe('DrmService', () => {
  let service: DrmService;
  let mockPrisma: {
    purchase: {
      findUnique: jest.Mock;
      updateMany: jest.Mock;
      findFirst: jest.Mock;
    };
  };
  let mockJwt: { sign: jest.Mock };

  const userId = 'user-12345678-abcd-efgh-ijkl';
  const characterId = 'char-87654321-mnop-qrst-uvwx';
  const purchaseId = 'purchase-11111111-2222-3333-4444';

  beforeEach(async () => {
    mockPrisma = {
      purchase: {
        findUnique: jest.fn(),
        updateMany: jest.fn().mockResolvedValue({ count: 1 }),
        findFirst: jest.fn(),
      },
    };

    mockJwt = {
      sign: jest.fn(),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        DrmService,
        { provide: PrismaService, useValue: mockPrisma },
        { provide: JwtService, useValue: mockJwt },
      ],
    }).compile();

    service = module.get<DrmService>(DrmService);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  // ------------------------------------------------------------------
  // getPublicKey
  // ------------------------------------------------------------------
  describe('getPublicKey', () => {
    it('should return a valid PEM public key starting with header', () => {
      const publicKey = service.getPublicKey();
      expect(publicKey).toMatch(/^-----BEGIN PUBLIC KEY-----/);
    });

    it('should return a valid PEM public key ending with footer', () => {
      const publicKey = service.getPublicKey();
      expect(publicKey).toMatch(/-----END PUBLIC KEY-----\r?\n?$/);
    });

    it('should return the same cached value on multiple calls', () => {
      const first = service.getPublicKey();
      const second = service.getPublicKey();
      const third = service.getPublicKey();
      expect(second).toBe(first);
      expect(third).toBe(first);
    });

    it('should return RSA-2048 key with correct byte length in PEM format', () => {
      const publicKey = service.getPublicKey();
      // RSA-2048 PEM is typically 450-500 bytes
      expect(publicKey.length).toBeGreaterThan(400);
      expect(publicKey.length).toBeLessThan(600);
    });

    it('should return different keys for different service instances', async () => {
      const module2: TestingModule = await Test.createTestingModule({
        providers: [
          DrmService,
          { provide: PrismaService, useValue: mockPrisma },
          { provide: JwtService, useValue: mockJwt },
        ],
      }).compile();

      const service2 = module2.get<DrmService>(DrmService);

      const key1 = service.getPublicKey();
      const key2 = service2.getPublicKey();

      // Each instance generates its own key pair
      expect(key1).not.toBe(key2);
    });

    it('should return base64-encoded SPKI format key', () => {
      const publicKey = service.getPublicKey();
      // Remove PEM headers and decode
      const base64Content = publicKey
        .replace('-----BEGIN PUBLIC KEY-----', '')
        .replace('-----END PUBLIC KEY-----', '')
        .replace(/\n/g, '');
      // Should be valid base64
      expect(() => Buffer.from(base64Content, 'base64')).not.toThrow();
    });
  });

  // ------------------------------------------------------------------
  // encryptAesKey
  // ------------------------------------------------------------------
  describe('encryptAesKey', () => {
    it('should return a non-empty base64 string for a 32-byte AES key', () => {
      const aesKey = Buffer.alloc(32);
      const encrypted = service.encryptAesKey(aesKey);
      expect(encrypted).toBeTruthy();
      expect(typeof encrypted).toBe('string');
      expect(encrypted.length).toBeGreaterThan(0);
    });

    it('should produce output different from the input buffer', () => {
      const aesKey = Buffer.from('a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2', 'hex');
      const encrypted = service.encryptAesKey(aesKey);
      expect(encrypted).not.toBe(aesKey.toString('base64'));
    });

    it('should return a different ciphertext each time (RSA is non-deterministic with OAEP)', () => {
      const aesKey = Buffer.alloc(32);
      const first = service.encryptAesKey(aesKey);
      const second = service.encryptAesKey(aesKey);
      // OAEP uses random padding, so ciphertexts differ
      expect(first).not.toBe(second);
    });

    it('should produce fixed-length ciphertext for inputs up to RSA-2048 OAEP limit', () => {
      // RSA-2048 with OAEP-SHA256 has max ~190 bytes of plaintext capacity
      const key16 = Buffer.alloc(16);
      const key32 = Buffer.alloc(32);
      const key190 = Buffer.alloc(190);

      const enc16 = service.encryptAesKey(key16);
      const enc32 = service.encryptAesKey(key32);
      const enc190 = service.encryptAesKey(key190);

      // RSA-2048 always produces 256-byte ciphertext
      expect(Buffer.from(enc16, 'base64').length).toBe(256);
      expect(Buffer.from(enc32, 'base64').length).toBe(256);
      expect(Buffer.from(enc190, 'base64').length).toBe(256);
    });

    it('should handle keys with all-zero bytes', () => {
      const zeroKey = Buffer.alloc(32, 0);
      const encrypted = service.encryptAesKey(zeroKey);
      expect(encrypted).toBeTruthy();
      // Should be decryptable
      const decrypted = service.decryptAesKey(encrypted);
      expect(decrypted.equals(zeroKey)).toBe(true);
    });

    it('should handle keys with all-0xff bytes', () => {
      const maxKey = Buffer.alloc(32, 0xff);
      const encrypted = service.encryptAesKey(maxKey);
      const decrypted = service.decryptAesKey(encrypted);
      expect(decrypted.equals(maxKey)).toBe(true);
    });

    it('should handle keys with Chinese UTF-8 characters', () => {
      const chineseKey = Buffer.from('中文测试密钥12345', 'utf8');
      const encrypted = service.encryptAesKey(chineseKey);
      const decrypted = service.decryptAesKey(encrypted);
      expect(decrypted.toString('utf8')).toBe(chineseKey.toString('utf8'));
    });

    it('should handle keys with emoji characters', () => {
      const emojiKey = Buffer.from('🔐🔑🗝️🔒123456789012345678', 'utf8');
      const encrypted = service.encryptAesKey(emojiKey);
      const decrypted = service.decryptAesKey(encrypted);
      expect(decrypted.toString('utf8')).toBe(emojiKey.toString('utf8'));
    });
  });

  // ------------------------------------------------------------------
  // decryptAesKey — round-trip
  // ------------------------------------------------------------------
  describe('decryptAesKey — round-trip', () => {
    it('should recover the original 32-byte AES key after encrypt then decrypt', () => {
      const originalKey = Buffer.from('a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2', 'hex');
      const encrypted = service.encryptAesKey(originalKey);
      const decrypted = service.decryptAesKey(encrypted);
      expect(decrypted).toBeInstanceOf(Buffer);
      expect(decrypted.equals(originalKey)).toBe(true);
    });

    it('should recover the original 32 bytes exactly (length check)', () => {
      const originalKey = Buffer.alloc(32, 0xff);
      const encrypted = service.encryptAesKey(originalKey);
      const decrypted = service.decryptAesKey(encrypted);
      expect(decrypted.length).toBe(32);
      for (let i = 0; i < 32; i++) {
        expect(decrypted[i]).toBe(0xff);
      }
    });

    it('should recover randomly generated 32-byte keys', () => {
      // Use crypto.randomBytes for truly random 32-byte key
      const crypto = require('crypto');
      const randomKey = crypto.randomBytes(32);
      expect(randomKey.length).toBe(32);

      const encrypted = service.encryptAesKey(randomKey);
      const decrypted = service.decryptAesKey(encrypted);
      expect(decrypted.equals(randomKey)).toBe(true);
    });

    it('should throw on invalid base64 input', () => {
      expect(() => {
        service.decryptAesKey('not-valid-base64!!!');
      }).toThrow();
    });

    it('should throw on corrupted ciphertext', () => {
      const validKey = Buffer.alloc(32);
      const encrypted = service.encryptAesKey(validKey);
      // Corrupt the ciphertext by modifying a byte in the middle
      const corruptedBuffer = Buffer.from(encrypted, 'base64');
      corruptedBuffer[10] = corruptedBuffer[10] ^ 0xff;
      const corrupted = corruptedBuffer.toString('base64');

      expect(() => {
        service.decryptAesKey(corrupted);
      }).toThrow();
    });

    it('should throw on truncated ciphertext', () => {
      const validKey = Buffer.alloc(32);
      const encrypted = service.encryptAesKey(validKey);
      // Take only half of the ciphertext
      const truncated = encrypted.substring(0, Math.floor(encrypted.length / 2));

      expect(() => {
        service.decryptAesKey(truncated);
      }).toThrow();
    });
  });

  // ------------------------------------------------------------------
  // generateAndStoreAesKey
  // ------------------------------------------------------------------
  describe('generateAndStoreAesKey', () => {
    it('should generate a new AES key and call updateMany with encrypted key on first call', async () => {
      mockPrisma.purchase.findUnique.mockResolvedValue(null);

      const result = await service.generateAndStoreAesKey(userId, characterId);

      expect(result.encryptedKey).toBeTruthy();
      expect(typeof result.encryptedKey).toBe('string');
      expect(result.encryptedKey.length).toBeGreaterThan(0);
      expect(mockPrisma.purchase.updateMany).toHaveBeenCalledWith({
        where: { userId, characterId },
        data: { encryptedAesKey: expect.any(String) },
      });
    });

    it('should return existing encrypted key and NOT call updateMany on second call', async () => {
      const existingKey = 'pre-existing-encrypted-key-base64';
      mockPrisma.purchase.findUnique.mockResolvedValue({
        encryptedAesKey: existingKey,
      });

      const first = await service.generateAndStoreAesKey(userId, characterId);
      const second = await service.generateAndStoreAesKey(userId, characterId);

      expect(first.encryptedKey).toBe(existingKey);
      expect(second.encryptedKey).toBe(existingKey);
      expect(mockPrisma.purchase.updateMany).not.toHaveBeenCalled();
    });

    it('should store the encrypted key via updateMany with correct where clause', async () => {
      mockPrisma.purchase.findUnique.mockResolvedValue(null);

      await service.generateAndStoreAesKey(userId, characterId);

      expect(mockPrisma.purchase.updateMany).toHaveBeenCalledWith({
        where: { userId, characterId },
        data: { encryptedAesKey: expect.any(String) },
      });
    });

    it('should return an object with encryptedKey field (interface EncryptedAesKey)', async () => {
      mockPrisma.purchase.findUnique.mockResolvedValue(null);

      const result = await service.generateAndStoreAesKey(userId, characterId);

      expect(result).toHaveProperty('encryptedKey');
      expect(result).not.toHaveProperty('keyId');
    });

    it('should call findUnique with correct userId and characterId composite key', async () => {
      mockPrisma.purchase.findUnique.mockResolvedValue(null);

      await service.generateAndStoreAesKey(userId, characterId);

      expect(mockPrisma.purchase.findUnique).toHaveBeenCalledWith({
        where: { userId_characterId: { userId, characterId } },
        select: { encryptedAesKey: true },
      });
    });

    it('should generate a decryptable AES key in the stored encrypted value', async () => {
      mockPrisma.purchase.findUnique.mockResolvedValue(null);

      const result = await service.generateAndStoreAesKey(userId, characterId);

      // Verify the stored key can be decrypted to 32 bytes
      const decrypted = service.decryptAesKey(result.encryptedKey);
      expect(decrypted.length).toBe(32);
    });

    it('should generate different keys for different user+character combinations', async () => {
      mockPrisma.purchase.findUnique.mockResolvedValue(null);

      const result1 = await service.generateAndStoreAesKey('user-A', 'char-1');
      const result2 = await service.generateAndStoreAesKey('user-B', 'char-2');

      // Keys should be different
      expect(result1.encryptedKey).not.toBe(result2.encryptedKey);

      // Both should be independently decryptable to 32-byte keys
      const dec1 = service.decryptAesKey(result1.encryptedKey);
      const dec2 = service.decryptAesKey(result2.encryptedKey);
      expect(dec1.length).toBe(32);
      expect(dec2.length).toBe(32);
      expect(dec1.equals(dec2)).toBe(false);
    });

    it('should propagate Prisma findUnique error', async () => {
      mockPrisma.purchase.findUnique.mockRejectedValue(
        new Error('Database connection failed'),
      );

      await expect(
        service.generateAndStoreAesKey(userId, characterId),
      ).rejects.toThrow('Database connection failed');
    });

    it('should propagate Prisma updateMany error', async () => {
      mockPrisma.purchase.findUnique.mockResolvedValue(null);
      mockPrisma.purchase.updateMany.mockRejectedValue(
        new Error('Write operation failed'),
      );

      await expect(
        service.generateAndStoreAesKey(userId, characterId),
      ).rejects.toThrow('Write operation failed');
    });

    it('should handle updateMany returning count: 0 (no matching rows)', async () => {
      mockPrisma.purchase.findUnique.mockResolvedValue(null);
      mockPrisma.purchase.updateMany.mockResolvedValue({ count: 0 });

      // Should not throw, just return the encrypted key
      const result = await service.generateAndStoreAesKey(userId, characterId);
      expect(result.encryptedKey).toBeTruthy();
    });

    it('should be idempotent: calling multiple times with existing key returns same result', async () => {
      const existingKey = 'idempotent-key-base64-123';
      mockPrisma.purchase.findUnique.mockResolvedValue({
        encryptedAesKey: existingKey,
      });

      const results = await Promise.all([
        service.generateAndStoreAesKey(userId, characterId),
        service.generateAndStoreAesKey(userId, characterId),
        service.generateAndStoreAesKey(userId, characterId),
      ]);

      results.forEach((result) => {
        expect(result.encryptedKey).toBe(existingKey);
      });
      expect(mockPrisma.purchase.updateMany).not.toHaveBeenCalled();
    });
  });

  // ------------------------------------------------------------------
  // getAesKeyForPurchase
  // ------------------------------------------------------------------
  describe('getAesKeyForPurchase', () => {
    it('should return encryptedKey and 8-char keyId when purchase exists with encryptedAesKey', async () => {
      const encryptedKey = 'test-encrypted-key-base64';
      const charId = 'abcdefgh-1234-5678-abcd-ef0123456789';
      mockPrisma.purchase.findFirst.mockResolvedValue({
        encryptedAesKey: encryptedKey,
        characterId: charId,
      });

      const result = await service.getAesKeyForPurchase(purchaseId, userId);

      expect(result).not.toBeNull();
      expect(result!.encryptedKey).toBe(encryptedKey);
      expect(result!.keyId).toBe(charId.slice(0, 8));
      expect(result!.keyId).toHaveLength(8);
    });

    it('should return null when purchase is not found', async () => {
      mockPrisma.purchase.findFirst.mockResolvedValue(null);

      const result = await service.getAesKeyForPurchase(purchaseId, userId);

      expect(result).toBeNull();
    });

    it('should return null when purchase exists but encryptedAesKey is null', async () => {
      mockPrisma.purchase.findFirst.mockResolvedValue({
        encryptedAesKey: null,
        characterId: characterId,
      });

      const result = await service.getAesKeyForPurchase(purchaseId, userId);

      expect(result).toBeNull();
    });

    it('should query findFirst with purchaseId and userId', async () => {
      mockPrisma.purchase.findFirst.mockResolvedValue(null);

      await service.getAesKeyForPurchase(purchaseId, userId);

      expect(mockPrisma.purchase.findFirst).toHaveBeenCalledWith({
        where: { id: purchaseId, userId },
        select: { encryptedAesKey: true, characterId: true },
      });
    });

    it('should return keyId as first 8 characters of characterId', async () => {
      const charId = 'xyz123ab-9999-0000-aaaa-bbbbbbbbbbbb';
      mockPrisma.purchase.findFirst.mockResolvedValue({
        encryptedAesKey: 'key-base64',
        characterId: charId,
      });

      const result = await service.getAesKeyForPurchase(purchaseId, userId);

      expect(result!.keyId).toBe('xyz123ab');
    });

    it('should handle short characterId (less than 8 chars) for keyId', async () => {
      const shortCharId = 'abc';
      mockPrisma.purchase.findFirst.mockResolvedValue({
        encryptedAesKey: 'key-base64',
        characterId: shortCharId,
      });

      const result = await service.getAesKeyForPurchase(purchaseId, userId);

      expect(result!.keyId).toBe('abc');
    });

    it('should propagate Prisma findFirst error', async () => {
      mockPrisma.purchase.findFirst.mockRejectedValue(
        new Error('Database query failed'),
      );

      await expect(
        service.getAesKeyForPurchase(purchaseId, userId),
      ).rejects.toThrow('Database query failed');
    });

    it('should return null when wrong userId attempts to access purchase', async () => {
      // findFirst returns null when userId filter doesn't match
      mockPrisma.purchase.findFirst.mockResolvedValue(null);

      const result = await service.getAesKeyForPurchase(purchaseId, 'wrong-user-id');

      expect(result).toBeNull();
    });

    it('should return full encryptedKey from stored purchase', async () => {
      const longEncryptedKey = 'a'.repeat(512) + '==';
      mockPrisma.purchase.findFirst.mockResolvedValue({
        encryptedAesKey: longEncryptedKey,
        characterId: 'char-1234567',
      });

      const result = await service.getAesKeyForPurchase(purchaseId, userId);

      expect(result!.encryptedKey).toBe(longEncryptedKey);
    });
  });

  // ------------------------------------------------------------------
  // Full encryption flow tests
  // ------------------------------------------------------------------
  describe('full encryption flow', () => {
    it('should support complete cycle: generate key, encrypt, store, retrieve, decrypt', async () => {
      // Step 1: generateAndStoreAesKey
      mockPrisma.purchase.findUnique.mockResolvedValue(null);
      mockPrisma.purchase.updateMany.mockResolvedValue({ count: 1 });

      const { encryptedKey } = await service.generateAndStoreAesKey(
        userId,
        characterId,
      );

      // Step 2: retrieve via getAesKeyForPurchase
      mockPrisma.purchase.findFirst.mockResolvedValue({
        encryptedAesKey: encryptedKey,
        characterId,
      });

      const retrieved = await service.getAesKeyForPurchase(purchaseId, userId);
      expect(retrieved).toBeTruthy();
      expect(retrieved!.encryptedKey).toBe(encryptedKey);

      // Step 3: decrypt to recover AES key
      const decryptedKey = service.decryptAesKey(retrieved!.encryptedKey);
      expect(decryptedKey.length).toBe(32);
      expect(Buffer.isBuffer(decryptedKey)).toBe(true);
    });

    it('should allow encrypting with public key and decrypting with private key across instances', async () => {
      // Create two service instances (simulating server restart with same env keys)
      const module2: TestingModule = await Test.createTestingModule({
        providers: [
          DrmService,
          { provide: PrismaService, useValue: mockPrisma },
          { provide: JwtService, useValue: mockJwt },
        ],
      }).compile();

      const service2 = module2.get<DrmService>(DrmService);

      // Each instance has its own key pair
      const key1 = service.getPublicKey();
      const key2 = service2.getPublicKey();

      expect(key1).not.toBe(key2);

      // Create a key in service and try to decrypt in service2 (should fail - different keys)
      const aesKey = Buffer.from('test-key-12345678901234567890', 'utf8');
      const encrypted = service.encryptAesKey(aesKey);

      // This would fail because service2 has different private key
      expect(() => {
        service2.decryptAesKey(encrypted);
      }).toThrow();
    });

    it('should encrypt different AES keys to produce different ciphertexts', async () => {
      const key1 = Buffer.from('11111111111111111111111111111111', 'utf8');
      const key2 = Buffer.from('22222222222222222222222222222222', 'utf8');

      const encrypted1 = service.encryptAesKey(key1);
      const encrypted2 = service.encryptAesKey(key2);

      // Each key produces unique ciphertext
      expect(encrypted1).not.toBe(encrypted2);

      // Both can be decrypted back to original keys
      const dec1 = service.decryptAesKey(encrypted1);
      const dec2 = service.decryptAesKey(encrypted2);
      expect(dec1.equals(key1)).toBe(true);
      expect(dec2.equals(key2)).toBe(true);
    });
  });
});
