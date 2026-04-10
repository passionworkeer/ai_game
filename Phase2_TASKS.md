# Phase 2 任务追踪

> 项目：AI乙游陪伴 App Phase 2 | 创建日期：2026-04-10
> 分支：main | 根目录：E:\desktop\ai_game

---

## 现状摸底

### llama.cpp 目录验证 ✅
- `android/app/src/main/cpp/llama.cpp/` 内容完整
- `CMakeLists.txt` ✅，主源码 `llama.cpp/` ✅，`common/` ✅，`ggml/` ✅
- `android/app/src/main/cpp/llama_jni.cpp` — **STUB**，Phase 2 需实现
- `android/app/build.gradle.kts` — CMake 配置已就绪 ✅

### Android 已有基础设施
- `LlamaEngine.kt` 接口 ✅
- `LlamaEngineImpl.kt` — JNI 声明已注释，待激活
- `engine/GGUFMetadataReader.kt` ✅
- `engine/ChatTemplateLoader.kt` ✅
- `engine/ModelDownloader.kt` ✅
- Room entities（ChatMessage/UserProfile/KeyEvent）✅

### Backend 已有基础设施
- 6 个 REST API ✅
- Prisma schema + JWT ✅

---

## 依赖关系图

```
Android P0 链：
[P0-1 验证 llama.cpp] → [P0-2 CMakeLists.txt 补完] → [P0-3 llama_jni.cpp 实现]
                                                                        ↓
[P0-5 内存双保险] ← [P0-4 JNI Kotlin 激活 + Token 流回调] → [P0-6 集成测试]
        ↓
[P0-7 多角色 Room 支持] → [P0-8 角色切换 Context]

Backend P1 链（独立）：
[P1-B1 微信/支付宝 SDK] → [P1-B2 账号升级] → [P1-B3 AES 密钥下发]

语音输入 P0：独立分支，依赖 Android P0-4 完成
```

---

## Android P0 任务

### P0-A1：llama.cpp 目录完整性验证 ✅
**状态**：已验证（见上文）
- [x] CMakeLists.txt 存在
- [x] llama.cpp 主源码存在（10000+ 行）
- [x] common/ 目录存在（common.cpp, common.h）
- [x] ggml/ 目录存在
- **验收**：llama.cpp 可以独立编译（`cmake --build .`）

### P0-A2：CMakeLists.txt 补完（若需）
**负责人**：
**状态**：✅ 已就绪（`cpp/CMakeLists.txt` 配置正确）
- [x] llama.cpp 作为 subdirectory 添加
- [x] llama_jni SHARED library 配置
- [x] cpu_features 链接
- [x] Android STL c++_shared
- **验收**：`./gradlew assembleDebug` 不报 CMake 错误

### P0-A3：llama_jni.cpp JNI 封装层实现
**负责人**：
**依赖**：P0-A2
**子任务**：
- [ ] P0-A3-1：实现 `nativeInitEngine` — 加载 GGUF，`llama_load_model_from_file`
- [ ] P0-A3-2：实现 `nativeFree` — `llama_free` 释放上下文
- [ ] P0-A3-3：实现 `nativeGetChatTemplate` — 读 GGUF metadata `tokenizer.chat_template`
- [ ] P0-A3-4：实现 `nativeGenerateStream` — 流式推理，token 通过 JNI callback 写回 Kotlin
- [ ] P0-A3-5：实现 JNI callback 方法 `Java_com_aiyougame_1companion_llm_LlamaEngineImpl_onToken`
- [ ] P0-A3-6：gguf_init_from_file 读取模型 metadata（chat_template + vocab size）
- [ ] P0-A3-7：llama.cpp 推理循环（batch decode + sample）
- [ ] P0-A3-8：日志清理（发布前删除 debug LOGD）
**验收**：Native library 编译成功（`build/intermediates/cmake/.../libllama_jni.so`），模型加载无 crash

### P0-A4：LlamaEngineImpl JNI 激活 + Token 流回调
**负责人**：
**依赖**：P0-A3
**子任务**：
- [ ] P0-A4-1：取消 `LlamaEngineImpl.kt` 中 JNI external 注释，激活 native 方法
- [ ] P0-A4-2：实现 `generateResponse` → `Flow<String>` 流式输出（Channel 作为 token 管道）
- [ ] P0-A4-3：切主线程回调（`withContext(Dispatchers.Main)` 保证 UI 线程安全）
- [ ] P0-A4-4：实现 `ChatTemplateLoaderImpl` 从 GGUF metadata 读取 chat_template
- [ ] P0-A4-5：GGUFMetadataReader 补完（vocab size, EOS token, BOS token）
**验收**：
- 模型加载 < 5s（中端机）
- 流式输出 ≥ 10 token/s
- 内存峰值 < 2.5GB（含 C++ 堆）

