# 后端开发文档

> Phase 1 实施指南 | 版本 v2.0 | 合并自 AGENT_BACKEND.md

---

## 一、排期总览

| 指标 | 数据 |
|------|------|
| 总工期 | **8 周**（与 Android Phase 1 并行）|
| 技术栈 | NestJS + TypeScript + Prisma + PostgreSQL |
| 交付物 | 可上线后端 API + Docker 部署配置 |

---

## 二、8 周详细计划

### Week 1–2：骨架 + 数据库

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 1–2 | NestJS 项目初始化 | `npm run start:dev` 成功 |
| Day 3 | Prisma + PostgreSQL 连接 | `prisma db push` 成功 |
| Day 3–4 | Prisma Schema（User/Character/Purchase）| 3 张表 |
| Day 5 | JWT 模块封装 | 签发 + 验证 |
| Day 6–7 | seed 数据（顾晨角色）| 可查询 |
| Day 8 | DTO 验证（class-validator）| 无效请求返回 400 |
| Day 10 | Docker Compose PostgreSQL | `docker-compose up` 成功 |

> **Checkpoint B1**：后端骨架完成。

---

### Week 3–4：核心 API

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 11–12 | `POST /auth/device` | UUID v4 注册，返回 JWT |
| Day 13 | JWT Guard | 无 Token 返回 401 |
| Day 13–14 | `GET /characters` | 返回角色列表 |
| Day 16–17 | `POST /purchase/verify`（简化）| 记录状态，不下发密钥 |
| Day 18 | 幂等性（ALREADY_PURCHASED）| 重复购买返回 409 |
| Day 19 | `GET /purchases` | 返回已购角色 |

> **Checkpoint B2**：Auth + Characters + Purchase 完成。

---

### Week 5–6：云同步 + 部署

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 21–22 | `GET /sync/:userId` | 返回 profileJson |
| Day 23–24 | `POST /sync/:userId` | 上报摘要，更新 User 表 |
| Day 27 | Dockerfile | 镜像 < 500MB |
| Day 28 | Nginx 反向代理 | HTTP → HTTPS |
| Day 29 | CI/CD GitHub Actions | main 分支自动部署 |

> **Checkpoint B3**：云同步 + 部署完成。

---

### Week 7–8：联调 + 压测

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 31–32 | Android × 后端联调 | 购买/同步/Auth 全链路 |
| Day 33 | OpenClaw 协议文档确认 | 配合 Android |
| Day 35–36 | 压力测试（k6）| 100 并发 P99 < 500ms |
| Day 37 | 安全审计 | 无 SQL 注入/XSS/越权 |
| Day 39–40 | 生产环境 PostgreSQL | RDS 或 Docker |

> **Checkpoint B4**：后端 Phase 1 交付。

---

## 三、Prisma Schema

```prisma
// backend/src/prisma/schema.prisma

generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

// 用户（设备匿名）
model User {
  id           String    @id @default(uuid())
  deviceId     String    @unique  // 客户端生成 UUID v4
  nickname     String    @default("宝宝")
  profileJson  String    @default("{}")  // 云端备份（不含聊天原文）
  purchases    Purchase[]
  @@map("users")
}

// 角色
model Character {
  id           String    @id @default(uuid())
  code         String    @unique          // "gu_chen"
  name         String                          // "顾晨"
  description  String
  price        Int                             // 分（5800 = 58元）
  previewUrl   String?
  assetsUrl    String                          // 模型下载地址
  systemPrompt String                          // System Prompt 全文
  purchases    Purchase[]
  @@map("characters")
}

// 购买记录
// Phase 1：只记录状态，不下发 AES 密钥
// Phase 2：才接入 AES-256 密钥下发
model Purchase {
  id              String   @id @default(uuid())
  userId          String
  characterId     String
  channel         String                      // wechat | alipay | apple
  channelOrderId  String?
  amount          Int
  status          String   @default("pending") // pending | completed | refunded
  paidAt          DateTime?
  encryptedAesKey String?   @db.Text           // Phase 2 才填值
  user            User     @relation(fields: [userId], references: [id])
  character       Character @relation(fields: [characterId], references: [id])
  @@unique([userId, characterId])
  @@map("purchases")
}
```

