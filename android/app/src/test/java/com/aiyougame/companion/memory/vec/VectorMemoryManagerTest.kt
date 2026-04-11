package com.aiyougame.companion.memory.vec

import com.aiyougame.companion.memory.db.ChatMessageDao
import com.aiyougame.companion.memory.db.ChatMessageEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit tests for VectorMemoryManager.
 *
 * Tests cover:
 * 1. processMessage — user vs assistant routing
 * 2. searchSimilar — blank query early return, FTS + fallback
 * 3. searchByKeywords — blank keywords early return, FTS delegation
 * 4. rebuildIndex — no-DAO early return, DAO rebuild with user-only filtering
 * 5. clearCharacterMemory — delegates to SqliteVecManager
 * 6. getMemoryCount — delegates to SqliteVecManager
 */
class VectorMemoryManagerTest {

    private val embeddingService = mock<EmbeddingService>()
    private val sqliteVecManager = mock<SqliteVecManager>()
    private val chatMessageDao = mock<ChatMessageDao>()

    private fun createManager(dao: ChatMessageDao? = chatMessageDao): VectorMemoryManager {
        return VectorMemoryManager(
            embeddingService = embeddingService,
            sqliteVecManager = sqliteVecManager,
            chatMessageDao = dao,
        )
    }

    // ---------------------------------------------------------------------------
    // processMessage tests
    // ---------------------------------------------------------------------------

    @Test
    fun `processMessage user role calls embed and insert`() = runTest {
        val manager = createManager()
        val embedding = floatArrayOf(0.1f, 0.2f, 0.3f)
        doReturn(embedding).whenever(embeddingService).embed("你好小明")
        doReturn(Unit).whenever(sqliteVecManager).insert(any(), any(), any(), any())
        doReturn(0).whenever(sqliteVecManager).getMemoryCount(any())

        manager.processMessage("你好小明", "gu_chen", "user")

        verify(embeddingService).embed("你好小明")
        verify(sqliteVecManager).insert(eq("gu_chen"), eq("你好小明"), eq(embedding), eq("user"))
    }

    @Test
    fun `processMessage assistant role does NOT call embed or insert`() = runTest {
        val manager = createManager()

        manager.processMessage("今天天气真好", "gu_chen", "assistant")

        verify(embeddingService, never()).embed(any())
        verify(sqliteVecManager, never()).insert(any(), any(), any(), any())
    }

    @Test
    fun `processMessage unknown role does NOT call embed or insert`() = runTest {
        val manager = createManager()

        manager.processMessage("some text", "gu_chen", "system")

        verify(embeddingService, never()).embed(any())
        verify(sqliteVecManager, never()).insert(any(), any(), any(), any())
    }

    // ---------------------------------------------------------------------------
    // searchSimilar tests
    // ---------------------------------------------------------------------------

    @Test
    fun `searchSimilar blank query returns empty list immediately`() = runTest {
        val manager = createManager()

        val result = manager.searchSimilar("", "gu_chen")
        val resultBlank = manager.searchSimilar("   ", "gu_chen")

        assertEquals(emptyList<MemoryResult>(), result)
        assertEquals(emptyList<MemoryResult>(), resultBlank)
        verify(embeddingService, never()).embed(any())
    }

    @Test
    fun `searchSimilar non-blank query calls embed and ftsSearch`() = runTest {
        val manager = createManager()
        val embedding = floatArrayOf(0.1f, 0.2f)
        val expectedResults = listOf(
            MemoryResult("id1", "gu_chen", "我喜欢游戏", 0.9f, 1000L, "user")
        )
        doReturn(embedding).whenever(embeddingService).embed("游戏")
        doReturn(expectedResults).whenever(sqliteVecManager).ftsSearch(any(), any(), any())

        val results = manager.searchSimilar("游戏", "gu_chen")

        verify(embeddingService).embed("游戏")
        verify(sqliteVecManager).ftsSearch(eq("gu_chen"), eq("游戏"), eq(5))
        assertEquals(expectedResults, results)
    }

    @Test
    fun `searchSimilar when FTS returns empty falls back to vector search`() = runTest {
        val manager = createManager()
        val embedding = floatArrayOf(0.1f, 0.2f)
        val fallbackResults = listOf(
            MemoryResult("id2", "gu_chen", "最近怎么样", 0.5f, 2000L, "user")
        )
        doReturn(embedding).whenever(embeddingService).embed("怎么样")
        doReturn(emptyList<MemoryResult>()).whenever(sqliteVecManager).ftsSearch(any(), any(), any())
        doReturn(fallbackResults).whenever(sqliteVecManager).search(any(), any(), any())

        val results = manager.searchSimilar("怎么样", "gu_chen")

        verify(embeddingService).embed("怎么样")
        verify(sqliteVecManager).ftsSearch(eq("gu_chen"), eq("怎么样"), eq(5))
        verify(sqliteVecManager).search(eq("gu_chen"), eq(embedding), eq(5))
        assertEquals(fallbackResults, results)
    }

