# Android 开发文档

> Phase 1 实施指南 | 版本 v2.3 | 重建自 GitHub 核实

---

## 一、排期总览

| 指标 | 数据 |
|------|------|
| 总工期 | **14 周** |
| 交付物 | 可演示 APK + Demo 视频 |
| 核心验收 | AI 对话流畅 + 记忆记住用户 + OpenClaw 联动 |

---

## 二、14 周详细计划

### Week 1–2：项目骨架 + 模型验证

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 1–2 | Android 项目初始化（Gradle + Compose + Hilt + Room）| `./gradlew assembleDebug` 成功 |
| Day 3 | llama.cpp CMake 构建配置 | CMake Build 成功 |
| Day 4 | 模型下载器接入 | App 首次启动从 CDN 下载 GGUF，存入 `files/models/` |
| Day 5 | llama-cli 测试（Windows WSL2）| token 输出正常 |
| Day 6 | AAR 接口接入 LlamaEngine.kt | 流式输出 |
| Day 7 | Tokenizer + Chat Template 动态读取 | 从 GGUF metadata，**禁止硬编码** |
| Day 8 | System Prompt 注入（顾晨人设）| 无越狱，assets/prompts/gu_chen.txt |
| Day 9 | 流式打字机效果 | 每 Token 实时渲染 |

> **Checkpoint 1**：项目可编译，模型推理正常。

---

### Week 3–4：LLM Core

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 10 | llama.cpp CMake JNI 封装（LlamaEngineImpl）| 流式输出 |
| Day 11 | Tokenizer + Chat Template 动态读取 | 从 GGUF metadata，**禁止硬编码** |
| Day 12 | Prompt 模板注入（人设"顾晨"）| 无越狱 |
| Day 13 | 流式打字机效果 | 每 Token 实时渲染 |
| Day 14 | MemoryManager + 三层记忆注入 | Prompt 含记忆 |
| Day 15 | 内存管理双保险 | `onCleared` + `onTrimMemory` |

> **Checkpoint 2**：LLM Core 流式推理，内存 < 2.5GB。

---

### Week 5–6：三层记忆系统

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 21 | Room 3 张表（ChatMessage/Profile/KeyEvent）| Entity + DAO |
| Day 23 | ProfileExtractor 规则引擎 | < 5ms/条 |
| Day 25 | 上下文窗口维护（20 条截断）| AI 能读上轮对话 |
| Day 28 | 连续 10 轮对话验证记忆 | AI 记住关键信息 |

> **Checkpoint 3**：记忆系统完整。

---

### Week 7–8：聊天 UI

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 33–34 | ChatScreen + LazyColumn | 用户右对齐/AI 左对齐 |
| Day 35 | 打字机效果 | 无闪烁 |
| Day 36 | TypingIndicator 动画 | 三点跳动 |
| Day 37 | 好感度心形动画 | +N 浮动数字 |
| Day 38 | ToolCallCard | 进度动画 |
| Day 41 | ProfileScreen | 立绘 + 好感度 |
| Day 42 | 设置页（mDNS 降级入口）| 手动 IP 可用 |

> **Checkpoint 4**：UI 全部完成，60fps。

---

### Week 9–10：OpenClaw 对接 + Agent

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 45–46 | GBNF grammar 定义 + 加载 | JSON 错误率 < 0.1% |
| Day 47 | StreamParser 意图拦截 | 实时分流 |
| Day 48–49 | PcDiscoveryManager（mDNS）| 发现成功率 ≥ 95% |
| Day 50 | WebSocket 心跳 + 断线重连 | 30s 自动重连 |
| Day 51 | AgentRouter 双轨路由 | PC 路由正确 |
| Day 54 | OpenClaw 对接联调 | PC → App 结果 |

> **Checkpoint 5**：OpenClaw PC 联动可用。

---

### Week 11–12：后端对接 + 集成

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 57–58 | 后端 Auth 模块对接 | JWT 存储 |
| Day 59–60 | 角色列表 + 购买对接 | 展示 + 购买 |
| Day 61 | 云同步上/下行 | 记忆同步正常 |
| Day 62–63 | 全链路集成测试 | 端到端无报错 |
| Day 64 | 性能验收（9 项指标）| 全部达标 |
| Day 65 | 抓包验证零上传 | 仅购买/同步上传 |

> **Checkpoint 6**：Phase 1 功能全部可演示。

---

### Week 13–14：Buffer + Demo

| 日期 | 任务 |
|------|------|
| Day 68–69 | Bug 修复 + 内存优化 |
| Day 72 | Phase 2 调研（Whisper / LoRA / DRM）|
| Day 74 | Demo 录制（3 分钟）|
| Day 77 | **Phase 1 正式交付** |

> **Checkpoint 7**：Phase 1 交付。

---

## 三、层内架构

### 包结构

```
com.aiyougame/companion/
├── ui/
│   ├── chat/
│   │   ├── ChatScreen.kt       # 聊天主页
│   │   ├── ChatViewModel.kt   # 状态管理
│   │   └── components/         # ChatBubble / TypingIndicator / ToolCallCard
│   ├── profile/
│   │   └── ProfileScreen.kt   # 立绘 + 好感度
│   └── settings/
│       └── SettingsScreen.kt  # mDNS 降级入口
├── engine/
│   ├── LlamaEngine.kt         # 抽象接口（ERR-02：禁止硬编码 Prompt）
│   └── LlamaEngineImpl.kt     # llama.cpp CMake 构建实现（见 docs/MODEL.md 方案 A）
├── llm/
│   └── LlamaScheduler.kt      # Prompt 构建 + Token 流解析
├── agent/
│   ├── StreamParser.kt        # 意图拦截 + JSON 解析（GBNF）
│   ├── AgentContract.kt       # JSON 数据类
│   ├── AgentRouter.kt         # 双轨路由
│   └── PcDiscoveryManager.kt  # mDNS 服务发现
├── memory/
│   ├── MemoryManager.kt       # 统一入口
│   ├── ProfileExtractor.kt    # 规则引擎（<5ms/条）
│   └── db/                    # Room Entity + DAO
├── network/
│   └── ApiService.kt          # Retrofit DTO + API 接口
├── di/
│   └── AppModule.kt           # Hilt 模块
└── AigameApplication.kt       # onTrimMemory + freeEngine
```

