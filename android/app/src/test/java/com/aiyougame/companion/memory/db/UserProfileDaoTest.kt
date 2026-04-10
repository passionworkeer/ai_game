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
 * Unit tests for UserProfileDao.
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
    fun insert_and_query() = runTest {
        val profile = UserProfileEntity(
            id = "local_user",
            nickname = "小鱼",
            interests = "[\"游戏\"]"
        )
        dao.insertOrUpdate(profile)

        val result = dao.get()
        assertNotNull(result)
        assertEquals("小鱼", result?.nickname)
        assertEquals("[\"游戏\"]", result?.interests)
        assertEquals(0, result?.affectionLevel)
    }

    @Test
    fun affection_adjustment() = runTest {
        dao.insertOrUpdate(UserProfileEntity(affectionLevel = 10))
        dao.adjustAffection(5)

        val result = dao.get()
        assertEquals(15, result?.affectionLevel)
    }

    @Test
    fun set_affection() = runTest {
        dao.insertOrUpdate(UserProfileEntity(affectionLevel = 10))
        dao.setAffection(100)

        val result = dao.get()
        assertEquals(100, result?.affectionLevel)
    }

    @Test
    fun update_nickname() = runTest {
        dao.insertOrUpdate(UserProfileEntity(nickname = "原名"))
        val timestamp = System.currentTimeMillis()
        dao.updateNickname("新名字", timestamp)

        val result = dao.get()
        assertEquals("新名字", result?.nickname)
    }

    @Test
    fun update_interests() = runTest {
        dao.insertOrUpdate(UserProfileEntity(interests = "[]"))
        dao.updateInterests("[\"游戏\", \"音乐\"]")

        val result = dao.get()
        assertEquals("[\"游戏\", \"音乐\"]", result?.interests)
    }

    @Test
    fun observe_emits_profile() = runTest {
        dao.insertOrUpdate(UserProfileEntity(nickname = "观察者"))

        val profile = dao.observe().first()
        assertEquals("观察者", profile?.nickname)
    }

    @Test
    fun insert_or_update_replaces_existing() = runTest {
        dao.insertOrUpdate(UserProfileEntity(nickname = "旧昵称", affectionLevel = 10))
        dao.insertOrUpdate(UserProfileEntity(nickname = "新昵称", affectionLevel = 20))

        val result = dao.get()
        assertEquals("新昵称", result?.nickname)
        assertEquals(20, result?.affectionLevel)
    }

    @Test
    fun get_returns_null_when_no_profile() = runTest {
        val result = dao.get()
        assertNull(result)
    }

    @Test
    fun affection_level_defaults_to_zero() = runTest {
        dao.insertOrUpdate(UserProfileEntity(nickname = "test"))
        assertEquals(0, dao.get()?.affectionLevel)
    }
}
