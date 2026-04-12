# 全量测试报告
> 生成时间: 2026-04-12

---

## 测试执行摘要

| 测试类型 | 状态 | 结果 |
|---------|------|------|
| 后端 Jest 单元测试 | ✅ PASS | **145 passed**, 4 skipped, 9 suites |
| 后端 API E2E 冒烟测试 | ✅ PASS | **16/16 passed** |
| Android 单元测试 | ✅ PASS | **501 passed** |
| Android 编译 | ✅ PASS | BUILD SUCCESSFUL (40MB APK) |
| Android BUILD | ✅ PASS | BUILD SUCCESSFUL |
| API 合约测试 | ✅ PASS | 全部端点符合文档 |

---

## 后端 Jest 单元测试（145 个）

| 模块 | 测试文件 | 用例数 | 状态 |
|------|---------|--------|------|
| Auth | `auth/__tests__/auth.service.test.ts` | 通过 | ✅ |
| Purchases | `purchases/__tests__/purchases.service.test.ts` | 通过 | ✅ |
| Characters | `characters/__tests__/characters.service.test.ts` | 通过 | ✅ |
| Sync | `sync/__tests__/sync.service.test.ts` | 通过 | ✅ |
| Admin | `admin/__tests__/admin.service.test.ts` | 通过 | ✅ |
| Admin Guard | `admin/__tests__/admin.guard.test.ts` | 通过 | ✅ |
| DRM | `drm/__tests__/drm.service.test.ts` | 通过 | ✅ |
| JWT Guard | `guards/__tests__/jwt-auth.guard.test.ts` | 通过 | ✅ |
| API Contract | `__tests__/api.contract.test.ts` | 通过 | ✅ |

---

## 后端 API E2E 冒烟测试（16 个）

执行命令: `bash backend/e2e-test.sh`

| # | 测试用例 | 预期结果 | 实际结果 | 状态 |
|---|---------|---------|---------|------|
| T1 | 设备注册 → 返回 token | token 存在 | token 存在 | ✅ |
| T2 | 重复注册 → 返回相同 userId | idempotent | idempotent | ✅ |
| T3 | 获取角色列表（带 token）| 200 + characters | 200 + characters | ✅ |
| T4 | 获取角色列表（无 token）| 401 | 401 | ✅ |
| T5 | 获取 RSA 公钥 | publicKey 存在 | publicKey 存在 | ✅ |
| T6 | 验证购买 | 200 + purchaseId | 200 + purchaseId | ✅ |
| T7 | 重复购买同一角色 | 409 ALREADY_PURCHASED | 409 ALREADY_PURCHASED | ✅ |
| T8 | 支付金额不足 | 422 | 422 | ✅ |
| T9 | 获取已购列表 | 200 + purchases | 200 + purchases | ✅ |
| T10 | 同步 GET | 200 + profileJson | 200 + profileJson | ✅ |
| T11 | 同步 POST 更新档案 | 200 + updatedAt | 200 + updatedAt | ✅ |
| T12 | 同步 GET 验证更新 | 包含 TestUser | 包含 TestUser | ✅ |
| T13 | 跨用户同步（无权限）| 403 | 403 | ✅ |
| T14 | DRM 密钥生成 | 200 + encryptedKey | 200 + encryptedKey | ✅ |
| T15 | DRM 密钥获取 | 200 + encryptedKey | 200 + encryptedKey | ✅ |
| T16 | DRM 跨用户访问 | 403 | 403 | ✅ |

---

## Android 单元测试（501 个）