### 核心接口

```kotlin
// LlamaEngine（engine/）
// 实现：llama.cpp CMake 构建 + JNI 封装（详见 docs/MODEL.md 方案 A）
interface LlamaEngine {
    fun initEngine(modelPath: String, systemPrompt: String): Long
    fun predictStream(ptr: Long, userMessage: String, callback: TokenCallback)
    fun freeEngine(ptr: Long)
    fun getChatTemplate(): String  // ERR-02：动态读取，禁止硬编码
}

interface TokenCallback {
    fun onToken(token: String)    // 每个 Token 触发
    fun onComplete(fullText: String)
    fun onError(error: String)
}

// MemoryManager（memory/）
class MemoryManager {
    suspend fun processAfterMessage(userMsg: String, modelMsg: String)
    fun buildSystemPrompt(profile: UserProfile, events: List<KeyEvent>): String
}

// StreamParser（agent/）
sealed class ParseResult {
    data class TextChunk(val token: String)  // 渲染到气泡
    data class ToolCall(val contract: AgentContract)  // 触发 Agent
    data object Buffering                            // 继续缓冲
}
```

### 状态流

```
用户输入 → ChatViewModel.sendMessage()
    ├─ MemoryManager.buildSystemPrompt() → LlamaScheduler
    ├─ LlamaEngine.predictStream()
    │     ├─ TokenCallback.onToken() → StreamParser.parse()
    │     │     ├─ TextChunk → ChatUiState.Generating() → UI
    │     │     └─ ToolCall → AgentRouter.route() → ToolCallCard
    │     └─ TokenCallback.onComplete() → MemoryManager.processAfterMessage() → DB
    └─ onCleared() → LlamaEngine.freeEngine()
```

### ChatUiState 状态机

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
```

---

## 四、三层记忆架构

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

## 五、线程约束（红线）

| 操作 | 线程 | 代码写法 |
|------|------|---------|
| `initEngine` | `Dispatchers.IO` | `withContext(Dispatchers.IO) { engine.init(...) }` |
| `predictStream` 回调 | C++ 子线程回调 | `StateFlow.emit()` 自动切主线程 |
| `freeEngine` | `Dispatchers.IO` | `withContext(Dispatchers.IO) { engine.free(...) }` |
| Room 读写 | `Dispatchers.IO` | `@Query` + `suspend` + `withContext(Dispatchers.IO)` |
| 网络请求 | `Dispatchers.IO` | Retrofit suspend |
| WebSocket | `Dispatchers.IO` | OkHttp Dispatcher |
| **绝对禁止** | Main Thread | 禁止 `Thread.sleep`、`while(true)`、同步 DB |

---

## 六、性能验收标准

| 指标 | 目标 | 测试设备 |
|------|------|---------|
| App 冷启动 | ≤ 2s | 中端机 |
| 模型加载 | ≤ 5s | E2B Q4_0 |
| 首字延迟 | ≤ 1s | 流式输出第一个字 |
| E2B Token 速度 | ≥ 10 token/s | 中端（实测 Q4_0）|
| 连续聊天 30min | 不闪退 | 标准档 |
| Android 内存峰值 | < 2.5 GB | 含 C++ 堆 |
| 规则提取延迟 | < 5ms/条 | 全机型 |

---

## 七、四条红线（不可违反）

### 红线 1：内存安全

- App 总内存峰值 **≤ 2.5 GB**（含 C++ 堆）
- 严禁同时加载两个 LLM 模型
- `onCleared()` 必须调用 `freeEngine()`
- `onTrimMemory` 等级 ≥ `TRIM_MEMORY_MODERATE` 时必须释放非必要缓存

### 红线 2：主线程隔离

所有耗时操作（推理/IO/解密）必须在线程池执行，详见上方线程约束表。

### 红线 3：隐私安全

聊天原文永不上传。`ChatMessage` 表仅存本地 SQLite。
**只有** `AgentContract.tool_call` 才走网络（OpenClaw）。

### 红线 4：禁止 Prompt 硬编码（ERR-02）

必须从 GGUF metadata 动态读取 `tokenizer.chat_template`，禁止写死在代码或 assets 中。

### 红线 5：llama-android AAR 不存在（ERR-08）

原文档引用的 `com.github.ArigatouJR:llama-android` 等 JitPack 坐标**均不存在**（已 GitHub 核实）。Phase 1 必须使用 llama.cpp CMake 构建方案，详见 `docs/MODEL.md` 方案 A。

---

## 八、依赖关系

```
GGUF 模型 CDN 下载（App 首次启动时下载，存到 `files/models/`）
CDN 源：[待配置]`gemma-4-E4B-it-Q4_0.gguf`
    ↓
engine/LlamaEngine.kt ← llama.cpp CMake 构建（libllama_jni.so + JNI）
    ↓
llm/LlamaScheduler.kt（Prompt 调度 + Token 流解析）
    ↓
agent/StreamParser.kt（JSON 拦截 + AgentRouter）
    ↓
ui/ChatViewModel.kt ← StateFlow
```

**禁止反向依赖**：ui 层不能直接调用 engine 层，必须通过 llm/agent 中转。
