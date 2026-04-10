package com.aiyougame.companion.memory.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for key events.
 * Key events are stored locally only — never uploaded to cloud per privacy requirements.
 */
@Dao
interface KeyEventDao {
    @Insert
    suspend fun insert(event: KeyEventEntity): Long

    @Query("SELECT * FROM key_events WHERE characterId = :characterId ORDER BY happenedAt DESC LIMIT :limit")
    fun queryByCharacter(characterId: String, limit: Int = 50): Flow<List<KeyEventEntity>>

    @Query("SELECT * FROM key_events WHERE characterId = :characterId AND type = :type ORDER BY happenedAt DESC LIMIT :limit")
    fun queryByCharacterAndType(characterId: String, type: String, limit: Int = 20): Flow<List<KeyEventEntity>>

    @Query("DELETE FROM key_events WHERE characterId = :characterId")
    suspend fun deleteByCharacter(characterId: String)

    @Query("DELETE FROM key_events WHERE characterId = :characterId AND type = :type")
    suspend fun deleteByCharacterAndType(characterId: String, type: String)

    @Query("SELECT COUNT(*) FROM key_events WHERE characterId = :characterId")
    suspend fun countByCharacter(characterId: String): Int
}
