import { Injectable, Logger } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { PrismaService } from '../prisma/prisma.service';

/**
 * Apple Sign In service
 * Phase 2: Simplified implementation for demo
 *
 * Full production implementation:
 * 1. Obtain Apple Developer credentials (Services ID + Private Key)
 * 2. Fetch Apple JWKS from https://appleid.apple.com/auth/keys
 * 3. Verify RSASSA-PKCS1-v1_5 signature with Apple's public key
 * 4. Validate iss=appleid.apple.com, aud=App Bundle ID, exp
 *
 * Phase 2 simplified: decode JWT payload without signature verification.
 * WARNING: Production MUST implement full signature verification.
 */

interface AppleIdTokenPayload {
  iss: string;
  aud: string;
  exp: number;
  iat: number;
  sub: string;
  email?: string;
}

@Injectable()
export class AppleService {
  private readonly logger = new Logger(AppleService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly jwtService: JwtService,
  ) {}

  /**
   * Phase 2 simplified: decode JWT without signature verification.
   * Production: MUST call verifySignature() using Apple's JWKS.
   */
  private decodeJwtPayload(identityToken: string): AppleIdTokenPayload | null {
    try {
      const parts = identityToken.split('.');
      if (parts.length !== 3) {
        this.logger.warn('Invalid Apple identity token format');
        return null;
      }
      const payload = JSON.parse(Buffer.from(parts[1], 'base64').toString('utf8'));
      return payload as AppleIdTokenPayload;
    } catch {
      return null;
    }
  }

  /**
   * Phase 2 simplified: validate basic JWT claims only.
   * Production: MUST verify RSASSA-PKCS1-v1_5 signature.
   */
  async verifyIdentityToken(identityToken: string): Promise<AppleIdTokenPayload | null> {
    const payload = this.decodeJwtPayload(identityToken);
    if (!payload) return null;

    // Validate issuer
    if (payload.iss !== 'https://appleid.apple.com') {
      this.logger.warn('Apple identity token issuer invalid: ' + payload.iss);
      return null;
    }

    // Validate audience (should match our app bundle ID)
    const expectedAud = process.env.APPLE_APP_ID || '';
    if (expectedAud && payload.aud !== expectedAud) {
      this.logger.warn('Apple identity token audience invalid: ' + payload.aud);
      // Allow through in demo mode if APPLE_APP_ID not set
      if (expectedAud) return null;
    }

    // Validate expiration
    if (payload.exp < Math.floor(Date.now() / 1000)) {
      this.logger.warn('Apple identity token expired');
      return null;
    }

    this.logger.warn('Apple token signature NOT VERIFIED - Phase 2 simplified. Use full JWKS verification in production.');

    return payload;
  }

  /**
   * Apple 登录：查找/创建用户并签发 JWT
   */
  async appleLogin(identityToken: string): Promise<{
    token: string;
    expiresAt: number;
    userId: string;
    isNewUser: boolean;
  } | null> {
    const payload = await this.verifyIdentityToken(identityToken);
    if (!payload) {
      return null;
    }

    const appleId = payload.sub;
    this.logger.log('Apple login: appleId=' + appleId.slice(0, 8) + '...');

    let user = await this.prisma.user.findUnique({
      where: { appleId },
    });

    let isNewUser = false;
    if (!user) {
      user = await this.prisma.user.create({
        data: {
          appleId,
          deviceId: 'apple_' + appleId.slice(0, 8),
          nickname: payload.email || 'Apple用户',
        },
      });
      isNewUser = true;
      this.logger.log('New user created via Apple login: userId=' + user.id.slice(0, 8));
    } else {
      this.logger.log('Existing user login via Apple: userId=' + user.id.slice(0, 8));
    }

    const jwtPayload = {
      sub: user.id,
      appleId: user.appleId || undefined,
    };

    const expiresAt = Math.floor(Date.now() / 1000 + 365 * 24 * 60 * 60);
    const token = this.jwtService.sign(jwtPayload);

    return { token, expiresAt, userId: user.id, isNewUser };
  }
}
