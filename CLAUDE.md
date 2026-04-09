# AI乙游陪伴 App — Claude Code 项目配置

---

## 项目概述

国内首款端侧离线 AI 乙女游戏陪伴 App（Gemma 4 本地推理 + OpenClaw PC 联动）。

**当前阶段**：Phase 0 — 文档规划，源码已清空，待重建。

**团队**：单人（你），全栈。

---

## 当前状态

```
Phase 0 ✅ 文档规划 ← 当前
Phase 1    核心聊天 Demo（14 周）
Phase 2    完整产品
```

**清理记录**（见 `CHANGELOG.md v2.2`）：
- 2026-04-09：源码全删，保留文档 + 模型
- 原因：骨架含编译错误，必须重写

---

## 重大决策（必读）

| # | 决策 | 影响 |
|---|------|------|
| D1 | Phase 1 砍掉语音输入 | 隐私优先，Phase 2 才做本地 Whisper |
| D2 | 手机 ↔ PC 直连，不走后端 | WebSocket 直连，新增 `docs/OPENCLAW.md` |
| D3 | 删除 `SyncRecord` 表 | 云同步用 `User.profileJson` |
| D4 | Phase 1 简化 DRM | 一次联网验证，Phase 2 才上 AES |
| D5 | Phase 1 使用 llama.cpp CMake 构建 | 原 AAR 方案不存在（GitHub 核实），改用 `nerve-sparks/iris_android` CMake 方案（ERR-08）|
| D7 | Phase 1 GGUF 不打入 APK | App 首次启动从 CDN 下载，存 `files/models/`（v2.3 修正）|
| D8 | Phase 1 内容安全必须接入第三方 | 占位符正则词库禁止上架（ERR-10）|
| D6 | Phase 0 代码全清，文档先行 | 源码从零重建 |

---

## PRD 错误修正（ERR-01~08）

| # | 修正内容 | 来源 |
|---|---------|------|
| ERR-01 | Gemma 4 **不支持音频输入**（仅图像+文本）| docs/MODEL.md |
| ERR-02 | **禁止硬编码 Prompt 模板**，必须读 GGUF metadata `chat_template` | docs/ANDROID.md |
| ERR-03 | JSON 输出用 **GBNF 语法约束**，错误率 < 0.1% | docs/OPENCLAW.md |
| ERR-04 | DRM 密钥存 **Android Keystore**（Phase 2 实施）| docs/PRIVACY.md |
| ERR-05 | WebSocket IP **硬编码 → mDNS 自动发现** | docs/OPENCLAW.md |
| ERR-06 | **设备分级量化顺序写反**（Q4_0 质量 < Q4_K_M，旗舰档不能用最差量化）| docs/MODEL.md v2.3 |
| ERR-07 | 息屏摘要用**规则引擎**，不用主模型 | docs/ANDROID.md |
| ERR-08 | **llama-android AAR 不存在**，JitPack 坐标均已核实为 404 | docs/MODEL.md |
| ERR-09 | **三档统一 Gemma E4B Q4_0**，原 PRD 三模型方案 Phase 2 再引入 | docs/MODEL.md v2.3 |
| ERR-10 | **内容安全词库是占位符**，Phase 1 须接入第三方服务才能上架 | docs/PRIVACY.md v2.3 |

---

## 代码规范（Phase 1 实施时生效）

### Android
- **UI 线程绝对禁止阻塞**：推理/IO/解密全在 `Dispatchers.IO` 或 C++ 线程池
- **内存红线**：App 进程（含 C++ 堆）≤ 2.5GB
- ViewModel 使用 `StateFlow`，回调必须通过 `emit()` 推回主线程
- `freeEngine` 在 `ViewModel.onCleared()` 和 `App.onTrimMemory()` **双保险调用**

### 后端
- TypeScript strict 模式
- 所有请求体用 `class-validator` DTO 验证
- 敏感数据不写入日志

### 隐私（不可违反）
- **聊天原文永不上传**：`ChatMessage` 表仅存本地 SQLite
- **设备 ID 脱敏**：禁止采集 IMEI/GAID，用客户端 UUID v4
- **云同步默认关闭**：用户手动 + 二次确认
- **好感度不上云**：仅存本地

---

## 项目结构

```
ai_game/
├── docs/              # 全部文档（Phase 0 已重建）
│   ├── README.md      # 文档入口
│   ├── SPEC.md        # 产品规格说明书
│   ├── PLAN.md        # Phase 1 + Phase 2 完整路线图
│   ├── ARCHITECTURE.md # 整体架构 + 数据流
│   ├── DATABASE.md    # 数据库设计（Prisma + Room）
│   ├── API_CONTRACT.md # 后端 REST 接口（6个）
│   ├── OPENCLAW.md   # Android ↔ PC WebSocket 协议
│   ├── MODEL.md      # 模型转换 + llama.cpp CMake 接入（已核实）
│   ├── ANDROID.md    # Android 排期 + 层内架构
│   ├── BACKEND.md    # 后端排期
│   ├── PRIVACY.md    # 隐私合规 + 国内法规
│   ├── CHANGELOG.md  # 变更记录 + Decision D1~D6
│   └── ONBOARDING.md # 新开发者上手指南
│
├── android/           # 【已清空】Phase 1 从零重建
│
├── backend/           # 【部分保留】
│   └── src/prisma/   # Schema 正确保留，其余从零重建
│       ├── schema.prisma
│       ├── seed.ts
│       └── prisma.service.ts
│
├── model/            # 模型文件
│   └── gemma-4-E4B-it-UD-MLX-4bit-main/  # 源 safetensors
│
├── gemma-4-E4B-it-Q4_0.gguf  # 4.6GB Q4_0，已就绪（CDN 源，Phase 1 App 首次启动下载）
│
├── CHANGELOG.md       # 变更记录
└── CLAUDE.md          # 本文件
```

---

## 下一步

**Phase 0 ✅ 已完成**（2026-04-09）：
- 全部文档已重建，ERR-08 已修正（D5 决策同步更新）
- 多余文件已清理（openclaw_integration/SPEC.md、root README.md、docs/API.md）

**Phase 1 实施前准备**：
1. 确认 llama.cpp CMake 构建方案（`docs/MODEL.md` 方案 A）
2. 搭建 WSL2 环境（Windows 用户必须）
3. 创建 Android 项目骨架

---

## 执行原则

1. **小步快跑**：每个任务单独测试后再提交
2. **有结果再说**：没验证不汇报"完成了"
3. **不问我**：执行过程中有疑问先尝试解决，解决不了再问
4. **先读文档**：涉及接口/架构问题时，先查 `docs/` 中的对应文档
