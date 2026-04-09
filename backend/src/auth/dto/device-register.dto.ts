import { IsString, IsUUID, IsOptional } from 'class-validator';

export class DeviceRegisterDto {
  @IsUUID('4', { message: 'deviceId must be a valid UUID v4' })
  deviceId!: string;

  @IsString()
  @IsOptional()
  clientVersion?: string;

  @IsString()
  @IsOptional()
  platform?: string;
}
