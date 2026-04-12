import { Controller, Post, Get, Body, HttpCode, HttpStatus, Logger, UseGuards } from '@nestjs/common';
import { AuthService } from './auth.service';
import { DeviceRegisterDto } from './dto/device-register.dto';
import { SmsService } from './sms.service';
import { AppleService } from './apple.service';
import { DrmService } from '../drm/drm.service';
import { JwtAuthGuard } from '../guards/jwt-auth.guard';
import { CurrentUser } from '../decorators/current-user.decorator';
import { CurrentUserPayload } from '../common/types';
import { PhoneLoginDto, AppleLoginDto, SendSmsDto, MergeAccountDto } from './dto/phone-auth.dto';

@Controller('auth')
export class AuthController {
  private readonly logger = new Logger(AuthController.name);

  constructor(
    private readonly authService: AuthService,
    private readonly smsService: SmsService,
    private readonly appleService: AppleService,
    private readonly drmService: DrmService,
  ) {}

  // ── Phase 1 已有接口 ──────────────────────────────────────────

  @Post('device')
  @HttpCode(HttpStatus.OK)
  async deviceRegister(@Body() dto: DeviceRegisterDto) {
    const result = await this.authService.deviceRegister(dto);
    return { success: true, data: result };
  }

  // ── P1-B2 短信登录 ────────────────────────────────────────────

  /**
   * POST /auth/sms/send
   * 发送验证码
   */
  @Post('sms/send')
  @HttpCode(HttpStatus.OK)
  async sendSms(@Body() dto: SendSmsDto) {
    const result = await this.smsService.sendCode(dto.phone);
    return { success: true, data: result };
  }

  /**
   * POST /auth/phone
   * 手机号 + 验证码登录
   */
  @Post('phone')
  @HttpCode(HttpStatus.OK)
  async phoneLogin(@Body() dto: PhoneLoginDto) {
    const valid = await this.smsService.verifyCode(dto.phone, dto.code);
    if (!valid) {
      return { success: false, error: { code: 'INVALID_CODE', message: '验证码错误或已过期' } };
    }

    const result = await this.authService.phoneLogin(dto.phone);
    return { success: true, data: result };
  }

  // ── P1-B2 Apple 登录 ──────────────────────────────────────────

  /**
   * POST /auth/apple
   * Apple Sign In 登录
   */
  @Post('apple')
  @HttpCode(HttpStatus.OK)
  async appleLogin(@Body() dto: AppleLoginDto) {
    const result = await this.appleService.appleLogin(dto.identityToken);
    if (!result) {
      return { success: false, error: { code: 'INVALID_TOKEN', message: 'Apple 登录失败，token 无效' } };
    }
    return { success: true, data: result };
  }

  // ── P1-B2 账号迁移 ────────────────────────────────────────────

  /**
   * POST /auth/merge
   * 设备匿名账号 → 正式账号（手机/Apple）迁移
   * 合并策略：
   *   - 购买记录：保留（通过 purchase.userId 映射）
   *   - 聊天记录：通过 sync.profileJson 迁移
   *   - 云同步：以正式账号为准
   */
  @Post('merge')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async mergeAccount(
    @Body() dto: MergeAccountDto,
    @CurrentUser() user: CurrentUserPayload,
  ) {
    const result = await this.authService.mergeDeviceToPhone(
      user.userId,
      dto.deviceId,
      dto.targetType,
    );
    return { success: true, data: result };
  }

  // ── P1-B3 DRM 密钥 ────────────────────────────────────────────

  /**
   * GET /auth/rsa-public-key
   * 获取 RSA 公钥（用于 Android 端加密 AES key）
   */
  @Get('rsa-public-key')
  @HttpCode(HttpStatus.OK)
  getRsaPublicKey() {
    const publicKey = this.drmService.getPublicKey();
    return { success: true, data: { publicKey } };
  }
}
