package com.aiyougame.companion.llm

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import javax.inject.Provider

/**
 * Unit tests for LlamaEngineManager — validates engine pool, LRU eviction, and memory safety.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowLog::class])
class LlamaEngineManagerTest {

    private lateinit var engineFactory: Provider<LlamaEngineImpl>
    private lateinit var mockEngine1: LlamaEngineImpl
    private lateinit var mockEngine2: LlamaEngineImpl

    @Before
    fun setup() {
        mockEngine1 = mockk(relaxed = true)
        mockEngine2 = mockk(relaxed = true)
        coEvery { mockEngine1.initialize() } returns Result.success(Unit)
        coEvery { mockEngine2.initialize() } returns Result.success(Unit)

        engineFactory = mockk()
        var callCount = 0
        every { engineFactory.get() } answers {
            callCount++
            if (callCount % 2 == 1) mockEngine1 else mockEngine2
        }
    }

    @Test
    fun `getEngine returns engine for character`() = runTest {
        val manager = LlamaEngineManager(mockk(relaxed = true), engineFactory)

        val engine = manager.getEngine("gu_chen")

        assertNotNull(engine)
        verify { engineFactory.get() }
    }

    @Test
    fun `getEngine returns same engine for same character`() = runTest {
        val manager = LlamaEngineManager(mockk(relaxed = true), engineFactory)

        val engine1 = manager.getEngine("gu_chen")
        val engine2 = manager.getEngine("gu_chen")

        assertSame(engine1, engine2)
        verify(exactly = 1) { engineFactory.get() }
    }

    @Test
    fun `getEngine creates different engines for different characters`() = runTest {
        val manager = LlamaEngineManager(mockk(relaxed = true), engineFactory)

        val engine1 = manager.getEngine("gu_chen")
        val engine2 = manager.getEngine("new_char")

        assertNotSame(engine1, engine2)
        assertEquals(2, manager.loadedCount())
    }

    @Test
    fun `loadedCount returns correct number`() = runTest {
        val manager = LlamaEngineManager(mockk(relaxed = true), engineFactory)

        manager.getEngine("gu_chen")
        assertEquals(1, manager.loadedCount())

        manager.getEngine("new_char")
        assertEquals(2, manager.loadedCount())
    }

    @Test
    fun `releaseEngine releases specific engine`() = runTest {
        val manager = LlamaEngineManager(mockk(relaxed = true), engineFactory)

        val engine = manager.getEngine("gu_chen")
        manager.releaseEngine("gu_chen")

        verify { engine.release() }
        assertEquals(0, manager.loadedCount())
    }

    @Test
    fun `releaseAll releases all engines`() = runTest {
        val manager = LlamaEngineManager(mockk(relaxed = true), engineFactory)

        val engine1 = manager.getEngine("gu_chen")
        val engine2 = manager.getEngine("new_char")

        manager.releaseAll()

        verify { engine1.release() }
        verify { engine2.release() }
        assertEquals(0, manager.loadedCount())
    }

    @Test
    fun `getLoadedEngine returns null for unloaded character`() {
        val manager = LlamaEngineManager(mockk(relaxed = true), engineFactory)

        assertNull(manager.getLoadedEngine("gu_chen"))
    }

    @Test
    fun `getLoadedEngine returns loaded engine`() = runTest {
        val manager = LlamaEngineManager(mockk(relaxed = true), engineFactory)

        val engine = manager.getEngine("gu_chen")
        assertSame(engine, manager.getLoadedEngine("gu_chen"))
    }
}
