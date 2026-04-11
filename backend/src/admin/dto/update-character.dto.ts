import { CreateCharacterDto } from './create-character.dto';

/**
 * UpdateCharacterDto — 所有字段均为可选
 * 使用 TypeScript 内置 Partial 工具类型
 */
export type UpdateCharacterDto = Partial<CreateCharacterDto>;
