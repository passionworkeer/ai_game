package com.aiyougame.companion.memory.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Key event entity for important user-character interactions.
 * Stored locally only — never uploaded to cloud per privacy requirements.
 *
 * @param id Primary key, auto-generated.
 * @param characterId Character code this event belongs to.
 * @param type Event type: "nickname_set" | "preference_shared" | "mood_recorded".
 * @param content Event content/summary.
 * @param happenedAt Timestamp when the event occurred.
 */
@Entity(
    tableName = "key_events",
    indices = [Index(value = ["characterId", "happenedAt"])]
)
data class KeyEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val characterId: String,
    val type: String,
    val content: String,
    val happenedAt: Long = System.currentTimeMillis()
)
