import { Module } from '@nestjs/common';
import { PrismaModule } from './prisma/prisma.module';
import { AuthModule } from './auth/auth.module';
import { CharactersModule } from './characters/characters.module';
import { PurchasesModule } from './purchases/purchases.module';
import { SyncModule } from './sync/sync.module';
import { PaymentModule } from './payment/payment.module';
import { DrmModule } from './drm/drm.module';
import { AdminModule } from './admin/admin.module';

@Module({
  imports: [
    PrismaModule,
    AuthModule,
    CharactersModule,
    PurchasesModule,
    SyncModule,
    PaymentModule,
    DrmModule,
    AdminModule,
  ],
})
export class AppModule {}
