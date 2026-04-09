# 隐私合规文档

> 本文档规范 AI乙游陪伴 App 的隐私数据处理策略，确保符合中国《个人信息保护法》《数据安全法》《生成式人工智能服务管理暂行办法》等法规要求。

---

## 一、核心原则

| 原则 | 说明 |
|------|------|
| **数据最小化** | 只收集业务必需的数据，不收集与功能无关的信息 |
| **本地优先** | 用户数据优先存储在设备本地，不上传云端 |
| **用户知情** | 明确告知用户收集哪些数据、为什么收集、如何使用 |
| **隐私授权** | 涉及敏感数据的操作必须用户明确授权 |
| **可撤回** | 用户可随时关闭云同步，清除本地数据 |

---

## 二、数据分类与处理策略

### 2.1 隐私敏感数据（严格保护）

| 数据类型 | 示例 | 处理策略 | 是否上传 |
|---------|------|---------|---------|
| 聊天原文 | 用户与角色的对话内容 | 仅存本地 SQLite，不上传 | ❌ 永不上传 |
| 语音输入 | 麦克风录制的音频 | Phase 1 不做；Phase 2 本地 Whisper | ❌ 永不上传 |
| 设备标识 | IMEI / GAID / MAC 地址 | 禁止采集，使用客户端生成的 UUID | ❌ 永不上传 |
| 位置信息 | GPS 坐标 | 不采集 | ❌ 永不上传 |
| 通讯录 | 联系人信息 | 不采集 | ❌ 永不上传 |
| 好感度 | 0–100 数值 | 仅存本地 | ❌ 永不上传 |

### 2.2 业务必需数据（用户授权后上传）

| 数据类型 | 示例 | 上传条件 | 传输方式 |
|---------|------|---------|---------|
| 脱敏设备 ID | 客户端 UUID v4 | 设备注册时 | TLS 加密 |
| 昵称 | 用户自己设置的称呼 | 用户主动同步时 | TLS 加密 |
| 结构化喜好 | likes/dislikes 数组 | 用户手动开启云同步 | TLS 加密 |
| 重要日期 | 生日、纪念日 | 用户手动开启云同步 | TLS 加密 |
| 购买记录 | 已购角色列表 | 支付成功时 | TLS 加密 |

### 2.3 技术统计数据（脱敏采集，需用户同意）

| 数据类型 | 示例 | 用途 | 是否关联用户 |
|---------|------|------|------------|
| 模型推理速度 | token/s | 性能优化 | ❌ 完全匿名 |
| App 崩溃日志 | 异常堆栈 | Bug 修复 | ⚠️ 匿名 Token |
| 功能使用率 | 按钮点击次数 | 产品改进 | ⚠️ 匿名 Token |

---

## 三、隐私协议要求

App 内必须展示《隐私政策》，包含以下章节：

### 3.1 必须包含的内容

```markdown
## 一、信息收集
我们收集以下信息以提供服务：

【我们不收集的内容】
- 您的聊天记录（全部存储在您的设备本地）
- 您的语音数据（Phase 1 不提供语音功能）
- 您的真实设备标识（IMEI/GAID）

【我们收集的内容】
- 脱敏设备标识（App 生成的随机 UUID，用于账号关联）
- 您主动同步的结构化记忆数据（昵称、喜好等）
- 购买记录（支付平台提供）

## 二、信息存储
- 聊天记录：存储在您设备的本地数据库，不会上传至我们的服务器
- 云同步：您开启云同步后，我们仅存储结构化的记忆摘要，不存储聊天原文

## 三、信息共享
我们不会将您的个人信息共享给任何第三方广告商或数据分析公司。

## 四、您的权利
- 可随时关闭云同步功能
- 可联系客服申请删除您的账号及所有关联数据
- 可导出您的本地聊天记录（设备文件管理）
```

### 3.2 首次启动授权流程

```
┌─────────────────────────────────────────────────────┐
│                   首次启动                          │
│                                                      │
│  1. 展示隐私协议摘要                                 │
│     「聊天记录永不上传，详见隐私政策」                  │
│                                                      │
│  2. 两个选项：                                       │
│     [同意并继续]  [查看完整隐私政策]                   │
│                                                      │
│  3. 用户点击「同意」→ 进入 App                       │
│     用户点击「查看」→ 打开完整隐私协议 WebView         │
│                                                      │
│  ⚠️ 拒绝同意 → 退出 App（无法使用）                   │
└─────────────────────────────────────────────────────┘
```

