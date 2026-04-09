# OpenClaw 对接协议

> Android App ↔ OpenClaw PC 客户端 WebSocket 直连协议。
> **不经过后端服务器**。后端只负责 Auth / 购买 / 云同步。
>
> 本文档合并自 `openclaw_integration/SPEC.md` + 原 `docs/OPENCLAW.md`。

---

## 一、连接架构

```
Android 手机                          PC (OpenClaw)
┌──────────────────────┐    mDNS     ┌────────────────────────────┐
│ PcDiscoveryManager  │ ────────── │ mDNS: _aiyougame._tcp.local  │
│ WebSocket Client   │◄───────────│ 端口 8080                    │
└────────────────────┘            │ WebSocket Server             │
                                   │ 任务执行引擎                  │
                                   │ （Python / Node.js）          │
                                   └────────────────────────────┘
```

**设计原则：**
- 手机 ↔ PC：局域网 WebSocket 直连，**不经过后端**
- 后端：只负责 Auth / 购买 / 云同步记忆
- OpenClaw：由 OpenClaw 项目实现，本项目只对接

---

## 二、mDNS 服务发现

### 约定

| 字段 | 值 |
|------|---|
| Service Type | `_aiyougame._tcp.local` |
| Service Name | `OpenClaw-PC`（可配置）|
| Port | `8080` |

### Android 端（NsdManager）

```kotlin
class PcDiscoveryManager(private val nsdManager: NsdManager) {
    companion object {
        private const val SERVICE_TYPE = "_aiyougame._tcp.local"
        private const val SERVICE_NAME = "OpenClaw-PC"
    }

    fun discover(): Flow<PcDevice> = callbackFlow {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                trySend(PcDevice.State.Searching)
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceName == SERVICE_NAME) {
                    nsdManager.resolveService(serviceInfo, resolveListener)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                trySend(PcDevice.State.Lost)
            }

            override fun onDiscoveryStopped(serviceType: String) { close() }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                trySend(PcDevice.State.Error("mDNS 启动失败"))
                close()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { nsdManager.stopServiceDiscovery(listener) }
    }

    // 注意：resolveListener 必须是 callbackFlow 外部类的成员，
    // 不能是匿名内部类（否则 trySend 作用域会报错）
}

sealed class PcDevice {
    sealed class State : PcDevice() {
        data object Searching : State()
        data class Found(val host: String, val port: Int) : State()
        data object Lost : State()
        data class Error(val message: String) : State()
    }
}
```

> **⚠️ 实现注意**：`resolveListener` 中 `trySend` 引用外部 `callbackFlow`，不能写成匿名内部类。

### PC 端注册（OpenClaw 项目实现）

```python
# OpenClaw PC 端注册 mDNS 服务
import zeroconf
zc = zeroconf.Zeroconf()
zc.register_service({
    "name": "OpenClaw-PC",
    "type": "_aiyougame._tcp.local.",
    "port": 8080,
    "properties": {
        "version": "1.0",
        "platform": "windows"  # windows | macos
    }
})
```

---

## 三、WebSocket 连接

### 连接地址

```
ws://<PC_IP>:8080/ws
```

### 心跳（30 秒间隔）

```json
// 客户端 → 服务端
{ "type": "ping" }

// 服务端 → 客户端
{ "type": "pong" }
```

### 断线重连

- 自动重连 3 次（退避：2s → 4s → 8s）
- WebSocket 断开后运行中任务自动终止

---

## 四、消息协议

### 4.1 手机 → PC（任务下发）

```json
{
  "type": "task",
  "taskId": "uuid-v4",
  "taskType": "file_search",
  "parameters": {
    "query": "上个月的财务报表",
    "target_device": "local_pc"
  },
  "replyText": "稍等宝宝，我去帮你找报表~"
}
```

### 4.2 PC → 手机（任务结果）

**成功：**
```json
{
  "type": "task_result",
  "taskId": "uuid-v4",
  "status": "completed",
  "result": {
    "files": [
      {
        "path": "C:/Users/xxx/Desktop/finance/report.xlsx",
        "name": "report.xlsx",
        "size": 102400,
        "modified": "2026-03-01T10:30:00Z"
      }
    ],
    "summary": "找到了 3 个相关文件"
  }
}
```

**失败：**
```json
{
  "type": "task_result",
  "taskId": "uuid-v4",
  "status": "failed",
  "error": {
    "code": "FILE_NOT_FOUND",
    "message": "未找到符合条件的文件"
  }
}
```

---

## 五、taskType 枚举

| taskType | 说明 | parameters |
|---------|------|-----------|
| `file_search` | 文件搜索 | `{ query: string }` |
| `file_read` | 读取文件内容 | `{ path: string, max_lines?: number }` |
| `web_search` | 网页搜索 | `{ query: string }` |
| `schedule_check` | 日程查询 | `{}` |
| `reminder_set` | 设置提醒 | `{ time: string, content: string }` |

---

## 六、Android 端 AgentRouter 实现

