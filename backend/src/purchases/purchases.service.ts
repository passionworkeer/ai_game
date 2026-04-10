import {
  Injectable,
  NotFoundException,
  ConflictException,
  UnprocessableEntityException,
  Logger,
} from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { VerifyPurchaseDto } from './dto/verify-purchase.dto';
import { CurrentUserPayload, ErrorCodes } from '../common/types';

@Injectable()
export class PurchasesService {
  private readonly logger = new Logger(PurchasesService.name);

  constructor(private readonly prisma: PrismaService) {}

  async verifyPurchase(
    dto: VerifyPurchaseDto,
    currentUser: CurrentUserPayload,
  ): Promise<{
    purchaseId: string;
    status: string;
    modelUrl: string;
  }> {
    const character = await this.prisma.character.findUnique({
      where: { id: dto.characterId },
    });

    if (!character) {
      throw new NotFoundException({
        code: ErrorCodes.CHARACTER_NOT_FOUND,
        message: '角色不存在',
      });
    }

    const existing = await this.prisma.purchase.findUnique({
      where: {
        userId_characterId: {
          userId: currentUser.userId,
          characterId: dto.characterId,
        },
      },
    });

    if (existing && existing.status === 'completed') {
      throw new ConflictException({
        code: ErrorCodes.ALREADY_PURCHASED,
        message: '已购买该角色',
      });
    }

    if (dto.paidAmount < character.price) {
      throw new UnprocessableEntityException({
        code: ErrorCodes.PURCHASE_VERIFY_FAILED,
        message: '支付金额不足，期望 ' + character.price + ' 分，实际 ' + dto.paidAmount + ' 分',
      });
    }

    if (dto.signature) {
      this.logger.debug(
        'Signature present, Phase 1 skip validation for user ' + currentUser.userId.slice(0, 8) + '...',
      );
    }

    const paidAtDate = new Date(dto.paidAt);

    const purchase = await this.prisma.purchase.upsert({
      where: {
        userId_characterId: {
          userId: currentUser.userId,
          characterId: dto.characterId,
        },
      },
      update: {
        channel: dto.channel,
        channelOrderId: dto.channelOrderId ?? null,
        amount: dto.paidAmount,
        status: 'completed',
        paidAt: paidAtDate,
      },
      create: {
        userId: currentUser.userId,
        characterId: dto.characterId,
        channel: dto.channel,
        channelOrderId: dto.channelOrderId ?? null,
        amount: dto.paidAmount,
        status: 'completed',
        paidAt: paidAtDate,
      },
    });

    this.logger.log(
      'Purchase verified for user ' + currentUser.userId.slice(0, 8) + '..., character: ' + character.code,
    );

    return {
      purchaseId: purchase.id,
      status: purchase.status,
      modelUrl: character.assetsUrl,
    };
  }

  async findUserPurchases(currentUser: CurrentUserPayload): Promise<{
    purchases: Array<{
      characterId: string;
      characterCode: string;
      characterName: string;
      paidAt: string;
    }>;
  }> {
    const records = await this.prisma.purchase.findMany({
      where: {
        userId: currentUser.userId,
        status: 'completed',
      },
      include: {
        character: {
          select: {
            id: true,
            code: true,
            name: true,
          },
        },
      },
      orderBy: { paidAt: 'desc' },
    });

    return {
      purchases: records.map((r) => ({
        characterId: r.character.id,
        characterCode: r.character.code,
        characterName: r.character.name,
        paidAt: r.paidAt?.toISOString() ?? '',
      })),
    };
  }

  async updatePurchaseByOrderId(
    outTradeNo: string,
    channel: string,
    channelTransactionId: string,
  ): Promise<void> {
    const purchase = await this.prisma.purchase.findFirst({
      where: { channelOrderId: outTradeNo },
    });

    if (!purchase) {
      this.logger.warn('Purchase not found for outTradeNo: ' + outTradeNo.slice(0, 8) + '...');
      return;
    }

    if (purchase.status === 'completed') {
      this.logger.log('Purchase already completed: ' + purchase.id.slice(0, 8));
      return;
    }

    await this.prisma.purchase.update({
      where: { id: purchase.id },
      data: {
        status: 'completed',
        channel: channel,
        channelOrderId: outTradeNo,
        paidAt: new Date(),
      },
    });

    this.logger.log('Purchase updated via callback: purchaseId=' + purchase.id.slice(0, 8) + ', channel=' + channel);
  }
}
