package com.aiyougame.companion.engine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ChatTemplateLoaderImpl.
 *
 * Phase 1: load() always returns "" — these tests verify that contract.
 * Phase 2: when llama.cpp GGUF API is wired in, load() should return the
 *          actual chat_template string and buildTokenizer() should return
 *          a non-null Tokenizer; this test class should be extended accordingly.
 */
class ChatTemplateLoaderTest {

    private lateinit var loader: ChatTemplateLoader

    @Before
    fun setup() {
        loader = ChatTemplateLoaderImpl()
    }

    // ─── load ─────────────────────────────────────────────────────────────────

    @Test
    fun `load returns empty string regardless of modelPath`() {
        assertEquals("", loader.load("/data/models/gemma-4-E4B-it-Q4_0.gguf"))
    }

    @Test
    fun `load returns empty string for arbitrary path`() {
        assertEquals("", loader.load("any/random/path.gguf"))
    }

    @Test
    fun `load returns empty string for blank path`() {
        assertEquals("", loader.load(""))
    }

    // ─── buildTokenizer ────────────────────────────────────────────────────────

    @Test
    fun `buildTokenizer returns null in Phase 1`() {
        val result = loader.buildTokenizer("""
            {% for message in messages %}{{ '<|' + message.role + '|>\n' + message.content + '<|end|>\n' }}{% endfor %}
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
        // Verify the interface exists and is functional (smoke test)
        val tokenizer = object : Tokenizer {
            override fun encode(text: String): List<Int> = listOf(1, 2, 3)
            override fun decode(tokenIds: List<Int>): String = "decoded"
        }
        assertEquals(listOf(1, 2, 3), tokenizer.encode("hello"))
        assertEquals("decoded", tokenizer.decode(listOf(1, 2, 3)))
    }
}
