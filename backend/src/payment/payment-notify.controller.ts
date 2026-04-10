import { Controller, Post, Req, Body, HttpCode, HttpStatus, Logger } from '@nestjs/common';
import { Request } from 'express';
import { WechatService } from './wechat.service';
import { AlipayService } from './alipay.service';
import { PurchasesService } from '../purchases/purchases.service';

@Controller('payment/notify')
export class PaymentNotifyController {
  private readonly logger = new Logger(PaymentNotifyController.name);

  constructor(
    private readonly wechatService: WechatService,
    private readonly alipayService: AlipayService,
    private readonly purchasesService: PurchasesService,
  ) {}

  @Post('wechat')
  @HttpCode(HttpStatus.OK)
  async wechatNotify(@Req() req: Request) {
    const body = (req as any).rawBody ? (req as any).rawBody.toString() : JSON.stringify(req.body);
    const headers = req.headers as Record<string, string>;

    this.logger.log('WeChat notify received');

    const verified = await this.wechatService.verifyNotification(headers, body);
    if (!verified.valid) {
      this.logger.warn('WeChat notify signature invalid');
      return { code: 'FAIL', message: 'signature verification failed' };
    }

    try {
      const notifyData = JSON.parse(body);
      const resource = notifyData.resource || notifyData;
      const decrypted = this.wechatService.decryptNotification(
        typeof resource.ciphertext === 'string' ? resource.ciphertext : JSON.stringify(resource),
      );

      const outTradeNo = decrypted['out_trade_no'] as string;
      const tradeState = decrypted['trade_state'] as string;

      this.logger.log('WeChat notify: outTradeNo=' + outTradeNo + ', state=' + tradeState);

      if (tradeState === 'SUCCESS') {
        await this.purchasesService.updatePurchaseByOrderId(
          outTradeNo,
          'wechat',
          (decrypted['transaction_id'] as string) || outTradeNo,
        );
      }

      return { code: 'SUCCESS', message: 'OK' };
    } catch (err) {
      this.logger.error('WeChat notify processing failed: ' + (err as Error).message);
      return { code: 'FAIL', message: (err as Error).message };
    }
  }

  @Post('alipay')
  @HttpCode(HttpStatus.OK)
  async alipayNotify(@Body() params: Record<string, string>) {
    this.logger.log(
      'Alipay notify: outTradeNo=' + params.out_trade_no + ', status=' + params.trade_status,
    );

    const verified = this.alipayService.verifyNotification(params);
    if (!verified) {
      this.logger.warn('Alipay notify signature invalid');
      return 'fail';
    }

    try {
      const tradeStatus = params.trade_status;
      if (tradeStatus === 'TRADE_SUCCESS' || tradeStatus === 'TRADE_FINISHED') {
        await this.purchasesService.updatePurchaseByOrderId(
          params.out_trade_no,
          'alipay',
          params.trade_no || params.out_trade_no,
        );
      }
      return 'success';
    } catch (err) {
      this.logger.error('Alipay notify processing failed: ' + (err as Error).message);
      return 'fail';
    }
  }
}
