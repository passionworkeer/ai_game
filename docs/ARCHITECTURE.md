# 完整架构图

> 版本 v2.0 | 重建自 v2.2 清理 | 反映 Phase 0 状态

---

## 一、系统架构图

```
┌──────────────────────────────────────────────────────────────────────┐
│                        用户手机（Android）                             │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │                     Android App（Kotlin）                     │  │
│  │                                                              │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │  │
│  │  │  Chat UI │  │  Profile │  │ Settings │  │ Purchase │  │  │
│  │  │(Compose) │  │  Screen  │  │  Screen  │  │  Screen  │  │  │
│  │  └─────┬────┘  └──────────┘  └────┬────┘  └────┬────┘  │  │
│  │        │                          │                   │  │  │
│  │  ┌─────▼──────────────────────────▼──────────────────┐ │  │
│  │  │              ui/ ChatViewModel                    │ │  │
│  │  │         (MVI StateFlow, ChatUiState Machine)      │ │  │
│  │  └───────────────────────┬────────────────────────────┘ │  │
│  │                           │                              │  │
│  │  ┌───────────────────────▼────────────────────────────┐ │  │
│  │  │            llm/ LlamaScheduler                      │ │  │
│  │  │   (Prompt 构建 + Token 流解析 + GBNF 约束)         │ │  │
│  │  └───────┬───────────────────────────────┬────────────┘ │  │
│  │          │                               │              │  │
│  │  ┌──────▼──────┐              ┌──────────▼──────────┐   │  │
│  │  │ StreamParser│              │  agent/ AgentRouter │   │  │
│  │  │(Token 分流) │              │ (mDNS + WebSocket)  │   │  │
│  │  └─────────────┘              └──────────┬──────────┘   │  │
│  │                                            │              │  │
│  │  ┌───────────────────────────────────────▼──────────┐   │  │
│  │  │          engine/ LlamaEngine                     │   │  │
│  │  │   (llama-android AAR → GGUF → Token Callback)   │   │  │
│  │  └──────────────────────────────────────────────────┘   │  │
│  │                                                              │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │  │
│  │  │ memory/      │  │ agent/       │  │ network/     │   │  │
│  │  │ MemoryManager│  │ PcDiscovery  │  │ ApiService   │   │  │
│  │  │ ProfileExtract│ │              │  │ Retrofit+OkHttp│  │  │
│  │  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘   │  │
│  │         │                  │                 │            │  │
│  │  ┌──────▼──────────────────▼─────────────────▼───────┐   │  │
│  │  │              Room SQLite / DataStore               │   │  │
│  │  │  ChatMessage │ UserProfile │ KeyEvent │  Prefs    │   │  │
│  │  └────────────────────────────────────────────────────┘   │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐  │
│  │  CDN 下载 → files/models/  (App 首次启动下载，不打入 APK)      │  │
│  │  模型地址：[待配置]                                  │  │
│  │              gemma-4-E4B-it-Q4_0.gguf`                        │  │
│  └──────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
         │                                    │ WebSocket（局域网直连）
         │ REST API                           ▼
         │                           ┌─────────────────┐
         ▼                           │   PC (OpenClaw)  │
┌─────────────────────┐              │                   │
│   后端（NestJS）    │              │ mDNS: _aiyougame  │
│                     │              │ WebSocket :8080    │
│ POST /auth/device   │              │ 任务执行引擎       │
│ GET /characters     │              └───────────────────┘
│ POST /purchase/verify│
│ GET /purchases      │
│ GET/POST /sync/:uid  │
└─────────┬───────────┘
          │
          ▼
┌─────────────────────┐
│  PostgreSQL 16     │
│  User │ Purchase   │
│  Character         │
└─────────────────────┘
```

---

## 二、模块依赖关系（有向无环图）

```
assets/
  GGUF (4.6GB)
      │
      │ LlamaEngine.initEngine()
      ▼
engine/LlamaEngine.kt (接口，llama-android AAR 实现)
      │
      │ TokenCallback.onToken(token)
      ▼
llm/LlamaScheduler.kt
      │
      ├─► StreamParser.parse(token)  ──► AgentContract
      │                                      │
      │                                      ├─► AgentRouter.route() ──► WebSocket ──► PC
      │                                      │
      │                                      └─► UI: ToolCallCard
      │
      ├─► PromptManager.buildPrompt() ──► memory/MemoryManager
      │                                      │
      │                                      ├─► UserProfile (中期记忆)
      │                                      ├─► KeyEvent (长期记忆)
      │                                      └─► ChatMessage (对话历史)
      │
      └─► StateFlow ──► ui/ChatViewModel
                          │
                          ├─► ChatScreen (Compose)
                          └─► typingText (打字机效果)
