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

  /**
   * P1-B2: 手机号登录（设备匿名 → 手机号账号）
   * 如果手机号对应的账号不存在，则关联现有设备匿名账号
   */
  async phoneLogin(phone: string): Promise<{
    token: string;
    expiresAt: number;
    userId: string;
    isNewUser: boolean;
  }> {
    const deviceId = 'phone_' + phone.slice(0, 8);

    let user = await this.prisma.user.findUnique({
      where: { phone },
    });

    let isNewUser = false;
    if (!user) {
      user = await this.prisma.user.create({
        data: {
          phone,
          deviceId,
          nickname: phone.slice(0, 3) + '****' + phone.slice(-4),
        },
      });
      isNewUser = true;
      this.logger.log('New user created via phone login: userId=' + user.id.slice(0, 8) + ', phone=' + phone.slice(0, 3) + '...');
    } else {
      this.logger.log('Existing user login via phone: userId=' + user.id.slice(0, 8));
    }

    const payload: JwtPayload = {
      sub: user.id,
      phone: user.phone || undefined,
      deviceId: user.deviceId,
    };

    const expiresAt = Math.floor(Date.now() / 1000 + 365 * 24 * 60 * 60);
    const token = this.jwtService.sign(payload);

    return { token, expiresAt, userId: user.id, isNewUser };
  }

  /**
   * P1-B2: 账号迁移（设备匿名账号 → 正式账号）
   * 将设备匿名账号的 data 合并到正式账号
   */
  /**
   * P1-B2: 账号迁移（设备匿名账号 → 正式账号）
   * 将设备匿名账号的 data 合并到正式账号
   * @param currentUserId JWT 中的当前用户 ID（已登录的正式账号）
   * @param deviceId 要合并的源设备匿名账号的 deviceId
   * @param targetType 'phone' | 'apple'
   */
  async mergeDeviceToPhone(
    currentUserId: string,
    deviceId: string,
    targetType: 'phone' | 'apple',
  ): Promise<{
    mergedUserId: string;
    purchaseCount: number;
    profileJson: string;
  }> {
    // P0 SECURITY: IDOR prevention — JWT 用户必须拥有该 deviceId 对应的账号
    const sourceUser = await this.prisma.user.findUnique({
      where: { id: currentUserId },
      select: { id: true, deviceId: true, profileJson: true },
    });

    if (!sourceUser) {
      throw new Error('当前账号不存在');
    }

    // 验证 JWT 用户拥有该设备账号（防止冒用他人设备账号）
    if (sourceUser.deviceId !== deviceId) {
      throw new Error('无权迁移该设备账号');
    }

    // 查找目标正式账号
    const targetField = targetType === 'phone' ? 'phone' : 'appleId';

    // 查找已关联该 phone/appleId 的正式账号
    const targetUser = await this.prisma.user.findFirst({
      where: {
        [targetField]: { not: null },
        id: { not: sourceUser.id },
      },
    });

    if (!targetUser) {
      throw new Error('目标正式账号不存在，请先登录手机号/Apple');
    }

    // 迁移购买记录（userId 映射）
    await this.prisma.purchase.updateMany({
      where: { userId: sourceUser.id },
      data: { userId: targetUser.id },
    });

    // 合并 profileJson（以目标账号为准，源账号的 profile 作为补充）
    const sourceProfile = sourceUser.profileJson ? JSON.parse(sourceUser.profileJson) : {};
    const targetProfile = targetUser.profileJson ? JSON.parse(targetUser.profileJson) : {};
    const mergedProfile = { ...sourceProfile, ...targetProfile };

    await this.prisma.user.update({
      where: { id: targetUser.id },
      data: { profileJson: JSON.stringify(mergedProfile) },
    });

    // 删除源设备账号
    await this.prisma.user.delete({
      where: { id: sourceUser.id },
    });

    // 统计迁移的购买记录数量
    const purchaseCount = await this.prisma.purchase.count({
      where: { userId: targetUser.id },
    });

    this.logger.log(
      'Account merged: source=' + sourceUser.id.slice(0, 8) + ' -> target=' + targetUser.id.slice(0, 8) + ', purchases=' + purchaseCount,
    );

    return {
      mergedUserId: targetUser.id,
      purchaseCount,
      profileJson: JSON.stringify(mergedProfile),
    };
  }

}