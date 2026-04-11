import { Injectable, NotFoundException, Logger } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { CreateCharacterDto } from './dto/create-character.dto';
import { UpdateCharacterDto } from './dto/update-character.dto';

export interface AdminStats {
  dau: number;      // 今日活跃用户数
  mau: number;      // 近30天活跃用户数
  totalUsers: number;
  paidUsers: number;
  revenue: number;  // 单位：分
  paidRate: number; // 付费率百分比
}

@Injectable()
export class AdminService {
  private readonly logger = new Logger(AdminService.name);

  constructor(private readonly prisma: PrismaService) {}

  // ------------------------------------------------------------------
  // 角色管理
  // ------------------------------------------------------------------

  async listCharacters() {
    return this.prisma.character.findMany({
      orderBy: { createdAt: 'asc' },
    });
  }

  async createCharacter(dto: CreateCharacterDto) {
    const character = await this.prisma.character.create({
      data: {
        code: dto.code,
        name: dto.name,
        description: dto.description,
        price: dto.price,
        assetsUrl: dto.assetsUrl,
        systemPrompt: dto.systemPrompt ?? '',
        previewUrl: dto.previewUrl,
        isActive: dto.isActive ?? true,
      },
    });

    this.logger.log(`Character created: code=${character.code}, id=${character.id.slice(0, 8)}`);
    return character;
  }

  async updateCharacter(id: string, dto: UpdateCharacterDto) {
    const existing = await this.prisma.character.findUnique({ where: { id } });
    if (!existing) {
      throw new NotFoundException({
        code: 'CHARACTER_NOT_FOUND',
        message: `角色不存在: ${id}`,
      });
    }

    return this.prisma.character.update({
      where: { id },
      data: dto as any,
    });
  }

  async deleteCharacter(id: string) {
    const existing = await this.prisma.character.findUnique({ where: { id } });
    if (!existing) {
      throw new NotFoundException({
        code: 'CHARACTER_NOT_FOUND',
        message: `角色不存在: ${id}`,
      });
    }

    await this.prisma.character.delete({ where: { id } });
    this.logger.log(`Character deleted: code=${existing.code}, id=${id.slice(0, 8)}`);
    return { id };
  }

  // ------------------------------------------------------------------
  // 数据看板
  // ------------------------------------------------------------------

  async getStats(): Promise<AdminStats> {
    const now = new Date();
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);

    // DAU: 今日活跃用户（lastActive >= todayStart）
    const dau = await this.prisma.user.count({
      where: { lastActive: { gte: todayStart } },
    });

    // MAU: 近30天活跃用户
    const mau = await this.prisma.user.count({
      where: { lastActive: { gte: thirtyDaysAgo } },
    });

    // 总用户数
    const totalUsers = await this.prisma.user.count();

    // 付费用户数（至少完成一笔 completed 购买）
    const paidResult = await this.prisma.purchase.groupBy({
      by: ['userId'],
      where: { status: 'completed' },
    });
    const paidUsers = paidResult.length;

    // 总收入（分）
    const revenueResult = await this.prisma.purchase.aggregate({
      where: { status: 'completed' },
      _sum: { amount: true },
    });
    const revenue = revenueResult._sum.amount ?? 0;

    // 付费率
    const paidRate = totalUsers > 0 ? (paidUsers / totalUsers) * 100 : 0;

    this.logger.log(`Stats: DAU=${dau}, MAU=${mau}, paidUsers=${paidUsers}, revenue=${revenue}`);

    return {
      dau,
      mau,
      totalUsers,
      paidUsers,
      revenue,
      paidRate: Math.round(paidRate * 100) / 100,
    };
  }

  // ------------------------------------------------------------------
  // 设备管理
  // ------------------------------------------------------------------

  async banDevice(deviceId: string) {
    const user = await this.prisma.user.findUnique({ where: { deviceId } });
    if (!user) {
      throw new NotFoundException({
        code: 'USER_NOT_FOUND',
        message: `设备不存在: ${deviceId.slice(0, 8)}...`,
      });
    }

    const updated = await this.prisma.user.update({
      where: { deviceId },
      data: { isBanned: true },
    });

    this.logger.log(`Device banned: deviceId=${deviceId.slice(0, 8)}..., userId=${user.id.slice(0, 8)}`);
    return { deviceId, isBanned: updated.isBanned };
  }

  async unbanDevice(deviceId: string) {
    const user = await this.prisma.user.findUnique({ where: { deviceId } });
    if (!user) {
      throw new NotFoundException({
        code: 'USER_NOT_FOUND',
        message: `设备不存在: ${deviceId.slice(0, 8)}...`,
      });
    }

    const updated = await this.prisma.user.update({
      where: { deviceId },
      data: { isBanned: false },
    });

    this.logger.log(`Device unbanned: deviceId=${deviceId.slice(0, 8)}..., userId=${user.id.slice(0, 8)}`);
    return { deviceId, isBanned: updated.isBanned };
  }

  async listDevices(page = 1, limit = 20) {
    const skip = (page - 1) * limit;

    const [users, total] = await Promise.all([
      this.prisma.user.findMany({
        skip,
        take: limit,
        orderBy: { createdAt: 'desc' },
        select: {
          id: true,
          deviceId: true,
          nickname: true,
          isBanned: true,
          role: true,
          createdAt: true,
          lastActive: true,
          _count: { select: { purchases: true } },
        },
      }),
      this.prisma.user.count(),
    ]);

    return {
      users: users.map((u: any) => ({
        userId: u.id,
        deviceId: u.deviceId,
        nickname: u.nickname,
        isBanned: u.isBanned,
        role: u.role,
        createdAt: u.createdAt.getTime(),
        lastActive: u.lastActive.getTime(),
        purchaseCount: u._count.purchases,
      })),
      total,
      page,
      limit,
    };
  }
}
