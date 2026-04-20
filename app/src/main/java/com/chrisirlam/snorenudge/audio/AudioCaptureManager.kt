package com.chrisirlam.snorenudge.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "AudioCaptureManager"

/**
 * Manages [AudioRecord] lifecycle and pumps PCM frames to a callback.
 *
 * Audio is never written to disk or sent over the network — it is processed
 * in-memory only and discarded after each frame analysis.
 */
class AudioCaptureManager(
    private val onFrame: (ShortArray) -> Unit
) {
    companion object {
        const val SAMPLE_RATE = 16000        // Hz — sufficient for snore frequency range
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // 50 ms frame at 16 kHz = 800 samples
        const val FRAME_SIZE_SAMPLES = 800
    }

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (audioRecord != null) return

        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufSize <= 0) {
            Log.e(TAG, "AudioRecord invalid min buffer size: $minBufSize")
            return
        }
        val bufferSize = maxOf(minBufSize, FRAME_SIZE_SAMPLES * 2 * 4)

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        } catch (se: SecurityException) {
            Log.e(TAG, "Microphone permission missing: ${se.message}")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioRecord: ${e.message}")
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            record.release()
            return
        }

        audioRecord = record
        try {
            record.startRecording()
        } catch (se: SecurityException) {
            Log.e(TAG, "Microphone permission denied at start: ${se.message}")
            record.release()
            return
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord start failed: ${e.message}")
            record.release()
            return
        }
        Log.d(TAG, "AudioRecord started, bufferSize=$bufferSize")

        captureJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(FRAME_SIZE_SAMPLES)
            while (isActive) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    onFrame(buffer.copyOf(read))
                } else {
                    Log.w(TAG, "AudioRecord.read returned $read")
                }
            }
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        audioRecord?.let { ar ->
            if (ar.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                ar.stop()
            }
            ar.release()
        }
        audioRecord = null
        Log.d(TAG, "AudioRecord stopped and released")
    }

    val isCapturing: Boolean
        get() = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
}
