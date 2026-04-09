import { Controller, Get, Post, Body, Param, UseGuards, HttpCode, HttpStatus } from '@nestjs/common';
import { SyncService } from './sync.service';
import { SyncProfileDto } from './dto/sync-profile.dto';
import { JwtAuthGuard } from '../guards/jwt-auth.guard';
import { CurrentUser } from '../decorators/current-user.decorator';
import { CurrentUserPayload } from '../common/types';

@Controller('sync')
export class SyncController {
  constructor(private readonly syncService: SyncService) {}

  @Get(':userId')
  @UseGuards(JwtAuthGuard)
  async getProfile(
    @Param('userId') userId: string,
    @CurrentUser() user: CurrentUserPayload,
  ) {
    const result = await this.syncService.getProfile(userId, user);
    return { success: true, data: result };
  }

  @Post(':userId')
  @UseGuards(JwtAuthGuard)
  @HttpCode(HttpStatus.OK)
  async updateProfile(
    @Param('userId') userId: string,
    @Body() dto: SyncProfileDto,
    @CurrentUser() user: CurrentUserPayload,
  ) {
    const result = await this.syncService.updateProfile(userId, dto, user);
    return { success: true, data: result };
  }
}
