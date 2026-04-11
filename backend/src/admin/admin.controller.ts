import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  UseGuards,
  HttpCode,
} from '@nestjs/common';
import { AdminService } from './admin.service';
import { JwtAuthGuard } from '../guards/jwt-auth.guard';
import { AdminGuard } from './guards/admin.guard';
import { CreateCharacterDto } from './dto/create-character.dto';
import { UpdateCharacterDto } from './dto/update-character.dto';

@Controller('admin')
@UseGuards(JwtAuthGuard, AdminGuard)
export class AdminController {
  constructor(private readonly adminService: AdminService) {}

  @Get('characters')
  async listCharacters() {
    const characters = await this.adminService.listCharacters();
    return { success: true, data: characters };
  }

  @Post('characters')
  async createCharacter(@Body() dto: CreateCharacterDto) {
    const character = await this.adminService.createCharacter(dto);
    return { success: true, data: character };
  }

  @Put('characters/:id')
  async updateCharacter(@Param('id') id: string, @Body() dto: UpdateCharacterDto) {
    const character = await this.adminService.updateCharacter(id, dto);
    return { success: true, data: character };
  }

  @Delete('characters/:id')
  async deleteCharacter(@Param('id') id: string) {
    const result = await this.adminService.deleteCharacter(id);
    return { success: true, data: result };
  }

  @Get('stats')
  async getStats() {
    const stats = await this.adminService.getStats();
    return { success: true, data: stats };
  }

  @Post('devices/:deviceId/ban')
  @HttpCode(200)
  async banDevice(@Param('deviceId') deviceId: string) {
    const result = await this.adminService.banDevice(deviceId);
    return { success: true, data: result };
  }

  @Post('devices/:deviceId/unban')
  @HttpCode(200)
  async unbanDevice(@Param('deviceId') deviceId: string) {
    const result = await this.adminService.unbanDevice(deviceId);
    return { success: true, data: result };
  }

  @Get('devices')
  async listDevices(@Query('page') page?: string, @Query('limit') limit?: string) {
    const p = page ? parseInt(page, 10) : 1;
    const l = limit ? parseInt(limit, 10) : 20;
    const result = await this.adminService.listDevices(p, l);
    return { success: true, data: result };
  }
}