### P0-A5：内存管理双保险
**负责人**：
**依赖**：P0-A4
**子任务**：
- [ ] P0-A5-1：`ViewModel.onCleared()` 调用 `llamaEngine.release()`
- [ ] P0-A5-2：`App.onTrimMemory(TRIM_MEMORY_COMPLETE)` 调用 `llamaEngine.release()`
- [ ] P0-A5-3：`llama_print_system_info()` 打印 NDK/ggml 版本到 Logcat
- [ ] P0-A5-4：Instrumented Test — 连续推理 30min 无 OOM
**验收**：
- 息屏 30min 后切回，内存释放干净
- 无 native 内存泄漏（use-after-free 检测）

### P0-A6：LlamaEngine 集成测试
**负责人**：
**依赖**：P0-A3 + P0-A4 + P0-A5
**子任务**：
- [ ] P0-A6-1：Unit Test — MockWebServer 验证 GGUF 下载 URL 正确
- [ ] P0-A6-2：Unit Test — Token 流顺序正确（"你好" → 应收到多个 token）
- [ ] P0-A6-3：Robolectric Test — `LlamaEngineImpl.initialize()` 成功路径
- [ ] P0-A6-4：Robolectric Test — `release()` 后 `generateResponse` 抛出 IllegalStateException
**验收**：≥ 10 new tests pass，覆盖 JNI/Kotlin 边界

### P0-A7：多角色 Room 支持
**负责人**：
**依赖**：Phase 1 Room（已有）
**子任务**：
- [ ] P0-A7-1：`ChatMessage.characterCode` 唯一索引（当前只有 userId）
- [ ] P0-A7-2：`UserProfile.characterCode` 唯一索引
- [ ] P0-A7-3：Room Migration 脚本（version 1 → version 2）
- [ ] P0-A7-4：`ChatViewModel` 支持动态 characterCode 切换
- [ ] P0-A7-5：角色切换时清空聊天列表（UI 刷新）
**验收**：
- Room Migration 测试通过（`migrationTest`）
- 多角色聊天记录隔离存储

### P0-A8：角色 Context 隔离（LlamaEngine 多实例）
**负责人**：
**依赖**：P0-A4
**子任务**：
- [ ] P0-A8-1：`LlamaEngine` 接口支持多实例（按 characterCode 管理）
- [ ] P0-A8-2：`LlamaScheduler` 调度器（多角色共用一个 llama_context 池）
- [ ] P0-A8-3：角色切换时 inject 不同 system prompt
- [ ] P0-A8-4：内存 ≤ 2.5GB（两角色共用 context window = 8192）
**验收**：两个角色（顾晨 + 新角色）各自记住独立记忆

### P0-A9：语音输入（Whisper tiny.en）
**负责人**：
**依赖**：P0-A4（推理引擎就绪后）
**子任务**：
- [ ] P0-A9-1：Whisper tiny.en GGML 模型下载（~75MB，assets 或 CDN）
- [ ] P0-A9-2：`SpeechRecognizer` Android API 集成
- [ ] P0-A9-3：Whisper JNI 封装（`whisper.cpp` CMake 编译）
- [ ] P0-A9-4：语音按钮 UI + 状态动画（录音中 / 识别中 / 完成）
- [ ] P0-A9-5：识别结果插入 ChatInput 输入框
**验收**：
- 录音 < 30s 识别完成
- 识别准确率 ≥ 85%（安静环境）
- 本地推理，零网络请求

---

## Backend P1 任务

### P1-B1：真实支付渠道接入
**负责人**：
**子任务**：
- [ ] P1-B1-1：微信支付 Android SDK 集成（`com.tencent.mm.opensdk`）
- [ ] P1-B1-2：支付宝 Android SDK 集成（`com.alipay.sdk`）
- [ ] P1-B1-3：后端 `POST /purchase/verify` 升级（接收微信/支付宝订单号）
- [ ] P1-B1-4：后端签名验证（微信/支付宝回调签名校验）
- [ ] P1-B1-5：沙箱环境测试（微信支付沙箱 + 支付宝沙箱）
- [ ] P1-B1-6：生产环境切换文档
**验收**：微信/支付宝支付全链路跑通（沙箱）

### P1-B2：用户账号体系升级
**负责人**：
**子任务**：
- [ ] P1-B2-1：Prisma schema 扩展（User 表加 phone/AppleId 字段）
- [ ] P1-B2-2：`POST /auth/phone` — 手机号 + 短信验证码登录
- [ ] P1-B2-3：`POST /auth/apple` — Apple ID 登录（Sign in with Apple）
- [ ] P1-B2-4：设备匿名账号 → 正式账号迁移（合并聊天记录/购买记录）
- [ ] P1-B2-5：JWT claims 扩展（userId + phone + appleId 三选一）
- [ ] P1-B2-6：现有 JWT 兼容（设备匿名 token 继续有效）
**验收**：
- 手机号登录返回 JWT
- Apple 登录返回 JWT
- 账号迁移后聊天记录不丢失

