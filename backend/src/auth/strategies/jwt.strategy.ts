import { Injectable, ForbiddenException } from '@nestjs/common';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import { JwtPayload, CurrentUserPayload } from '../../common/types';
import { PrismaService } from '../../prisma/prisma.service';

@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy) {
  constructor(private readonly prisma: PrismaService) {
    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      ignoreExpiration: false,
      secretOrKey: process.env.JWT_SECRET ?? 'dev_secret_change_in_production',
    });
  }

  async validate(payload: JwtPayload): Promise<CurrentUserPayload> {
    let role = payload.role;
    let isBanned = false;
    let deviceId = payload.deviceId || '';

    try {
      const user = await this.prisma.user.findUnique({
        where: { id: payload.sub },
        select: { id: true, deviceId: true, isBanned: true, role: true },
      });

      if (user) {
        role = user.role ?? payload.role;
        isBanned = user.isBanned;
        deviceId = user.deviceId;

        if (user.isBanned) {
          throw new ForbiddenException({
            code: 'FORBIDDEN',
            message: '账号已被封禁，请联系客服',
          });
        }

        this.prisma.user
          .update({
            where: { id: payload.sub },
            data: { lastActive: new Date() },
          })
          .catch(() => { /* ignore */ });
      }
    } catch (err) {
      if (err instanceof ForbiddenException) throw err;
    }

    return {
      userId: payload.sub,
      deviceId,
      role,
      isBanned,
    };
  }
}
