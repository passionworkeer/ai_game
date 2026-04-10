import {
  Controller, Post, Body, HttpCode, HttpStatus, Logger, UseGuards,
} from '@nestjs/common';
import { WechatService } from './wechat.service';
import { AlipayService } from './alipay.service';
import { WechatPrecreateDto, AlipayPrecreateDto, AppPayDto, VerifyChannelDto } from './dto/payment.dto';
import { PurchasesService } from '../purchases/purchases.service';
import { JwtAuthGuard } from '../guards/jwt-auth.guard';
import { CurrentUser } from '../decorators/current-user.decorator';
import { CurrentUserPayload } from '../common/types';

@Controller('payment')
export class PaymentController {
  private readonly logger = new Logger(PaymentController.name);

  constructor(
    private readonly wechatService: WechatService,
    private readonly alipayService: AlipayService,
    private readonly purchasesService: PurchasesService,
  ) {}

  @Post('wechat/precreate')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async wechatPrecreate(@Body() dto: WechatPrecreateDto, @CurrentUser() user: CurrentUserPayload) {
    this.logger.log('WeChat precreate: user=' + user.userId.slice(0, 8) + ', amount=' + dto.amount);
    const result = await this.wechatService.nativePrecreate({
      outTradeNo: dto.outTradeNo, amount: dto.amount,
      description: dto.description, userId: user.userId,
    });
    return { success: true, data: { codeUrl: result.codeUrl, outTradeNo: dto.outTradeNo, expiresIn: 900 } };
  }

  @Post('wechat/jsapi')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async wechatJsapi(@Body() dto: WechatPrecreateDto, @CurrentUser() user: CurrentUserPayload) {
    if (!dto.openid) throw new Error('openid is required for JSAPI');
    const result = await this.wechatService.jsapiPrecreate({
      outTradeNo: dto.outTradeNo, amount: dto.amount,
      description: dto.description, openid: dto.openid,
    });
    return { success: true, data: { prepayId: result.prepayId, outTradeNo: dto.outTradeNo } };
  }

  @Post('wechat/query')
  @UseGuards(JwtAuthGuard)
  async wechatQuery(@Body() body: { outTradeNo: string }) {
    const result = await this.wechatService.queryOrder({ outTradeNo: body.outTradeNo });
    return { success: true, data: result };
  }

  @Post('alipay/precreate')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async alipayPrecreate(@Body() dto: AlipayPrecreateDto, @CurrentUser() user: CurrentUserPayload) {
    this.logger.log('Alipay precreate: user=' + user.userId.slice(0, 8) + ', amount=' + dto.totalAmount);
    const result = await this.alipayService.tradePrecreate({
      outTradeNo: dto.outTradeNo, totalAmount: dto.totalAmount,
      subject: dto.subject, userId: user.userId,
    });
    return { success: true, data: { qrCode: result.qrCode, outTradeNo: dto.outTradeNo } };
  }

  @Post('alipay/app')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async alipayAppPay(@Body() dto: AppPayDto, @CurrentUser() _user: CurrentUserPayload) {
    const orderString = await this.alipayService.tradeAppPay({
      outTradeNo: dto.outTradeNo, totalAmount: dto.totalAmount, subject: dto.subject,
    });
    return { success: true, data: { orderString, outTradeNo: dto.outTradeNo } };
  }

  @Post('alipay/query')
  @UseGuards(JwtAuthGuard)
  async alipayQuery(@Body() body: { outTradeNo: string }) {
    const result = await this.alipayService.queryOrder(body.outTradeNo);
    return { success: true, data: result };
  }

  @Post('verify')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async verifyPayment(@Body() dto: VerifyChannelDto, @CurrentUser() _user: CurrentUserPayload) {
    if (dto.channel === 'wechat') {
      const r = await this.wechatService.queryOrder({ outTradeNo: dto.channelOrderId });
      if (r.status !== 'SUCCESS') return { success: true, data: { verified: false, status: r.status } };
    } else if (dto.channel === 'alipay') {
      const r = await this.alipayService.queryOrder(dto.channelOrderId);
      if (r.status !== 'TRADE_SUCCESS') return { success: true, data: { verified: false, status: r.status } };
    }
    return { success: true, data: { verified: true } };
  }
}
