# 变更日志

> 记录所有 PRD 修正、设计决策和重大变更。

---

## v2.3 — 模型路径修正（2026-04-09）

### GGUF 模型不能打入 APK（关键修正）

| 问题 | 原写法 | 修正后 |
|------|--------|--------|
| GGUF 模型路径 | `assets/models/gemma-4-E4B-it-Q4_0.gguf`（打入 APK） | App 首次启动从 CDN 下载，存入 `files/models/` |
| 影响范围 | docs/ANDROID.md, docs/ARCHITECTURE.md, docs/MODEL.md, docs/PLAN.md, docs/ONBOARDING.md, CLAUDE.md | — |

**原因**：Google Play 和各大应用市场限制主包 ≤ 100MB，4.6GB GGUF 打入 APK 无法上架。
**正确方案**：`https://cdn.aiyougame.com/models/gemma-4-E4B-it-Q4_0.gguf` → App 首次启动下载 → `/data/data/com.aiyougame/files/models/`

System Prompt（`assets/prompts/gu_chen.txt`）不受影响，仍然打入 APK（仅几十 KB）。

### Phase 命名澄清

文档**已统一**使用 Phase 0 / Phase 1 / Phase 2，无不一致：
- SPEC.md 里"Demo" = Phase 1 阶段的中文俗称（"核心聊天 Demo"），不是独立命名
- PRD V2.1 = SPEC.md 的版本号，不是另一套命名体系
- 详见 CHANGELOG v2.3

### Q4_0 vs Q4_K_M 澄清

| 文件 | 量化格式 | 说明 |
|------|---------|------|
| 现有 GGUF | **Q4_0** | 项目根目录 `gemma-4-E4B-it-Q4_0.gguf`，Phase 1 直接使用 |
| 设备分级表 | ~~Q4_K_M~~ | 原表顺序写反（Q4_0 质量低于 Q4_K_M，旗舰用更差量化），已修正 |
| SPEC.md | Q4_0 | ✅ 正确，无需修改 |

| 文件 | 量化格式 | 说明 |
|------|---------|------|
| 现有 GGUF | **Q4_0** | 项目根目录 `gemma-4-E4B-it-Q4_0.gguf`，Phase 1 直接使用 |
| 设备分级表 | Q4_K_M / Q4_K_S | 未来新量化参考，非当前文件 |
| SPEC.md | Q4_0 | ✅ 正确，无需修改 |

---

## v2.2 — Phase 0 文档建设（2026-04-09）

### Phase 0 交付完成

| 文档 | 状态 | 说明 |
|------|------|------|
| `docs/SPEC.md` | ✅ 新建 | 产品规格说明书（顶层设计）|
| `docs/ANDROID.md` | ✅ 重建 | 14周排期 + 层内架构 + 红线约束 |
| `docs/OPENCLAW.md` | ✅ 合并重建 | 合并 openclaw_integration/SPEC.md + 修复 PcDiscoveryManager Bug |
| `docs/BACKEND.md` | ✅ 合并重建 | 合并 AGENT_BACKEND.md + 三条红线 |
| `docs/README.md` | ✅ 更新 | 文档索引 + Decision D6 |
| `CLAUDE.md` | ✅ 重建 | Phase 0 状态 + 新项目结构 |
| `CHANGELOG.md` | ✅ 更新 | v2.2 记录 |

**保留未动的文档**（内容正确）：
- `docs/ARCHITECTURE.md` — 架构图完整，无需重建
- `docs/DATABASE.md` — Schema 正确，无需重建
- `docs/API.md` / `API_CONTRACT.md` — 接口合同正确
- `docs/PRIVACY.md` — 隐私部分强，内容安全部分待补实（优先级低，Phase 1 前做）
- `docs/MODEL.md` — AAR 坐标待上线验证
- `docs/ONBOARDING.md` — 上手指南完整
- `docs/PLAN.md` — 排期完整

---

## v2.1 — 工程 PRD 修正版（文档整理时）

### PRD 错误修正（ERR-01 ~ ERR-07）

