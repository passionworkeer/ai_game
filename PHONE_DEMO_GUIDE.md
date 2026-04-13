# 真机演示指南 — Gemma 4 本地推理

> Phase 2 完成。手机跑 Gemma 4 Q4_0 推理，无需云端。

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

## 模型文件：本地服务器方案

APK 不含 GGUF 模型（4.6 GB太大不入APK）。手机需要从电脑下载。

### Step 1: 启动模型下载服务器
```
serve-model.bat     # 新开一个终端窗口运行，保持窗口开着
```

### Step 2: 修改 APK 的下载地址
编辑 `android/app/src/main/java/com/aiyougame/companion/llm/LlamaEngineImpl.kt`：

```kotlin
// 把这行：
private const val CDN_URL = "https://cdn.aiyougame.com/models/gemma-4-E4B-it-Q4_0.gguf"

// 改成你的电脑IP + 8765端口，例如：
private const val CDN_URL = "http://192.168.1.100:8765/gemma-4-E4B-it-Q4_0.gguf"
```

> 怎么查本机IP？运行 `serve-model.bat` 会自动显示。

### Step 3: 重新打包 + 安装
```
cd android
.\gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Step 4: 运行 App
1. 打开 App
2. 授权**麦克风权限**（Whisper 语音输入需要）
3. App 自动下载模型（手机和电脑必须在同一WiFi）
4. 下载完成后，开始聊天！Gemma 4 在手机上本地运行

---

## 模型下载（约4.6 GB）

| 方式 | 速度 | 说明 |
|------|------|------|
| **本地WiFi下载** | 10-50 MB/s | 电脑开serve-model.bat，手机连同一WiFi |
| **ADB Push** | 30-100 MB/s | `adb push gemma-4-E4B-it-Q4_0.gguf /data/local/tmp/models/`（需root或特殊权限）|
| **QQ/微信传文件** | 慢 | 不推荐 |

---

## 查看日志（调试用）

```bash
# 实时查看 llama.cpp JNI 日志
adb logcat -s LlamaJni:V LlamaEngineImpl:V VoiceRecog:V *:S

# 过滤关键词：inference、token、model
adb logcat | findstr "inference token model error"
```

---

## 已知限制（Demo级别）

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| 首次推理慢（10-30秒） | Q4_0 CPU推理，无GPU加速 | 正常现象，后续token会持续输出 |
| 手机发烫 | CPU满载 | 正常，Demo时注意散热 |
| 8+核手机太慢 | n_threads=核数-1，可能太多 | 可在llama_jni.cpp里手动修改线程数 |
| 长时间推理卡住 | llama.cpp CPU模式可能卡 | 30秒内无响应可点"停止"按钮 |

---

## 推送模型到SD卡（ADB push 失败时的备选）

```bash
# 把模型文件传到手机下载目录
adb push gemma-4-E4B-it-Q4_0.gguf /sdcard/Download/

# 修改 ModelDownloaderImpl.kt 读取本地路径：
# 文件：android/app/src/main/java/com/aiyougame/companion/engine/ModelDownloaderImpl.kt
# 修改 getModelPath() 和 download() 直接读 /sdcard/Download/gemma-4-E4B-it-Q4_0.gguf
```

---

## 快速检查清单

- [ ] 手机开启了 USB 调试
- [ ] 运行 `phone-demo-setup.bat` 成功
- [ ] `serve-model.bat` 开着
- [ ] APK 的 CDN_URL 改成电脑IP
- [ ] 手机和电脑在同一WiFi
- [ ] 麦克风权限已授权
- [ ] 模型下载完成（约4.6 GB）
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
