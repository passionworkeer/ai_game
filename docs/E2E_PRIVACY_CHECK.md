# 隐私合规 E2E 检查清单

> 本文档定义 Phase 1 上架前必须通过的隐私合规端到端验证流程。
> 适用角色：测试工程师、隐私合规负责人。
> 参考标准：《个人信息保护法》《数据安全法》《生成式人工智能服务管理暂行办法》

---

## 环境准备

### 1. 工具安装

```bash
# 安装 mitmproxy（HTTP/HTTPS 抓包）
pip install mitmproxy

# 验证安装
mitmproxy --version
```

### 2. Android 模拟器配置

1. 启动 Android 模拟器（建议 Pixel 5 / API 34）。
2. 打开设置 → 网络 → 手动代理：
   - **代理主机名**：`本机 IP`（Windows: `ipconfig` 查看，例如 `192.168.1.100`）
   - **代理端口**：`8080`
3. 安装 CA 证书（mitmproxy 需要 CA 才能解密 HTTPS）：
   ```bash
   # 下载 CA 证书
   curl -x http://127.0.0.1:8080 http://mitm.it/cert/pem -o mitmproxy-ca-cert.pem

   # 安装到模拟器（需要 adb）
   adb push mitmproxy-ca-cert.pem /sdcard/
   adb shell settings put secure install_non_market_apps 1
   # 手动：在设置 → 安全 → 加密与凭据 → 安装证书 → 选择 mitmproxy-ca-cert.pem
   ```

### 3. 启动 mitmproxy

```bash
# 在终端启动（8080 端口）
mitmproxy --listen-port 8080

# 如需保存流量到文件（用于事后分析）
mitmproxy --listen-port 8080 --set confdir=~/.mitmproxy -w capture.mitm
```

### 4. 启动后端服务（确保 API 可达）

```bash
cd backend
docker start aiyougame-db
npm run start:dev
```

---

## 零容忍项（任何一项违规 = 不合格）

> 以下任何一项在抓包中发现，Phase 1 即判定为不合格，必须修复后才能上架。

| # | 检查项 | 违规示例 |
|---|--------|---------|
| P1 | **ChatMessage 内容出现在 HTTP 请求 body 中** | POST /api/v1/sync/... body 包含 `"content": "我想吃火锅"` |
| P2 | **完整 deviceId 出现在 URL 参数或 body** | GET `/api/v1/sync/uuid-xxxx-xxxx-xxxx-xxxx` 或 body 出现未脱敏 UUID |
| P3 | **IMEI / GAID / AndroidId 出现在任何网络请求中** | 请求 header 或 body 出现 `deviceId=86a...`、`androidId=1234...` |
| P4 | **聊天原文出现在日志中** | `Log.d(tag, "user input: ...")`、`Log.i(tag, "response: ...")` 输出对话内容 |

---

## 验证端点清单

> 所有网络流量经由模拟器代理（mitmproxy）观察。

| # | 端点 | 方法 | 预期内容 | 违规信号 |
|---|------|------|---------|---------|
| E1 | `/api/v1/auth/device` | POST | 仅 `deviceId`（UUID 格式，如 `f47ac10b-58cc-4372-a567-0e02b2c3d479`）、`clientVersion`、`platform`。无硬件 ID | 出现 IMEI、GAID、AndroidId；deviceId 非 UUID 格式 |
| E2 | `/api/v1/characters` | GET | Authorization header 含 Bearer token；响应为角色列表 JSON，无聊天内容 | 响应 body 出现任何用户输入文本 |
| E3 | `/api/v1/purchase/verify` | POST | body 含 `characterId`、`channel`、`paidAmount`。无聊天内容 | 出现用户对话内容 |
| E4 | `/api/v1/sync/{userId}` | GET/POST | body 仅含 `nickname`、`profileJson`（likes/dislikes/mood/importantDates）。**不包含 chatMessage 数组** | profileJson 出现 `"content"` 字段；出现对话原文 |
| E5 | 任意 WebSocket 连接 | — | Phase 1 无 WebSocket，验证无 `ws://` 或 `wss://` 连接（Phase 2 OpenClaw 才引入） | 存在 ws 连接且含聊天数据 |

