# AI乙游陪伴 App — 产品规格说明书

> Phase 0 交付物 | 版本 v1.0 | 2026-04-09
>
> 本文档是项目的**顶层设计文档**，从工程 PRD 和融资计划书中提炼，是所有开发决策的最终参考。

---

## 1. 产品定位

**一句话**：国内首款**端侧离线** AI 乙女游戏陪伴 App。

**核心差异点**：
- 100% 本地推理，聊天数据**永不离开手机**
- 角色"顾晨"——温柔青梅竹马人设，可交互记忆
- OpenClaw PC 联动——手机控制 PC 执行任务（文件搜索/日程等）

**目标用户**：18-28 岁女性，对 AI 陪伴有需求，注重隐私，看重"懂我"的体验。

**Phase 1 定位**：单角色（顾晨）文字聊天 Demo，用于验证：
1. 端侧推理性能是否满足体验
2. 记忆系统是否有效
3. 用户是否愿意为"隐私优先"买单

---

## 2. 用户故事与核心路径

### 核心用户故事

```
作为用户，
我希望和"顾晨"聊天时她能记住我说过的话（昵称/喜好/重要事件），
这样她会像真实的人一样更懂我。

作为用户，
我希望在我提到"帮我找一下财务报表"时，顾晨能真的找到文件，
这样我觉得她是真的在陪我生活，不只是聊天。

作为用户，
我希望我的聊天记录永远不会被上传到任何服务器，
这样我才敢对她说真心话。
```

### 核心用户路径（Happy Path）

```
安装 App → 隐私协议 → 角色选择（顾晨）→ 聊天主页
     ↓
输入消息 → 模型推理 → 记忆提取 → 记忆存储 → 回复渲染
     ↓
[可选] OpenClaw 任务 → PC 执行 → 结果包装 → 用户看到
```

### Phase 1 不支持的路径

| 用户行为 | 当前处理 |
|---------|---------|
| 语音输入 | 提示"敬请期待"，不崩溃 |
| 多角色切换 | Phase 1 单角色，无需切换 |
| 付费购买 | 顾晨为免费角色（Demo 阶段）|
| 云同步 | 设置页入口，但默认关闭 |

---

## 3. 功能优先级（MoSCoW）

### Must Have（Phase 1 必须交付）

| 功能 | 验收标准 | 对应文档 |
|------|---------|---------|
| 文字聊天（流式输出）| 首字延迟 ≤ 1s，Token ≥ 12/s | ANDROID.md |
| 记忆系统（三层）| 连续10轮对话，AI记住昵称+喜好 | DATABASE.md |
| 好感度 | 随对话递增动画，可视化 | ANDROID.md |
| OpenClaw PC 联动 | mDNS 发现 → 文件搜索 → 结果返回 | OPENCLAW.md |
| 隐私合规 | 抓包确认聊天零上传 | PRIVACY.md |
| 购买验证（后端）| 联网一次，记录状态 | API.md |
| 云同步（后端）| 用户手动+二次确认 | API.md |

### Should Have（Phase 1 争取交付）

| 功能 | 说明 |
|------|------|
| ToolCallCard 动画 | 工具执行时展示进度 |
| Emoji 表情动画 | 正则触发动画 |
| ProfileScreen | 立绘 + 好感度 |
| mDNS 降级入口 | 手动 IP 连接 |

### Could Have（Phase 1 Buffer）

| 功能 | 条件 |
|------|------|
| 内容安全越狱测试 | 时间有余再做 |
| 多角色架构代码预留 | Buffer 周预留 |

### Won't Have（Phase 2）

| 功能 | 移到 | 原因 |
|------|------|------|
| 语音输入 | Phase 2 | 隐私优先，本地 Whisper |
| AES-256 DRM | Phase 2 | Phase 1 简化购买验证 |
| LoRA 微调 | Phase 2 | Phase 1 用 Prompt 注入 |
| iOS | Phase 2 | 专注 Android |
| sqlite-vec 向量记忆 | Phase 2 | Phase 1 用规则引擎 |
| 多角色 | Phase 2 | Phase 1 单角色验证 |
| 真实支付 | Phase 2 | 接微信/支付宝 SDK |

---

## 4. 非功能需求

### 性能指标

| 指标 | 目标 | 测试设备 |
|------|------|---------|
| App 冷启动 | ≤ 2s | 中端机 |
| 模型加载 | ≤ 5s | E2B Q4_0 |
| 首字延迟 | ≤ 1s | 流式输出第一个字 |
| Token 速度 | ≥ 10 token/s | 中端机（Q4_0，实测优先）|
| 连续聊天 30min | 不闪退 | 标准档 |
| App 总内存峰值 | < 2.5 GB | 含 C++ 堆 |
| 规则提取延迟 | < 5ms/条 | ProfileExtractor |

> **Token 速度说明**：现有 GGUF 为 Q4_0 量化（非 Q4_K_M），质量更高但速度可能略慢。先实测，低于 10 token/s 再决定是否重新量化。

### 隐私约束（红线）

| 约束 | 实施方式 |
|------|---------|
| 聊天原文永不上传 | `ChatMessage` 表仅存本地 SQLite |
| 好感度不上云 | `affectionScore` 仅存本地 Room |
| 设备 ID 脱敏 | 禁止采集 IMEI/GAID，用客户端 UUID v4 |
| 云同步默认关闭 | 用户手动 + 二次确认 |
| 内容安全过滤 | 接入第三方或自建关键词库 |

