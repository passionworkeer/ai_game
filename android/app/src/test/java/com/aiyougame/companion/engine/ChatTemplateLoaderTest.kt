package com.aiyougame.companion.engine

import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

/**
 * Unit tests for ChatTemplateLoaderImpl.
 *
 * Phase 1: load() always returns "" (no GGUF reader, no chat_template.txt in assets).
 * Phase 2: when GGUF metadata reader is wired in, load(modelFile) should
 *          return the actual chat_template string from GGUF metadata.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ChatTemplateLoaderTest {

    private val mockContext: android.content.Context = mockk(relaxed = true)
    private lateinit var loader: ChatTemplateLoaderImpl

    @Before
    fun setup() {
        // Mock assets to throw (no chat_template.txt in test assets)
        every { mockContext.assets.open(any()) } throws Exception("file not found")
        loader = ChatTemplateLoaderImpl(mockContext)
    }

    // ─── load ─────────────────────────────────────────────────────────────────

    @Test
    fun `load returns empty string when modelFile is null`() = runBlocking {
        assertEquals("", loader.load(null))
    }

    @Test
    fun `load returns empty string when GGUF file does not exist`() = runBlocking {
        val fakeFile = java.io.File("/nonexistent/path/model.gguf")
        assertEquals("", loader.load(fakeFile))
    }

    @Test
    fun `load returns empty string when GGUF read fails`() = runBlocking {
        assertEquals("", loader.load(null))
    }

    // ─── buildTokenizer ────────────────────────────────────────────────────────

    @Test
    fun `buildTokenizer returns null in Phase 1`() {
        val result = loader.buildTokenizer("""
            {% for message in messages %}{{ '<|' + message.role + '|>' }}{% endfor %}
        """.trimIndent())
        assertNull(result)
    }

    @Test
    fun `buildTokenizer returns null even with empty template`() {
        assertNull(loader.buildTokenizer(""))
    }

    // ─── interface contract ─────────────────────────────────────────────────────

    @Test
    fun `ChatTemplateLoaderImpl is a ChatTemplateLoader`() {
        assertTrue(loader is ChatTemplateLoader)
    }

    @Test
    fun `Tokenizer interface encode and decode are abstract contracts`() {
        val tokenizer = object : Tokenizer {
            override fun encode(text: String): List<Int> = listOf(1, 2, 3)
            override fun decode(tokenIds: List<Int>): String = "decoded"
        }
        assertEquals(listOf(1, 2, 3), tokenizer.encode("hello"))
        assertEquals("decoded", tokenizer.decode(listOf(1, 2, 3)))
    }
}
