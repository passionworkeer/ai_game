import { Controller, Get, Param, UseGuards, HttpCode, HttpStatus, Logger } from '@nestjs/common';
import { DrmService } from './drm.service';
import { JwtAuthGuard } from '../guards/jwt-auth.guard';
import { CurrentUser } from '../decorators/current-user.decorator';
import { CurrentUserPayload } from '../common/types';
import { PrismaService } from '../prisma/prisma.service';

@Controller('purchase')
export class DrmController {
  private readonly logger = new Logger(DrmController.name);

  constructor(
    private readonly drmService: DrmService,
    private readonly prisma: PrismaService,
  ) {}

  @Get(':purchaseId/key')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async getAesKey(
    @Param('purchaseId') purchaseId: string,
    @CurrentUser() user: CurrentUserPayload,
  ) {
    const purchase = await this.prisma.purchase.findUnique({
      where: { id: purchaseId },
      select: { userId: true, characterId: true, encryptedAesKey: true },
    });

    if (!purchase || purchase.userId !== user.userId) {
      return { success: false, error: { code: 'UNAUTHORIZED', message: '无权访问该密钥' } };
    }

    if (!purchase.encryptedAesKey) {
      return { success: false, error: { code: 'KEY_NOT_FOUND', message: '密钥不存在，请先生成' } };
    }

    return {
      success: true,
      data: {
        encryptedKey: purchase.encryptedAesKey,
        keyId: purchase.characterId.slice(0, 8),
      },
    };
  }

  @Get(':purchaseId/key/generate')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async generateAesKey(
    @Param('purchaseId') purchaseId: string,
    @CurrentUser() user: CurrentUserPayload,
  ) {
    const purchase = await this.prisma.purchase.findUnique({
      where: { id: purchaseId },
    });

    if (!purchase) {
      return { success: false, error: { code: 'NOT_FOUND', message: '购买记录不存在' } };
    }

    if (purchase.userId !== user.userId) {
      return { success: false, error: { code: 'UNAUTHORIZED', message: '无权操作' } };
    }

    const key = await this.drmService.generateAndStoreAesKey(user.userId, purchase.characterId);
    return { success: true, data: key };
  }
}