---

## 四、国内合规要求

### 4.1 生成式 AI 备案

| 要求 | 实施方式 | 状态 |
|------|---------|------|
| 算法备案 | 填写《生成式人工智能服务备案表》| 需跟进 |
| 安全评估 | 依据《互联网信息服务深度合成管理规定》| 需跟进 |
| 模型说明 | App 内标注：「AI 能力基于 Google Gemma 4 开源模型构建」| ✅ 实施 |

**备案说明**：
- Gemma 4 使用 Apache 2.0 授权，可商用
- 端侧小参数模型（≤ 7B）的监管风险较低
- 建议在上架前完成备案申请

### 4.2 应用市场上架

| 平台 | 合规要点 | 备注 |
|------|---------|------|
| 华为应用市场 | 隐私政策 URL 必须可访问 | 需准备 Web 页面 |
| 小米应用商店 | 需要软著证书 | 需提前申请 |
| OPPO / vivo | 同华为 | — |
| 应用宝 | 需企业主体 | — |
| App Store | 本地 AI 推理可上架 | 内容合规即可 |

### 4.3 内容安全

App 内必须实现内容安全过滤，在**模型输出到 UI 之前**执行拦截：

```kotlin
// ContentSafetyManager.kt

object ContentSafetyManager {

    // ── 政治敏感词库（按类别分组，非穷举示例）───────────
    private val POLITICAL_PATTERNS = listOf(
        // 绝对红线：暴力恐怖分裂
        Regex("(?i)分裂国家|暴力恐怖|极端主义"),
        Regex("(?i)推翻政府|政治敏感内容"),
    )

    // ── 低俗内容词库 ⚠️ 占位符，Phase 1 上线前必须替换 ──────────────
    // ⚠️ 强制：这些正则不能拦截任何真实低俗内容，上线直接用=裸奔
    // Phase 1 方案：接入网易易盾 / 腾讯防水墙 REST API
    // Phase 2 方案：私有化词库部署
    private val VULGAR_PATTERNS = listOf(
        // ❌ 占位符，无拦截能力（字面描述，不是真实正则）
        // Regex("(?i)色情低俗相关词"),  // ← 删除这行占位符
        // ✅ 示例：真实正则（按需扩展，不要依赖这些示例词）
        Regex("(?i)嫖娼|卖淫|约炮"),
    )

    // ── 暴力内容词库 ⚠️ 占位符 ───────────────────────────────────
    // ⚠️ 强制：Phase 1 上线前必须接入真实词库
    private val VIOLENCE_PATTERNS = listOf(
        // ❌ 占位符，无拦截能力
        // Regex("(?i)暴力描述相关词"),  // ← 删除这行占位符
        // ✅ 示例：真实正则（按需扩展）
        Regex("(?i)杀人|虐待|折磨"),
    )

    // ── 自残自杀 ───────────────────────────────────────
    private val SELF_HARM_PATTERNS = listOf(
        Regex("(?i)自杀方法|自残指导"),
    )

    /**
     * 检查输出内容，返回安全结果
     * ⚠️ 在模型输出到 UI 之前调用
     */
    fun check(text: String): SafetyResult {
        return when {
            SELF_HARM_PATTERNS.any { it.containsMatchIn(text) } -> {
                SafetyResult.Blocked(category = "SELF_HARM", reason = "包含自残相关内容")
            }
            POLITICAL_PATTERNS.any { it.containsMatchIn(text) } -> {
                SafetyResult.Blocked(category = "POLITICAL", reason = "包含敏感政治内容")
            }
            VULGAR_PATTERNS.any { it.containsMatchIn(text) } -> {
                SafetyResult.Blocked(category = "VULGAR", reason = "包含低俗内容")
            }
            VIOLENCE_PATTERNS.any { it.containsMatchIn(text) } -> {
                SafetyResult.Blocked(category = "VIOLENCE", reason = "包含暴力内容")
            }
            else -> SafetyResult.Pass
        }
    }

    /**
     * 越狱检测：Prompt 注入模式
     */
    fun detectPromptInjection(input: String): Boolean {
        val injectionPatterns = listOf(
            Regex("(?i)ignore previous instructions", RegexOption.IGNORE_CASE),
            Regex("(?i)你是一个.{0,10}角色，现在请你", RegexOption.IGNORE_CASE),
            Regex("(?i)system prompt:|## Instructions:", RegexOption.IGNORE_CASE),
            Regex("(?i)你现在可以.{0,20}任何事", RegexOption.IGNORE_CASE),
        )
        return injectionPatterns.any { it.containsMatchIn(input) }
    }
}

sealed class SafetyResult {
    data object Pass : SafetyResult()
    data class Blocked(
        val category: String,
        val reason: String
    ) : SafetyResult()
}
```

