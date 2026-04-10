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
 */
@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 'local_user' LIMIT 1")
    fun observe(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 'local_user' LIMIT 1")
    suspend fun get(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET affectionLevel = affectionLevel + :delta WHERE id = 'local_user'")
    suspend fun adjustAffection(delta: Int)

    @Query("UPDATE user_profile SET nickname = :nickname, updatedAt = :timestamp WHERE id = 'local_user'")
    suspend fun updateNickname(nickname: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET interests = :interests, updatedAt = :timestamp WHERE id = 'local_user'")
    suspend fun updateInterests(interests: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET affectionLevel = :level WHERE id = 'local_user'")
    suspend fun setAffection(level: Int)
}
