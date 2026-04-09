# AI乙游陪伴 App — 文档入口

> 国内首款端侧离线 AI 乙女游戏陪伴 App
> 端侧推理 × 隐私优先 × OpenClaw PC 联动

**Phase 0**：文档规划 ✅ 已完成
**Phase 1**：核心聊天 Demo（14 周 Android + 8 周后端并行）

---

## 文档索引

### 战略与计划
| 文档 | 内容 | 状态 |
|------|------|------|
| `SPEC.md` | 产品规格说明书（顶层设计）| ✅ |
| `PLAN.md` | Phase 1 + Phase 2 完整路线图 | ✅ |

### 架构设计
| 文档 | 内容 | 状态 |
|------|------|------|
| `ARCHITECTURE.md` | 系统架构/依赖图/数据流/状态机/记忆架构 | ✅ v2.2 |
| `DATABASE.md` | Prisma Schema + Room Entity | ✅ |

### API 与接口
| 文档 | 内容 | 状态 |
|------|------|------|
| `API_CONTRACT.md` | 完整合同（请求/响应/错误码）| ✅ |
| `API.md` | 接口速查 | ✅ |
| `OPENCLAW.md` | WebSocket 协议（合并 SPEC.md）| ✅ v2.2 |
| `PRIVACY.md` | 隐私合规 + 内容安全实现 | ✅ v2.2 补实 |

### 模块详情
| 文档 | 内容 | 状态 |
|------|------|------|
| `MODEL.md` | Gemma 4 模型 + llama-android AAR | ✅ |
| `ANDROID.md` | 14周排期 + 层内架构 + 红线约束 | ✅ v2.2 |
| `BACKEND.md` | 8周排期 + Prisma Schema + 红线约束 | ✅ v2.2 |
| `ONBOARDING.md` | 新开发者上手指南 | ✅ 已对齐路径 |

### 项目管理
| 文档 | 内容 |
|------|------|
| `CHANGELOG.md` | 变更记录 + Decision D1~D6 |
| `CLAUDE.md` | Claude Code 配置（Phase 0 状态）|

---

## 快速开始

### Android 开发（Phase 0 阶段）
```bash
# Phase 0：先读文档
# docs/SPEC.md      — 产品规格说明书
# docs/ANDROID.md   — 14周开发计划 + 红线约束
# docs/ARCHITECTURE.md — 系统架构图

# Phase 1：项目初始化
open android/
# 参考 docs/ANDROID.md Week 1 任务
```

### 后端开发（Phase 0 阶段）
```bash
# Phase 0：先读文档
# docs/BACKEND.md   — 8周开发计划 + 三条红线
# docs/API_CONTRACT.md — 接口合同

# Phase 1：项目初始化
# 1. 启动 PostgreSQL
docker run --name aiyougame-db \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=aiyougame_dev \
  -p 5432:5432 \
  -d postgres:16

# 2. 初始化
cd backend
npm install && npx prisma db push && npx prisma db seed
npm run start:dev

# 3. 验证
curl -X POST http://localhost:3000/api/v1/auth/device \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"test-uuid-v4","clientVersion":"1.0.0","platform":"android"}'
```

---

## 重大决策（Decision）

| # | 决策 | 影响 |
|---|------|------|
| D1 | Phase 1 砍掉语音输入 | 隐私优先，Phase 2 才做本地 Whisper |
| D2 | 手机 ↔ PC 直连，不走后端 | WebSocket 直连，新增 `docs/OPENCLAW.md` |
| D3 | 删除 `SyncRecord` 表 | 云同步用 `User.profileJson` |
| D4 | Phase 1 简化 DRM | 一次联网验证，Phase 2 才上 AES |
| D5 | 优先 llama-android AAR | 降低 NDK 门槛，Phase 2 再手动编译 |
| D6 | Phase 0 代码全清，文档先行 | 源码从零重建（2026-04-09）|

---

## 当前阶段

**Phase 0** ✅ 已完成（2026-04-09）

文档全部就绪，源码从零开始：
- ✅ `docs/SPEC.md` — 产品规格说明书
- ✅ `docs/ANDROID.md` — 重建（14周 + 红线）
- ✅ `docs/ARCHITECTURE.md` — 重建（架构图 + 模块关系）
- ✅ `docs/OPENCLAW.md` — 合并 + 修复 PcDiscoveryManager Bug
- ✅ `docs/BACKEND.md` — 合并 + 三条红线
- ✅ `docs/PRIVACY.md` — 内容安全实现补实
- ✅ `docs/PLAN.md` — 对齐 Phase 0 状态
- ✅ `docs/README.md` — 本文档
- ✅ `docs/ONBOARDING.md` — 路径对齐

---

## Phase 1 验收门槛

| 指标 | 目标 |
|------|------|
| App 冷启动 | ≤ 2s |
| 模型加载 | ≤ 5s |
| 首字延迟 | ≤ 1s |
| Token 速度 | ≥ 10 token/s |
| App 总内存峰值 | < 2.5 GB |
| 聊天原文零上传 | 抓包确认 |
| 连续10轮对话 | AI 记住昵称+喜好 |
