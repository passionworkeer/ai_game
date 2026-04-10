package com.aiyougame.companion.memory

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aiyougame.companion.memory.db.AppDatabase
import com.aiyougame.companion.memory.db.ChatMessageEntity
import com.aiyougame.companion.memory.db.ChatMessageDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode

/**
 * Performance tests for Room database operations.
 *
 * Phase 1 baseline targets:
 * - 100 ChatMessage inserts  < 100ms
 * - 100 ChatMessage queries   < 100ms
 *
 * These are unit tests using Robolectric in-memory database.
 * Currently @Ignored — timing assertions are unreliable under Robolectric
 * due to simulated Android framework overhead. Run manually with
 * `./gradlew testDebugUnitTest --tests "com.aiyougame.companion.memory.PerformanceTest"`
 * to benchmark on a real device or remove @Ignore after Robolectric upgrade.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Ignore("Performance assertions unreliable under Robolectric — run manually")
class PerformanceTest {

    private lateinit var database: AppDatabase
    private lateinit var chatDao: ChatMessageDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        chatDao = database.chatMessageDao()
    }

    @After
    fun close() {
        database.close()
    }

    /**
     * T1: Insert 100 chat messages and measure elapsed time.
     * Target: < 100ms on a modern development machine.
     */
    @Test
    fun chat_message_100_inserts_under_100ms() = runTest {
        val start = System.currentTimeMillis()

        repeat(100) { i ->
            chatDao.insert(
                ChatMessageEntity(
                    role = if (i % 2 == 0) "user" else "model",
                    content = "Performance test message $i",
                    timestamp = System.currentTimeMillis() + i,
                    characterCode = "gu_chen"
                )
            )
        }

        val elapsed = System.currentTimeMillis() - start
        assertTrue(
            "100 inserts took ${elapsed}ms, target < 100ms",
            elapsed < 100
        )
    }

    /**
     * T2: Insert 100 messages, then query them back.
     * Measures the combined cost of write + indexed read.
     * Target: query < 100ms (insert time excluded from this assertion).
     */
    @Test
    fun chat_message_100_query_under_100ms() = runTest {
        // Pre-populate
        repeat(100) { i ->
            chatDao.insert(
                ChatMessageEntity(
                    role = "user",
                    content = "Query test message $i",
                    timestamp = System.currentTimeMillis() + i,
                    characterCode = "gu_chen"
                )
            )
        }

        // Measure query only
        val start = System.currentTimeMillis()
        chatDao.queryByCharacter("gu_chen", limit = 100).first()
        val elapsed = System.currentTimeMillis() - start

        assertTrue(
            "100-message query took ${elapsed}ms, target < 100ms",
            elapsed < 100
        )
    }

    /**
     * T3: Single insert + immediate read.
     * Target: < 10ms.
     */
    @Test
    fun chat_message_single_insert_read_under_10ms() = runTest {
        val start = System.currentTimeMillis()

        val id = chatDao.insert(
            ChatMessageEntity(
                role = "user",
                content = "Single message test",
                timestamp = System.currentTimeMillis(),
                characterCode = "gu_chen"
            )
        )

        val messages = chatDao.queryByCharacter("gu_chen", limit = 1).first()
        assertTrue("Should find the inserted message", messages.any { it.msgId == id })

        val elapsed = System.currentTimeMillis() - start
        assertTrue(
            "Single insert+read took ${elapsed}ms, target < 10ms",
            elapsed < 10
        )
    }

    /**
     * T4: Delete all messages for a character.
     * Target: < 50ms.
     */
    @Test
    fun chat_message_delete_all_under_50ms() = runTest {
        // Populate
        repeat(100) { i ->
            chatDao.insert(
                ChatMessageEntity(
                    role = "user",
                    content = "Delete test $i",
                    timestamp = System.currentTimeMillis() + i,
                    characterCode = "gu_chen"
                )
            )
        }

        val start = System.currentTimeMillis()
        chatDao.deleteByCharacter("gu_chen")
        val elapsed = System.currentTimeMillis() - start

        assertTrue(
            "Delete 100 messages took ${elapsed}ms, target < 50ms",
            elapsed < 50
        )
    }
}
