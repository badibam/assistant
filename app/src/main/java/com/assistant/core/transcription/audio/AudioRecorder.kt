package com.assistant.core.transcription.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.assistant.core.utils.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Audio recorder using AudioRecord API with direct WAV file output
 * Records audio in WAV format (16kHz mono 16-bit PCM) suitable for Vosk transcription
 *
 * WAV Format:
 * - Sample rate: 16000 Hz (Vosk requirement)
 * - Channels: 1 (mono)
 * - Bit depth: 16-bit PCM
 *
 * Usage:
 * ```kotlin
 * val recorder = AudioRecorder(context)
 * recorder.start(outputFile) { success, error ->
 *     if (success) {
 *         // Recording started
 *     }
 * }
 * recorder.pause()
 * recorder.resume()
 * recorder.stop()
 * ```
 */
class AudioRecorder(private val context: Context) {

    // Audio configuration (Vosk requirements)
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val channels = 1
    private val bitsPerSample = 16

    // Recording state
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var isPaused = false
    private var outputFile: File? = null
    private var startTime: Long = 0
    private var pauseTime: Long = 0
    private var totalPauseDuration: Long = 0
    private var bytesWritten: Long = 0

    // Coroutine for background recording
    private var recordingScope: CoroutineScope? = null
    private var recordingJob: Job? = null

    /**
     * Start recording audio to file
     *
     * @param file Output file (will be created/overwritten)
     * @param onResult Callback with (success, errorMessage)
     */
    fun start(file: File, onResult: (Boolean, String?) -> Unit) {
        if (isRecording) {
            onResult(false, "Already recording")
            return
        }

        try {
            // Ensure parent directory exists
            file.parentFile?.mkdirs()

            // Calculate buffer size
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                onResult(false, "Failed to get buffer size")
                return
            }

            // Initialize AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onResult(false, "Failed to initialize AudioRecord")
                cleanup()
                return
            }

            // Create WAV file with empty header (will be updated when stopped)
            writeWavHeader(file, 0)

            // Start recording
            audioRecord?.startRecording()

            isRecording = true
            isPaused = false
            outputFile = file
            startTime = System.currentTimeMillis()
            totalPauseDuration = 0
            bytesWritten = 0

            // Start background recording job
            recordingScope = CoroutineScope(Dispatchers.IO)
            recordingJob = recordingScope?.launch {
                recordAudioData(file, bufferSize)
            }

