import { IsString, IsOptional, IsObject } from 'class-validator';

export class SyncProfileDto {
  @IsString()
  @IsOptional()
  nickname?: string;

  @IsObject()
  @IsOptional()
  profileJson?: Record<string, unknown>;
}
