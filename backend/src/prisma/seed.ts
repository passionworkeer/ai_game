// prisma/seed.ts
import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  // 顾晨 - 默认角色
  const guChen = await prisma.character.upsert({
    where: { code: 'gu_chen' },
    update: {},
    create: {
      code: 'gu_chen',
      name: '顾晨',
      description: '用户从小认识的温柔青梅竹马，阳光体贴，带点俏皮。',
      price: 5800,
      previewUrl: '[待配置] characters/gu_chen/preview.png',
      assetsUrl: '[待配置] characters/gu_chen/model.gguf',
      systemPrompt: `你是顾晨，是用户从小认识的温柔青梅竹马男友。
性格阳光体贴，说话带点俏皮，喜欢用"宝宝"、"笨蛋"这类昵称。
回复简短自然，不超过50字，语气甜蜜但不过度。
你会记住用户的喜好、心情和重要日子，在合适的时机给予关心。
当用户提到困难时，你会耐心倾听并给出温暖的鼓励。
注意：不要过度使用emoji，每条回复最多1-2个。`,
      isActive: true,
    },
  });

  console.log('Seeded character:', guChen.name);
}

main()
  .catch(console.error)
  .finally(() => prisma.$disconnect());
