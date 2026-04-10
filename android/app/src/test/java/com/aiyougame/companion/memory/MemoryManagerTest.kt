package com.aiyougame.companion.memory

import com.aiyougame.companion.memory.db.ChatMessageDao
import com.aiyougame.companion.memory.db.ChatMessageEntity
import com.aiyougame.companion.memory.db.KeyEventDao
import com.aiyougame.companion.memory.db.KeyEventEntity
import com.aiyougame.companion.memory.db.UserProfileDao
import com.aiyougame.companion.memory.db.UserProfileEntity
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryManagerTest {

    private val userProfileDao = mockk<UserProfileDao>(relaxed = true)
    private val keyEventDao = mockk<KeyEventDao>(relaxed = true)
    private val chatMessageDao = mockk<ChatMessageDao>(relaxed = true)
    private val profileExtractor = mockk<ProfileExtractor>(relaxed = true)

    // Use real IO dispatcher — qualified @IoDispatcher can't be satisfied by MockK
    private val manager = MemoryManager(
        userProfileDao = userProfileDao,
        keyEventDao = keyEventDao,
        chatMessageDao = chatMessageDao,
        profileExtractor = profileExtractor,
        io = Dispatchers.IO,
    )

    @Test
    fun `buildSnapshot returns MemorySnapshot with all three layers`() = runTest {
        val messages = listOf(
            ChatMessageEntity(role = "user", content = "你好", timestamp = 1000, characterCode = "gu_chen"),
            ChatMessageEntity(role = "assistant", content = "你好呀", timestamp = 1001, characterCode = "gu_chen"),
        )
        every { chatMessageDao.queryRecentByCharacter("gu_chen", 20) } returns flowOf(messages)

        val profile = UserProfileEntity(
            nickname = "小明",
            interests = "[\"游戏\"]",
            affectionLevel = 50,
        )
        coEvery { userProfileDao.get() } returns profile
        every { profileExtractor.parseJsonArray("[\"游戏\"]") } returns listOf("游戏")

        val events = listOf(
            KeyEventEntity(characterId = "gu_chen", type = "birthday", content = "生日", happenedAt = 999),
        )
        every { keyEventDao.queryByCharacter("gu_chen", 10) } returns flowOf(events)

        val snapshot = manager.buildSnapshot("gu_chen")

        assertNotNull(snapshot)
        assertTrue(snapshot.recentContext.contains("你好"))           // Layer 1: from messages
        assertTrue(snapshot.userProfile.contains("昵称：小明"))        // Layer 2: from profile
        assertTrue(snapshot.userProfile.contains("游戏"))            // Layer 2: parsed interests
        assertTrue(snapshot.keyEvents.contains("birthday"))          // Layer 3: from key events
    }

    @Test
    fun `buildSnapshot handles empty database gracefully`() = runTest {
        every { chatMessageDao.queryRecentByCharacter("gu_chen", 20) } returns flowOf(emptyList())
        coEvery { userProfileDao.get() } returns null
        every { keyEventDao.queryByCharacter("gu_chen", 10) } returns flowOf(emptyList())

        val snapshot = manager.buildSnapshot("gu_chen")

        assertEquals("（暂无对话历史）", snapshot.recentContext)
        assertEquals("（暂无用户画像）", snapshot.userProfile)
        assertEquals("（暂无关键事件）", snapshot.keyEvents)
    }

    @Test
    fun `processAfterMessage persists both messages and updates profile`() = runTest {
        coEvery { userProfileDao.get() } returns null
        every { profileExtractor.extract("我叫小明") } returns ProfileExtraction(nickname = "小明")
        every { profileExtractor.parseJsonArray(any()) } returns emptyList()

        manager.processAfterMessage(
            userMsg = "我叫小明",
            assistantMsg = "很高兴认识你！",
            characterCode = "gu_chen",
        )

        coVerify { chatMessageDao.insert(match { it.role == "user" && it.content == "我叫小明" }) }
        coVerify { chatMessageDao.insert(match { it.role == "assistant" && it.content == "很高兴认识你！" }) }
        coVerify { userProfileDao.insertOrUpdate(match { it.nickname == "小明" }) }
    }

    @Test
    fun `processAfterMessage extracts and saves key event`() = runTest {
        coEvery { userProfileDao.get() } returns null
        every { profileExtractor.extract("明天是我生日") } returns ProfileExtraction(
            keyEvent = KeyEventData("生日", "birthday", 5)
        )
        every { profileExtractor.parseJsonArray(any()) } returns emptyList()

        manager.processAfterMessage(
            userMsg = "明天是我生日",
            assistantMsg = "生日快乐！",
            characterCode = "gu_chen",
        )

        coVerify { keyEventDao.insert(match { it.type == "birthday" }) }
    }
}
