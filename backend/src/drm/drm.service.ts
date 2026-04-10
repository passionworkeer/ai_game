import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { JwtService } from '@nestjs/jwt';
import * as crypto from 'crypto';

/**
 * DRM 服务
 * Phase 2：生成 RSA 密钥对，下发 AES-256 密钥
 *
 * 安全设计：
 * 1. RSA-2048 密钥对：公钥下发给客户端，私钥存环境变量
 * 2. 每用户每角色一对 AES-256 密钥
 * 3. AES key 用 RSA 公钥加密后传输，客户端用私钥解密
 * 4. AES key 存 Android Keystore（不落地文件）
 */

/**
 * RSA 密钥对
 */
export interface RsaKeyPair {
  publicKey: string;  // PEM 格式公钥
  privateKey: string; // PEM 格式私钥（仅服务端使用）
}

/**
 * AES key + 加密结果
 */
export interface EncryptedAesKey {
  /** Base64 RSA-OAEP 加密后的 AES-256 key */
  encryptedKey: string;
  /** 用于 RSA 解密的 modulus (n) */
  keyId?: string;
}

@Injectable()
export class DrmService {
  private readonly logger = new Logger(DrmService.name);
  /** 内存缓存的私钥（Phase 2 简化，生产用 KMS） */
  private privateKeyPem: string | null = null;
  /** 内存缓存的公钥 */
  private publicKeyPem: string | null = null;

  constructor(
    private readonly prisma: PrismaService,
    private readonly jwtService: JwtService,
  ) {
    this.loadOrGenerateKeys();
  }

  /**
   * 加载或生成 RSA 密钥对
   * Phase 2 简化：密钥存环境变量（或启动时生成）
   * 生产环境：使用 AWS KMS / Azure Key Vault
   */
  private loadOrGenerateKeys(): void {
    const envPrivateKey = process.env.DRM_RSA_PRIVATE_KEY || '';
    const envPublicKey = process.env.DRM_RSA_PUBLIC_KEY || '';

    if (envPrivateKey && envPublicKey) {
      this.privateKeyPem = envPrivateKey;
      this.publicKeyPem = envPublicKey;
      this.logger.log('DRM RSA keys loaded from environment');
      return;
    }

    // 启动时生成（仅 Phase 2 简化用，不适合生产）
    const { publicKey, privateKey } = crypto.generateKeyPairSync('rsa', {
      modulusLength: 2048,
      publicKeyEncoding: { type: 'spki', format: 'pem' },
      privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
    });

    this.privateKeyPem = privateKey;
    this.publicKeyPem = publicKey;

    this.logger.warn(
      'DRM RSA keys generated at startup. ' +
      'Set DRM_RSA_PRIVATE_KEY and DRM_RSA_PUBLIC_KEY env vars for production.',
    );
    // publicKeyPem available in logs if DEBUG logging is enabled
  }

  /**
   * 获取当前 RSA 公钥（PEM 格式）
   */
  getPublicKey(): string {
    if (!this.publicKeyPem) {
      throw new Error('DRM RSA public key not initialized');
    }
    return this.publicKeyPem;
  }

  /**
   * 生成 AES-256 密钥
   */
  private generateAesKey(): Buffer {
    return crypto.randomBytes(32); // 256 bits
  }

  /**
   * 用 RSA-OAEP 加密 AES key
   * Android 端使用相同的 RSA 公钥加密后传输（AES key 用于加密模型文件）
   */
  encryptAesKey(aesKey: Buffer): string {
    if (!this.publicKeyPem) {
      throw new Error('DRM RSA public key not initialized');
    }

    // @ts-ignore oaepHashAlgorithm supported in Node.js 20+
    const encrypted = crypto.publicEncrypt(
      {
        key: this.publicKeyPem,
        padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
        oaepHashAlgorithm: 'sha256',
      } as any,
      aesKey,
    );

    return encrypted.toString('base64');
  }

  /**
   * 用 RSA-OAEP 解密 AES key
   */
  decryptAesKey(encryptedKey: string): Buffer {
    if (!this.privateKeyPem) {
      throw new Error('DRM RSA private key not initialized');
    }

    // @ts-ignore oaepHashAlgorithm supported in Node.js 20+
    const decrypted = crypto.privateDecrypt(
      {
        key: this.privateKeyPem,
        padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
        oaepHashAlgorithm: 'sha256',
      } as any,
      Buffer.from(encryptedKey, 'base64'),
    );

    return decrypted;
  }

  /**
   * 生成并存储 AES 密钥（对应特定用户+角色购买）
   * 存储到 Purchase 表的 encryptedAesKey 字段
   */
  async generateAndStoreAesKey(
    userId: string,
    characterId: string,
  ): Promise<EncryptedAesKey> {
    // 检查是否已有密钥
    const existing = await this.prisma.purchase.findUnique({
      where: { userId_characterId: { userId, characterId } },
      select: { encryptedAesKey: true },
    });

    if (existing?.encryptedAesKey) {
      this.logger.log('AES key already exists for user=' + userId.slice(0, 8) + ', charId=' + characterId.slice(0, 8));
      return { encryptedKey: existing.encryptedAesKey };
    }

    // 生成新的 AES-256 key
    const aesKey = this.generateAesKey();
    const encryptedKey = this.encryptAesKey(aesKey);

    // 存储加密后的 key 到 Purchase 表
    await this.prisma.purchase.updateMany({
      where: { userId, characterId },
      data: { encryptedAesKey: encryptedKey },
    });

    this.logger.log('AES key generated and stored: user=' + userId.slice(0, 8) + ', charId=' + characterId.slice(0, 8));

    return { encryptedKey };
  }

  /**
   * 获取指定购买记录的 AES key
   */
  async getAesKeyForPurchase(
    purchaseId: string,
    userId: string,
  ): Promise<EncryptedAesKey | null> {
    const purchase = await this.prisma.purchase.findFirst({
      where: { id: purchaseId, userId },
      select: { encryptedAesKey: true, characterId: true },
    });

    if (!purchase || !purchase.encryptedAesKey) {
      return null;
    }

    return {
      encryptedKey: purchase.encryptedAesKey,
      keyId: purchase.characterId.slice(0, 8),  // 用于标识密钥版本
    };
  }
}
