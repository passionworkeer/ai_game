# 数据库设计文档

---

## 一、PostgreSQL（后端）— Prisma Schema

### 简化说明
- **删除了 `SyncRecord` 表**：云同步只存一份结构化 profile，直接放在 `User.profileJson` 字段
- **简化购买验证**：Phase 1 只记录购买状态，不做实时 AES 密钥下发

### 部署信息
- 本地开发：`PostgreSQL 16`，端口 `5432`
- 数据库名：`aiyougame_dev`

---

### Schema 定义

```prisma
// prisma/schema.prisma

generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}

// ─────────────────────────────────────────────────────────────
// 用户
// ─────────────────────────────────────────────────────────────
model User {
  id        String   @id @default(uuid())
  /// 脱敏设备标识：由客户端生成 UUID v4，存储在 Android Keystore
  /// 不使用真实 IMEI/GAID，严格与真实设备脱敏
  deviceId  String   @unique
  /// 用户昵称（来自客户端同步）
  nickname  String   @default("宝宝")
  /// 云端备份的结构化 profile（JSON）
  /// 仅包含：likes/dislikes/currentMood/importantDates，不含聊天原文
  profileJson String @default("{}")
  createdAt DateTime @default(now())
  updatedAt DateTime @updatedAt

  /// 已购买的角色
  purchases Purchase[]

  @@map("users")
}

// ─────────────────────────────────────────────────────────────
// 角色
// ─────────────────────────────────────────────────────────────
model Character {
  id          String   @id @default(uuid())
  /// 角色代码，对应资源目录名，如 "gu_chen"
  code        String   @unique
  /// 显示名称，如 "顾晨"
  name        String
  /// 角色描述（展示用）
  description String
  /// 价格（单位：分，5800 = 58元）
  price       Int
  /// 预览图 URL
  previewUrl  String?
  /// 角色资源下载地址（GGUF 模型）
  assetsUrl   String
  /// 角色人设 Prompt（System Prompt 全文）
  systemPrompt String
  /// 是否上线
  isActive    Boolean  @default(true)
  createdAt   DateTime @default(now())

  purchases Purchase[]

  @@map("characters")
}

// ─────────────────────────────────────────────────────────────
// 购买记录
// Phase 1：只记录状态，联网校验一次
// Phase 2：才加入 AES 密钥下发
// ─────────────────────────────────────────────────────────────
model Purchase {
  id              String    @id @default(uuid())
  userId          String
  characterId     String
  /// 支付渠道：wechat | alipay | apple
  channel         String
  /// 渠道订单号
  channelOrderId  String?
  /// 支付金额（分）
  amount          Int
  /// 支付状态：pending | completed | refunded
  status          String    @default("pending")
  paidAt          DateTime?
  /// Phase 1 简化为空，Phase 2 才存放加密 AES 密钥
  encryptedAesKey String?    @db.Text
  createdAt       DateTime  @default(now())

  user        User      @relation(fields: [userId], references: [id])
  character   Character @relation(fields: [characterId], references: [id])

  @@unique([userId, characterId])
  @@map("purchases")
}
```

### 主要变更对比

| 项目 | 原设计 | 修订后 |
|------|--------|--------|
| SyncRecord 表 | 独立表存记忆摘要 | 删除，用 `User.profileJson` 替代 |
| 购买验证 | 下发 AES 加密密钥 | 记录状态，Phase 1 不下发密钥 |
| User 表 | 只有 id + deviceId | 增加 nickname + profileJson 字段 |

---

## 二、Android Room（本地）— Entity 定义

### 数据库信息
- 文件位置：`/data/data/com.aiyougame/databases/aiyougame.db`
- 版本：1（Migration 后续扩展）

---

### Entity 定义

```kotlin
// memory/db/ChatMessage.kt

@Entity(
    tableName = "chat_history",
    indices = [Index(value = ["timestamp"])]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val msgId: Long = 0,

    /// "user" | "model"
    val role: String,

    /// 消息原文
    val content: String,

    /// System.currentTimeMillis()
    val timestamp: Long,

    /// 是否已被提炼进长期记忆
    val isSummarized: Boolean = false,

    /// 所属角色 code（多角色支持）
    val characterCode: String = "gu_chen",

    /// 所属用户 localId
    val userId: String = "local"
)
```

```kotlin
// memory/db/UserProfile.kt

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey
    val userId: String = "local",

    /// 用户昵称，AI 叫她昵称或"宝宝"
    val nickname: String = "宝宝",

    /// 好感度 0–100，影响对话语气
    val affectionScore: Int = 50,

    /// JSON 数组，如 ["奶茶", "猫", "漫威电影"]
    val likes: String = "[]",

    /// JSON 数组，如 ["香菜", "早起"]
    val dislikes: String = "[]",

    /// "happy" | "stressed" | "neutral" | "sad"
    val currentMood: String = "neutral",

    /// JSON: { "birthday": "0312", "anniversary": "0601" }
    val importantDates: String = "{}",

    /// 最后更新时间
    val lastUpdated: Long = System.currentTimeMillis(),

    /// 所属角色 code
    val characterCode: String = "gu_chen"
)
```

