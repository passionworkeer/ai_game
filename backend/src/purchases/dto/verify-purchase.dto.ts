import {
  IsString,
  IsUUID,
  IsNumber,
  IsOptional,
  Min,
} from 'class-validator';

export class VerifyPurchaseDto {
  @IsUUID('4', { message: 'characterId must be a valid UUID v4' })
  characterId!: string;

  @IsString()
  channel!: string; // alipay | wechat | apple

  @IsString()
  @IsOptional()
  channelOrderId?: string;

  @IsNumber()
  @Min(0)
  paidAmount!: number; // 单位：分

  @IsNumber()
  paidAt!: number; // unix timestamp ms

  // Phase 1 简化：signature 不校验，但留好扩展点
  @IsString()
  @IsOptional()
  signature?: string;
}