```kotlin
class AgentRouter(
    private val pcDiscovery: PcDiscoveryManager,
    private val okHttpClient: OkHttpClient
) {
    private var cachedPc: PcDevice.State.Found? = null
    private var webSocket: WebSocket? = null
    private val pending = ConcurrentHashMap<String, CompletableDeferred<TaskResult>>()
    private val gson = Gson()

    suspend fun route(contract: AgentContract): TaskResult {
        // 发现 PC（最多等 5s）
        val pc = cachedPc ?: discoverPc(5_000)
            ?: return TaskResult.Failed("PC 未发现，请在设置页手动输入 IP。")

        ensureConnected(pc)

        val taskId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<TaskResult>()
        pending[taskId] = deferred

        val taskMsg = TaskMessage(
            type = "task",
            taskId = taskId,
            taskType = contract.taskType.name.lowercase(),
            parameters = contract.parameters,
            replyText = contract.replyText
        )

        val sent = webSocket?.send(gson.toJson(taskMsg)) ?: false
        if (!sent) return TaskResult.Failed("发送失败")

        // 60s 超时
        return withTimeoutOrNull(60_000) { deferred.await() }
            ?: TaskResult.Failed("任务超时（60s）")
    }

    private suspend fun discoverPc(timeout: Long): PcDevice.State.Found? {
        val deadline = System.currentTimeMillis() + timeout
        pcDiscovery.discover().collect { state ->
            if (state is PcDevice.State.Found) {
                cachedPc = state
                return state
            }
            if (System.currentTimeMillis() > deadline) {
                return@collect  // 超时后继续等待下一次发现
            }
        }
        return cachedPc
    }

    private fun ensureConnected(pc: PcDevice.State.Found) {
        if (webSocket != null) return
        val request = Request.Builder()
            .url("ws://${pc.host}:${pc.port}/ws")
            .build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = gson.fromJson(text, TaskResultMessage::class.java)
                pending.remove(msg.taskId)?.complete(msg.toResult())
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                this@AgentRouter.webSocket = null
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                this@AgentRouter.webSocket = null
                cachedPc = null
            }
        })
    }
}

data class TaskMessage(
    val type: String,
    val taskId: String,
    val taskType: String,
    val parameters: Map<String, @Composable Any>,
    val replyText: String
)

data class TaskResultMessage(
    val type: String,
    val taskId: String,
    val status: String,
    val result: Map<String, Any>?,
    val error: Map<String, String>?
) {
    fun toResult() = when (status) {
        "completed" -> TaskResult.Success(gson.toJson(result))
        else -> TaskResult.Failed(error?.get("message") ?: "未知错误")
    }
}

sealed class TaskResult {
    data class Success(val value: String) : TaskResult()
    data class Failed(val message: String) : TaskResult()
}
```

---

## 七、Agent Contract（LLM JSON 协议）

### GBNF 语法定义（ERR-03：JSON 错误率 < 0.1%）

```bnf
root ::= text-reply | tool-call
text-reply ::= [^{] .*
tool-call ::= "{" ws "\"intent\"" ws ":" ws "\"tool_call\"" ws ","
              ws "\"task_type\"" ws ":" ws string ws ","
              ws "\"parameters\"" ws ":" ws object ws ","
              ws "\"reply_text\"" ws ":" ws string ws "}"
object ::= "{" (string ":" value ("," string ":" value)*)? "}"
string ::= "\"" [^"]* "\""
value ::= string | number | object | array | "true" | "false" | "null"
number ::= "-"? [0-9]+ ("." [0-9]+)?
array ::= "[" (value ("," value)*)? "]"
ws ::= [ \t\n]*
```

### Android 端 StreamParser 实现要点

```
Token 流输入
    ↓
检测到非 { 字符 → TextChunk → 渲染到气泡
    ↓
检测到 { 开头 → 缓冲（Buffering 状态）
    ↓
JSON 完整 + GBNF 校验通过 → ToolCall → AgentRouter.route()
    ↓
JSON 校验失败 → 丢弃 buffer，重置为普通文本渲染
```

---

## 八、错误处理

| 场景 | 处理 |
|------|------|
| mDNS 发现失败 | 提示用户检查 OpenClaw，显示手动 IP 入口 |
| WebSocket 连接失败 | 自动重连 3 次（2s/4s/8s 退避）|
| 任务执行超时 | 60s 超时，角色口吻告知 |
| OpenClaw 返回错误 | 提取 message，包装角色口吻 |
| PC 端关闭 | 监听 onClosed，断开后提示 |

---

## 九、安全约束

| 约束 | 说明 |
|------|------|
| 仅局域网 | WebSocket URL 为 `ws://192.168.x.x`，无公网暴露 |
| 权限白名单 | PC 端只允许白名单内操作（禁止执行任意命令）|
| 二次确认 | 高危操作（删除文件等）需 PC 端二次确认 |
| 断线即停 | WebSocket 断开后运行中任务自动终止 |
