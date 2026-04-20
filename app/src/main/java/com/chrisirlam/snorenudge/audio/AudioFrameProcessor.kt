package com.chrisirlam.snorenudge.audio

import kotlin.math.sqrt

/**
 * Converts raw PCM short arrays into [AudioFrame] objects containing
 * normalised floating-point samples and the computed RMS energy level.
 */
class AudioFrameProcessor {

    /**
     * Processes a raw PCM-16 buffer and returns an [AudioFrame].
     *
     * @param rawSamples Short array from AudioRecord
     * @return [AudioFrame] with normalised floats and RMS
     */
    fun process(rawSamples: ShortArray): AudioFrame {
        val floatSamples = FloatArray(rawSamples.size) { i ->
            rawSamples[i] / 32768f
        }
        val rms = computeRms(floatSamples)
        return AudioFrame(samples = floatSamples, rms = rms)
    }

    private fun computeRms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sumSq = 0.0
        for (s in samples) sumSq += (s * s).toDouble()
        return sqrt(sumSq / samples.size).toFloat()
    }
}

/**
 * A single processed audio frame ready for feature extraction.
 *
 * @property samples Normalised PCM samples in [-1, 1]
 * @property rms     Root-mean-square energy of this frame
 */
data class AudioFrame(
    val samples: FloatArray,
    val rms: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return rms == other.rms && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + rms.hashCode()
        return result
    }
}
