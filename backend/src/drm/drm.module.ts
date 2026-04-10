import { Module } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';
import { DrmService } from './drm.service';
import { DrmController } from './drm.controller';
import { PrismaModule } from '../prisma/prisma.module';

@Module({
  imports: [
    PrismaModule,
    JwtModule.register({
      secret: process.env.JWT_SECRET ?? 'dev_secret_change_in_production',
      signOptions: { expiresIn: process.env.JWT_EXPIRES_IN ?? '365d' },
    }),
  ],
  controllers: [DrmController],
  providers: [DrmService],
  exports: [DrmService],
})
export class DrmModule {}
