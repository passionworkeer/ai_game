# AI乙游陪伴 — 项目总计划

> Phase 1 + Phase 2 完整路线图 | 版本 v2.2 | 2026-04-09 重建

---

## 产品定位

**国内首款端侧离线 AI 乙女游戏陪伴 App**
- 100% 本地推理，聊天数据永不离开手机
- OpenClaw PC 联动（手机控制 PC 执行任务）
- 多角色支持，记忆跨设备同步（用户授权）

---

## 两阶段路线图

```
Phase 0（当前）→ Phase 1 → Phase 2
────────────────────────────────────────────
文档规划         → 核心 Demo → 完整产品
                → 单角色（顾晨）
                → 文字交互   → 语音 + 表情动画
                               → 多角色 + 语音
```

---

## Phase 0 概览（当前）

**目标**：完成顶层设计文档，源码从零开始。

**交付物**：
- `docs/SPEC.md` — 产品规格说明书 ✅
- `docs/ANDROID.md` — 14周排期 + 层内架构 ✅
- `docs/ARCHITECTURE.md` — 完整架构图 ✅
- `docs/OPENCLAW.md` — WebSocket 协议 ✅
- `docs/BACKEND.md` — 后端排期 + 红线约束 ✅

---

## Phase 1 概览

**目标**：核心聊天 Demo 可演示
**工期**：约 14 周（Android）+ 8 周（后端，并行）
**验收**：AI 对话流畅 + 记忆记住用户 + OpenClaw 联动

### Phase 1 团队分工

| 模块 | 负责人 | 工期 |
|------|--------|------|
| Android App | 你 | 14 周 |
| 后端 API | AI 辅助 | 8 周（并行）|
| OpenClaw 对接 | OpenClaw 项目提供 | — |

---

## Phase 1 Android：14 周详细计划

> 详细任务定义见 `docs/ANDROID.md`

### Week 1–2：项目骨架 + 模型验证

| 日期 | Android 任务 | 验收标准 |
|------|------------|---------|
| Day 1–4 | Android Studio + Gradle + Compose + Hilt + Room | `assembleDebug` 成功 |
| Day 3–4 | llama-android AAR 依赖配置 | CMake Build 成功 |
| Day 4 | 模型下载器 + CDN 首次下载 | App 首次启动从 CDN 下载 GGUF 到 `files/models/` |
| Day 5 | llama-cli 测试（WSL2）| token ≥ 10/s |
| Day 6 | LlamaEngine.kt 接口定义 + AAR 实现 | 流式输出 |
| Day 7 | Tokenizer + Chat Template 动态读取 | 从 GGUF metadata，**禁止硬编码** |
| Day 8 | System Prompt 配置 | assets/prompts/gu_chen.txt |
| Day 9 | 流式打字机效果 | 每 Token 实时渲染 |

> **Checkpoint 1**：Android 项目可编译，模型推理正常。

### Week 3–4：LLM Core

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 10 | llama-android AAR 集成（LlamaEngineImpl）| 流式输出 |
| Day 11 | Tokenizer + Chat Template 动态读取 | 从 GGUF metadata |
| Day 12 | Prompt 模板注入（人设"顾晨"）| 无越狱 |
| Day 13 | 流式打字机效果 | 每 Token 实时渲染 |
| Day 14 | MemoryManager + 三层记忆注入 | Prompt 含记忆 |
| Day 15 | 内存管理双保险 | `onCleared` + `onTrimMemory` |

> **Checkpoint 2**：LLM Core 流式推理，内存 < 2.5GB

### Week 5–6：三层记忆系统

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 21 | Room 3 张表（ChatMessage/Profile/KeyEvent）| Entity + DAO |
| Day 23 | ProfileExtractor 规则引擎 | < 5ms/条 |
| Day 25 | 上下文窗口维护（20 条截断）| AI 能读上轮对话 |
| Day 28 | 连续 10 轮对话验证记忆 | AI 记住关键信息 |

> **Checkpoint 3**：记忆系统完整

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

> **Checkpoint 4**：UI 全部完成，60fps

### Week 9–10：OpenClaw 对接 + Agent

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 45–46 | GBNF grammar 定义 + 加载 | JSON 错误率 < 0.1% |
| Day 47 | StreamParser 意图拦截 | 实时分流 |
| Day 48–49 | PcDiscoveryManager（mDNS）| 发现成功率 ≥ 95% |
| Day 50 | WebSocket 心跳 + 断线重连 | 30s 自动重连 |
| Day 51 | AgentRouter 双轨路由 | PC 路由正确 |
| Day 54 | OpenClaw 对接联调 | PC → App 结果 |

> **Checkpoint 5**：OpenClaw PC 联动可用

### Week 11–12：后端对接 + 集成

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 57–58 | 后端 Auth 模块对接 | JWT 存储 |
| Day 59–60 | 角色列表 + 购买对接 | 展示 + 购买 |
| Day 61 | 云同步上/下行 | 记忆同步正常 |
| Day 62–63 | 全链路集成测试 | 端到端无报错 |
| Day 64 | 性能验收（9 项指标）| 全部达标 |
| Day 65 | 抓包验证零上传 | 仅购买/同步上传 |

> **Checkpoint 6**：Phase 1 功能全部可演示

