import { Injectable, Logger } from '@nestjs/common';
import Pay from 'wechatpay-node-v3';
import { PrismaService } from '../prisma/prisma.service';

export interface WechatPrecreateResult {
  codeUrl: string;
  prepayId: string;
}

/**
 * Wechat Pay V3 service
 * Phase 2: connect real Wechat Pay channel
 *
 * Required env vars (production):
 *   WECHAT_APP_ID          - App ID (wx...公众号 or mobile app)
 *   WECHAT_MCH_ID          - Merchant ID
 *   WECHAT_SERIAL_NO       - Merchant certificate serial number
 *   WECHAT_PRIVATE_KEY     - Merchant RSA private key (PEM format)
 *   WECHAT_MERCHANT_PUBLIC_KEY - Merchant RSA public key (PEM format, uploaded to WeChat Pay)
 *   WECHAT_APIV3_KEY       - APIv3 key (used for AES-256-GCM callback decryption)
 *   WECHAT_NOTIFY_URL      - Payment result callback URL
 *
 * Sandbox: set WECHAT_SANDBOX=true
 */
@Injectable()
export class WechatService {
  private readonly logger = new Logger(WechatService.name);
  private client: Pay | null = null;
  private readonly isSandbox: boolean;
  /** APIv3 key for AES-256-GCM callback decryption */
  private readonly apiv3Key: string;

  constructor(private readonly prisma: PrismaService) {
    const appId = process.env.WECHAT_APP_ID || '';
    const mchId = process.env.WECHAT_MCH_ID || '';
    const serialNo = process.env.WECHAT_SERIAL_NO || '';
    const privateKeyPem = process.env.WECHAT_PRIVATE_KEY || '';
    const publicKeyPem = process.env.WECHAT_MERCHANT_PUBLIC_KEY || '';
    this.apiv3Key = process.env.WECHAT_APIV3_KEY || '';
    this.isSandbox = process.env.WECHAT_SANDBOX === 'true';

    if (!appId || !mchId || !privateKeyPem || !publicKeyPem) {
      this.logger.warn(
        'WeChat Pay not configured: WECHAT_APP_ID/WECHAT_MCH_ID/WECHAT_PRIVATE_KEY/WECHAT_MERCHANT_PUBLIC_KEY not set. Using mock mode.',
      );
      return;
    }

    this.client = new Pay({
      appid: appId,
      mchid: mchId,
      serial_no: serialNo,
      privateKey: Buffer.from(privateKeyPem, 'utf8'),
      publicKey: Buffer.from(publicKeyPem, 'utf8'),
    });

    this.logger.log('WeChat Pay initialized (sandbox=' + this.isSandbox + ', appid=' + appId.slice(0, 8) + '...)');
  }

  async nativePrecreate(params: {
    outTradeNo: string;
    amount: number;
    description: string;
    userId: string;
  }): Promise<WechatPrecreateResult> {
    if (!this.client) {
      return {
        codeUrl: 'weixin://wxpay/bizpayurl?pr=' + params.outTradeNo,
        prepayId: 'mock_prepay_' + params.outTradeNo,
      };
    }

    try {
      const result = await this.client['transactions_native']({
        out_trade_no: params.outTradeNo,
        description: params.description,
        amount: { total: params.amount, currency: 'CNY' },
        notify_url: process.env.WECHAT_NOTIFY_URL || '',
      });

      this.logger.log(
        'WeChat Native precreate: outTradeNo=' + params.outTradeNo + ', codeUrl=' + (result as any).code_url,
      );

      return {
        codeUrl: (result as any).code_url,
        prepayId: (result as any).prepay_id,
      };
    } catch (err) {
      this.logger.error('WeChat precreate failed: ' + (err as Error).message);
      throw err;
    }
  }

  async jsapiPrecreate(params: {
    outTradeNo: string;
    amount: number;
    description: string;
    openid: string;
  }): Promise<WechatPrecreateResult> {
    if (!this.client) {
      return { codeUrl: '', prepayId: 'mock_jsapi_' + params.outTradeNo };
    }

    try {
      const result = await this.client['transactions_jsapi']({
        out_trade_no: params.outTradeNo,
        description: params.description,
        amount: { total: params.amount, currency: 'CNY' },
        payer: { openid: params.openid },
        notify_url: process.env.WECHAT_NOTIFY_URL || '',
      });

      return { codeUrl: '', prepayId: (result as any).prepay_id };
    } catch (err) {
      this.logger.error('WeChat JSAPI precreate failed: ' + (err as Error).message);
      throw err;
    }
  }

  async queryOrder(query: { transactionId?: string; outTradeNo?: string }): Promise<{
    status: string;
    transactionId?: string;
    amount?: number;
  }> {
    if (!this.client) {
      return { status: 'SUCCESS', transactionId: 'mock_tx_' + (query.outTradeNo || '') };
    }

    try {
      const result = query.transactionId
        ? await this.client.query({ transaction_id: query.transactionId })
        : await this.client.query({ out_trade_no: query.outTradeNo || '' });

      return {
        status: (result as any).trade_state,
        transactionId: (result as any).transaction_id,
        amount: (result as any).amount?.total,
      };
    } catch (err) {
      this.logger.error('WeChat query failed: ' + (err as Error).message);
      return { status: 'UNKNOWN' };
    }
  }

  async closeOrder(outTradeNo: string): Promise<void> {
    if (!this.client) return;

    try {
      await (this.client as any)['close'](outTradeNo);
      this.logger.log('WeChat order closed: outTradeNo=' + outTradeNo);
    } catch (err) {
      this.logger.error('WeChat close failed: ' + (err as Error).message);
    }
  }

  async verifyNotification(
    headers: Record<string, string>,
    body: string,
  ): Promise<{ valid: boolean }> {
    if (!this.client) {
      return { valid: true };
    }

    try {
      const timestamp = headers['wechatpay-timestamp'] || '';
      const nonce = headers['wechatpay-nonce'] || '';
      const signature = headers['wechatpay-signature'] || '';
      const serial = headers['wechatpay-serial'] || '';
      const valid = await this.client.verifySign({
        timestamp,
        nonce,
        body,
        serial,
        signature,
      });
      return { valid };
    } catch {
      return { valid: false };
    }
  }

  decryptNotification(encryptedData: string): Record<string, unknown> {
    if (!this.client || !this.apiv3Key) {
      return JSON.parse(Buffer.from(encryptedData, 'base64').toString());
    }

    try {
      const buf = Buffer.from(encryptedData, 'base64');
      const tagLen = 16;
      const ciphertext = buf.slice(0, buf.length - tagLen);
      const authTag = buf.slice(buf.length - tagLen);
      // WeChat Pay V3: AES-256-GCM with APIv3 key
      // ciphertext is base64-encoded
      const decrypted = (this.client as any).decipher_gcm(
        ciphertext.toString('base64'),
        '',  // associated_data (empty for simple notifications)
        '',  // nonce
        this.apiv3Key,
      );

      return JSON.parse(decrypted);
    } catch (err) {
      this.logger.error('Decrypt notification failed: ' + (err as Error).message);
      throw err;
    }
  }
}
