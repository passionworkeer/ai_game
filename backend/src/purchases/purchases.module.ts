import { Module } from '@nestjs/common';
import { PurchasesService } from './purchases.service';
import { PurchasesController, PurchasesListController } from './purchases.controller';
import { PrismaModule } from '../prisma/prisma.module';

@Module({
  imports: [PrismaModule],
  controllers: [PurchasesController, PurchasesListController],
  providers: [PurchasesService],
  exports: [PurchasesService],
})
export class PurchasesModule {}