### P1-B3：AES 密钥下发
**负责人**：
**依赖**：P1-B2（账号体系）
**子任务**：
- [ ] P1-B3-1：后端生成 AES-256 密钥对（每用户每设备）
- [ ] P1-B3-2：`GET /purchase/:purchaseId/key` — 验证购买后下发 AES 密钥
- [ ] P1-B3-3：密钥传输用非对称加密（后端 RSA 公钥加密 AES 密钥）
- [ ] P1-B3-4：Android 端 RSA 解密后存 Android Keystore
- [ ] P1-B3-5：DRM 解密流程（Keystore → SecureBuffer → 内存解密 → llama.cpp）
**验收**：
- 购买验证后 AES 密钥安全下发
- Android 端密钥不落地（Keystore 管理）

### P1-B4：运营后台（Phase 2 后半）
**负责人**：
**子任务**：
- [ ] P1-B4-1：NestJS Admin 模块（JWT admin role）
- [ ] P1-B4-2：角色管理 CRUD（增删改查角色）
- [ ] P1-B4-3：数据看板（DAU/MAU/付费率）
- [ ] P1-B4-4：用户管理（封禁/解封设备）
**验收**：管理后台可访问，数据实时

---

## Android P1 任务（Phase 2 后半）

### P1-A1：AES-256 DRM
**依赖**：P1-B3（后端密钥下发）+ P0-A4（LlamaEngine 就绪）
**负责人**：

### P1-A2：sqlite-vec 向量记忆
**依赖**：P0-A7（多角色 Room）
**负责人**：

---

## 任务优先级总览

| ID | 任务 | 依赖 | 优先级 | 预计工时 | 状态 |
|----|------|------|--------|---------|------|
| P0-A3 | llama_jni.cpp 实现 | P0-A2 | P0 | 2d | 待开始 |
| P0-A4 | JNI Kotlin 激活 | P0-A3 | P0 | 1d | 待开始 |
| P0-A5 | 内存双保险 | P0-A4 | P0 | 0.5d | 待开始 |
| P0-A6 | LlamaEngine 测试 | P0-A5 | P0 | 1d | 待开始 |
| P0-A7 | 多角色 Room | Phase1 | P0 | 0.5d | 待开始 |
| P0-A8 | 角色 Context 隔离 | P0-A4 | P0 | 1d | 待开始 |
| P0-A9 | 语音输入 | P0-A4 | P0 | 3d | 待开始 |
| P1-B1 | 微信/支付宝 SDK | - | P1 | 3d | 待开始 |
| P1-B2 | 账号升级 | - | P1 | 2d | 待开始 |
| P1-B3 | AES 密钥下发 | P1-B2 | P1 | 2d | 待开始 |
| P1-A1 | AES DRM Android | P1-B3 | P1 | 2d | 待开始 |

---

## 验收标准总表

| 模块 | 指标 | 目标 |
|------|------|------|
| LlamaEngine | 模型加载 | < 5s（中端机）|
| LlamaEngine | 推理速度 | ≥ 10 token/s |
| LlamaEngine | 内存峰值 | < 2.5GB（含 C++ 堆）|
| LlamaEngine | 流式输出 | 首字 < 1s |
| Memory | 息屏释放 | 30min 后无泄漏 |
| Room | 多角色隔离 | 聊天记录按 characterCode 分离 |
| Payment | 微信/支付宝 | 沙箱全链路通 |
| Auth | 账号升级 | 迁移后记录不丢 |
| AES | 密钥安全 | Android Keystore 管理 |
| Whisper | 识别延迟 | < 30s 录音识别 |
| Whisper | 隐私 | 本地推理，零上传 |

---

## 当前里程碑

### Milestone M1：LlamaEngine 真实推理（目标：本周）
```
P0-A3 → P0-A4 → P0-A5 → P0-A6
```
**完成标准**：
1. `./gradlew assembleDebug` BUILD SUCCESSFUL（含 native lib）
2. 模拟器/真机推理流式输出
3. 内存 < 2.5GB
4. 10+ new tests pass

### Milestone M2：多角色 + 语音（目标：下周）
```
M1 完成 + P0-A7 → P0-A8 → P0-A9
```
**完成标准**：两个角色独立记忆，语音输入可用

### Milestone M3：后端 P1（目标：本周并行）
```
P1-B1 → P1-B2 → P1-B3
```
**完成标准**：支付/账号全链路可用

