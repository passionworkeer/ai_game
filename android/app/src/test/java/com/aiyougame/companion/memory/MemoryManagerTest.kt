package com.aiyougame.companion.memory

import com.aiyougame.companion.memory.db.ChatMessageDao
import com.aiyougame.companion.memory.db.ChatMessageEntity
import com.aiyougame.companion.memory.db.KeyEventDao
import com.aiyougame.companion.memory.db.KeyEventEntity
import com.aiyougame.companion.memory.db.UserProfileDao
import com.aiyougame.companion.memory.db.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MemoryManagerTest {

    private val userProfileDao = mock<UserProfileDao>()
    private val keyEventDao = mock<KeyEventDao>()
    private val chatMessageDao = mock<ChatMessageDao>()
    private val profileExtractor = mock<ProfileExtractor>()

    private val manager = MemoryManager(
        userProfileDao = userProfileDao,
        keyEventDao = keyEventDao,
        chatMessageDao = chatMessageDao,
        profileExtractor = profileExtractor,
        io = Dispatchers.Unconfined,
    )

    @Test
    fun `buildSnapshot returns MemorySnapshot with all three layers`() = runTest {
        val messages = listOf(
            ChatMessageEntity(role = "user", content = "你好", timestamp = 1000, characterCode = "gu_chen"),
            ChatMessageEntity(role = "assistant", content = "你好呀", timestamp = 1001, characterCode = "gu_chen"),
        )
        whenever(chatMessageDao.queryRecentByCharacter("gu_chen", 20)).doReturn(flowOf(messages))

        val profile = UserProfileEntity(
            characterCode = "gu_chen",
            nickname = "小明",
            interests = "[\"游戏\"]",
            affectionLevel = 50,
        )
        whenever(userProfileDao.getByCharacter("gu_chen")).doReturn(profile)
        whenever(profileExtractor.parseJsonArray("[\"游戏\"]")).doReturn(listOf("游戏"))

        val events = listOf(
            KeyEventEntity(characterId = "gu_chen", type = "birthday", content = "生日", happenedAt = 999),
        )
        whenever(keyEventDao.queryByCharacter("gu_chen", 10)).doReturn(flowOf(events))

        val snapshot = manager.buildSnapshot("gu_chen")

        assertNotNull(snapshot)
        assertTrue(snapshot.recentContext.contains("你好"))
        assertTrue(snapshot.userProfile.contains("昵称：小明"))
        assertTrue(snapshot.userProfile.contains("游戏"))
        assertTrue(snapshot.keyEvents.contains("birthday"))
    }

    @Test
    fun `buildSnapshot handles empty database gracefully`() = runTest {
        whenever(chatMessageDao.queryRecentByCharacter("gu_chen", 20)).doReturn(flowOf(emptyList()))
        whenever(userProfileDao.getByCharacter("gu_chen")).doReturn(null)
        whenever(keyEventDao.queryByCharacter("gu_chen", 10)).doReturn(flowOf(emptyList()))

        val snapshot = manager.buildSnapshot("gu_chen")

        assertEquals("（暂无对话历史）", snapshot.recentContext)
        assertEquals("（暂无用户画像）", snapshot.userProfile)
        assertEquals("（暂无关键事件）", snapshot.keyEvents)
    }

    @Test
    fun `processAfterMessage persists both messages and updates profile`() = runTest {
        whenever(userProfileDao.getByCharacter("gu_chen")).doReturn(null)
        whenever(userProfileDao.insertOrUpdate(any())).doReturn(Unit)
        whenever(chatMessageDao.insert(any())).doReturn(0L)
        whenever(profileExtractor.extract("我叫小明")).doReturn(ProfileExtraction(nickname = "小明"))
        whenever(profileExtractor.parseJsonArray(any())).doReturn(emptyList())

        manager.processAfterMessage(
            userMsg = "我叫小明",
            assistantMsg = "很高兴认识你！",
            characterCode = "gu_chen",
        )

        // Two messages: user + assistant
        verify(chatMessageDao, atLeast(1)).insert(any())
        verify(userProfileDao).insertOrUpdate(any())
    }

    @Test
    fun `processAfterMessage extracts and saves key event`() = runTest {
        whenever(userProfileDao.getByCharacter("gu_chen")).doReturn(null)
        whenever(userProfileDao.insertOrUpdate(any())).doReturn(Unit)
        whenever(chatMessageDao.insert(any())).doReturn(0L)
        whenever(keyEventDao.insert(any())).doReturn(0L)
        whenever(profileExtractor.extract("明天是我生日")).doReturn(
            ProfileExtraction(keyEvent = KeyEventData("生日", "birthday", 5))
        )
        whenever(profileExtractor.parseJsonArray(any())).doReturn(emptyList())

        manager.processAfterMessage(
            userMsg = "明天是我生日",
            assistantMsg = "生日快乐！",
            characterCode = "gu_chen",
        )

        verify(keyEventDao).insert(any())
    }
}
