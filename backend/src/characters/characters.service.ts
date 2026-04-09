import { Injectable } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';
import { CurrentUserPayload } from '../common/types';

export interface CharacterResponse {
  id: string;
  code: string;
  name: string;
  description: string;
  price: number;
  previewUrl: string | null;
  assetsUrl: string;
  isOwned: boolean;
}

@Injectable()
export class CharactersService {
  constructor(private readonly prisma: PrismaService) {}

  async findAllActive(currentUser: CurrentUserPayload): Promise<{
    characters: CharacterResponse[];
  }> {
    // 只返回 isActive=true 的角色
    const characters = await this.prisma.character.findMany({
      where: { isActive: true },
      orderBy: { createdAt: 'asc' },
    });

    // 查询当前用户已购买的角色 ID
    const purchases = await this.prisma.purchase.findMany({
      where: {
        userId: currentUser.userId,
        status: 'completed',
      },
      select: { characterId: true },
    });

    const ownedIds = new Set(purchases.map((p) => p.characterId));

    return {
      characters: characters.map((c) => ({
        id: c.id,
        code: c.code,
        name: c.name,
        description: c.description,
        price: c.price,
        previewUrl: c.previewUrl,
        assetsUrl: c.assetsUrl,
        isOwned: ownedIds.has(c.id),
      })),
    };
  }

  async findById(id: string) {
    return this.prisma.character.findUnique({ where: { id } });
  }
}