| 模块 | 测试文件 | 用例数 | 状态 |
|------|---------|--------|------|
| ChatMessageDao | `ChatMessageDaoTest.kt` | 9 | ✅ |
| UserProfileDao | `UserProfileDaoTest.kt` | 11 | ✅ |
| KeyEventDao | `KeyEventDaoTest.kt` | 10 | ✅ |
| ProfileExtractor | `ProfileExtractorTest.kt` | 18 | ✅ |
| VoiceRecognitionManager | `VoiceRecognitionManagerTest.kt` | 14 | ✅ |
| VectorMemoryManager | `VectorMemoryManagerTest.kt` | 12 | ✅ |
| LlamaEngineManager | `LlamaEngineManagerTest.kt` | — | ✅ |
| MockLlamaEngine | `MockLlamaEngineTest.kt` | 12 (新增) | ✅ |
| TokenManager | `TokenManagerTest.kt` | 15 (新增) | ✅ |
| ChatViewModel | `ChatViewModelTest.kt` | — | ✅ |
| AuthRepository | `AuthRepositoryTest.kt` | — | ✅ |
| AuthViewModel | `AuthViewModelTest.kt` | — | ✅ |
| CharactersViewModel | `CharactersViewModelTest.kt` | — | ✅ |
| PurchaseViewModel | `PurchaseViewModelTest.kt` | — | ✅ |
| SyncViewModel | `SyncViewModelTest.kt` | — | ✅ |
| AiyougameApi | `AiyougameApiTest.kt` | — | ✅ |
| AuthInterceptor | `AuthInterceptorTest.kt` | — | ✅ |

---

## 安全测试

| 测试项 | 描述 | 状态 |
|-------|------|------|
| IDOR-01 | mergeDeviceToPhone 校验 JWT userId 所有权 | ✅ 已修复 |
| IDOR-02 | Sync GET/POST 校验 userId 匹配 | ✅ 已通过（T13）|
| IDOR-03 | DRM Key 跨用户访问校验 | ✅ 已通过（T16）|
| 错误泄漏 | AuthRepository 不暴露服务端原始错误 | ✅ 已修复 |
| Webhook 签名 | 微信/支付宝回调 HMAC 校验 | ✅ 已验证 |
| JWT 脱敏 | deviceId 日志只打前 8 位 | ✅ 已确认 |

---

## 发现并修复的问题

### P0（已修复）
1. **mergeDeviceToPhone IDOR** — 攻击者可冒用任意 deviceId 合并他人账号
   - 修复：加 JWT userId 所有权校验
   - commit: `09f55de`

### P1（已修复）
1. **VoiceRecognitionManager 协程泄漏** — raw CoroutineScope 导致孤儿协程
   - 修复：CoroutineScope + SupervisorJob + destroy()
   - commit: `09f55de`

2. **LlamaEngineImpl @Singleton 误导** — Provider<T>.get() 总是创建新实例
   - 修复：移除 @Singleton + 注释说明
   - commit: `09f55de`

3. **LlamaEngineManager LRU 时间戳竞态** — 时间戳更新在锁外
   - 修复：移入 mutex.withLock
   - commit: `09f55de`

4. **AuthRepository 错误信息泄漏** — 原始服务端错误暴露给 UI
   - 修复：改为固定文案
   - commit: `09f55de`

5. **DrmController 返回 200 + error body** — 应该返回 403
   - 修复：throw ForbiddenException
   - commit: `db8471f`

---

## 已知限制

1. **Android 模拟器** — 当前环境无 GPU/HV 支持，无法在 headless 模式下启动模拟器
   - 解决：真机 USB 调试，或配置完整 GPU 支持的模拟器环境
   - APK 已就绪：`android/app/build/outputs/apk/debug/app-debug.apk`

2. **llama_jni.so** — 真机才包含原生库，模拟器回退到 MockLlamaEngine

3. **Whisper 语音** — 需要真实麦克风输入，模拟器无法测试

---

## 快速验证命令

```bash
# 后端测试
cd backend && npx jest --coverage --ci

# 后端 API E2E
cd backend && bash e2e-test.sh

# Android 单元测试
cd android && ./gradlew testDebugUnitTest

# Android 编译
cd android && ./gradlew assembleDebug

# 安装 APK（真机）
adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# 启动后端
cd backend && node dist/main.js
```

---

## APK 信息

- 文件: `android/app/build/outputs/apk/debug/app-debug.apk`
- 大小: 40MB
- 构建时间: 2026-04-12
- 签名: debug
