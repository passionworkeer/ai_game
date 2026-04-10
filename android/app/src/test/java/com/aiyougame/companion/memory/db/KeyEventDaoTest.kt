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
 * Unit tests for KeyEventDao.
 * Uses Room.inMemoryDatabaseBuilder with Robolectric for fast, isolated testing.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class KeyEventDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: KeyEventDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = database.keyEventDao()
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun insert_and_query() = runTest {
        val timestamp = System.currentTimeMillis()
        dao.insert(
            KeyEventEntity(
                characterId = "gu_chen",
                type = "nickname_set",
                content = "小鱼",
                happenedAt = timestamp
            )
        )

        val events = dao.queryByCharacter("gu_chen", limit = 10).first()
        assertEquals(1, events.size)
        assertEquals("nickname_set", events[0].type)
        assertEquals("小鱼", events[0].content)
    }

    @Test
    fun delete_by_character() = runTest {
        dao.insert(KeyEventEntity(characterId = "gu_chen", type = "test", content = "测试"))
        dao.insert(KeyEventEntity(characterId = "gu_chen", type = "test2", content = "测试2"))

        dao.deleteByCharacter("gu_chen")

        assertTrue(dao.queryByCharacter("gu_chen", limit = 10).first().isEmpty())
    }

    @Test
    fun query_by_character_and_type() = runTest {
        val timestamp = System.currentTimeMillis()
        dao.insert(KeyEventEntity(characterId = "gu_chen", type = "nickname_set", content = "nick1", happenedAt = timestamp))
        dao.insert(KeyEventEntity(characterId = "gu_chen", type = "preference_shared", content = "pref1", happenedAt = timestamp + 1))
        dao.insert(KeyEventEntity(characterId = "gu_chen", type = "nickname_set", content = "nick2", happenedAt = timestamp + 2))

        val nicknameEvents = dao.queryByCharacterAndType("gu_chen", "nickname_set", limit = 10).first()
        assertEquals(2, nicknameEvents.size)
        assertTrue(nicknameEvents.all { it.type == "nickname_set" })
    }

    @Test
    fun delete_by_character_and_type() = runTest {
        dao.insert(KeyEventEntity(characterId = "gu_chen", type = "nickname_set", content = "nick"))
        dao.insert(KeyEventEntity(characterId = "gu_chen", type = "preference_shared", content = "pref"))

        dao.deleteByCharacterAndType("gu_chen", "nickname_set")

        val remaining = dao.queryByCharacter("gu_chen", limit = 10).first()
        assertEquals(1, remaining.size)
        assertEquals("preference_shared", remaining[0].type)
    }

    @Test
    fun count_by_character() = runTest {
        repeat(5) { i ->
            dao.insert(KeyEventEntity(characterId = "gu_chen", type = "test", content = "test$i"))
        }
        dao.insert(KeyEventEntity(characterId = "other", type = "test", content = "other"))

        val count = dao.countByCharacter("gu_chen")
        assertEquals(5, count)
    }

    @Test
    fun query_respects_limit() = runTest {
        val baseTime = System.currentTimeMillis()
        repeat(30) { i ->
            dao.insert(
                KeyEventEntity(
                    characterId = "gu_chen",
                    type = "test",
                    content = "event$i",
                    happenedAt = baseTime + i
                )
            )
        }

        val events = dao.queryByCharacter("gu_chen", limit = 10).first()
        assertEquals(10, events.size)
    }

    @Test
    fun auto_generates_id() = runTest {
        val id1 = dao.insert(KeyEventEntity(characterId = "gu_chen", type = "test", content = "test1"))
        val id2 = dao.insert(KeyEventEntity(characterId = "gu_chen", type = "test", content = "test2"))

        assertTrue(id1 > 0)
        assertTrue(id2 > id1)
    }

    @Test
    fun query_by_character_returns_descending_order() = runTest {
        val baseTime = System.currentTimeMillis()
        dao.insert(KeyEventEntity(characterId = "gc", type = "t", content = "oldest", happenedAt = baseTime))
        dao.insert(KeyEventEntity(characterId = "gc", type = "t", content = "newest", happenedAt = baseTime + 100))

        val events = dao.queryByCharacter("gc", limit = 10).first()
        assertEquals("newest", events[0].content)
        assertEquals("oldest", events[1].content)
    }
}
