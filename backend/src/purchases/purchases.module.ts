import { Module } from '@nestjs/common';
import { PurchasesService } from './purchases.service';
import { PurchasesController, PurchasesListController } from './purchases.controller';

@Module({
  controllers: [PurchasesController, PurchasesListController],
  providers: [PurchasesService],
  exports: [PurchasesService],
})
export class PurchasesModule {}
