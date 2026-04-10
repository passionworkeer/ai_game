package com.aiyougame.companion.memory.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User profile entity stored locally only — never uploaded to cloud.
 * Affection level is local-only per privacy requirements.
 * Profile is keyed by (id, characterCode) composite key for multi-character support.
 *
 * @param id User identifier (fixed as "local_user" for single-user app).
 * @param characterCode The character this profile belongs to (e.g. "gu_chen").
 * @param nickname User's nickname extracted from chat.
 * @param gender User's self-reported gender.
 * @param age User's self-reported age.
 * @param interests JSON array of interests.
 * @param moodLogs JSON array of mood history.
 * @param affectionLevel Affection level with the character (local-only, not synced to cloud).
 * @param updatedAt Last update timestamp.
 */
@Entity(
    tableName = "user_profile",
    primaryKeys = ["id", "characterCode"],
    indices = [Index(value = ["id", "characterCode"], unique = true)]
)
data class UserProfileEntity(
    val id: String = "local_user",
    val characterCode: String = "gu_chen",
    val nickname: String? = null,
    val gender: String? = null,
    val age: Int? = null,
    val interests: String = "[]",
    val moodLogs: String = "[]",
    val affectionLevel: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
