package com.aiyougame.companion.memory.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for user profile.
 * User profile is stored locally only — never uploaded to cloud per privacy requirements.
 * Only syncEnabled=true user consent data is synced via SyncRepository.
 *
 * Supports multi-character: each (userId, characterCode) pair has an independent profile.
 */
@Dao
interface UserProfileDao {
    /**
     * Observe the profile for a specific character.
     * Uses default 'local_user' as the user ID.
     */
    @Query("SELECT * FROM user_profile WHERE id = 'local_user' AND characterCode = :characterCode LIMIT 1")
    fun observeByCharacter(characterCode: String): Flow<UserProfileEntity?>

    /**
     * Get the profile for a specific character.
     * Uses default 'local_user' as the user ID.
     */
    @Query("SELECT * FROM user_profile WHERE id = 'local_user' AND characterCode = :characterCode LIMIT 1")
    suspend fun getByCharacter(characterCode: String): UserProfileEntity?

    /**
     * Legacy observe method for backward compatibility (defaults to 'gu_chen').
     */
    @Query("SELECT * FROM user_profile WHERE id = 'local_user' AND characterCode = 'gu_chen' LIMIT 1")
    fun observe(): Flow<UserProfileEntity?>

    /**
     * Legacy get method for backward compatibility (defaults to 'gu_chen').
     */
    @Query("SELECT * FROM user_profile WHERE id = 'local_user' AND characterCode = 'gu_chen' LIMIT 1")
    suspend fun get(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: UserProfileEntity)

    /**
     * Adjust affection level for a specific character profile.
     */
    @Query("UPDATE user_profile SET affectionLevel = affectionLevel + :delta WHERE id = 'local_user' AND characterCode = :characterCode")
    suspend fun adjustAffection(delta: Int, characterCode: String)

    /**
     * Legacy adjustAffection for backward compatibility (defaults to 'gu_chen').
     */
    @Query("UPDATE user_profile SET affectionLevel = affectionLevel + :delta WHERE id = 'local_user' AND characterCode = 'gu_chen'")
    suspend fun adjustAffection(delta: Int)

    /**
     * Update nickname for a specific character profile.
     */
    @Query("UPDATE user_profile SET nickname = :nickname, updatedAt = :timestamp WHERE id = 'local_user' AND characterCode = :characterCode")
    suspend fun updateNickname(nickname: String, characterCode: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Legacy updateNickname for backward compatibility.
     */
    @Query("UPDATE user_profile SET nickname = :nickname, updatedAt = :timestamp WHERE id = 'local_user' AND characterCode = 'gu_chen'")
    suspend fun updateNickname(nickname: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Update interests for a specific character profile.
     */
    @Query("UPDATE user_profile SET interests = :interests, updatedAt = :timestamp WHERE id = 'local_user' AND characterCode = :characterCode")
    suspend fun updateInterests(interests: String, characterCode: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Legacy updateInterests for backward compatibility.
     */
    @Query("UPDATE user_profile SET interests = :interests, updatedAt = :timestamp WHERE id = 'local_user' AND characterCode = 'gu_chen'")
    suspend fun updateInterests(interests: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Set absolute affection level for a specific character profile.
     */
    @Query("UPDATE user_profile SET affectionLevel = :level WHERE id = 'local_user' AND characterCode = :characterCode")
    suspend fun setAffection(level: Int, characterCode: String)

    /**
     * Legacy setAffection for backward compatibility.
     */
    @Query("UPDATE user_profile SET affectionLevel = :level WHERE id = 'local_user' AND characterCode = 'gu_chen'")
    suspend fun setAffection(level: Int)
}
