import { Injectable, Logger } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { PrismaService } from '../prisma/prisma.service';
import { DeviceRegisterDto } from './dto/device-register.dto';
import { JwtPayload } from '../common/types';

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  constructor(
    private readonly prisma: PrismaService,
    private readonly jwtService: JwtService,
  ) {}

  async deviceRegister(dto: DeviceRegisterDto): Promise<{
    token: string;
    expiresAt: number;
    userId: string;
  }> {
    const deviceId = dto.deviceId;
    // 日志只打前 8 位（脱敏）
    const deviceIdShort = deviceId.slice(0, 8);

    let user = await this.prisma.user.findUnique({
      where: { deviceId },
    });

    if (!user) {
      user = await this.prisma.user.create({
        data: { deviceId },
      });
      this.logger.log(`New user created, deviceId: ${deviceIdShort}...`);
    } else {
      this.logger.log(`Existing user login, deviceId: ${deviceIdShort}...`);
    }

    const payload: JwtPayload = {
      sub: user.id,
      deviceId: user.deviceId,
    };

    const expiresAt = Math.floor(
      Date.now() / 1000 + 365 * 24 * 60 * 60,
    ); // 365d

    const token = this.jwtService.sign(payload);

    return { token, expiresAt, userId: user.id };
  }
}
