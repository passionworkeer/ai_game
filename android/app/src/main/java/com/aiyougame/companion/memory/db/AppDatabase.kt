package com.aiyougame.companion.memory.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Main Room database for the app.
 * Contains:
 * - ChatMessageEntity: Chat history (local only, never synced)
 * - UserProfileEntity: User profile with affection level (local only, never synced)
 * - KeyEventEntity: Important user-character events (local only, never synced)
 *
 * Migration 1 -> 2:
 * - chat_history: add characterCode TEXT NOT NULL DEFAULT 'gu_chen'
 * - user_profile: add characterCode TEXT NOT NULL DEFAULT 'gu_chen'
 */
@Database(
    entities = [
        ChatMessageEntity::class,
        UserProfileEntity::class,
        KeyEventEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        const val DATABASE_NAME = "aiyougame.db"

        @JvmStatic val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE chat_history ADD COLUMN characterCode TEXT NOT NULL DEFAULT 'gu_chen'"
                )
                database.execSQL(
                    "ALTER TABLE user_profile ADD COLUMN characterCode TEXT NOT NULL DEFAULT 'gu_chen'"
                )
            }
        }
    }

    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun keyEventDao(): KeyEventDao
}
