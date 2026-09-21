# AI 角色陪伴 App 工程原型

> **归档日期：2026-09-21。** 本项目停止主动维护，作为 AI 角色陪伴 App 的工程原型公开保留。历史计划不代表所有功能已实现或完成真机验证；欢迎 fork 后自行研究与更新。

仓库保留 Android 客户端、NestJS 后端、角色提示词、记忆模块与本地模型接入实验。最后一轮功能开发记录为 2026-04-14 的 Ollama HTTP 引擎接入，见 [CHANGELOG.md](CHANGELOG.md)。早期 JNI / NDK 方案的部分脚本已移至 `archive/`。

## 阅读入口

| 内容 | 位置 |
|------|------|
| Android 客户端与构建配置 | [android/](android/) |
| NestJS 后端与 Prisma 数据模型 | [backend/](backend/) |
| Ollama HTTP 接入与历史演示步骤 | [PHONE_DEMO_GUIDE.md](PHONE_DEMO_GUIDE.md) |
| 变更记录与历史设计决策 | [CHANGELOG.md](CHANGELOG.md) |
| 系统设计 | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| API 契约 | [docs/API_CONTRACT.md](docs/API_CONTRACT.md) |
| 数据库设计 | [docs/DATABASE.md](docs/DATABASE.md) |
| 历史测试记录 | [docs/TEST_REPORT.md](docs/TEST_REPORT.md) |
| 已停用的方案与脚本 | [archive/README.md](archive/README.md) |

## 历史状态与复现边界

- `docs/README.md` 等早期文档仍保留 Phase 0 / Phase 1 排期；当前代码与后续进展请结合变更日志和实际源码查看。
- 演示指南中的旧 NDK 脚本已归档，后期模型接入实验见指南中的“方式 C：Ollama HTTP 引擎”。
- 本次归档仅整理仓库说明，未重新执行 Android 构建、真机测试或后端端到端验证。历史测试记录仅对应当时的环境与代码。
- 模型权重和本地运行配置需要自行准备；第三方代码、模型与材料继续遵循其原有许可与使用条件。
