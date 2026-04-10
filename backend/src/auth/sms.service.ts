import { Injectable, Logger } from '@nestjs/common';

/**
 * 短信验证码服务
 * Phase 2：Mock 实现，短信发送需要接入阿里云/腾讯云 SMS
 *
 * 防刷策略：
 * 1. 每手机号 1 分钟 1 次
 * 2. 每手机号 24 小时最多 10 次
 * 3. 需要图形验证码（前端）
 */

interface SmsCodeEntry {
  code: string;
  expiresAt: number;  // timestamp
  count24h: number;   // 24小时内发送次数
}

@Injectable()
export class SmsService {
  private readonly logger = new Logger(SmsService.name);
  /** In-memory store: phone -> { code, expiresAt, count24h } */
  private codeStore = new Map<string, SmsCodeEntry>();
  private readonly CODE_TTL_MS = 10 * 60 * 1000;   // 10 minutes
  private readonly MAX_PER_DAY = 10;
  private readonly MIN_INTERVAL_MS = 60 * 1000;    // 1 minute

  constructor() {
    // 定期清理过期验证码（每5分钟）
    setInterval(() => {
      const now = Date.now();
      for (const [phone, entry] of this.codeStore.entries()) {
        if (entry.expiresAt < now) {
          this.codeStore.delete(phone);
        }
      }
    }, 5 * 60 * 1000);
  }

  /**
   * 发送验证码
   */
  async sendCode(phone: string): Promise<{ success: boolean; message: string }> {
    const now = Date.now();
    const entry = this.codeStore.get(phone);

    // 防刷：1分钟限制
    if (entry && entry.expiresAt > now && now - (entry.expiresAt - this.CODE_TTL_MS) < this.MIN_INTERVAL_MS) {
      return { success: false, message: '发送太频繁，请稍后再试' };
    }

    // 防刷：24小时限制
    const currentCount = entry?.count24h || 0;
    if (currentCount >= this.MAX_PER_DAY) {
      return { success: false, message: '今日发送次数已用完，请明天再试' };
    }

    // 生成 6 位验证码
    const code = Math.floor(100000 + Math.random() * 900000).toString();

    this.codeStore.set(phone, {
      code,
      expiresAt: now + this.CODE_TTL_MS,
      count24h: currentCount + 1,
    });

    this.logger.log('SMS code sent to ' + phone.slice(0, 3) + '****' + phone.slice(-4) + ': ' + code);

    // Phase 2 简化：Mock，直接打印到日志
    // 生产：调用阿里云/腾讯云 SMS API
    // await this.sendSmsViaAliyun(phone, code);

    return { success: true, message: '验证码已发送' };
  }

  /**
   * 验证验证码
   */
  async verifyCode(phone: string, code: string): Promise<boolean> {
    const now = Date.now();
    const entry = this.codeStore.get(phone);

    if (!entry) {
      return false;
    }

    if (entry.expiresAt < now) {
      this.codeStore.delete(phone);
      return false;
    }

    if (entry.code !== code) {
      return false;
    }

    // 验证成功后删除（一次性）
    this.codeStore.delete(phone);
    return true;
  }
}
