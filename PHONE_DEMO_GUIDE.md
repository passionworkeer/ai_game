# 真机演示指南 — Gemma 4 本地推理

> **Phase 3 状态**：Ollama HTTP 引擎已完成验证。方式 C（Ollama HTTP）为当前推荐方案，无需 NDK，无需 GGUF 下载。
>
> Phase 2 ✅ 完成 | Phase 3 🔄 进行中：支持 Ollama HTTP 引擎真机验证

## 准备工作（只需做一次）

### 0. 安装 JDK 17（必须）

Android Gradle Plugin 需要 **Java 17+** 才能编译/打包。

- 推荐安装：JDK 17（或更高）
- 确保命令行可用：`java -version` 显示 17+
- 或者在 `android/gradle.properties` 里配置 `org.gradle.java.home`

### 1. 手机开启开发者模式
```
设置 → 关于手机 → 连续点击"版本号"7次 → 输入PIN码 → 开启成功
设置 → 开发者选项 → USB调试 = 开启
```
> 注意：不需要root，不需要解BL锁。

### 2. 电脑安装 Android NDK（如果还没装）

运行 **install-ndk.bat**，或手动下载：
- 下载地址：https://developer.android.com/ndk/downloads
- 选择：`Android NDK r26b` for Windows
- 安装到：`%LOCALAPPDATA%\Android\Sdk\ndk\26.1.10809160`

---

## 编译 + 部署（每次演示前运行）

### 方式 A：一键搞定（推荐）
```
phone-demo-setup.bat
```
自动完成：安装NDK → 编译C++ → 算SHA-256 → 打包APK → 推送模型

### 方式 B：分步执行
```bash
# Step 1: 编译原生库（首次需5-15分钟，之后2-3分钟）
build-native.bat

# Step 2: 打包APK（包含native libs）
cd android
.\gradlew.bat :app:assembleDebug

# Step 3: 安装到手机
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

### 方式 C：Ollama HTTP 引擎（无需 NDK，推荐开发验证）

无需编译原生库，直接用 Windows Ollama 通过 HTTP 调用模型。
**优势**：绕过 ARM/x86 架构限制，Windows 直接验证 Prompt 人设效果

#### Step 1: 确认 Ollama 运行
```powershell
# 启动 Ollama（如果还没运行）
ollama serve

# 确认模型已加载
curl http://127.0.0.1:11434/api/tags
```
确保模型 `gemma-4-e2b-uncensored` 在列表中。

#### Step 2: 配置 App 使用 Ollama
编辑 `android/app/build.gradle.kts`：
```kotlin
val OLLAMA_ENABLED = true  // ← 改成 true
// URL 配置（模拟器用 10.0.2.2，真机用电脑局域网 IP）
buildConfigField("String", "OLLAMA_URL", "http://10.0.2.2:11434")
// 真机时改成: "http://192.168.x.x:11434"
buildConfigField("String", "OLLAMA_MODEL", "gemma-4-e2b-uncensored")
```

#### Step 3: 编译 + 安装
```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### Step 4: 一键检查环境
双击 `check_deps.bat` 检查所有依赖是否就绪。


## 查看日志（调试用）

```bash
# 实时查看 OllamaEngine 日志
adb logcat -s OllamaEngine:V ChatViewModel:V *:S

# 过滤关键词：inference、token、model、error
adb logcat | findstr "inference token model error"
```

---

## 快速检查清单（方式 C — Ollama HTTP）

- [ ] 手机开启了 USB 调试
- [ ] Ollama 已启动（`ollama serve`）
- [ ] Ollama 模型就绪（`curl http://127.0.0.1:11434/api/tags`）
- [ ] 手机和电脑在同一WiFi
- [ ] 麦克风权限已授权
- [ ] 聊天界面输入文字
- [ ] 看到 AI 回复流式输出

---

## 性能参考

| 手机 | SoC | 推理速度 |
|------|-----|---------|
| 小米 13 | Snapdragon 8 Gen 2 | ~15-20 tok/s |
| Pixel 8 | Tensor G3 | ~10-15 tok/s |
| Redmi Note 12 | Snapdragon 4 | ~5-8 tok/s |

> Q4_0 量化，context=4096，CPU-only（无 Vulkan）。真机实测为准。
