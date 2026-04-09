import { Module } from '@nestjs/common';
import { PrismaModule } from './prisma/prisma.module';
import { AuthModule } from './auth/auth.module';
import { CharactersModule } from './characters/characters.module';
import { PurchasesModule } from './purchases/purchases.module';
import { SyncModule } from './sync/sync.module';

@Module({
  imports: [
    PrismaModule,
    AuthModule,
    CharactersModule,
    PurchasesModule,
    SyncModule,
  ],
})
export class AppModule {}