            LogManager.service("Audio recording started: ${file.absolutePath}")
            onResult(true, null)

        } catch (e: SecurityException) {
            LogManager.service("Audio permission not granted: ${e.message}", "ERROR", e)
            cleanup()
            onResult(false, "Audio permission not granted")
        } catch (e: Exception) {
            LogManager.service("Failed to start audio recording: ${e.message}", "ERROR", e)
            cleanup()
            onResult(false, e.message ?: "Unknown error")
        }
    }

    /**
     * Background coroutine that reads audio data and writes to file
     */
    private suspend fun recordAudioData(file: File, bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        FileOutputStream(file, true).use { outputStream ->
            // Skip WAV header (44 bytes)
            outputStream.channel.position(44)

            while (isRecording) {
                if (isPaused) {
                    // Sleep while paused to avoid busy waiting
                    kotlinx.coroutines.delay(100)
                    continue
                }

                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                    bytesWritten += read
                }
            }
        }

        LogManager.service("Recording thread finished, bytes written: $bytesWritten")
    }

    /**
     * Pause recording
     */
    fun pause(): Boolean {
        if (!isRecording || isPaused) {
            LogManager.service("Cannot pause: not recording or already paused", "WARN")
            return false
        }

        isPaused = true
        pauseTime = System.currentTimeMillis()
        LogManager.service("Audio recording paused")
        return true
    }

    /**
     * Resume recording
     */
    fun resume(): Boolean {
        if (!isRecording || !isPaused) {
            LogManager.service("Cannot resume: not recording or not paused", "WARN")
            return false
        }

        totalPauseDuration += (System.currentTimeMillis() - pauseTime)
        isPaused = false
        LogManager.service("Audio recording resumed")
        return true
    }

    /**
     * Stop recording and finalize WAV file
     *
     * @return Output file if successful, null otherwise
     */
    fun stop(): File? {
        if (!isRecording) {
            LogManager.service("Cannot stop: not recording", "WARN")
            return null
        }

        return try {
            // Stop AudioRecord
            audioRecord?.stop()

            // Wait for recording job to finish
            recordingJob?.cancel()
            recordingScope?.cancel()

            val file = outputFile
            val duration = getCurrentDuration()

            // Update WAV header with actual file size
            file?.let {
                updateWavHeader(it, bytesWritten)
                LogManager.service("Audio recording stopped: ${it.absolutePath} (duration: ${duration}ms, size: ${it.length()} bytes)")
            }

            cleanup()
            file

        } catch (e: Exception) {
            LogManager.service("Failed to stop recording: ${e.message}", "ERROR", e)
            cleanup()
            null
        }
    }

    /**
     * Cancel recording and delete file
     */
    fun cancel() {
        try {
            audioRecord?.stop()
            recordingJob?.cancel()
            recordingScope?.cancel()

            outputFile?.let { file ->
                if (file.exists()) {
                    file.delete()
                    LogManager.service("Audio recording cancelled, file deleted: ${file.absolutePath}")
                }
            }

        } catch (e: Exception) {
            LogManager.service("Error during cancel: ${e.message}", "WARN", e)
        } finally {
            cleanup()
        }
    }

    /**
     * Get current recording duration in milliseconds (excluding pause time)
     */
    fun getCurrentDuration(): Long {
        if (!isRecording) return 0

        val elapsed = System.currentTimeMillis() - startTime
        val pauseOffset = if (isPaused) {
            System.currentTimeMillis() - pauseTime
        } else {
            0
        }

        return elapsed - totalPauseDuration - pauseOffset
    }

    /**
     * Get current recording state
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Get current pause state
     */
    fun isPaused(): Boolean = isPaused

    /**
     * Get max amplitude (for visual feedback)
     * Returns 0-32767 range
     */
    fun getMaxAmplitude(): Int {
        // AudioRecord doesn't have maxAmplitude like MediaRecorder
        // We could calculate it from the buffer if needed
        return 0
    }

    // ========================================================================================
    // WAV File Format Utilities
    // ========================================================================================

    /**
     * Write WAV header to file
     * WAV format: RIFF header + fmt chunk + data chunk
     */
    private fun writeWavHeader(file: File, dataSize: Long) {
        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            header.put("RIFF".toByteArray())
            header.putInt((36 + dataSize).toInt()) // File size - 8
            header.put("WAVE".toByteArray())

            // fmt chunk
            header.put("fmt ".toByteArray())
            header.putInt(16) // fmt chunk size (PCM)
            header.putShort(1) // Audio format (1 = PCM)
            header.putShort(channels.toShort()) // Number of channels
            header.putInt(sampleRate) // Sample rate
            header.putInt(sampleRate * channels * bitsPerSample / 8) // Byte rate
            header.putShort((channels * bitsPerSample / 8).toShort()) // Block align
            header.putShort(bitsPerSample.toShort()) // Bits per sample

            // data chunk
            header.put("data".toByteArray())
            header.putInt(dataSize.toInt()) // Data size

            out.write(header.array())
        }
    }

    /**
     * Update WAV header with actual data size after recording
     */
    private fun updateWavHeader(file: File, dataSize: Long) {
        RandomAccessFile(file, "rw").use { raf ->
            // Update RIFF chunk size (offset 4)
            raf.seek(4)
            raf.write(intToByteArray((36 + dataSize).toInt()))

            // Update data chunk size (offset 40)
            raf.seek(40)
            raf.write(intToByteArray(dataSize.toInt()))
        }
    }

    /**
     * Convert int to byte array (little endian)
     */
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun cleanup() {
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            LogManager.service("Error releasing AudioRecord: ${e.message}", "WARN")
        }

        recordingJob?.cancel()
        recordingScope?.cancel()

        audioRecord = null
        recordingScope = null
        recordingJob = null
        isRecording = false
        isPaused = false
        outputFile = null
        startTime = 0
        pauseTime = 0
        totalPauseDuration = 0
        bytesWritten = 0
    }
}
