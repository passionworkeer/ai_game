import { IsString, IsNumber, Min, MaxLength, IsOptional, Length, IsIn } from 'class-validator';

/**
 * 手机号登录 DTO
 * POST /auth/phone
 */
export class PhoneLoginDto {
  @IsString()
  @MaxLength(20)
  phone!: string;

  @IsString()
  @Length(6, 6)
  code!: string;  // 6位短信验证码
}

/**
 * Apple 登录 DTO
 * POST /auth/apple
 */
export class AppleLoginDto {
  @IsString()
  @MaxLength(2048)
  identityToken!: string;  // Apple ID JWT token

  @IsString()
  @IsOptional()
  @MaxLength(2048)
  authorizationCode?: string;
}

/**
 * 发送验证码 DTO
 * POST /auth/sms/send
 */
export class SendSmsDto {
  @IsString()
  @MaxLength(20)
  phone!: string;

  @IsString()
  @MaxLength(10)
  captcha!: string;  // 图形验证码防刷
}

/**
 * 账号迁移 DTO
 * POST /auth/merge
 */
export class MergeAccountDto {
  @IsString()
  deviceId!: string;  // 设备匿名账号 deviceId

  @IsString()
  @IsIn(['phone', 'apple'])
  targetType!: 'phone' | 'apple';
}
