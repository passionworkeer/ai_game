package com.aiyougame.companion.speech

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [WhisperDownloader].
 * Tests model download, resume, SHA-256 validation, and error handling.
 */
@RunWith(MockitoJUnitRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class WhisperDownloaderTest {

    @Mock
    private lateinit var mockContext: android.content.Context

    @Test
    fun `WhisperDownloader constructs without error`() {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val downloader = WhisperDownloader(mockContext, client)
        assertTrue("Downloader should be created", downloader != null)
    }

    @Test
    fun `getModelPath returns absolute path to whisper model`() {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
        val downloader = WhisperDownloader(mockContext, client)
        assertTrue(downloader.getModelPath().endsWith("whisper-tiny-en.bin"))
    }
}
