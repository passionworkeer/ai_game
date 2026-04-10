import { Module } from '@nestjs/common';
import { PaymentController } from './payment.controller';
import { PaymentNotifyController } from './payment-notify.controller';
import { WechatService } from './wechat.service';
import { AlipayService } from './alipay.service';
import { PurchasesModule } from '../purchases/purchases.module';
import { PrismaModule } from '../prisma/prisma.module';
import { AuthModule } from '../auth/auth.module';

@Module({
  imports: [PurchasesModule, PrismaModule, AuthModule],
  controllers: [PaymentController, PaymentNotifyController],
  providers: [WechatService, AlipayService],
})
export class PaymentModule {}
