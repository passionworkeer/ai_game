package com.aiyougame.companion.memory.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Chat message entity stored locally only — never uploaded to cloud.
 *
 * @param msgId Primary key, auto-generated.
 * @param role "user" or "model".
 * @param content Raw message text.
 * @param timestamp Millis from System.currentTimeMillis().
 * @param isSent Whether the message has been sent to the model (used for optimistic UI).
 * @param isSummarized Whether this message has been extracted into long-term memory.
 * @param characterCode The character this message belongs to.
 * @param userId The local user ID (UUID v4, stored in Android Keystore).
 */
@Entity(
    tableName = "chat_history",
    indices = [Index(value = ["timestamp"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val msgId: Long = 0,
    val role: String,
    val content: String,
    val timestamp: Long,
    val isSent: Boolean = true,
    val isSummarized: Boolean = false,
    val characterCode: String = "gu_chen",
    val userId: String = "local"
)
