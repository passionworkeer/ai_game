import { IsString, IsNumber, IsIn, Min, IsOptional, MaxLength } from 'class-validator';

export class WechatPrecreateDto {
  @IsString()
  @MaxLength(64)
  outTradeNo!: string;

  @IsNumber()
  @Min(1)
  amount!: number; // in fen

  @IsString()
  @MaxLength(256)
  description!: string;

  @IsString()
  userId!: string;

  @IsString()
  @IsOptional()
  openid?: string;
}

export class AlipayPrecreateDto {
  @IsString()
  @MaxLength(64)
  outTradeNo!: string;

  @IsNumber()
  @Min(0.01)
  totalAmount!: number;

  @IsString()
  @MaxLength(256)
  subject!: string;

  @IsString()
  userId!: string;
}

export class AppPayDto {
  @IsString()
  @MaxLength(64)
  outTradeNo!: string;

  @IsNumber()
  @Min(0.01)
  totalAmount!: number;

  @IsString()
  @MaxLength(256)
  subject!: string;
}

export class VerifyChannelDto {
  @IsString()
  @IsIn(['wechat', 'alipay'])
  channel!: string;

  @IsString()
  @MaxLength(128)
  channelOrderId!: string;
}