**调用位置（在 LlamaScheduler 中）**：

```kotlin
// LlamaScheduler.kt

suspend fun generateResponse(input: String): Flow<ParseResult> = flow {
    // 越狱检测：用户输入阶段
    if (ContentSafetyManager.detectPromptInjection(input)) {
        emit(ParseResult.Error("内容安全检测未通过"))
        return@flow  // ✅ 在 flow {} 里用 return@flow，不能 return flowOf()
    }

    // ... 推理 ...

    // 模型输出过滤：输出到 UI 之前
    yield() // 让出协程
    val safety = ContentSafetyManager.check(fullText)
    if (safety is SafetyResult.Blocked) {
        emit(ParseResult.Error("抱歉，相关内容无法展示。"))
        return@flow  // ✅ 同上
    }
}.flowOn(Dispatchers.IO)
```

**第三方内容安全服务（推荐）**：

| 服务商 | 说明 | 接入方式 |
|-------|------|---------|
| 网易易盾 | 国内合规词库，支持私有化部署 | REST API / Android SDK |
| 腾讯防水墙 | 微信同源，覆盖全面 | REST API |
| 百度内容审核 | 支持文本/图片/视频 | REST API / SDK |
| 阿里云内容安全 | 绿网升级版 | REST API |

> **Phase 1 要求**：必须接入第三方内容安全服务（按调用量付费），仅靠正则词库**禁止上架**。

---

## 五、技术实现要求

### 5.1 设备 ID 脱敏

```kotlin
// DeviceIdManager.kt

object DeviceIdManager {
    private const val KEY_DEVICE_ID = "aiyougame_device_id"

    fun getOrCreate(context: Context): String {
        val prefs = context.getSharedPreferences("device", MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
            newId
        }
    }
}
```

### 5.2 云同步默认关闭

```kotlin
class SyncPrefs(private val prefs: SharedPreferences) {
    // 云同步默认关闭
    var syncEnabled: Boolean
        @Suppress("UNUSED")
        get() = prefs.getBoolean("cloud_sync_enabled", false)
        set(value) {
            // ⚠️ 错误写法（property setter 禁止异步 UI）：
            // if (value) { showConfirmationDialog() }
            // prefs.edit().putBoolean("cloud_sync_enabled", value).apply()  // 不等确认就写了
            //
            // 正确写法：把 setter 保持纯数据操作，异步确认逻辑移到 ViewModel：
            //   class CloudSyncViewModel {
            //       fun requestEnableSync() {
            //           viewModelScope.launch { if (confirm("确认开启云同步？")) syncPrefs.syncEnabled = true }
            //       }
            //   }
            prefs.edit().putBoolean("cloud_sync_enabled", value).apply()
        }
}
```

### 5.3 数据删除

```kotlin
// 用户申请注销账号时
suspend fun deleteUserData(userId: String) {
    // 1. 删除本地数据
    database.clearAllTables()

    // 2. 删除云端数据
    api.deleteUserAccount()

    // 3. 清除设备 ID（下次启动生成新的）
    prefs.edit().clear().apply()
}
```

---

## 六、检查清单

### 上架前必须完成

- [ ] 隐私政策 Web 页面上线（URL 必须可访问）
- [ ] App 内标注 Gemma 4 模型来源
- [ ] 首次启动隐私协议授权 UI
- [ ] 云同步默认关闭 + 二次确认
- [ ] 内容安全第三方服务接入（⚠️ Phase 1 ⚠️ 占位符词库禁止上线）
- [ ] 设备 ID 脱敏验证（抓包确认无 IMEI 上传）
- [ ] 聊天原文零上传验证（Charles / mitmproxy 抓包）
- [ ] 软著证书申请（应用市场上架必需）
- [ ] 生成式 AI 备案申请

### 开发过程中持续遵守

- [ ] 新增任何数据采集必须经过隐私评审
- [ ] 第三方 SDK 接入必须确认其隐私合规
- [ ] 重大功能变更更新隐私政策