```

**依赖规则：**
- 箭头方向 = 依赖方向
- `ui/` 不能直接调用 `engine/`，必须通过 `llm/` 中转
- `llm/` 不能直接操作 `Room`，必须通过 `memory/` 中转

---

## 三、消息完整生命周期

```
用户输入：「宝宝帮我找一下财务报表」
│
├─ 1. ChatViewModel.sendMessage()
│       ↓
├─ 2. MemoryManager.getMemorySnapshot()
│       ├─ 读取 UserProfile（昵称/好感度/喜好）
│       ├─ 读取 KeyEvent（近期事件）
│       └─ 返回「昵称：小鱼，喜好：奶茶，猫...」
│       ↓
├─ 3. PromptManager.buildPrompt()
│       ├─ 加载 System Prompt（assets/prompts/gu_chen.txt）
│       ├─ 注入记忆快照 → {MEMORY_SNAPSHOT}
│       ├─ 从 GGUF metadata 读取 tokenizer.chat_template（ERR-02 禁止硬编码）
│       └─ 返回完整 Prompt
│       ↓
├─ 4. LlamaEngine.predictStream()
│       ├─ llama-android AAR → C++ llama_decode()
│       ├─ 每个 Token 通过 callback 回调 Kotlin
│       └─ SharedFlow.emit() 推送
│       ↓
├─ 5. StreamParser.parse()
│       ├─ 普通文字（不以 { 开头）→ TextChunk → ChatBubble 渲染
│       └─ JSON 开始（{ 开头）→ 缓冲（GBNF 校验）
│              ↓
│       ├─ GBNF 校验通过 → AgentContract
│       └─ GBNF 校验失败 → 丢弃 buffer，重置为普通文本
│       ↓
├─ 6. LlamaScheduler.handleToolCall()
│       ├─ UI State → Thinking（ToolCallCard 显示）
│       ├─ AgentRouter.route(contract)
│       │     ├─ PcDiscoveryManager.discover()（mDNS，5s 超时）
│       │     └─ WebSocket 发送任务到 OpenClaw PC
│       ├─ PC 执行 → 返回 TaskResult
│       └─ 包装成角色口吻 → UI 渲染
│       ↓
├─ 7. MemoryManager.processAfterMessage()
│       ├─ ChatMessageDao.insert()（对话入库）
│       ├─ ProfileExtractor.extract()（< 5ms，规则提取）
│       │     ├─ 昵称/喜好/讨厌/心情 → UserProfile.upsert()
│       │     └─ 重要事件 → KeyEvent.insert()
│       └─ UserProfileDao.adjustAffection(+1)
│       ↓
└─ 8. UI 更新
        ├─ 角色回复气泡（打字机动画）
        ├─ 好感度动画（+1 浮动数字）
        └─ Emoji 表情驱动动画
```

---

## 四、ChatUiState 状态机

```
                  ┌──────────┐
                  │   Idle   │  ← 等待用户输入
                  └───┬──────┘
                      │ sendMessage()
                      ▼
              ┌───────────────┐
              │    Loading   │  ← 正在生成（打字机效果）
              └───────┬───────┘
                      │ onToken()
          ┌───────────┼───────────┐
          │                       │
    普通文字                    JSON 开始 {
    (TextChunk)                  ▼
          │           ┌──────────────────┐
          │           │     Thinking      │  ← 工具执行中
          │           │   (ToolCallCard)   │
          │           └─────────┬──────────┘
          │                     │ ToolCall 完成
          │                     ▼
          │              ┌──────────────────┐
          │              │    Complete      │  ← 回复完成
          │              └─────────┬────────┘
          │                        │
          └─────── (累积) ◄────────┘
                      │ clear()
                      ▼
                  ┌──────────┐
                  │   Idle   │
                  └──────────┘

    onError() ──► ┌──────────┐
                  │   Error   │
                  └────┬─────┘
                       │ dismiss
                       ▼
                  ┌──────────┐
                  │   Idle   │
                  └──────────┘
```

---

## 五、三层记忆架构

```
Layer 1：上下文窗口（ContextWindow）
─────────────────────────────────────
存储：内存（messageHistory[]）
容量：最近 20 条对话原文
淘汰：tokenCount > maxTokens * 0.8 → 截断最早消息
注入：每次 Prompt 构建时拼接

        ↓ 每次对话结束

Layer 2：中期记忆（UserProfile）
─────────────────────────────────────
存储：Room SQLite → user_profile 表
内容：nickname / likes[] / dislikes[] / currentMood / affectionScore
更新：ProfileExtractor 规则引擎（< 5ms/条）
触发：每条用户消息后

        ↓ 标记重要事件

Layer 3：长期记忆（KeyEvent）
─────────────────────────────────────
存储：Room SQLite → key_events 表
内容：「周日一起看了漫威电影」等高光事件
触发：事件正则匹配（考试/生日/约定/活动）
Phase 2：BGE-micro 向量化 + sqlite-vec KNN 检索

        ↓ PromptManager 需要时查询

    所有三层 → 注入 System Prompt → {MEMORY_SNAPSHOT}
```

---

## 六、WebSocket 双轨架构

```
模型输出 JSON
    │
├─► StreamParser 拦截
│       │
│       └─► AgentContract { taskType: "file_search" }
│               │
│               └─► AgentRouter.route()
│                       │
│       ┌───────────────┼───────────────┐
│       │                               │
│   local_pc                        cloud_api
│   (OpenClaw PC)                 (Phase 2 扩展)
│       │
│   mDNS 发现
│   ws://192.168.x.x:8080/ws
│       │
│   WebSocket 长连接
│       │
│   任务下发 → PC 执行 → TaskResult
│       │
│       └─► 角色口吻包装 → UI 显示
```

---

## 七、数据存储分布

```
数据                        存储位置         是否上云     同步方式
─────────────────────────────────────────────────────────────
聊天原文（ChatMessage）      手机 Room       ❌ 永不上云    —
好感度（affectionScore）   手机 Room       ❌ 永不上云    —
用户画像（UserProfile）     手机 Room       ✅ 可选同步    用户手动+授权
角色购买记录（Purchase）     后端 PostgreSQL ✅ 必须上传    支付成功时
云端备份（profileJson）     后端 PostgreSQL ✅ 用户授权    用户手动+授权
GGUF 模型文件               App 私有目录/CDN   ❌ 永不上传    App 首次启动下载到 `files/models/`
System Prompt               手机 assets/     ❌ 永不上传    —
角色资源（预览图/模型）      CDN/后端        ✅ 下载安装    用户购买后
```

---

## 八、后端模块架构

```
backend/src/
├── prisma/
│   ├── prisma.service.ts    # Prisma 客户端单例
│   ├── schema.prisma         # User / Character / Purchase
│   └── seed.ts               # 顾晨角色种子数据
│
├── auth/
│   ├── auth.controller.ts    # POST /auth/device
│   ├── auth.service.ts       # JWT 签发
│   ├── auth.module.ts
│   └── dto/
│       └── device-register.dto.ts  # class-validator 校验
│
├── characters/
│   ├── characters.controller.ts   # GET /characters
│   ├── characters.service.ts
│   └── characters.module.ts
│
├── purchases/
│   ├── purchases.controller.ts     # POST /purchase/verify, GET /purchases
│   ├── purchases.service.ts        # 幂等性校验
│   └── purchases.module.ts
│
├── sync/
│   ├── sync.controller.ts         # GET/POST /sync/:userId
│   ├── sync.service.ts
│   └── sync.module.ts
│
├── guards/
│   └── jwt-auth.guard.ts          # JWT Bearer 校验
│
├── decorators/
│   └── current-user.decorator.ts  # @CurrentUser()
│
└── common/
    ├── types.ts                   # 统一响应格式
    └── filters/
        └── http-exception.filter.ts
```

---

## 九、Phase 0 vs Phase 1 架构边界

```
Phase 0（当前）                          Phase 1 交付后
─────────────────────────────────────────────────────────
Android App                               Android App ✅
  ├─ engine/LlamaEngine.kt ← [待建]        ├─ engine/LlamaEngine.kt ✅
  ├─ llm/LlamaScheduler.kt ← [待建]        ├─ llm/LlamaScheduler.kt ✅
  ├─ agent/AgentRouter.kt ← [待建]         ├─ agent/AgentRouter.kt ✅
  └─ memory/MemoryManager.kt ← [待建]      └─ memory/MemoryManager.kt ✅

后端 API（部分保留）                      后端 API ✅
  └─ src/prisma/schema.prisma ← [已就绪]     ├─ auth ✅
                                           ├─ characters ✅
                                           ├─ purchases ✅
                                           └─ sync ✅

OpenClaw PC 联动 ← [OpenClaw项目提供]      OpenClaw PC 联动 ✅

GGUF 模型 ← [已就绪 4.6GB]                 GGUF 模型 ✅
```
