import {
  Injectable,
  NotFoundException,
  ForbiddenException,
  Logger,
} from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { SyncProfileDto } from './dto/sync-profile.dto';
import { CurrentUserPayload, ErrorCodes } from '../common/types';

@Injectable()
export class SyncService {
  private readonly logger = new Logger(SyncService.name);

  constructor(private readonly prisma: PrismaService) {}

  async getProfile(
    userId: string,
    currentUser: CurrentUserPayload,
  ): Promise<{
    nickname: string;
    profileJson: Record<string, unknown>;
    keyEvents: unknown[];
    updatedAt: number;
  }> {
    // 校验 JWT 中的 userId === :userId，防止越权
    if (currentUser.userId !== userId) {
      this.logger.warn(
        `Unauthorized sync access attempt: JWT user=${currentUser.userId.slice(0, 8)}..., requested=${userId.slice(0, 8)}...`,
      );
      throw new ForbiddenException({
        code: ErrorCodes.UNAUTHORIZED,
        message: '无权访问该用户数据',
      });
    }

    const user = await this.prisma.user.findUnique({
      where: { id: userId },
    });

    if (!user) {
      throw new NotFoundException({
        code: ErrorCodes.USER_NOT_FOUND,
        message: '用户不存在',
      });
    }

    let profileJson: Record<string, unknown> = {};
    try {
      profileJson =
        typeof user.profileJson === 'string'
          ? (JSON.parse(user.profileJson) as Record<string, unknown>)
          : (user.profileJson as Record<string, unknown>);
    } catch {
      profileJson = {};
    }

    return {
      nickname: user.nickname,
      profileJson,
      keyEvents: [], // Phase 2 才支持
      updatedAt: user.updatedAt.getTime(),
    };
  }

  async updateProfile(
    userId: string,
    dto: SyncProfileDto,
    currentUser: CurrentUserPayload,
  ): Promise<{ updatedAt: number }> {
    // 校验 JWT 中的 userId === :userId，防止越权
    if (currentUser.userId !== userId) {
      this.logger.warn(
        `Unauthorized sync update attempt: JWT user=${currentUser.userId.slice(0, 8)}..., requested=${userId.slice(0, 8)}...`,
      );
      throw new ForbiddenException({
        code: ErrorCodes.UNAUTHORIZED,
        message: '无权修改该用户数据',
      });
    }

    const user = await this.prisma.user.findUnique({
      where: { id: userId },
    });

    if (!user) {
      throw new NotFoundException({
        code: ErrorCodes.USER_NOT_FOUND,
        message: '用户不存在',
      });
    }

    const data: { nickname?: string; profileJson?: string } = {};

    if (dto.nickname !== undefined) {
      data.nickname = dto.nickname;
    }

    if (dto.profileJson !== undefined) {
      data.profileJson = JSON.stringify(dto.profileJson);
    }

    if (Object.keys(data).length === 0) {
      return { updatedAt: user.updatedAt.getTime() };
    }

    const updated = await this.prisma.user.update({
      where: { id: userId },
      data,
    });

    return { updatedAt: updated.updatedAt.getTime() };
  }
}
