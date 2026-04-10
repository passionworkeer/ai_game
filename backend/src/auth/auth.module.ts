import { Module } from '@nestjs/common';
import { JwtModule } from '@nestjs/jwt';
import { PassportModule } from '@nestjs/passport';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { SmsService } from './sms.service';
import { AppleService } from './apple.service';
import { JwtStrategy } from './strategies/jwt.strategy';
import { PrismaModule } from '../prisma/prisma.module';
import { DrmModule } from '../drm/drm.module';

@Module({
  imports: [
    PassportModule,
    JwtModule.register({
      secret: process.env.JWT_SECRET ?? 'dev_secret_change_in_production',
      signOptions: {
        expiresIn: process.env.JWT_EXPIRES_IN ?? '365d',
      },
    }),
    PrismaModule,
    DrmModule,
  ],
  controllers: [AuthController],
  providers: [AuthService, SmsService, AppleService, JwtStrategy],
  exports: [AuthService, SmsService, AppleService],
})
export class AuthModule {}