### 安全约束

| 约束 | 说明 |
|------|------|
| JWT 有效期 | 1 年（设备匿名，不需要短期）|
| 购买验证签名 | Phase 1 简化，Phase 2 接入真实签名校验 |
| OpenClaw WebSocket | 仅局域网 `ws://192.168.x.x`，无公网暴露 |

---

## 5. 技术选型与约束

### Android

| 组件 | 选型 | 约束 |
|------|------|------|
| 语言 | Kotlin | — |
| UI | Jetpack Compose | Material 3 |
| DI | Hilt | — |
| 本地数据库 | Room + SQLite | 3 张表：ChatMessage / UserProfile / KeyEvent |
| 推理引擎 | llama.cpp CMake 构建（NDK）| Phase 1 推荐；Phase 2 可切换 |
| 模型格式 | GGUF Q4_0 | 已有 `gemma-4-E4B-it-Q4_0.gguf`（4.6GB）|
| 网络 | Retrofit + OkHttp | 仅购买验证/云同步/OpenClaw |
| 状态管理 | ViewModel + StateFlow | — |

**内存红线**：App 总进程（含 C++ 堆）≤ 2.5GB

**线程红线**：
- 推理/IO/解密：`Dispatchers.IO` 或 C++ 线程池
- UI 更新：`StateFlow.collect` 在主线程
- `onCleared()` 必须调用 `freeEngine()`

### 后端

| 组件 | 选型 | 约束 |
|------|------|------|
| 框架 | NestJS + TypeScript | strict 模式 |
| ORM | Prisma | PostgreSQL 16 |
| 认证 | JWT（设备匿名）| Token 1年有效期 |
| 验证 | class-validator DTO | 所有请求体校验 |
| 部署 | Docker | PostgreSQL + NestJS |

### OpenClaw PC 联动

| 组件 | 实现方 | 说明 |
|------|-------|------|
| mDNS 发现 | Android NsdManager | `_aiyougame._tcp.local` |
| WebSocket 协议 | 本项目 + OpenClaw | 局域网直连，不走后端 |
| PC Agent | OpenClaw 项目 | 本项目只对接 |

### 模型

| 项目 | 数据 |
|------|------|
| 基础模型 | Gemma 4 2B Instruction Tuned |
| 量化格式 | Q4_0（已有）|
| 量化工具 | llama.cpp |
| Tokenizer | 从 GGUF metadata 动态读取，禁止硬编码 |

---

## 6. Phase 1 / Phase 2 分界线

```
Phase 1（当前）→ Phase 2
────────────────────────────
核心聊天 Demo   → 完整产品
单角色（顾晨）  → 多角色 + 语音
文字交互       → 语音 + 表情动画
Phase 1 Demo  → 上架应用市场
```

**Phase 1 验收门槛**（达到才进入 Phase 2）：
- [ ] 连续 10 轮对话，AI 记住所有关键信息
- [ ] Token 速度 ≥ 10 token/s（中端机实测）
- [ ] 内存峰值 < 2.5 GB
- [ ] OpenClaw PC 联动端到端可演示
- [ ] 抓包确认聊天原文零上传

---

## 7. 验收标准（可测试）

### 功能测试用例

| ID | 用例 | 步骤 | 预期结果 |
|----|------|------|---------|
| F01 | 首次对话 | 输入"我叫小鱼" | 3轮内回复中出现"小鱼" |
| F02 | 喜好记忆 | 说"我喜欢喝奶茶" | 下次提到饮品时提及奶茶 |
| F03 | 好感度递增 | 连续对话 10 轮 | 好感度 +10，可视化动画 |
| F04 | 工具触发 | 说"帮我找财务报表" | ToolCallCard 出现 |
| F05 | PC 联动 | 说出文件搜索任务 | 结果正确返回，角色口吻播报 |
| F06 | OpenClaw 离线 | PC 端关闭时触发工具 | 角色口吻告知 PC 离线 |
| F07 | 云同步关闭 | 检查设置页 | 默认关闭，有二次确认 |
| F08 | 隐私零上传 | Charles 抓包 | 无 ChatMessage 表内容上传 |

### 性能测试用例

| ID | 指标 | 测试方法 | 目标 |
|----|------|---------|------|
| P01 | 冷启动 | 卸载重装，首次 LAUNCHER 点击到可交互 | ≤ 2s |
| P02 | 模型加载 | 首次进入聊天到首字输出 | ≤ 5s |
| P03 | 首字延迟 | 流式输出第一个字 | ≤ 1s |
| P04 | Token 速度 | 连续推理时采样 | ≥ 10 token/s |
| P05 | 内存峰值 | 连续聊天 30min，dumpsys meminfo | < 2.5 GB |
| P06 | 规则提取 | 100条消息 ProfileExtractor 耗时 | < 500ms |

---

## 8. 当前状态与下一步

**Phase 0**：文档规划 ✅（本文档交付）
- [x] 产品规格说明书（本文档）
- [ ] 重建 `docs/ANDROID.md`
- [ ] 合并 `docs/OPENCLAW.md` + openclaw_integration/SPEC.md
- [ ] 合并 `docs/BACKEND.md` + 后端规范
- [ ] 重建 `docs/PRIVACY.md`
- [ ] 更新 `docs/README.md`

**Phase 1**：核心聊天 Demo（14 周 Android + 8 周后端并行）

**Phase 2**：完整产品（Phase 1 验收后启动）
