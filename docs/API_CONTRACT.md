# API 合同

> Phase 1 完整 API 规范，后端实现依据

---

## 基本信息

| 字段 | 值 |
|------|------|
| Base URL（测试）| `http://localhost:3000/api/v1` |
| Base URL（生产）| `https://api.aiyougame.com/api/v1` |
| 协议 | REST |
| 认证 | JWT Bearer Token（设备匿名）|
| 编码 | UTF-8 |
| Content-Type | `application/json` |

---

## 认证方式

除 `/auth/device` 外，所有接口在 Header 携带 JWT：

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

JWT Payload 结构：
```json
{
  "sub": "user_uuid",
  "deviceId": "client_generated_uuid_v4",
  "iat": 1719840000,
  "exp": 1751376000
}
```

---

## 统一响应格式

**成功：**
```json
{
  "success": true,
  "data": { ... }
}
```

**失败：**
```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "人类可读错误信息"
  }
}
```

---

## 错误码

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

## 接口清单

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/auth/device` | — | 设备匿名注册 |
| GET | `/characters` | JWT | 角色列表 |
| POST | `/purchase/verify` | JWT | 购买验证（Phase 1 简化）|
| GET | `/purchases` | JWT | 已购角色列表 |
| GET | `/sync/:userId` | JWT | 拉取云端备份 |
| POST | `/sync/:userId` | JWT | 上报本地记忆 |

---

## 接口详情

### POST /auth/device

设备匿名注册，获取 JWT。

**请求：**
```json
{
  "deviceId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "clientVersion": "1.0.0",
  "platform": "android"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| deviceId | string | ✅ | 客户端生成 UUID v4，存 Android Keystore |
| clientVersion | string | ❌ | App 版本号，默认 "1.0.0" |
| platform | string | ✅ | `android` 或 `ios` |

**响应 `200`：**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresAt": 1751376000,
    "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
  }
}
```

---

### GET /characters

获取角色列表（含是否已购买）。

**请求：**
```
GET /characters
Authorization: Bearer <token>
```

**响应 `200`：**
```json
{
  "success": true,
  "data": {
    "characters": [
      {
        "id": "uuid",
        "code": "gu_chen",
        "name": "顾晨",
        "description": "用户从小认识的温柔青梅竹马...",
        "price": 5800,
        "previewUrl": "[待配置] characters/gu_chen/preview.png",
        "isOwned": false
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 角色 UUID |
| code | string | 角色代码，对应资源目录名 |
| name | string | 显示名称 |
| description | string | 展示用描述 |
| price | int | 价格（单位：分，5800=58元）|
| previewUrl | string | 预览图 URL |
| isOwned | boolean | 当前用户是否已购买 |

---

### POST /purchase/verify

购买验证（Phase 1 简化版：只记录状态，不下发 AES 密钥）。

**请求：**
```json
{
  "characterId": "uuid",
  "channel": "alipay",
  "channelOrderId": "202604091234567890",
  "paidAmount": 5800,
  "paidAt": 1719840000000,
  "signature": "base64_sign_data"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| characterId | string | ✅ | 角色 UUID |
| channel | string | ✅ | `wechat` / `alipay` / `apple` |
| channelOrderId | string | ❌ | 渠道订单号 |
| paidAmount | int | ✅ | 支付金额（分），需 ≥ 角色价格 |
| paidAt | long | ✅ | 支付时间戳（毫秒）|
| signature | string | ❌ | 渠道签名（Phase 2 必填）|

**响应 `200`：**
```json
{
  "success": true,
  "data": {
    "purchaseId": "uuid",
    "status": "completed",
    "modelUrl": "[待配置] characters/gu_chen/model.gguf"
  }
}
```

> Phase 1：`modelUrl` 返回明文下载地址。Phase 2 才接入 AES-256 加密。

**409 ALREADY_PURCHASED：**
```json
{
  "success": false,
  "error": { "code": "ALREADY_PURCHASED", "message": "已购买此角色" }
}
```

---

### GET /purchases

查询当前用户已购买的角色。

**响应 `200`：**
```json
{
  "success": true,
  "data": {
    "purchases": [
      {
        "characterId": "uuid",
        "characterCode": "gu_chen",
        "characterName": "顾晨",
        "paidAt": "2026-04-09T12:00:00Z"
      }
    ]
  }
}
```

---

### GET /sync/:userId

拉取云端备份（仅结构化摘要，聊天原文永不上传）。

**响应 `200`：**
```json
{
  "success": true,
  "data": {
    "nickname": "小鱼",
    "profileJson": {
      "likes": ["奶茶", "猫", "漫威电影"],
      "dislikes": ["香菜", "早起"],
      "currentMood": "stressed",
      "importantDates": { "birthday": "0312", "anniversary": "0601" }
    },
    "keyEvents": [
      {
        "summary": "一起去看了漫威新片",
        "timestamp": 1719840000000,
        "category": "activity"
      }
    ],
    "updatedAt": 1719840000000
  }
}
```

> ⚠️ 注意：云端不存储聊天原文，只存结构化 profile。

---

### POST /sync/:userId

上报本地记忆摘要（用户手动授权才触发）。

**请求：**
```json
{
  "nickname": "小鱼",
  "profileJson": {
    "likes": ["奶茶", "猫"],
    "dislikes": ["香菜"],
    "currentMood": "neutral",
    "importantDates": { "birthday": "0312" }
  }
}
```

**响应 `200`：**
```json
{
  "success": true,
  "data": {
    "updatedAt": 1719840000000
  }
}
```

---

## 环境变量

```bash
DATABASE_URL="postgresql://postgres:password@localhost:5432/aiyougame_dev"
JWT_SECRET="replace_with_minimum_32_char_secret"   # 生产环境至少 32 字符
JWT_EXPIRES_IN="365d"
PORT=3000
NODE_ENV="development"
```

---

## 本地开发

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
