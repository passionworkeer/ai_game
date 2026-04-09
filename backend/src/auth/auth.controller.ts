import { Controller, Post, Body, HttpCode, HttpStatus } from '@nestjs/common';
import { AuthService } from './auth.service';
import { DeviceRegisterDto } from './dto/device-register.dto';

@Controller('auth')
export class AuthController {
  constructor(private readonly authService: AuthService) {}

  @Post('device')
  @HttpCode(HttpStatus.OK)
  async deviceRegister(@Body() dto: DeviceRegisterDto) {
    const result = await this.authService.deviceRegister(dto);
    return { success: true, data: result };
  }
}
