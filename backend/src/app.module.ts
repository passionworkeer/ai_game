import { Module } from '@nestjs/common';
import { PrismaService } from './prisma/prisma.service';
import { AuthModule } from './auth/auth.module';
import { CharactersModule } from './characters/characters.module';
import { PurchasesModule } from './purchases/purchases.module';
import { SyncModule } from './sync/sync.module';

@Module({
  imports: [
    AuthModule,
    CharactersModule,
    PurchasesModule,
    SyncModule,
  ],
  providers: [PrismaService],
})
export class AppModule {}
