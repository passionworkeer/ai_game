# Phase 1 性能基线文档

> 本文档定义 AI乙游陪伴 App Phase 1 的性能基准和测量方法。
> 目标读者：测试工程师、Android 性能优化工程师。

---

## 1. Phase 1 性能目标

Phase 1 简化版不含本地 AI 推理（LlamaEngine 尚未集成），性能基准聚焦于：

| 指标 | 目标值 | 说明 |
|------|--------|------|
| App 冷启动时间 | **≤ 2 秒** | 从点击图标到主界面渲染完成（无模型加载阶段） |
| API 请求成功率 | **≥ 95%** | 5 次测试，至少 5 次成功 |
| API 平均延迟（P50） | **≤ 500ms** | 后端单接口响应（模拟器 → localhost:3000） |
| Room 写入（100 条 ChatMessage） | **≤ 100ms** | 100 条消息批量插入耗时 |
| Room 查询（100 条 ChatMessage） | **≤ 100ms** | 按 characterId 批量查询耗时 |

> **Phase 2 展望**：LlamaEngine 集成后，冷启动将增加模型加载阶段（预计 5-15s，取决于设备档次），LlamaScheduler 将引入 token/s 推理速度指标。

---

## 2. 测量环境

### 2.1 硬件要求

| 组件 | 最低配置 | 推荐配置 |
|------|---------|---------|
| 测试设备 | Android 模拟器（API 34） | 真机（8GB RAM，Android 12+） |
| CPU | x86_64 模拟器 | 高通骁龙 8+ / 天玑 9000+ |
| 网络 | 模拟器直连宿主机（10.0.2.2:3000） | - |
| 后端 | localhost PostgreSQL + NestJS | 同物理机 |

### 2.2 软件版本

| 软件 | 版本 |
|------|------|
| Android minSdk | 26（Android 8.0） |
| Android targetSdk | 34（Android 14） |
| Kotlin | 1.9.22 |
| Room | 2.5.2 |
| Retrofit | 2.9.0 |
| OkHttp | 4.12.0 |
| Compose BOM | 2024.02.00 |
| 后端 Node.js | 20 LTS |
| PostgreSQL | 16 |

### 2.3 工具链

```bash
# Android Profiler（Android Studio 内置）
# 用于 CPU / Memory / Network 实时分析

# mitmproxy（用于网络延迟统计）
pip install mitmproxy

# Jetpack Benchmark（用于 Room 性能测试）
# 已通过 testImplementation 引入，无需额外安装

# 内置 JUnit + Turbine（用于 ViewModel StateFlow 测试）
# 已通过 testImplementation 引入
```

---

## 3. 测试用例

### 3.1 Room 性能测试

测试文件：`android/app/src/test/java/com/aiyougame/companion/PerformanceTest.kt`

**前提**：Room 数据库实体和 DAO 已就绪：
- `ChatMessageEntity`（id, characterId, role, content, timestamp）
- `ChatMessageDao`（insert, queryByCharacter, deleteAll）
- `AppDatabase`

**测试用例：**

| # | 用例 | 验收标准 | 预期耗时 |
|---|------|---------|---------|
| T1 | 100 条 ChatMessage 批量插入 | 耗时 < 100ms | ≤ 100ms |
| T2 | 100 条 ChatMessage 按 characterId 查询 | 耗时 < 100ms | ≤ 100ms |
| T3 | 1 条 ChatMessage 单独插入 + 读取 | 耗时 < 10ms | ≤ 10ms |
| T4 | 删除全部消息 | 耗时 < 50ms | ≤ 50ms |

**运行方式：**

```bash
cd android
./gradlew test --tests "com.aiyougame.companion.PerformanceTest"
```

### 3.2 API 性能测试

测试文件：`android/app/src/test/java/com/aiyougame/companion/ApiPerformanceTest.kt`（需新建）

| # | 用例 | 验收标准 | 预期耗时 |
|---|------|---------|---------|
| T5 | GET /characters | P50 < 500ms | ≤ 500ms |
| T6 | POST /auth/device | P50 < 300ms | ≤ 300ms |
| T7 | POST /purchase/verify | P50 < 500ms | ≤ 500ms |
| T8 | GET /sync/{userId} | P50 < 500ms | ≤ 500ms |

