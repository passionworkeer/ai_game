package com.aiyougame.companion.speech

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the voice recognition lifecycle: recording -> transcription -> result.
 *
 * Privacy: all operations are LOCAL. Audio never leaves the device.
 *
 * States:
 * - [Idle] — ready to record
 * - [Recording] — actively recording audio
 * - [Recognizing] — downloading model / transcribing with Whisper
 * - [Done] — transcription complete with result text
 * - [Error] — transcription failed
 */
@Singleton
class VoiceRecognitionManager @Inject constructor(
    private val audioRecorder: AudioRecorder,
    private val whisperEngine: WhisperEngine,
) {
    companion object {
        private const val TAG = "VoiceRecognitionManager"
    }

    sealed class State {
        object Idle : State()
        data class Recording(val filePath: String) : State()
        data class Recognizing(val progress: Float = 0f) : State()
        data class Done(val text: String) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Structured scope — cancelled on [destroy()], preventing orphaned coroutines. */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var recordingJob: Job? = null

    /**
     * Check if RECORD_AUDIO permission is granted.
     */
    fun hasPermission(): Boolean = audioRecorder.hasPermission()

    /**
     * Start recording audio.
     * @return True if recording started successfully.
     */
    fun startRecording(): Boolean {
        if (_state.value !is State.Idle) {
            Log.w(TAG, "Cannot start recording, state is not Idle")
            return false
        }

        val filePath = audioRecorder.startRecording()
        if (filePath == null) {
            _state.value = State.Error("无法启动录音，请检查麦克风权限")
            return false
        }

        _state.value = State.Recording(filePath)

        // Start recording coroutine — scoped so it is cancelled on [destroy()]
        recordingJob = scope.launch {
            audioRecorder.recordToFile(filePath) {
                stopRecording()
            }
        }

        return true
    }

    /**
     * Stop recording and start transcription.
     */
    fun stopRecording() {
        val currentState = _state.value
        if (currentState !is State.Recording) {
            Log.w(TAG, "Not recording, state: $currentState")
            return
        }

        recordingJob?.cancel()
        recordingJob = null

        val filePath = currentState.filePath
        if (!audioRecorder.stopRecording(filePath)) {
            _state.value = State.Error("录音保存失败")
            return
        }

        // Start transcription — ensure Whisper is initialized first
        _state.value = State.Recognizing()

        scope.launch {
            // Initialize Whisper engine if not yet ready (triggers model download on first use)
            if (!whisperEngine.isReady()) {
                val initResult = whisperEngine.initialize()
                if (initResult.isFailure) {
                    _state.value = State.Error("语音模型初始化失败: ${initResult.exceptionOrNull()?.message}")
                    return@launch
                }
            }

            val result = whisperEngine.transcribe(filePath)

            if (result.isSuccess) {
                val text = result.getOrThrow()
                _state.value = State.Done(text)
            } else {
                val exception = result.exceptionOrNull()
                val message = when (exception) {
                    is NotYetIntegratedException -> "语音识别功能正在准备中"
                    else -> exception?.message ?: "识别失败，请重试"
                }
                _state.value = State.Error(message)
            }

            // Clean up temp file
            try {
                java.io.File(filePath).delete()
            } catch (_: Exception) {}
        }
    }

    /**
     * Cancel the current recording and return to idle.
     */
    fun cancel() {
        recordingJob?.cancel()
        recordingJob = null
        audioRecorder.cancelRecording()
        _state.value = State.Idle
    }

    /**
     * Reset to idle state (e.g., after text is used).
     */
    fun reset() {
        _state.value = State.Idle
    }

    /**
     * Get the last recognized text, or null if not available.
     */
    fun getLastText(): String? {
        return (_state.value as? State.Done)?.text
    }

    /**
     * Destroy the manager, cancelling all pending coroutines.
     * Call this from ViewModel.onCleared() or when the manager is no longer needed.
     */
    fun destroy() {
        scope.cancel()
        Log.d(TAG, "destroyed — all coroutines cancelled")
    }
}