### Week 13–14：Buffer + Demo

| 日期 | 任务 |
|------|------|
| Day 68–69 | Bug 修复 + 内存优化 |
| Day 72 | Phase 2 调研（Whisper / LoRA / DRM）|
| Day 74 | Demo 录制（3 分钟）|
| Day 77 | **Phase 1 正式交付** |

> **Checkpoint 7**：Phase 1 交付

---

## Phase 1 后端：8 周并行计划

> 详细任务定义见 `docs/BACKEND.md`

### Week 1–2：骨架 + 数据库

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 1–2 | NestJS 项目初始化 | `npm run start:dev` 成功 |
| Day 3 | Prisma + PostgreSQL 连接 | `prisma db push` 成功 |
| Day 3–4 | Prisma Schema（User/Character/Purchase）| 3 张表 |
| Day 5 | JWT 模块封装 | 签发 + 验证 |
| Day 6–7 | seed 数据（顾晨角色）| 可查询 |
| Day 8 | DTO 验证（class-validator）| 无效请求返回 400 |

> **Checkpoint B1**：后端骨架完成

### Week 3–4：核心 API

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 11–12 | `POST /auth/device` | UUID v4 注册，返回 JWT |
| Day 13 | JWT Guard | 无 Token 返回 401 |
| Day 13–14 | `GET /characters` | 返回角色列表 |
| Day 16–17 | `POST /purchase/verify`（简化）| 记录状态，不下发密钥 |
| Day 18 | 幂等性（ALREADY_PURCHASED）| 重复购买返回 409 |
| Day 19 | `GET /purchases` | 返回已购角色 |

> **Checkpoint B2**：Auth + Characters + Purchase 完成

### Week 5–6：云同步 + 部署

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 21–22 | `GET /sync/:userId` | 返回 profileJson |
| Day 23–24 | `POST /sync/:userId` | 上报摘要，更新 User 表 |
| Day 27 | Dockerfile | 镜像 < 500MB |
| Day 28 | Nginx 反向代理 | HTTP → HTTPS |
| Day 29 | CI/CD GitHub Actions | main 分支自动部署 |

> **Checkpoint B3**：云同步 + 部署完成

### Week 7–8：联调 + 压测

| 日期 | 任务 | 验收标准 |
|------|------|---------|
| Day 31–32 | Android × 后端联调 | 购买/同步/Auth 全链路 |
| Day 33 | OpenClaw 协议文档确认 | 配合 Android |
| Day 35–36 | 压力测试（k6）| 100 并发 P99 < 500ms |
| Day 37 | 安全审计 | 无 SQL 注入/XSS/越权 |
| Day 39–40 | 生产环境 RDS 部署 | — |

> **Checkpoint B4**：后端 Phase 1 交付

---

## Phase 2 计划（Phase 1 验收后启动）

### Android 增强

| 功能 | 技术方案 | 优先级 |
|------|---------|--------|
| 语音输入 | 本地 Whisper tiny.en | P0 |
| 多角色支持 | Room 多 characterCode | P0 |
| AES-256 DRM | Android Keystore + 内存解密 | P1 |
| sqlite-vec 向量记忆 | BGE-micro + sqlite-vec | P1 |
| LoRA 微调 | Unsloth，rank=16 | P2 |
| 表情动画 | Lottie + 3D 角色立绘 | P2 |

### 后端增强

| 功能 | 说明 |
|------|------|
| 真实支付渠道 | 接入微信/支付宝 SDK |
| AES 密钥下发 | Phase 1 简化 DRM 升级 |
| 用户账号体系 | 设备匿名 → 手机号/Apple ID |
| 运营后台 | 角色管理 / 数据看板 |

---

## Phase 1 验收标准

### 性能指标

| 指标 | 目标 | 测试方法 |
|------|------|---------|
| App 冷启动 | ≤ 2s | 中端机 |
| 模型加载 | ≤ 5s | 首次对话前 |
| 首字延迟 | ≤ 1s | 流式输出第一个字 |
| Token 速度 | ≥ 10 token/s | 中端机（Q4_0，实测）|
| 连续聊天 30min | 不闪退 | 标准档 |
| App 总内存峰值 | < 2.5 GB | 含 C++ 堆 |
| 规则提取延迟 | < 5ms/条 | ProfileExtractor |

### 功能指标

- [ ] 聊天对话流畅（流式打字机效果）
- [ ] 记忆记住用户昵称、喜好、心情
- [ ] 好感度随对话递增动画
- [ ] ToolCallCard 在工具执行时展示
- [ ] mDNS 发现 PC 端（局域网）
- [ ] PC 端任务结果返回 App
- [ ] JWT 设备注册成功
- [ ] 购买验证记录到后端
- [ ] 云同步上下行正常
- [ ] 抓包确认聊天原文零上传

---

## Phase 1 不做的事

| 功能 | 移到 | 原因 |
|------|------|------|
| 语音输入 | Phase 2 | 隐私优先，Phase 2 用本地 Whisper |
| AES-256 DRM | Phase 2 | Phase 1 简化购买验证 |
| LoRA 微调 | Phase 2 | Phase 1 用 Prompt 注入 |
| iOS | Phase 2 | Phase 1 专注 Android |
| sqlite-vec | Phase 2 | Phase 1 用规则引擎 |
