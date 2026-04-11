package com.aiyougame.companion.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Audio recorder for voice input.
 *
 * Records audio in WAV format at 16kHz, 16-bit PCM mono
 * which is the required format for Whisper inference.
 *
 * Maximum recording duration: 30 seconds.
 *
 * Privacy: all recording is LOCAL, audio never leaves the device.
 */
@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "AudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val MAX_DURATION_MS = 30_000L
    }

    private var audioRecord: AudioRecord? = null
    private var recordingFile: File? = null
    private var isRecording = false

    /**
     * Guards AudioRecord stop/release between [stopRecording] and [recordToFile] coroutine.
     * Without this lock, stopRecording() releasing AudioRecord while recordToFile()
     * is still inside audioRecord?.read() causes IllegalStateException crash.
     * ReentrantLock.tryLock(timeout, unit) is used here because stopRecording()
     * is a non-suspending function — kotlinx.coroutines Mutex has no timeout variant.
     */
    private val audioMutex = ReentrantLock()

    /**
     * Check if RECORD_AUDIO permission is granted.
     */
    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Start recording audio.
     * Creates a temporary WAV file in the app's cache directory.
     *
     * @return The path to the recording file, or null if recording could not start.
     */
    fun startRecording(): String? {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return null
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        )
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return null
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized")
                audioRecord?.release()
                audioRecord = null
                return null
            }

            val tempFile = File.createTempFile("voice_input_", ".wav", context.cacheDir)
            recordingFile = tempFile
            isRecording = true
            audioRecord?.startRecording()

            Log.d(TAG, "Recording started: ${tempFile.absolutePath}")
            return tempFile.absolutePath
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied", e)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            audioRecord?.release()
            audioRecord = null
            return null
        }
    }

    /**
     * Stop recording and finalize the WAV file.
     * The file will contain a valid WAV header with the recorded audio data.
     *
     * @param recordingPath The path returned by [startRecording].
     * @return True if the WAV file was successfully finalized, false otherwise.
     */
    fun stopRecording(recordingPath: String): Boolean {
        if (!isRecording || recordingFile?.absolutePath != recordingPath) {
            Log.w(TAG, "Not recording or path mismatch")
            return false
        }

        return try {
            // Acquire lock to safely stop/release AudioRecord.
            // The recordToFile coroutine acquires this lock briefly around each read(),
            // so this will block until the current read() completes.
            audioMutex.tryLock(5_000L, TimeUnit.MILLISECONDS)
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            isRecording = false
            audioMutex.unlock()

            Log.d(TAG, "Recording stopped: $recordingPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            if (audioMutex.isHeldByCurrentThread) audioMutex.unlock()
            audioRecord?.release()
            audioRecord = null
            isRecording = false
            false
        }
    }

    /**
     * Cancel the current recording and delete the temp file.
     */
    fun cancelRecording() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        isRecording = false
        recordingFile?.delete()
        recordingFile = null
    }

    /**
     * Write PCM audio data to a WAV file in a background coroutine.
     * Runs until [stopRecording] is called or [MAX_DURATION_MS] is reached.
     *
     * @param filePath The path to the WAV file.
     * @param onDurationReached Callback when max duration is reached.
     */
    suspend fun recordToFile(
        filePath: String,
        onDurationReached: () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val buffer = ShortArray(bufferSize)
        val byteBuffer = ByteArray(bufferSize * 2)

        FileOutputStream(file).use { fos ->
            // Write placeholder WAV header (will be updated after recording)
            val header = createWavHeader(0)
            fos.write(header)

            var totalBytesWritten = 0
            val startTime = System.currentTimeMillis()

            while (isRecording && coroutineContext.isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= MAX_DURATION_MS) {
                    Log.d(TAG, "Max duration reached: $MAX_DURATION_MS ms")
                    withContext(Dispatchers.Main) { onDurationReached() }
                    break
                }

                // Acquire lock briefly around read() to avoid racing with stopRecording().
                // This ensures stopRecording() cannot call stop()/release() while we are
                // inside read(), preventing the IllegalStateException crash.
                run lock@ {
                    audioMutex.lock()
                    try {
                        if (!isRecording) return@lock
                        val readCount = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                        if (readCount > 0) {
                            // Convert shorts to bytes (little-endian)
                            ByteBuffer.wrap(byteBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(buffer, 0, readCount)
                            fos.write(byteBuffer, 0, readCount * 2)
                            totalBytesWritten += readCount * 2
                        }
                    } finally {
                        audioMutex.unlock()
                    }
                }
            }

            // Update WAV header with correct data size
            updateWavHeader(file, totalBytesWritten)
            Log.d(TAG, "Wrote $totalBytesWritten bytes to $filePath")
        }
    }

    /**
     * Create a minimal WAV header for a given data size.
     */
    private fun createWavHeader(dataSize: Int): ByteArray {
        val header = ByteArray(44)
        val byteRate = SAMPLE_RATE * 1 * 16 / 8 // channels * bits per sample / 8
        val blockAlign = 1 * 16 / 8 // channels * bits per sample / 8

        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // File size - 8
        val fileSize = dataSize + 36
        header[4] = (fileSize and 0xFF).toByte()
        header[5] = ((fileSize shr 8) and 0xFF).toByte()
        header[6] = ((fileSize shr 16) and 0xFF).toByte()
        header[7] = ((fileSize shr 24) and 0xFF).toByte()

        // WAVE
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // Subchunk1Size (16 for PCM)
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // AudioFormat (1 = PCM)
        header[21] = 0
        header[22] = 1 // NumChannels (mono)
        header[23] = 0
        // SampleRate
        header[24] = (SAMPLE_RATE and 0xFF).toByte()
        header[25] = ((SAMPLE_RATE shr 8) and 0xFF).toByte()
        header[26] = ((SAMPLE_RATE shr 16) and 0xFF).toByte()
        header[27] = ((SAMPLE_RATE shr 24) and 0xFF).toByte()
        // ByteRate
        header[28] = (byteRate and 0xFF).toByte()
        header[29] = ((byteRate shr 8) and 0xFF).toByte()
        header[30] = ((byteRate shr 16) and 0xFF).toByte()
        header[31] = ((byteRate shr 24) and 0xFF).toByte()
        // BlockAlign
        header[32] = blockAlign.toByte()
        header[33] = 0
        // BitsPerSample
        header[34] = 16
        header[35] = 0

        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        // Data size
        header[40] = (dataSize and 0xFF).toByte()
        header[41] = ((dataSize shr 8) and 0xFF).toByte()
        header[42] = ((dataSize shr 16) and 0xFF).toByte()
        header[43] = ((dataSize shr 24) and 0xFF).toByte()

        return header
    }

    /**
     * Update the WAV header with the correct data size.
     */
    private fun updateWavHeader(file: File, dataSize: Int) {
        try {
            val raf = java.io.RandomAccessFile(file, "rw")
            val fileSize = dataSize + 36

            // Update RIFF chunk size (bytes 4-7)
            raf.seek(4)
            raf.write(fileSize and 0xFF)
            raf.write((fileSize shr 8) and 0xFF)
            raf.write((fileSize shr 16) and 0xFF)
            raf.write((fileSize shr 24) and 0xFF)

            // Update data chunk size (bytes 40-43)
            raf.seek(40)
            raf.write(dataSize and 0xFF)
            raf.write((dataSize shr 8) and 0xFF)
            raf.write((dataSize shr 16) and 0xFF)
            raf.write((dataSize shr 24) and 0xFF)

            raf.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update WAV header", e)
        }
    }
}
