import { Injectable, Logger } from '@nestjs/common';
import { AlipaySdk } from 'alipay-sdk';
import { PrismaService } from '../prisma/prisma.service';

export interface AlipayTradeResult {
  qrCode: string;
  tradeNo: string;
}

/**
 * Alipay当面付 service
 * Phase 2: connect real Alipay channel
 */
@Injectable()
export class AlipayService {
  private readonly logger = new Logger(AlipayService.name);
  private client: AlipaySdk | null = null;
  private readonly isSandbox: boolean;

  constructor(private readonly prisma: PrismaService) {
    const appId = process.env.ALIPAY_APP_ID || '';
    const privateKey = process.env.ALIPAY_PRIVATE_KEY || '';
    const alipayPublicKey = process.env.ALIPAY_PUBLIC_KEY || '';
    this.isSandbox = process.env.ALIPAY_SANDBOX === 'true';

    if (!appId || !privateKey || !alipayPublicKey) {
      this.logger.warn(
        'Alipay not configured: ALIPAY_APP_ID/ALIPAY_PRIVATE_KEY/ALIPAY_PUBLIC_KEY not set',
      );
      return;
    }

    this.client = new AlipaySdk({
      appId,
      privateKey,
      alipayPublicKey,
    });

    this.logger.log('Alipay initialized (sandbox=' + this.isSandbox + ')');
  }

  async tradePrecreate(params: {
    outTradeNo: string;
    totalAmount: number;
    subject: string;
    userId: string;
  }): Promise<AlipayTradeResult> {
    if (!this.client) {
      return {
        qrCode: 'https://qr.alipay.com/baz/' + params.outTradeNo,
        tradeNo: 'mock_alipay_' + params.outTradeNo,
      };
    }

    try {
      const result = await this.client.exec('alipay.trade.precreate', {
        outTradeNo: params.outTradeNo,
        totalAmount: params.totalAmount.toFixed(2),
        subject: params.subject,
        notifyUrl: process.env.ALIPAY_NOTIFY_URL || '',
        businessParams: { merchantUserId: params.userId },
      });

      const res = result as { qrCode?: string; tradeNo?: string };
      const qrCode = res.qrCode;
      this.logger.log('Alipay precreate: outTradeNo=' + params.outTradeNo + ', qrCode=' + qrCode);

      return {
        qrCode: qrCode || '',
        tradeNo: res.tradeNo || '',
      };
    } catch (err) {
      this.logger.error('Alipay precreate failed: ' + (err as Error).message);
      throw err;
    }
  }

  async tradeAppPay(params: {
    outTradeNo: string;
    totalAmount: number;
    subject: string;
  }): Promise<string> {
    if (!this.client) {
      return 'mock_app_pay_' + params.outTradeNo;
    }

    try {
      const result = await this.client.exec('alipay.trade.app.pay', {
        outTradeNo: params.outTradeNo,
        totalAmount: params.totalAmount.toFixed(2),
        subject: params.subject,
        productCode: 'QUICK_MSECURITY_PAY',
        notifyUrl: process.env.ALIPAY_NOTIFY_URL || '',
      });

      // trade.app.pay returns a string (order string for App SDK)
      if (typeof result === 'string') return result;
      return (result as { code?: string }).code || JSON.stringify(result);
    } catch (err) {
      this.logger.error('Alipay app pay failed: ' + (err as Error).message);
      throw err;
    }
  }

  async queryOrder(outTradeNo: string): Promise<{
    status: string;
    tradeNo?: string;
    amount?: number;
  }> {
    if (!this.client) {
      return { status: 'TRADE_SUCCESS', tradeNo: 'mock_alipay_' + outTradeNo };
    }

    try {
      const result = await this.client.exec('alipay.trade.query', { outTradeNo });
      const res = result as { tradeStatus?: string; tradeNo?: string; totalAmount?: string };

      return {
        status: res.tradeStatus || 'UNKNOWN',
        tradeNo: res.tradeNo,
        amount: parseFloat(res.totalAmount || '0'),
      };
    } catch (err) {
      this.logger.error('Alipay query failed: ' + (err as Error).message);
      return { status: 'UNKNOWN' };
    }
  }

  async closeOrder(outTradeNo: string): Promise<void> {
    if (!this.client) return;

    try {
      await this.client.exec('alipay.trade.close', { outTradeNo });
      this.logger.log('Alipay order closed: outTradeNo=' + outTradeNo);
    } catch (err) {
      this.logger.error('Alipay close failed: ' + (err as Error).message);
    }
  }

  verifyNotification(params: Record<string, string>): boolean {
    if (!this.client) {
      return true;
    }

    try {
      return this.client.checkNotifySign(params);
    } catch {
      return false;
    }
  }
}
