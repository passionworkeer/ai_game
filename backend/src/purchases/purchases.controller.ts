import { Controller, Post, Get, Body, UseGuards, HttpCode, HttpStatus } from '@nestjs/common';
import { PurchasesService } from './purchases.service';
import { VerifyPurchaseDto } from './dto/verify-purchase.dto';
import { JwtAuthGuard } from '../guards/jwt-auth.guard';
import { CurrentUser } from '../decorators/current-user.decorator';
import { CurrentUserPayload } from '../common/types';

@Controller('purchase')
export class PurchasesController {
  constructor(private readonly purchasesService: PurchasesService) {}

  @Post('verify')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async verifyPurchase(
    @Body() dto: VerifyPurchaseDto,
    @CurrentUser() user: CurrentUserPayload,
  ) {
    const result = await this.purchasesService.verifyPurchase(dto, user);
    return { success: true, data: result };
  }
}

@Controller('purchases')
export class PurchasesListController {
  constructor(private readonly purchasesService: PurchasesService) {}

  @Get()
  @UseGuards(JwtAuthGuard)
  async findAll(@CurrentUser() user: CurrentUserPayload) {
    const result = await this.purchasesService.findUserPurchases(user);
    return { success: true, data: result };
  }
}
