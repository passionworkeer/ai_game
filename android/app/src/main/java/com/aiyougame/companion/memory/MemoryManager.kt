package com.aiyougame.companion.memory

import com.aiyougame.companion.di.IoDispatcher
import com.aiyougame.companion.memory.db.ChatMessageDao
import com.aiyougame.companion.memory.db.ChatMessageEntity
import com.aiyougame.companion.memory.db.KeyEventDao
import com.aiyougame.companion.memory.db.KeyEventEntity
import com.aiyougame.companion.memory.db.UserProfileDao
import com.aiyougame.companion.memory.db.UserProfileEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Three-layer memory manager.
 *
 * Layer 1 — Context Window: recent chat messages (in-memory + Room)
 * Layer 2 — Mid-term Memory: user profile (nickname/likes/dislikes/mood/affection)
 * Layer 3 — Long-term Memory: key events (birthday/promise/activity)
 *
 * All operations run on [IoDispatcher].
 */
@Singleton
class MemoryManager @Inject constructor(
    private val userProfileDao: UserProfileDao,
    private val keyEventDao: KeyEventDao,
    private val chatMessageDao: ChatMessageDao,
    private val profileExtractor: ProfileExtractor,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    companion object {
        private const val RECENT_MESSAGE_LIMIT = 20
        private const val AFFECTION_PER_ROUND = 1
        private const val MAX_AFFECTION = 100
    }

    /**
     * Three-layer snapshot injected into System Prompt.
     */
    data class MemorySnapshot(
        val recentContext: String,   // Layer 1
        val userProfile: String,     // Layer 2
        val keyEvents: String,       // Layer 3
    )

    /**
     * Build a three-layer snapshot for Prompt injection.
     * Safe to call even if DB is empty (returns empty strings).
     */
    suspend fun buildSnapshot(characterCode: String): MemorySnapshot =
        withContext(io) {
            val recentMessages = chatMessageDao
                .queryRecentByCharacter(characterCode, RECENT_MESSAGE_LIMIT)
                .first()

            val layer1 = buildRecentContext(recentMessages)
            val layer2 = buildUserProfile()
            val layer3 = buildKeyEvents()

            MemorySnapshot(
                recentContext = layer1,
                userProfile = layer2,
                keyEvents = layer3,
            )
        }

    /**
     * Process after each message exchange.
     * 1. Persist user + assistant messages
     * 2. Extract profile updates via ProfileExtractor
     * 3. Persist key event if extracted
     */
    suspend fun processAfterMessage(
        userMsg: String,
        assistantMsg: String,
        characterCode: String,
    ) = withContext(io) {
        // Layer 1: persist messages
        val now = System.currentTimeMillis()
        chatMessageDao.insert(
            ChatMessageEntity(
                role = "user",
                content = userMsg,
                timestamp = now,
                characterCode = characterCode,
            )
        )
        chatMessageDao.insert(
            ChatMessageEntity(
                role = "assistant",
                content = assistantMsg,
                timestamp = now + 1,
                characterCode = characterCode,
            )
        )

        // Layer 2: extract + update profile
        val extraction = profileExtractor.extract(userMsg)
        val current = userProfileDao.get() ?: UserProfileEntity()

        val updatedNickname = current.nickname ?: extraction.nickname

        val existingLikes = if (current.interests.isNotBlank() && current.interests != "[]") {
            profileExtractor.parseJsonArray(current.interests).toMutableList()
        } else {
            mutableListOf()
        }
        extraction.likes?.let { newLikes ->
            existingLikes.addAll(profileExtractor.parseJsonArray(newLikes))
        }
        val updatedLikes = if (existingLikes.isNotEmpty()) {
            "[" + existingLikes.distinct().joinToString(",") { "\"$it\"" } + "]"
        } else {
            current.interests
        }

        val newAffection = (current.affectionLevel + AFFECTION_PER_ROUND).coerceAtMost(MAX_AFFECTION)

        userProfileDao.insertOrUpdate(
            current.copy(
                nickname = updatedNickname,
                interests = updatedLikes,
                affectionLevel = newAffection,
                updatedAt = now,
            )
        )

        // Layer 3: persist key event if extracted
        extraction.keyEvent?.let { event ->
            keyEventDao.insert(
                KeyEventEntity(
                    characterId = characterCode,
                    type = event.category,
                    content = event.summary,
                    happenedAt = now,
                )
            )
        }
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private fun buildRecentContext(messages: List<ChatMessageEntity>): String {
        if (messages.isEmpty()) return "（暂无对话历史）"
        return messages.joinToString("\n") { msg ->
            val role = if (msg.role == "user") "用户" else "顾晨"
            "$role：${msg.content}"
        }
    }

    private suspend fun buildUserProfile(): String {
        val profile = userProfileDao.get() ?: return "（暂无用户画像）"

        val parts = mutableListOf<String>()
        profile.nickname?.let { parts.add("昵称：$it") }
        if (profile.interests.isNotBlank() && profile.interests != "[]") {
            val likes = profileExtractor.parseJsonArray(profile.interests).joinToString("、")
            if (likes.isNotBlank()) parts.add("喜好：$likes")
        }
        profile.moodLogs?.takeIf { it.isNotBlank() && it != "[]" }?.let { parts.add("心情日志：$it") }
        parts.add("好感度：${profile.affectionLevel}/$MAX_AFFECTION")

        return if (parts.isEmpty()) "（暂无用户画像）" else parts.joinToString("\n")
    }

    private suspend fun buildKeyEvents(): String {
        val events = keyEventDao.queryByCharacter("gu_chen", 10).first()
        if (events.isEmpty()) return "（暂无关键事件）"
        return events.joinToString("\n") { event ->
            "[${event.type}] ${event.content}"
        }
    }
}
