import { Controller, Get, UseGuards } from '@nestjs/common';
import { CharactersService } from './characters.service';
import { JwtAuthGuard } from '../guards/jwt-auth.guard';
import { CurrentUser } from '../decorators/current-user.decorator';
import { CurrentUserPayload } from '../common/types';

@Controller('characters')
export class CharactersController {
  constructor(private readonly charactersService: CharactersService) {}

  @Get()
  @UseGuards(JwtAuthGuard)
  async findAll(@CurrentUser() user: CurrentUserPayload) {
    const result = await this.charactersService.findAllActive(user);
    return { success: true, data: result };
  }
}