---

## 四、模块划分

```
src/
├── auth/              # 设备匿名注册 + JWT
│   ├── auth.controller.ts
│   ├── auth.service.ts
│   ├── auth.module.ts
│   └── dto/
│       └── device-register.dto.ts
├── characters/        # 角色列表查询
│   ├── characters.controller.ts
│   ├── characters.service.ts
│   └── characters.module.ts
├── purchases/         # 购买验证 + 已购查询
│   ├── purchases.controller.ts
│   ├── purchases.service.ts
│   └── purchases.module.ts
├── sync/              # 云端记忆同步
│   ├── sync.controller.ts
│   ├── sync.service.ts
│   └── sync.module.ts
├── prisma/           # Schema + seed
│   ├── prisma.service.ts
│   ├── schema.prisma
│   └── seed.ts
├── guards/           # JwtAuthGuard
│   └── jwt-auth.guard.ts
├── decorators/       # @CurrentUser()
│   └── current-user.decorator.ts
└── common/          # 统一响应格式 + 异常过滤器
    ├── types.ts
    └── filters/
        └── http-exception.filter.ts
```

---

## 五、三条红线（不可违反）

### 红线 1：禁止日志泄露敏感信息

```typescript
// ❌ 禁止
logger.log(`deviceId=${deviceId}, token=${token}`);

// ✅ 正确
logger.log(`Device registered: ${deviceId.substring(0, 8)}...`);
```

### 红线 2：禁止未校验 JWT 就操作用户数据

```typescript
// ❌ 禁止
async getPurchases(userId: string) {
  return this.prisma.purchase.findMany({ where: { userId } });
}

// ✅ 正确：必须通过 @CurrentUser() 从 JWT 提取 userId
async getPurchases(@CurrentUser() user: UserPayload) {
  return this.prisma.purchase.findMany({ where: { userId: user.userId } });
}
```

### 红线 3：购买验证必须校验签名

```typescript
// ❌ 禁止：直接信任客户端数据
async verifyPurchase(dto: PurchaseVerifyDto) {
  await this.prisma.purchase.create({ data: dto });
}

// ✅ 正确：Phase 1 简化，Phase 2 接入真实验签
async verifyPurchase(dto: PurchaseVerifyDto) {
  const valid = await this.verifyChannelSignature(dto);
  if (!valid) throw new ForbiddenException('PURCHASE_VERIFY_FAILED');
}
```

---

## 六、统一响应格式

```typescript
// 成功
{ "success": true, "data": { ... } }

// 失败
{ "success": false, "error": { "code": "INVALID_DEVICE_ID", "message": "..." } }
```

---

## 七、错误码规范

| code | HTTP | 说明 |
|------|------|------|
| INVALID_DEVICE_ID | 400 | 设备 ID 格式错误（需 UUID v4）|
| VALIDATION_ERROR | 400 | 请求参数校验失败 |
| UNAUTHORIZED | 401 | JWT 无效或过期 |
| CHARACTER_NOT_FOUND | 404 | 角色不存在 |
| ALREADY_PURCHASED | 409 | 已购买，不能重复购买 |
| PURCHASE_VERIFY_FAILED | 422 | 支付验证失败（金额不足/签名错误）|
| USER_NOT_FOUND | 404 | 用户不存在 |
| INTERNAL_ERROR | 500 | 服务器内部错误 |

---

## 八、环境变量

```bash
DATABASE_URL="postgresql://postgres:password@localhost:5432/aiyougame_dev"
JWT_SECRET="replace_with_minimum_32_char_secret"
JWT_EXPIRES_IN="365d"
PORT=3000
NODE_ENV="development"
```

---

## 九、本地开发

```bash
# 1. 启动 PostgreSQL（Docker）
docker run --name aiyougame-db \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=aiyougame_dev \
  -p 5432:5432 \
  -d postgres:16

# 2. 安装依赖
cd backend && npm install

# 3. 初始化数据库
npx prisma db push

# 4. 种子数据
npx prisma db seed   # 创建"顾晨"角色

# 5. 启动
npm run start:dev

# 验证
curl -X POST http://localhost:3000/api/v1/auth/device \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"test-uuid-1234","clientVersion":"1.0.0","platform":"android"}'
```