| 编号 | 原始错误 | 修正内容 | 来源 |
|------|---------|---------|------|
| ERR-01 | Gemma 4 原生支持音频输入 | **Gemma 4 多模态仅支持图像**，音频必须走 ASR 降级方案 | 模块一 1.4 |
| ERR-02 | 硬编码 Gemma 2 Prompt 模板 | 改为运行时读取 GGUF metadata `chat_template` 字段，fallback 输出告警日志 | 模块一 1.2 |
| ERR-03 | 未处理小模型 JSON 输出不稳定 | 引入 **GBNF 语法约束解码**，目标格式错误率 < 0.1% | 模块三 3.2 |
| ERR-04 | DRM 密钥存储方案缺失 | 补充 **Android Keystore 硬件加密存储**方案（Phase 2 实施）| 模块六 6.1 |
| ERR-05 | WebSocket IP 硬编码 | 改用 **mDNS 局域网自动发现**，降级方案：设置页手动输入 IP | 模块三 3.4 |
| ERR-06 | sqlite-vec 与 Room 集成未说明 | 补充自定义 `SupportSQLiteOpenHelper` + `sqlite3_load_extension` 方案（Phase 2）| 模块二 2.5 |
| ERR-07 | 息屏用主模型做摘要 | Demo 改用**规则引擎**；Phase 2 用独立 1B 小模型 | 模块二 2.3 |

> **当前状态**：ERR-04 和 ERR-06 已降级为 Phase 2 处理，Phase 1 简化 DRM 方案。

---

## 设计决策记录

### Decision 1：Phase 1 砍掉语音输入
**日期**：文档整理阶段
**问题**：Whisper API 云端上传违背"隐私优先"核心卖点
**决策**：Phase 1 删除语音输入，纯文字 Demo；Phase 2 才引入本地 Whisper tiny.en
**影响**：减少 Week 11 一个任务节

### Decision 2：手机 ↔ PC 直连，不经过后端
**日期**：文档整理阶段
**问题**：后端 WebSocket 混淆了网络架构
**决策**：
- 后端只负责：Auth / 购买 / 云同步
- 手机 ↔ PC：WebSocket 直连（局域网 mDNS 发现）
- OpenClaw 已有，本项目只对接
**影响**：后端删除 `/ws/task` 接口，`openclaw_integration/SPEC.md` 新增协议文档

### Decision 3：简化 Prisma Schema
**日期**：文档整理阶段
**问题**：`SyncRecord` 表冗余，与 `User.profileJson` 功能重复
**决策**：删除 `SyncRecord` 表，云同步直接使用 `User.profileJson` 字段
**影响**：后端少一张表，API 简化

### Decision 4：简化 DRM 购买流程
**日期**：文档整理阶段
**问题**：Phase 1 模型文件不加密，公钥加密 AES 密钥流程过于复杂
**决策**：
- Phase 1：购买一次联网验证（首次打开角色时），不实时下发密钥
- Phase 2：才上 Android Keystore + AES-256 模型加密
**影响**：后端 `purchase.verify` 接口简化，`encryptedAesKey` 字段 Phase 2 才填值

### Decision 5：Android NDK 接入方案
**日期**：文档整理阶段
**问题**：手动编译 llama.cpp NDK 对 Android 开发者门槛高
**决策**：
- Phase 1：优先使用 `llama-android` AAR 封装，降低接入难度
- Phase 2：需要 GBNF / NPU 优化时，再切换手动 NDK 编译
**影响**：`model/CONVERT-GUIDE.md` 新增 AAR 方案

---

## 文档版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | — | 初始文档（来自 docx）|
| v2.0 | — | 第一轮整理：模块拆分 + 排期 |
| v2.1 | 2026-04-08 | 第二轮整理：砍语音/简化DRM/新增OpenClaw协议 |
| v2.2 | 2026-04-09 | Phase 0 清理：源码全删，文档重建，Decision D6 |

---

## Phase 1 交付清单（决策后）

### 新增文档
- `openclaw_integration/SPEC.md` — OpenClaw 对接协议 ✅

### 修改文档
- `README.md` — 新增决策记录
- `docs/ARCHITECTURE.md` — 删除云端 WebSocket
- `docs/DATABASE.md` — 删除 SyncRecord 表
- `backend/API-SPEC.md` — 简化购买流程，删除 WebSocket
- `backend/SCHEDULE.md` — 同步简化
- `android/SCHEDULE.md` — 删除音频章节，增加 NDK Buffer
- `model/CONVERT-GUIDE.md` — 新增 AAR 方案

### 待新增文档
- `docs/PRIVACY.md` — 隐私合规文档 ✅（本文档）
- `docs/ONBOARDING.md` — 新开发者上手指南 ✅（本文档）
- `android/ARCHITECTURE.md` — Android 层内架构 ✅（本文档）
- `CHANGELOG.md` — 变更日志 ✅（本文档）