**运行方式：**

```bash
# 启动后端
cd backend && npm run start:dev

# 运行测试
cd android
./gradlew test --tests "com.aiyougame.companion.ApiPerformanceTest"
```

### 3.3 冷启动测试

使用 Android Studio 的 **CPU Profiler** 或以下命令：

```bash
# 通过 ActivityManager 测量（近似值）
adb shell am start -W -n com.aiyougame.companion/.MainActivity \
  | grep "TotalTime"
```

| # | 用例 | 验收标准 | 预期耗时 |
|---|------|---------|---------|
| T9 | App 冷启动（首次点击图标到主界面渲染） | ≤ 2 秒 | ≤ 2000ms |

---

## 4. 内存基准

> Phase 1 无 AI 推理，内存压力主要来自 Compose UI + Room 数据库。

| 指标 | 安全阈值 | 告警阈值 |
|------|---------|---------|
| App 进程内存（Java/Kotlin 堆） | **≤ 200MB** | > 200MB |
| C++ 堆（Phase 2 LlamaEngine） | **Phase 2 单独测量** | - |
| 总进程内存（含 C++ 堆） | Phase 1 不限制 | **> 2.5GB**（内存红线） |

**测量方法：**

```bash
# 查看 App 内存使用
adb shell dumpsys meminfo com.aiyougame.companion | grep "TOTAL"

# 记录空闲时内存和峰值内存
adb shell dumpsys meminfo com.aiyougame.companion
```

---

## 5. API 成功率统计

### mitmproxy 统计方法

```bash
# 启动 mitmproxy 并保存流量
mitmproxy --listen-port 8080 -w api_test.mitm

# 测试后生成报告
mitmdump --flow-file api_test.mitm --save-stream-folder ./captures

# 或使用 Python 脚本解析
python3 -c "
import mitmproxy.io
from collections import Counter

with open('api_test.mitm', 'rb') as f:
    flows = list(mitmproxy.io.read_flows_from_file(f))

total = len([f for f in flows if hasattr(f, 'request')])
success = len([f for f in flows if f.response and f.response.status_code in (200, 201)])
rate = success / total * 100 if total > 0 else 0
print(f'Total: {total}, Success: {success}, Rate: {rate:.1f}%')
"
```

### 手动 curl 测试

```bash
BASE="http://10.0.2.2:3000/api/v1"
TOKEN=$(curl -s -X POST "$BASE/auth/device" \
  -H "Content-Type: application/json" \
  -d '{"deviceId":"test-'"$(date +%s)"'","clientVersion":"1.0","platform":"android"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

echo "Token: ${TOKEN:0:20}..."

# Test all endpoints
curl -s -o /dev/null -w "%{http_code}" "$BASE/characters"
curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" "$BASE/purchases"
```

---

## 6. Phase 2 性能优化方向（预告）

Phase 1 验收通过后，以下优化方向供后续参考：

| 方向 | 措施 | 预期收益 |
|------|------|---------|
| 模型 CDN 分发 | GGUF 首次启动从 CDN 下载，存 `files/models/` | APK 体积减少 4.6GB |
| 增量更新 | 模型分片下载，断点续传 | 减少重复下载 |
| 模型缓存策略 | 模型文件不清理（Phase 2 首次启动后永久保留） | 二次启动无下载 |
| 首帧优化 | Compose LazyColumn 虚拟化，减少重组 | 聊天滚动性能 |
| Room 索引 | ChatMessageDao 加 `(characterId, timestamp)` 索引 | 查询提速 50%+ |
| LlamaEngine 内存管理 | `freeEngine` 双保险调用 | 防止 OOM |

---

## 7. 性能基线记录表

> 每次测试后填写此表，作为历史性能追踪依据。

| 日期 | 测试人 | 冷启动 T9 | Room 写入 T1 | Room 查询 T2 | API 成功率 | 通过？ |
|------|--------|----------|------------|------------|-----------|--------|
| 2026-04-10 | （待填写） | — ms | — ms | — ms | — % | — |
| | | | | | | |
| | | | | | | |
