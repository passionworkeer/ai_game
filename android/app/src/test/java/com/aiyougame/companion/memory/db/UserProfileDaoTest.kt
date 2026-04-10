package com.aiyougame.companion.memory.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode

/**
 * Unit tests for UserProfileDao with multi-character support.
 * Uses Room.inMemoryDatabaseBuilder with Robolectric for fast, isolated testing.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class UserProfileDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: UserProfileDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = database.userProfileDao()
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun insert_and_query_by_character() = runTest {
        val profile = UserProfileEntity(
            id = "local_user",
            characterCode = "gu_chen",
            nickname = "小鱼",
            interests = "[\"游戏\"]"
        )
        dao.insertOrUpdate(profile)

        val result = dao.getByCharacter("gu_chen")
        assertNotNull(result)
        assertEquals("小鱼", result?.nickname)
        assertEquals("[\"游戏\"]", result?.interests)
        assertEquals(0, result?.affectionLevel)
        assertEquals("gu_chen", result?.characterCode)
    }

    @Test
    fun affection_adjustment_by_character() = runTest {
        dao.insertOrUpdate(UserProfileEntity(characterCode = "gu_chen", affectionLevel = 10))
        dao.adjustAffection(5, "gu_chen")

        val result = dao.getByCharacter("gu_chen")
        assertEquals(15, result?.affectionLevel)
    }

    @Test
    fun set_affection_by_character() = runTest {
        dao.insertOrUpdate(UserProfileEntity(characterCode = "gu_chen", affectionLevel = 10))
        dao.setAffection(100, "gu_chen")

        val result = dao.getByCharacter("gu_chen")
        assertEquals(100, result?.affectionLevel)
    }

    @Test
    fun update_nickname_by_character() = runTest {
        dao.insertOrUpdate(UserProfileEntity(characterCode = "gu_chen", nickname = "原名"))
        val timestamp = System.currentTimeMillis()
        dao.updateNickname("新名字", "gu_chen", timestamp)

        val result = dao.getByCharacter("gu_chen")
        assertEquals("新名字", result?.nickname)
    }

    @Test
    fun update_interests_by_character() = runTest {
        dao.insertOrUpdate(UserProfileEntity(characterCode = "gu_chen", interests = "[]"))
        dao.updateInterests("[\"游戏\", \"音乐\"]", "gu_chen")

        val result = dao.getByCharacter("gu_chen")
        assertEquals("[\"游戏\", \"音乐\"]", result?.interests)
    }

    @Test
    fun observe_emits_profile_by_character() = runTest {
        dao.insertOrUpdate(UserProfileEntity(characterCode = "gu_chen", nickname = "观察者"))

        val profile = dao.observeByCharacter("gu_chen").first()
        assertEquals("观察者", profile?.nickname)
    }

    @Test
    fun insert_or_update_replaces_existing() = runTest {
        dao.insertOrUpdate(UserProfileEntity(characterCode = "gu_chen", nickname = "旧昵称", affectionLevel = 10))
        dao.insertOrUpdate(UserProfileEntity(characterCode = "gu_chen", nickname = "新昵称", affectionLevel = 20))

        val result = dao.getByCharacter("gu_chen")
        assertEquals("新昵称", result?.nickname)
        assertEquals(20, result?.affectionLevel)
    }

    @Test
    fun get_returns_null_when_no_profile() = runTest {
        val result = dao.getByCharacter("gu_chen")
        assertNull(result)
    }

    @Test
    fun affection_level_defaults_to_zero() = runTest {
        dao.insertOrUpdate(UserProfileEntity(characterCode = "gu_chen", nickname = "test"))
        assertEquals(0, dao.getByCharacter("gu_chen")?.affectionLevel)
    }

    @Test
    fun different_characters_have_isolated_profiles() = runTest {
        // Create profiles for two different characters
        dao.insertOrUpdate(
            UserProfileEntity(characterCode = "gu_chen", nickname = "顾晨粉丝", affectionLevel = 80)
        )
        dao.insertOrUpdate(
            UserProfileEntity(characterCode = "new_char", nickname = "新角色粉丝", affectionLevel = 20)
        )

        // Each character has independent profile
        val guChenProfile = dao.getByCharacter("gu_chen")
        val newCharProfile = dao.getByCharacter("new_char")

        assertEquals("顾晨粉丝", guChenProfile?.nickname)
        assertEquals(80, guChenProfile?.affectionLevel)
        assertEquals("gu_chen", guChenProfile?.characterCode)

        assertEquals("新角色粉丝", newCharProfile?.nickname)
        assertEquals(20, newCharProfile?.affectionLevel)
        assertEquals("new_char", newCharProfile?.characterCode)

        // Adjust one doesn't affect the other
        dao.adjustAffection(10, "gu_chen")
        assertEquals(90, dao.getByCharacter("gu_chen")?.affectionLevel)
        assertEquals(20, dao.getByCharacter("new_char")?.affectionLevel)
    }

    @Test
    fun getByCharacter_returns_null_for_wrong_character() = runTest {
        dao.insertOrUpdate(UserProfileEntity(characterCode = "gu_chen", nickname = "顾晨"))
        assertNull(dao.getByCharacter("wrong_char"))
    }
}