    @Test
    fun `searchSimilar uses custom topK`() = runTest {
        val manager = createManager()
        val embedding = floatArrayOf(0.1f)
        doReturn(embedding).whenever(embeddingService).embed("hello")
        doReturn(emptyList<MemoryResult>()).whenever(sqliteVecManager).ftsSearch(any(), any(), any())
        doReturn(emptyList<MemoryResult>()).whenever(sqliteVecManager).search(any(), any(), any())

        manager.searchSimilar("hello", "gu_chen", topK = 10)

        verify(sqliteVecManager).ftsSearch(eq("gu_chen"), eq("hello"), eq(10))
        verify(sqliteVecManager).search(eq("gu_chen"), eq(embedding), eq(10))
    }

    // ---------------------------------------------------------------------------
    // searchByKeywords tests
    // ---------------------------------------------------------------------------

    @Test
    fun `searchByKeywords blank keywords returns empty list`() = runTest {
        val manager = createManager()

        val result = manager.searchByKeywords("", "gu_chen")
        val resultBlank = manager.searchByKeywords("  ", "gu_chen")

        assertEquals(emptyList<MemoryResult>(), result)
        assertEquals(emptyList<MemoryResult>(), resultBlank)
        verify(sqliteVecManager, never()).ftsSearch(any(), any(), any())
    }

    @Test
    fun `searchByKeywords non-blank calls ftsSearch with default topK`() = runTest {
        val manager = createManager()
        val expected = listOf(
            MemoryResult("id3", "gu_chen", "生日是什么时候", 0f, 3000L, "user")
        )
        doReturn(expected).whenever(sqliteVecManager).ftsSearch(any(), any(), any())

        val results = manager.searchByKeywords("生日", "gu_chen")

        verify(sqliteVecManager).ftsSearch(eq("gu_chen"), eq("生日"), eq(5))
        assertEquals(expected, results)
    }

    // ---------------------------------------------------------------------------
    // rebuildIndex tests
    // ---------------------------------------------------------------------------

    @Test
    fun `rebuildIndex when chatMessageDao is null does nothing`() = runTest {
        val manager = createManager(dao = null)

        manager.rebuildIndex("gu_chen")

        verify(sqliteVecManager, never()).deleteByCharacter(any())
        verify(sqliteVecManager, never()).insert(any(), any(), any(), any())
    }

    @Test
    fun `rebuildIndex with DAO clears and re-inserts user messages only`() = runTest {
        val manager = createManager(dao = chatMessageDao)
        val messages = listOf(
            ChatMessageEntity(msgId = 1, role = "user", content = "我喜欢游戏", timestamp = 1000L, characterCode = "gu_chen"),
            ChatMessageEntity(msgId = 2, role = "assistant", content = "真的吗", timestamp = 1001L, characterCode = "gu_chen"),
            ChatMessageEntity(msgId = 3, role = "user", content = "是的我爱玩", timestamp = 1002L, characterCode = "gu_chen"),
        )
        val embedding = floatArrayOf(0.5f)
        doReturn(flowOf(messages)).whenever(chatMessageDao).queryRecentByCharacter("gu_chen", 1000)
        doReturn(Unit).whenever(sqliteVecManager).deleteByCharacter("gu_chen")
        doReturn(embedding).whenever(embeddingService).embed(any())
        doReturn(Unit).whenever(sqliteVecManager).insert(any(), any(), any(), any())
        doReturn(0).whenever(sqliteVecManager).getMemoryCount(any())

        manager.rebuildIndex("gu_chen")

        verify(sqliteVecManager).deleteByCharacter("gu_chen")
        // Only 2 user messages should be re-processed
        verify(embeddingService).embed("我喜欢游戏")
        verify(embeddingService).embed("是的我爱玩")
        verify(embeddingService, never()).embed("真的吗")
        verify(sqliteVecManager, never()).insert(eq("gu_chen"), eq("真的吗"), any(), any())
    }

    // ---------------------------------------------------------------------------
    // clearCharacterMemory tests
    // ---------------------------------------------------------------------------

    @Test
    fun `clearCharacterMemory delegates to sqliteVecManager`() = runTest {
        val manager = createManager()
        doReturn(0).whenever(sqliteVecManager).deleteByCharacter("gu_chen")

        manager.clearCharacterMemory("gu_chen")

        verify(sqliteVecManager).deleteByCharacter("gu_chen")
    }

    // ---------------------------------------------------------------------------
    // getMemoryCount tests
    // ---------------------------------------------------------------------------

    @Test
    fun `getMemoryCount delegates to sqliteVecManager`() = runTest {
        val manager = createManager()
        doReturn(42).whenever(sqliteVecManager).getMemoryCount("gu_chen")

        val count = manager.getMemoryCount("gu_chen")

        assertEquals(42, count)
        verify(sqliteVecManager).getMemoryCount("gu_chen")
    }
}
