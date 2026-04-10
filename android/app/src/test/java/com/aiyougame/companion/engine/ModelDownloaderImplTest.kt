package com.aiyougame.companion.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import org.mockito.kotlin.mock
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Unit tests for ModelDownloaderImpl.
 * OkHttp Call/Response are mocked; real SHA-256 computation is tested with temp files.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloaderImplTest {

    private lateinit var tempDir: File
    private lateinit var mockContext: Context
    private lateinit var mockFilesDir: File
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var downloader: ModelDownloaderImpl

    private val realSha256 = "7c6dec4f0480ab4109743ab7cf13c07c850a99a6907c0366fa1678cef377e8ca"
    private val cdnUrl = "https://cdn.aiyougame.com/models/gemma-4-E4B-it-Q4_0.gguf"

    @Before
    fun setup() {
        tempDir = createTempDir()
        mockFilesDir = File(tempDir, "files")
        mockFilesDir.mkdirs()

        mockContext = mock {
            on { filesDir } doReturn mockFilesDir
        }

        // Use a real single-threaded OkHttpClient for synchronous mock responses
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        okHttpClient = OkHttpClient.Builder()
            .dispatcher(okhttp3.Dispatcher(executor))
            .build()

        downloader = ModelDownloaderImpl(mockContext, okHttpClient)
    }

    // ─── isModelReady ─────────────────────────────────────────────────────────

    @Test
    fun `isModelReady returns false when file does not exist`() = runTest {
        assertFalse(downloader.isModelReady(realSha256))
    }

    @Test
    fun `isModelReady returns true when SHA-256 matches`() = runTest {
        // Create a real temp file and write known content
        val modelDir = File(mockFilesDir, "models")
        modelDir.mkdirs()
        val modelFile = File(modelDir, "gemma-4-E4B-it-Q4_0.gguf")

        // Compute SHA-256 of an empty file (deterministic)
        val emptySha = computeSha256Hex("")
        modelFile.writeText("")
        assertTrue(downloader.isModelReady(emptySha))
        modelFile.delete()
    }

    @Test
    fun `isModelReady returns false when SHA-256 does not match`() = runTest {
        val modelDir = File(mockFilesDir, "models")
        modelDir.mkdirs()
        val modelFile = File(modelDir, "gemma-4-E4B-it-Q4_0.gguf")
        modelFile.writeText("different content")
        assertFalse(downloader.isModelReady(realSha256))
        modelFile.delete()
    }

    // ─── deleteLocalModel ─────────────────────────────────────────────────────

    @Test
    fun `deleteLocalModel removes existing file`() = runTest {
        val modelDir = File(mockFilesDir, "models")
        modelDir.mkdirs()
        val modelFile = File(modelDir, "gemma-4-E4B-it-Q4_0.gguf")
        modelFile.writeText("placeholder")
        assertTrue(modelFile.exists())

        downloader.deleteLocalModel()

        assertFalse(modelFile.exists())
    }

    @Test
    fun `deleteLocalModel is safe when file does not exist`() = runTest {
        // Should not throw
        downloader.deleteLocalModel()
        assertFalse(File(mockFilesDir, "models/gemma-4-E4B-it-Q4_0.gguf").exists())
    }

    // ─── download — full run with mocked OkHttp ───────────────────────────────

    @Test
    fun `download returns failure when HEAD request fails`() = runTest {
        val mockCall = mock<Call>()
        whenever(mockCall.execute()).thenReturn(
            responseOf(code = 404, body = null, contentLength = null)
        )
        val client = mock<OkHttpClient> {
            on { newCall(any()) } doReturn mockCall
        }
        val dl = ModelDownloaderImpl(mockContext, client)

        val result = dl.download(cdnUrl, realSha256)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("404"))
    }

    @Test
    fun `download returns failure when Content-Length header is missing`() = runTest {
        val mockCall = mock<Call>()
        whenever(mockCall.execute()).thenReturn(
            responseOf(code = 200, body = null, contentLength = null)
        )
        val client = mock<OkHttpClient> {
            on { newCall(any()) } doReturn mockCall
        }
        val dl = ModelDownloaderImpl(mockContext, client)

        val result = dl.download(cdnUrl, realSha256)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Content-Length"))
    }

    // ─── Helper functions ──────────────────────────────────────────────────────

    /**
     * Build a mock [Response] for OkHttp Call.execute().
     * Only fields actually used by [ModelDownloaderImpl] are configured.
     */
    private fun responseOf(
        code: Int,
        body: String?,
        contentLength: Long?,
    ): Response {
        @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
        return Response.Builder()
            .request(okhttp3.Request.Builder().url("https://example.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .body(body?.toResponseBody(null) ?: null)
            .apply {
                if (contentLength != null) {
                    header("Content-Length", contentLength.toString())
                }
            }
            .build()
    }

    /**
     * Compute SHA-256 hex of a string (for test fixtures).
     */
    private fun computeSha256Hex(content: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(content.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it as Byte) }
    }

    private fun createTempDir(): File {
        val f = File.createTempFile("test", "dir")
        f.delete()
        f.mkdirs()
        return f
    }
}
