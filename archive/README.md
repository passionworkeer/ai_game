# Archive — 归档说明

归档时间：2026-04-14
归档原因：Phase 3 采用 Ollama HTTP 引擎替代 Phase 2 JNI 方案，相关脚本和模型文件退出活跃开发流程。

---

## 目录结构

- `scripts/`：Phase 2 NDK 编译脚本（已停用）。
- `docs/`：Phase 2 任务文档。
- `gguf/`：本地模型存放位置，不纳入 Git。

---

## scripts/ — Phase 2 NDK 脚本

| 文件 | 用途 | 停用原因 |
|------|------|---------|
| `build-native.bat` | 编译 llama_jni.so + whisper_jni.so | Phase 3 改用 Ollama HTTP，无需 NDK 编译 |
| `install-ndk.bat` | 安装 Android NDK r27c | 同上，NDK 依赖已移除 |
| `cleanup-jdk11.bat` | 清理 JDK 11 残留 | Phase 2 遗留工具脚本 |
| `setup-phone.bat` | 手机 USB 调试环境配置 | 已被 `phone-demo-setup.bat` 替代 |

---

## gguf/ — GGUF 模型文件

> 注意：GGUF 文件体积较大（总计约 9.2 GB），未被 git 追踪（.gitignore 排除 *.gguf）。

| 文件 | 大小 | 说明 |
|------|------|------|
| `gemma-4-E4B-it-Q4_0.gguf` | 4.6 GB | Phase 2 主推理模型，Gemma 4 E4B Q4_0 量化 |
| `Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-Q4_K_P.gguf` | 3.3 GB | Phase 3 实验用模型 |
| `mmproj-Gemma-4-E2B-Uncensored-HauhauCS-Aggressive-f16.gguf` | 940 MB | 多模态投影模型（配合上述 E2B 使用） |

**Phase 3 Ollama 说明**：Phase 3 通过 Ollama HTTP API 调用模型，Ollama 有独立的模型仓库（`~/.ollama/models/`），不直接读取上述 GGUF 文件。如需导入到 Ollama，执行：

```bash
ollama create gemma4 -f Modelfile
# 或直接 pull
ollama pull gemma2:2b
```

---

## docs/ — Phase 2 任务文档

| 文件 | 说明 |
|------|------|
| `Phase2_TASKS.md` | Phase 2 全量任务追踪（M1~M4 全部完成），已归入 CLAUDE.md 状态表 |

---

## 恢复方法

如需恢复某个脚本回根目录：

```bash
git mv archive/scripts/build-native.bat build-native.bat
git commit -m "chore: restore build-native.bat from archive"
```