---

## 日志检查（代码层面）

### 需要扫描的文件

使用 Grep 工具扫描以下路径，禁止出现任何聊天原文日志语句：

```bash
# 检查 Log.d / Log.i / Log.e 是否输出用户消息内容
grep -rn "Log\.[diwe]" android/app/src/main/java/com/aiyougame/companion/ \
  --include="*.kt" \
  | grep -v "// " \
  | grep -E "(message|input|content|response|chat)" \
  | grep -v "message.*=" # 排除合法的 UI 消息变量名
```

### 允许的日志语句

```kotlin
// ✅ 允许：日志标签 + 非对话内容
Log.d("AigameApp", "Phase 1 - App Started")
Log.w("AigameApp", "Memory low (level $level)")

// ✅ 允许：错误处理中的通用消息
Log.e("NetworkModule", "Request failed: ${e.message}")

// ❌ 禁止：直接打印用户输入或模型输出
Log.d("ChatVM", "user input: $inputText")
Log.i("LlamaEngine", "response: $fullText")
```

---

## 首次启动隐私协议展示验证

### 检查步骤

1. 清除 App 数据（`adb shell pm clear com.aiyougame.companion`）
2. 重启 App
3. **预期**：在进入主界面之前，弹出隐私协议展示 UI
4. 验证隐私协议内容包含：
   - 聊天记录仅存本地、永不上传
   - 设备 ID 为 App 生成的 UUID
   - 云同步默认关闭
   - 联系方式：support@aiyougame.com

### 隐私协议 UI 检查点

| 检查项 | 预期行为 |
|--------|---------|
| 首次启动展示隐私协议 | 是 |
| 有"同意并继续"按钮 | 是 |
| 有"查看完整隐私政策"入口 | 是 |
| 拒绝同意时退出 App | 是 |

---

## 通过标准

- [ ] **P1 ~ P4 零容忍项**：全部未触发
- [ ] **E1 ~ E5 验证端点**：全部通过，无违规内容
- [ ] **日志扫描**：无违规日志语句
- [ ] **首次启动隐私协议**：展示正常，内容完整
- [ ] **云同步默认关闭**：代码层面确认 `syncEnabled = false` 为默认值

### 合格输出示例

```
=== 隐私合规 E2E 检查报告 ===
检查日期：2026-04-10
检查人：测试工程师

[零容忍项]
  P1 ChatMessage 网络上传    ✅ 未发现
  P2 完整 deviceId 泄露       ✅ 未发现
  P3 IMEI/GAID/AndroidId     ✅ 未发现
  P4 聊天原文日志             ✅ 未发现

[验证端点]
  E1 /auth/device            ✅ 符合预期
  E2 /characters            ✅ 符合预期
  E3 /purchase/verify       ✅ 符合预期
  E4 /sync/:userId           ✅ 符合预期（无 chatMessage）
  E5 WebSocket               ✅ 无 WebSocket 连接

[日志扫描]
  违规日志语句               ✅ 未发现

[隐私协议]
  首次启动展示               ✅ 正常
  内容完整性                 ✅ 完整

结论：✅ 通过，建议上架
```

---

## 附录：常见违规模式参考

| 模式 | 违规描述 |
|------|---------|
| `ANDROID_ID` 或 `Secure.getString(context.contentResolver, "android_id")` | 读取 Android 硬件 ID |
| `TelephonyManager.getDeviceId()` | 读取 IMEI |
| `AdvertisingIdClient.getAdvertisingIdInfo()` | 读取 GAID |
| `"deviceId": "86a..."` 在请求 body | 真实硬件 ID 上传 |
| `Log.d("Chat", "user: $input")` | 用户输入进入日志 |
| `Log.d("LLM", "raw: $output")` | 模型输出进入日志 |
| `syncProfile(chatMessages = ...)` | 尝试上传聊天消息 |
