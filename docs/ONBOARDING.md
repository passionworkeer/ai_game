# 新开发者上手指南

> 本地开发环境从零搭建，所需时间：约 4–8 小时（取决于网络速度）
> **Phase 0 清理后更新**：文档路径已对齐，部分源码待重建。

---

## 前置条件

### 必须安装
| 工具 | 版本 | 用途 |
|------|------|------|
| Android Studio | Hedgehog (2023.1.1)+ | Android 开发 |
| JDK | 17+ | Gradle / Android |
| Node.js | 20 LTS+ | 后端开发 |
| Python | 3.10+ | 模型转换 |
| Git | 2.40+ | 版本控制 |
| WSL2 | latest | llama.cpp 编译（Windows 用户必须）|

### 推荐安装
| 工具 | 用途 |
|------|------|
| Docker Desktop | PostgreSQL 本地数据库 |
| Postman / Bruno | API 测试 |
| Charles / mitmproxy | 抓包验证隐私 |
| Navicat | 数据库查看 |

---

## 一、克隆项目

```bash
git clone <repo-url>
cd ai_game

# 查看项目结构
ls
# android/  backend/  docs/  model/  openclaw_integration/  README.md  CHANGELOG.md
```

---

## 二、Android 本地开发环境

### 2.1 打开项目

```bash
# 用 Android Studio 打开 android/ 目录
# File → Open → 选择 ai_game/android/

# 等待 Gradle Sync 完成（约 3–5 分钟）
```

### 2.2 依赖安装（Android Studio 会自动处理）

```
主要依赖：
- Jetpack Compose BOM 2024.02
- Hilt 2.50
- Room 2.6.1
- OkHttp 4.12.0
```

### 2.3 NDK 配置

Android Studio 自动下载 NDK，也可手动指定：

```bash
# 在 local.properties 中添加
ndk.dir=D\:\\Android\\Sdk\\ndk\\26.1.10909125
```

### 2.4 模型文件准备

```bash
# GGUF 模型文件已存在于项目根目录（4.6GB Q4_0）
# Phase 1 方案：App 首次启动时从 CDN 下载，不打入 APK
# CDN 地址：[待配置] gemma-4-E4B-it-Q4_0.gguf
# 本地路径：/data/data/com.aiyougame/files/models/gemma-4-E4B-it-Q4_0.gguf
# 模型下载由 App 内 ModelDownloadManager 处理，无需手动复制
```

### 2.5 运行项目

```bash
# 连接 Android 真机（或启动模拟器）
# 推荐用真机测试（NDK 推理在模拟器上极慢）

./gradlew installDebug
# 或在 Android Studio 中点击 Run 按钮
```

### 2.6 验证安装

```
启动后应看到：
✅ App 可打开
⚠️ 首次加载模型需要 5–10 秒（正常）
✅ 出现角色对话界面
```

---

## 三、后端本地开发环境

### 3.1 启动 PostgreSQL

```bash
# 方式 A：Docker（推荐）
docker run --name aiyougame-db \
  -e POSTGRES_PASSWORD=password \
  -e POSTGRES_DB=aiyougame_dev \
  -p 5432:5432 \
  -d postgres:16

# 方式 B：本地安装 PostgreSQL 16
# 下载：https://www.postgresql.org/download/
```

### 3.2 启动后端

```bash
cd backend

# 安装依赖
npm install

# 复制环境变量
cp .env.example .env
# 编辑 .env，填写 DATABASE_URL 和 JWT_SECRET

# 运行数据库迁移
npx prisma db push

# 初始化角色数据
npx prisma db seed   # 如果有 seed 文件

# 启动开发服务器
npm run start:dev

# 应看到：
# Nest application successfully started
# listening on port 3000
```

### 3.3 验证后端

```bash
# 测试设备注册
curl -X POST http://localhost:3000/api/v1/auth/device \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"test-uuid-v4","clientVersion":"1.0.0","platform":"android"}'

# 应返回 JWT token
```

---

## 四、OpenClaw 对接（本项目只对接，不需要开发 OpenClaw）

```bash
# OpenClaw PC 端需要运行在和手机同一局域网
# 确认 OpenClaw 已注册 mDNS 服务：_aiyougame._tcp.local

# 测试 mDNS 发现（在 Android 真机上运行 App）：
# App 设置页 → PC 连接 → 应能看到 OpenClaw-PC
```

> 如果没有 OpenClaw PC 端：跳过 PC 联动功能，聊天对话本身可以独立运行。

---

## 五、常见问题

### 问题：Gradle Sync 失败
```bash
# 清理缓存
./gradlew clean
rm -rf ~/.gradle/caches/transforms-*
# 重启 Android Studio → Invalidate Caches → Restart
```

### 问题：NDK 编译失败（Windows）
```bash
# 确保 WSL2 已安装
wsl --install

# 在 WSL2 中编译 llama.cpp（不要在 Windows 原生环境硬撑）
```

### 问题：模型文件找不到
```bash
# 确认模型在 CDN（无需本地复制）：
# [待配置] gemma-4-E4B-it-Q4_0.gguf
```

### 问题：后端连接失败
```bash
# 确认 PostgreSQL 正在运行
docker ps

# 确认端口没被占用
lsof -i :5432

# 检查 .env 数据库 URL
cat backend/.env
```

### 问题：App 启动后闪退
```bash
# 查看 logcat
adb logcat | grep -i "aiyougame\|fatal\|exception"

# 常见原因：
# 1. 模型文件损坏 → 重新下载
# 2. 内存不足 → 关闭其他 App 后重试
# 3. NDK so 文件未正确打包 → ./gradlew assembleDebug --info | grep llama
```

---

## 六、代码规范

### Android
- Kotlin 风格：遵循 [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- 禁止在主线程（Main Thread）执行 IO / 推理操作
- 所有 JNI/native 方法在 `Dispatchers.IO` 或独立线程池中调用
- ViewModel 中使用 `StateFlow` 管理状态

### 后端
- TypeScript strict 模式
- 遵循 NestJS 风格：Controller → Service → Repository
- 所有请求体用 `class-validator` DTO 验证
- 敏感数据（密钥）不写入日志

### Git
```bash
# 分支命名
feature/xxx        # 功能分支
fix/xxx            # 修复分支
docs/xxx           # 文档分支

# Commit 格式
<type>: <description>
# types: feat | fix | refactor | docs | test | chore

# 示例
feat: add三层记忆系统ProfileExtractor规则引擎
fix: LLM Core内存泄漏freeEngine路径
docs: 更新API接口文档
```

---

## 七、关键文件索引

| 文件 | 说明 | 必读 |
|------|------|------|
| `README.md` | 项目总览 | ✅ |
| `CHANGELOG.md` | 决策记录 | ✅ |
| `docs/SPEC.md` | 产品规格说明书 | ✅ |
| `docs/ARCHITECTURE.md` | 系统架构 | ✅ |
| `docs/PLAN.md` | 完整排期 | ✅ |
| `docs/DATABASE.md` | 数据库设计 | ✅ |
| `docs/ANDROID.md` | Android 开发约束 | 🔧 |
| `docs/BACKEND.md` | 后端开发约束 | 🔧 |
| `docs/API_CONTRACT.md` | API 完整合同 | 🔧 |
| `docs/OPENCLAW.md` | OpenClaw 协议 | 🔧 |
| `docs/MODEL.md` | 模型接入指南 | 🔧 |
| `docs/PRIVACY.md` | 隐私合规 | 🔧 |

> 🔧 = 按需查阅，✅ = 必读
