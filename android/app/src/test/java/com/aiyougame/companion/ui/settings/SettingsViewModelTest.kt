package com.aiyougame.companion.ui.settings

import android.content.SharedPreferences
import com.aiyougame.companion.data.prefs.TokenManager
import com.aiyougame.companion.di.MainDispatcher
import app.cash.turbine.test
import io.mockk.*
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * TDD RED: SettingsViewModel tests — define the contract for settings screen state.
 * Cloud sync defaults to OFF (privacy-first). OpenClaw IP is optional.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val tokenManager: TokenManager = mockk(relaxed = true)
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor

    private lateinit var viewModel: SettingsViewModel
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockPrefsEditor = mockk(relaxed = true)
        every { mockPrefsEditor.putBoolean(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.putString(any(), any()) } returns mockPrefsEditor
        every { mockPrefsEditor.apply() } returns Unit
        mockPrefs = mockk<SharedPreferences> {
            every { getBoolean(any(), any()) } returns false
            every { getString(any(), any()) } returns null
            every { edit() } returns mockPrefsEditor
        }

        viewModel = SettingsViewModel(mockPrefs, tokenManager, testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has cloud sync disabled`() = runTest {
        assert(!viewModel.uiState.value.cloudSyncEnabled)
    }

    @Test
    fun `toggleCloudSync emits updated state`() = runTest {
        viewModel.toggleCloudSync(true)

        val state = viewModel.uiState.value
        assert(state.cloudSyncEnabled)
        verify { mockPrefsEditor.putBoolean("cloud_sync_enabled", true) }
        verify { mockPrefsEditor.apply() }
    }

    @Test
    fun `toggleCloudSync to false persists value`() = runTest {
        viewModel.toggleCloudSync(true)
        viewModel.toggleCloudSync(false)

        val state = viewModel.uiState.value
        assert(!state.cloudSyncEnabled)
        verify { mockPrefsEditor.putBoolean("cloud_sync_enabled", false) }
    }

    @Test
    fun `setOpenClawIp updates state`() = runTest {
        viewModel.setOpenClawIp("192.168.1.100")

        val state = viewModel.uiState.value
        assert(state.openClawIp == "192.168.1.100")
    }

    @Test
    fun `setOpenClawIp persists value`() = runTest {
        viewModel.setOpenClawIp("192.168.1.100")

        verify { mockPrefsEditor.putString("openclaw_ip", "192.168.1.100") }
        verify { mockPrefsEditor.apply() }
    }

    @Test
    fun `setOpenClawIp with empty string clears IP`() = runTest {
        viewModel.setOpenClawIp("")
        assert(viewModel.uiState.value.openClawIp == "")
    }

    @Test
    fun `saveSettings persists both cloud sync and IP`() = runTest {
        viewModel.toggleCloudSync(true)
        viewModel.setOpenClawIp("10.0.0.5")
        viewModel.saveSettings()
        advanceUntilIdle()

        verify { mockPrefsEditor.putBoolean("cloud_sync_enabled", true) }
        verify { mockPrefsEditor.putString("openclaw_ip", "10.0.0.5") }
        verify { mockPrefsEditor.apply() }
    }

    @Test
    fun `loadSettings reads persisted cloud sync value`() = runTest {
        val prefsWithSync: SharedPreferences = mockk()
        every { prefsWithSync.getBoolean("cloud_sync_enabled", false) } returns true
        every { prefsWithSync.getString("openclaw_ip", null) } returns null
        every { prefsWithSync.edit() } returns mockPrefsEditor

        val vm = SettingsViewModel(prefsWithSync, tokenManager, testDispatcher)
        vm.loadSettings()
        advanceUntilIdle()

        assert(vm.uiState.value.cloudSyncEnabled)
    }

    @Test
    fun `loadSettings reads persisted IP value`() = runTest {
        val prefsWithIp: SharedPreferences = mockk()
        every { prefsWithIp.getBoolean("cloud_sync_enabled", false) } returns false
        every { prefsWithIp.getString("openclaw_ip", null) } returns "10.0.0.1"
        every { prefsWithIp.edit() } returns mockPrefsEditor

        val vm = SettingsViewModel(prefsWithIp, tokenManager, testDispatcher)
        vm.loadSettings()
        advanceUntilIdle()

        assert(vm.uiState.value.openClawIp == "10.0.0.1")
    }

    @Test
    fun `clearAll calls tokenManager clear and resets state`() = runTest {
        every { tokenManager.clear() } just Runs

        viewModel.toggleCloudSync(true)
        viewModel.setOpenClawIp("192.168.1.1")
        viewModel.clearAll()
        advanceUntilIdle()

        verify { tokenManager.clear() }
        val state = viewModel.uiState.value
        assert(!state.cloudSyncEnabled)
        assert(state.openClawIp == "")
    }

    @Test
    fun `logout emits LoggedOut event`() = runTest {
        every { tokenManager.clear() } just Runs

        var eventReceived = false
        viewModel.events.test {
            viewModel.logout()
            advanceUntilIdle()
            eventReceived = try {
                val event = awaitItem()
                event is SettingsViewModel.SettingsUiEvent.LoggedOut
            } catch (_: Exception) {
                false
            }
        }
        assert(eventReceived)
    }
}