```kotlin
// memory/db/KeyEvent.kt

@Entity(
    tableName = "key_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["category"])
    ]
)
data class KeyEvent(
    @PrimaryKey(autoGenerate = true)
    val eventId: Long = 0,

    /// 事件摘要，如 "周日一起看了漫威电影"
    val summary: String,

    /// 发生时间戳
    val timestamp: Long,

    /// 事件分类: "activity" | "emotion" | "promise" | "preference"
    val category: String,

    /// 重要性评分
    val importance: Int = 1,

    /// 是否来自规则提取
    val fromRule: Boolean = true,

    /// 所属角色 code
    val characterCode: String = "gu_chen",

    /// 所属用户 localId
    val userId: String = "local"
)
```

---

### DAO 定义

```kotlin
// memory/db/ChatMessageDao.kt

@Dao
interface ChatMessageDao {
    @Insert
    suspend fun insert(msg: ChatMessage): Long

    /// 获取最近 N 条消息（短期记忆）
    @Query("""
        SELECT * FROM chat_history
        WHERE userId = :userId AND characterCode = :code
        ORDER BY timestamp DESC LIMIT :limit
    """)
    fun getRecent(userId: String, code: String, limit: Int): Flow<List<ChatMessage>>

    /// 获取未提炼的消息
    @Query("""
        SELECT * FROM chat_history
        WHERE userId = :userId AND isSummarized = 0
        ORDER BY timestamp DESC LIMIT :limit
    """)
    suspend fun getUnSummarized(userId: String, limit: Int): List<ChatMessage>

    @Query("UPDATE chat_history SET isSummarized = 1 WHERE msgId IN (:ids)")
    suspend fun markSummarized(ids: List<Long>)

    /// 截断超出限制的旧消息（保留最近 500 条）
    @Query("""
        DELETE FROM chat_history
        WHERE msgId NOT IN (
            SELECT msgId FROM chat_history ORDER BY timestamp DESC LIMIT 500
        )
    """)
    suspend fun pruneOldMessages()
}
```

```kotlin
// memory/db/UserProfileDao.kt

@Dao
interface UserProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfile)

    @Query("SELECT * FROM user_profile WHERE userId = :userId AND characterCode = :code LIMIT 1")
    fun getProfile(userId: String, code: String): Flow<UserProfile?>

    /// 原子性好感度增减（线程安全）
    @Query("""
        UPDATE user_profile SET
            affectionScore = MIN(100, affectionScore + :delta),
            lastUpdated = :ts
        WHERE userId = :userId AND characterCode = :code
    """)
    suspend fun adjustAffection(
        userId: String,
        code: String,
        delta: Int,
        ts: Long = System.currentTimeMillis()
    )
}
```

```kotlin
// memory/db/KeyEventDao.kt

@Dao
interface KeyEventDao {
    @Insert
    suspend fun insert(event: KeyEvent): Long

    @Query("""
        SELECT * FROM key_events
        WHERE userId = :userId AND characterCode = :code
        ORDER BY timestamp DESC LIMIT :limit
    """)
    fun getRecent(userId: String, code: String, limit: Int): Flow<List<KeyEvent>>

    @Query("""
        SELECT * FROM key_events
        WHERE userId = :userId AND characterCode = :code AND category = :category
        ORDER BY timestamp DESC
    """)
    fun getByCategory(userId: String, code: String, category: String): Flow<List<KeyEvent>>
}
```

---

### 数据库迁移策略

```kotlin
// memory/db/Migration.kt

@Database(
    entities = [ChatMessage::class, UserProfile::class, KeyEvent::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun keyEventDao(): KeyEventDao
}

// Phase 2 迁移示例：
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 添加向量列 for sqlite-vec
        // db.execSQL("ALTER TABLE key_events ADD COLUMN vector BLOB")
    }
}
```

---

## 三、PostgreSQL ↔ Android Room 字段映射

| 概念 | PostgreSQL | Android Room | 说明 |
|------|-----------|--------------|------|
| 用户标识 | `User.deviceId` | `UserProfile.userId` | 均脱敏，客户端生成 UUID |
| 昵称 | `User.nickname` | `UserProfile.nickname` | 客户端可同步至云端 |
| 云端结构化数据 | `User.profileJson` | `UserProfile` 字段子集 | 云端备份（不含聊天原文）|
| 好感度 | — | `UserProfile.affectionScore` | **仅本地，不上云** |
| 聊天原文 | — | `ChatMessage.content` | **仅本地，永不上云** |

### 设计原则
- **聊天原文**：`ChatMessage` 表，仅存本地 SQLite，不上传
- **好感度**：`UserProfile.affectionScore`，不上云，本地私有
- **结构化摘要**：`UserProfile` 字段，可云同步（用户授权）
- **云端备份**：`User.profileJson`，仅存结构化数据 JSON
