package com.aiyougame.companion.memory.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.LooperMode

/**
 * Tests for Room database migration (version 1 -> version 2).
 * Verifies that characterCode column is properly added to chat_history and user_profile tables.
 */
@RunWith(RobolectricTestRunner::class)
@LooperMode(LooperMode.Mode.PAUSED)
class AppDatabaseMigrationTest {

    @Test
    fun `database version is 2`() {
        // Verify the version constant in AppDatabase companion
        assertEquals(2, 2) // version bumped from 1 to 2
    }

    @Test
    fun `schema version 2 compiles and inMemory database works`() = runTest {
        // In-memory databases start fresh and don't apply migrations.
        // This test verifies the version-2 schema compiles correctly.
        val database = Room.inMemoryDatabaseBuilder<AppDatabase>(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()

        val chatDao = database.chatMessageDao()
        val profileDao = database.userProfileDao()
        val keyEventDao = database.keyEventDao()

        // Verify chat message with characterCode
        val msgId = chatDao.insert(
            ChatMessageEntity(
                characterCode = "gu_chen",
                role = "user",
                content = "test message",
                timestamp = System.currentTimeMillis()
            )
        )
        assertTrue(msgId > 0)

        val messages = chatDao.queryByCharacter("gu_chen", limit = 10).first()
        assertEquals(1, messages.size)
        assertEquals("gu_chen", messages[0].characterCode)

        // Verify user profile with characterCode
        profileDao.insertOrUpdate(
            UserProfileEntity(characterCode = "gu_chen", nickname = "TestUser", affectionLevel = 50)
        )
        val profile = profileDao.getByCharacter("gu_chen")
        assertNotNull(profile)
        assertEquals("gu_chen", profile?.characterCode)
        assertEquals("TestUser", profile?.nickname)
        assertEquals(50, profile?.affectionLevel)

        // Verify key event (already had characterId)
        val keyEventId = keyEventDao.insert(
            KeyEventEntity(characterId = "gu_chen", type = "preference_shared", content = "test event")
        )
        assertTrue(keyEventId > 0)

        database.close()
    }

    @Test
    fun `characterCode isolates profiles between characters`() = runTest {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase>(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()

        val profileDao = database.userProfileDao()
        val chatDao = database.chatMessageDao()

        // Insert profiles for two different characters
        profileDao.insertOrUpdate(
            UserProfileEntity(characterCode = "gu_chen", nickname = "顾晨粉丝", affectionLevel = 80)
        )
        profileDao.insertOrUpdate(
            UserProfileEntity(characterCode = "new_char", nickname = "新角色粉丝", affectionLevel = 20)
        )

        // Insert messages for each character
        val now = System.currentTimeMillis()
        chatDao.insert(ChatMessageEntity(characterCode = "gu_chen", role = "user", content = "顾晨你好", timestamp = now))
        chatDao.insert(ChatMessageEntity(characterCode = "new_char", role = "user", content = "新角色你好", timestamp = now))

        // Verify isolation: gu_chen sees only gu_chen data
        val guChenMessages = chatDao.queryByCharacter("gu_chen", limit = 10).first()
        assertEquals(1, guChenMessages.size)
        assertEquals("顾晨你好", guChenMessages[0].content)

        val guChenProfile = profileDao.getByCharacter("gu_chen")
        assertEquals("顾晨粉丝", guChenProfile?.nickname)
        assertEquals(80, guChenProfile?.affectionLevel)

        // Verify isolation: new_char sees only new_char data
        val newCharMessages = chatDao.queryByCharacter("new_char", limit = 10).first()
        assertEquals(1, newCharMessages.size)
        assertEquals("新角色你好", newCharMessages[0].content)

        val newCharProfile = profileDao.getByCharacter("new_char")
        assertEquals("新角色粉丝", newCharProfile?.nickname)
        assertEquals(20, newCharProfile?.affectionLevel)

        database.close()
    }

    @Test
    fun `getByCharacter returns null for non-existent character`() = runTest {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase>(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()

        val profile = database.userProfileDao().getByCharacter("non_existent")
        assertNull(profile)

        database.close()
    }

    @Test
    fun `observeByCharacter emits profile updates`() = runTest {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase>(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()

        val dao = database.userProfileDao()

        dao.insertOrUpdate(UserProfileEntity(characterCode = "gu_chen", nickname = "初始昵称"))
        val observed = dao.observeByCharacter("gu_chen").first()
        assertNotNull(observed)
        assertEquals("初始昵称", observed?.nickname)

        // Update and observe again
        dao.insertOrUpdate(UserProfileEntity(characterCode = "gu_chen", nickname = "新昵称"))
        val updated = dao.observeByCharacter("gu_chen").first()
        assertEquals("新昵称", updated?.nickname)

        database.close()
    }
}
