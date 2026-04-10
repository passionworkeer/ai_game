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
 * Unit tests for ChatMessageDao.
 * Uses Room.inMemoryDatabaseBuilder with Robolectric for fast, isolated testing.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class ChatMessageDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ChatMessageDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        dao = database.chatMessageDao()
    }

    @After
    fun close() {
        database.close()
    }

    @Test
    fun insert_and_query_by_character() = runTest {
        val timestamp = System.currentTimeMillis()
        dao.insert(ChatMessageEntity(characterCode = "gu_chen", role = "user", content = "你好", timestamp = timestamp))
        dao.insert(ChatMessageEntity(characterCode = "gu_chen", role = "model", content = "你好呀", timestamp = timestamp + 1))
        dao.insert(ChatMessageEntity(characterCode = "other_char", role = "user", content = "hello", timestamp = timestamp))

        val messages = dao.queryByCharacter("gu_chen", limit = 10).first()
        assertEquals(2, messages.size)
        assertEquals("你好", messages[0].content)
        assertEquals("user", messages[0].role)
        assertEquals("你好呀", messages[1].content)
    }

    @Test
    fun delete_by_character() = runTest {
        val timestamp = System.currentTimeMillis()
        dao.insert(ChatMessageEntity(characterCode = "gu_chen", role = "user", content = "测试", timestamp = timestamp))
        dao.insert(ChatMessageEntity(characterCode = "gu_chen", role = "model", content = "回复", timestamp = timestamp + 1))

        dao.deleteByCharacter("gu_chen")

        val messages = dao.queryByCharacter("gu_chen", limit = 10).first()
        assertTrue(messages.isEmpty())
    }

    @Test
    fun query_respects_limit() = runTest {
        val baseTime = System.currentTimeMillis()
        repeat(20) { i ->
            dao.insert(ChatMessageEntity(characterCode = "gu_chen", role = "user", content = "msg$i", timestamp = baseTime + i))
        }

        val messages = dao.queryByCharacter("gu_chen", limit = 5).first()
        assertEquals(5, messages.size)
    }

    @Test
    fun mark_as_summarized() = runTest {
        val timestamp = System.currentTimeMillis()
        val id1 = dao.insert(ChatMessageEntity(characterCode = "gu_chen", role = "user", content = "msg1", timestamp = timestamp))
        val id2 = dao.insert(ChatMessageEntity(characterCode = "gu_chen", role = "user", content = "msg2", timestamp = timestamp + 1))

        dao.markAsSummarized(listOf(id1, id2))

        val messages = dao.queryByCharacter("gu_chen", limit = 10).first()
        assertTrue(messages.all { it.isSummarized })
    }

    @Test
    fun count_unsummarized() = runTest {
        val timestamp = System.currentTimeMillis()
        dao.insert(ChatMessageEntity(characterCode = "gu_chen", role = "user", content = "msg1", timestamp = timestamp, isSummarized = false))
        dao.insert(ChatMessageEntity(characterCode = "gu_chen", role = "user", content = "msg2", timestamp = timestamp + 1, isSummarized = false))
        dao.insert(ChatMessageEntity(characterCode = "gu_chen", role = "user", content = "msg3", timestamp = timestamp + 2, isSummarized = true))

        val count = dao.countUnsummarized("gu_chen")
        assertEquals(2, count)
    }

    @Test
    fun query_recent_by_character() = runTest {
        val baseTime = System.currentTimeMillis()
        repeat(10) { i ->
            dao.insert(ChatMessageEntity(characterCode = "gu_chen", role = "user", content = "msg$i", timestamp = baseTime + i * 1000))
        }

        val messages = dao.queryRecentByCharacter("gu_chen", limit = 3).first()
        assertEquals(3, messages.size)
        // Most recent first
        assertTrue(messages[0].content.contains("9"))
    }

    @Test
    fun insert_returns_nonzero_id() = runTest {
        val id = dao.insert(
            ChatMessageEntity(characterCode = "gc", role = "user", content = "test", timestamp = System.currentTimeMillis())
        )
        assertTrue(id > 0)
    }

    @Test
    fun query_by_character_returns_ascending_order() = runTest {
        val baseTime = System.currentTimeMillis()
        dao.insert(ChatMessageEntity(characterCode = "gc", role = "user", content = "first", timestamp = baseTime))
        dao.insert(ChatMessageEntity(characterCode = "gc", role = "user", content = "second", timestamp = baseTime + 1))

        val messages = dao.queryByCharacter("gc", limit = 10).first()
        assertEquals("first", messages[0].content)
        assertEquals("second", messages[1].content)
    }
}
