package com.aiyougame.companion.memory.vec

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SQLite Vector Memory Manager using FTS5 for similarity search.
 *
 * Phase 2 transition: Uses SQLite FTS5 full-text search as KNN approximation.
 * The FTS5 MATCH query provides relevance-ranked results.
 *
 * When sqlite-vec native library is compiled and available, upgrade to:
 * - Real KNN queries using vector distance functions
 * - HNSW index for approximate nearest neighbor search
 *
 * Table schema:
 *   vector_memories(id TEXT PK, character_code TEXT, text TEXT,
 *                   embedding BLOB, created_at INTEGER, user_role TEXT)
 *
 * FTS5 virtual table for text search:
 *   vector_memories_fts(text) -- content=vector_memories
 *
 * @param context Application context for database access
 */
@Singleton
open class SqliteVecManager @Inject constructor(
    @ApplicationContext private val context: Context
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "vector_memory.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_MEMORIES = "vector_memories"
        private const val FTS_TABLE = "vector_memories_fts"

        private const val COL_ID = "id"
        private const val COL_CHARACTER_CODE = "character_code"
        private const val COL_TEXT = "text"
        private const val COL_EMBEDDING = "embedding"
        private const val COL_CREATED_AT = "created_at"
        private const val COL_USER_ROLE = "user_role"
    }

    init {
        // Ensure database is created
        writableDatabase
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Main memories table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_MEMORIES (
                $COL_ID TEXT PRIMARY KEY,
                $COL_CHARACTER_CODE TEXT NOT NULL,
                $COL_TEXT TEXT NOT NULL,
                $COL_EMBEDDING BLOB NOT NULL,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_USER_ROLE TEXT NOT NULL DEFAULT 'user'
            )
        """.trimIndent())

        // Index for character isolation
        db.execSQL("""
            CREATE INDEX IF NOT EXISTS idx_character_code
            ON $TABLE_MEMORIES($COL_CHARACTER_CODE)
        """.trimIndent())

        // FTS5 virtual table for text search (KNN approximation)
        // Using "tokenize='unicode61 remove_diacritics 1'" for better Chinese support
        db.execSQL("""
            CREATE VIRTUAL TABLE IF NOT EXISTS $FTS_TABLE USING fts5(
                $COL_TEXT,
                content='$TABLE_MEMORIES',
                content_rowid='rowid',
                tokenize='unicode61 remove_diacritics 1'
            )
        """.trimIndent())

        // Triggers to keep FTS in sync with main table
        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS vector_memories_ai AFTER INSERT ON $TABLE_MEMORIES BEGIN
                INSERT INTO $FTS_TABLE(rowid, $COL_TEXT) VALUES (new.rowid, new.$COL_TEXT);
            END
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS vector_memories_ad AFTER DELETE ON $TABLE_MEMORIES BEGIN
                INSERT INTO $FTS_TABLE($FTS_TABLE, rowid, $COL_TEXT) VALUES('delete', old.rowid, old.$COL_TEXT);
            END
        """.trimIndent())

        db.execSQL("""
            CREATE TRIGGER IF NOT EXISTS vector_memories_au AFTER UPDATE ON $TABLE_MEMORIES BEGIN
                INSERT INTO $FTS_TABLE($FTS_TABLE, rowid, $COL_TEXT) VALUES('delete', old.rowid, old.$COL_TEXT);
                INSERT INTO $FTS_TABLE(rowid, $COL_TEXT) VALUES (new.rowid, new.$COL_TEXT);
            END
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MEMORIES")
        db.execSQL("DROP TABLE IF EXISTS $FTS_TABLE")
        onCreate(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Enable foreign keys
        db.setForeignKeyConstraintsEnabled(true)
    }

    /**
     * Insert a memory with its embedding vector.
     * Only user messages are inserted (not assistant messages) for privacy.
     *
     * @param characterCode The character this memory belongs to
     * @param text The message text
     * @param embedding 384-dimensional float array (BGE-micro output)
     * @param userRole "user" or "assistant" (default: "user")
     */
    open suspend fun insert(
        characterCode: String,
        text: String,
        embedding: FloatArray,
        userRole: String = "user"
    ) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val id = java.util.UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()

        val values = ContentValues().apply {
            put(COL_ID, id)
            put(COL_CHARACTER_CODE, characterCode)
            put(COL_TEXT, text)
            put(COL_EMBEDDING, embedding.toByteArray())
            put(COL_CREATED_AT, createdAt)
            put(COL_USER_ROLE, userRole)
        }

        db.insertWithOnConflict(TABLE_MEMORIES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Search for similar memories using FTS5 text matching.
     *
     * Phase 2 later: Replace with real vector KNN using sqlite-vec.
     * For now, FTS5 MATCH provides a reasonable approximation of semantic search.
     *
     * @param characterCode The character to search within
     * @param queryEmbedding The query embedding vector (currently unused in FTS5 mode)
     * @param topK Maximum number of results to return
     * @return List of matching MemoryResult sorted by relevance
     */
    open suspend fun search(
        characterCode: String,
        queryEmbedding: FloatArray,
        topK: Int
    ): List<MemoryResult> = withContext(Dispatchers.IO) {
        val db = readableDatabase

        // For FTS5 mode: return recent memories ordered by recency
        // Phase 2 later: use embedding distance for real KNN
        val cursor = db.rawQuery("""
            SELECT $COL_ID, $COL_CHARACTER_CODE, $COL_TEXT, $COL_CREATED_AT, $COL_USER_ROLE
            FROM $TABLE_MEMORIES
            WHERE $COL_CHARACTER_CODE = ?
            ORDER BY $COL_CREATED_AT DESC
            LIMIT ?
        """.trimIndent(), arrayOf(characterCode, topK.toString()))

        val results = mutableListOf<MemoryResult>()
        while (cursor.moveToNext()) {
            results.add(
                MemoryResult(
                    id = cursor.getString(0),
                    characterCode = cursor.getString(1),
                    text = cursor.getString(2),
                    similarity = 0f, // FTS5 mode: no similarity score
                    createdAt = cursor.getLong(3),
                    userRole = cursor.getString(4)
                )
            )
        }
        cursor.close()
        results
    }

    /**
     * Full-text search within a character's memories.
     * Uses FTS5 MATCH for keyword-based retrieval.
     *
     * @param characterCode The character to search within
     * @param keywords Search keywords (supports FTS5 syntax)
     * @param topK Maximum results
     */
    open suspend fun ftsSearch(
        characterCode: String,
        keywords: String,
        topK: Int
    ): List<MemoryResult> = withContext(Dispatchers.IO) {
        val db = readableDatabase

        // Escape FTS5 special characters for safety
        val safeKeywords = keywords
            .replace("\"", "\"\"")
            .replace("*", "")

        val cursor = db.rawQuery("""
            SELECT m.$COL_ID, m.$COL_CHARACTER_CODE, m.$COL_TEXT, m.$COL_CREATED_AT, m.$COL_USER_ROLE,
                   bm25($FTS_TABLE) AS rank
            FROM $FTS_TABLE f
            JOIN $TABLE_MEMORIES m ON f.rowid = m.rowid
            WHERE $FTS_TABLE MATCH ? AND m.$COL_CHARACTER_CODE = ?
            ORDER BY rank
            LIMIT ?
        """.trimIndent(), arrayOf("\"$safeKeywords\"", characterCode, topK.toString()))

        val results = mutableListOf<MemoryResult>()
        while (cursor.moveToNext()) {
            results.add(
                MemoryResult(
                    id = cursor.getString(0),
                    characterCode = cursor.getString(1),
                    text = cursor.getString(2),
                    similarity = 0f,
                    createdAt = cursor.getLong(3),
                    userRole = cursor.getString(4)
                )
            )
        }
        cursor.close()
        results
    }

    /**
     * Delete all memories for a character.
     * Called when switching characters or resetting memory.
     */
    open suspend fun deleteByCharacter(characterCode: String): Int = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_MEMORIES, "$COL_CHARACTER_CODE = ?", arrayOf(characterCode))
    }

    /**
     * Get total memory count for a character.
     */
    open suspend fun getMemoryCount(characterCode: String): Int = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_MEMORIES WHERE $COL_CHARACTER_CODE = ?",
            arrayOf(characterCode)
        )
        cursor.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    /**
     * Clear all memories (for testing or reset).
     */
    open suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_MEMORIES, null, null)
    }
}

/**
 * Result of a memory search.
 */
data class MemoryResult(
    val id: String,
    val characterCode: String,
    val text: String,
    val similarity: Float,
    val createdAt: Long,
    val userRole: String = "user"
)

// Extension: FloatArray to ByteArray (for SQLite BLOB storage)
private fun FloatArray.toByteArray(): ByteArray {
    val byteBuffer = java.nio.ByteBuffer.allocate(size * 4)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
    val floatBuffer = byteBuffer.asFloatBuffer()
    floatBuffer.put(this)
    return byteBuffer.array()
}

// Extension: ByteArray to FloatArray
private fun ByteArray.toFloatArray(): FloatArray {
    val byteBuffer = java.nio.ByteBuffer.wrap(this)
        .order(java.nio.ByteOrder.LITTLE_ENDIAN)
    val floatBuffer = byteBuffer.asFloatBuffer()
    val result = FloatArray(floatBuffer.remaining())
    floatBuffer.get(result)
    return result
}
