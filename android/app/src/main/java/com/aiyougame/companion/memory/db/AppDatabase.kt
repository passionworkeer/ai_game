package com.aiyougame.companion.memory.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Main Room database for the app.
 * Contains:
 * - ChatMessageEntity: Chat history (local only, never synced)
 * - UserProfileEntity: User profile with affection level (local only, never synced)
 * - KeyEventEntity: Important user-character events (local only, never synced)
 */
@Database(
    entities = [
        ChatMessageEntity::class,
        UserProfileEntity::class,
        KeyEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    companion object {
        const val DATABASE_NAME = "aiyougame.db"
    }

    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun keyEventDao(): KeyEventDao
}
