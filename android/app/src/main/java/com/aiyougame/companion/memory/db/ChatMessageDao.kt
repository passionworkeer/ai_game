package com.aiyougame.companion.memory.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for chat messages.
 * Chat messages are stored locally only — never uploaded to cloud per privacy requirements.
 */
@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity): Long

    @Query("SELECT * FROM chat_history WHERE characterCode = :characterCode ORDER BY timestamp ASC LIMIT :limit")
    fun queryByCharacter(characterCode: String, limit: Int = 100): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_history WHERE characterCode = :characterCode ORDER BY timestamp DESC LIMIT :limit")
    fun queryRecentByCharacter(characterCode: String, limit: Int = 50): Flow<List<ChatMessageEntity>>

    @Query("DELETE FROM chat_history WHERE characterCode = :characterCode")
    suspend fun deleteByCharacter(characterCode: String)

    @Query("UPDATE chat_history SET isSummarized = 1 WHERE msgId IN (:msgIds)")
    suspend fun markAsSummarized(msgIds: List<Long>)

    @Query("SELECT COUNT(*) FROM chat_history WHERE characterCode = :characterCode AND isSummarized = 0")
    suspend fun countUnsummarized(characterCode: String): Int
}
