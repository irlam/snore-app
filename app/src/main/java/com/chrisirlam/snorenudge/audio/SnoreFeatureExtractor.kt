package com.chrisirlam.snorenudge.audio

import kotlin.math.*

/**
 * Extracts audio features from a processed [AudioFrame] that are useful
 * for distinguishing snore-like sounds from other noise.
 *
 * Extension point: swap this class out or add additional extractors when
 * moving to a TFLite model. The feature vector is intentionally kept as a
 * plain [SnoreFeatures] data class so it can be serialised as a model input.
 */
class SnoreFeatureExtractor(private val sampleRate: Int = AudioCaptureManager.SAMPLE_RATE) {

    // Snore fundamental frequency sits roughly in 80–500 Hz.
    // We emphasise the 80–300 Hz sub-band, with secondary attention to 300–500 Hz.
    companion object {
        const val LOW_FREQ_LOW_HZ = 40f
        const val LOW_FREQ_HIGH_HZ = 180f
        const val SNORE_LOW_HZ = 80f
        const val SNORE_HIGH_HZ = 500f
        const val SNORE_PRIMARY_HIGH_HZ = 300f
    }

    fun extract(frame: AudioFrame): SnoreFeatures {
        val fftSize = nextPowerOfTwo(frame.samples.size)
        val magnitude = computeMagnitudeSpectrum(frame.samples, fftSize)
        val freqResolution = sampleRate.toFloat() / fftSize

        val snoreEnergy = bandEnergy(magnitude, freqResolution, SNORE_LOW_HZ, SNORE_HIGH_HZ)
        val primarySnoreEnergy = bandEnergy(magnitude, freqResolution, SNORE_LOW_HZ, SNORE_PRIMARY_HIGH_HZ)
        val lowFrequencyEnergy = bandEnergy(magnitude, freqResolution, LOW_FREQ_LOW_HZ, LOW_FREQ_HIGH_HZ)
        val totalEnergy = bandEnergy(magnitude, freqResolution, 0f, sampleRate / 2f)

        val spectralRatio = if (totalEnergy > 1e-6f) snoreEnergy / totalEnergy else 0f
        val primaryRatio = if (totalEnergy > 1e-6f) primarySnoreEnergy / totalEnergy else 0f
        val lowFrequencyRatio = if (totalEnergy > 1e-6f) lowFrequencyEnergy / totalEnergy else 0f

        val spectralCentroid = computeSpectralCentroid(magnitude, freqResolution)
        val spectralFlatness = computeSpectralFlatness(magnitude, freqResolution, SNORE_LOW_HZ, SNORE_HIGH_HZ)
        val zeroCrossingRate = computeZeroCrossingRate(frame.samples)

        return SnoreFeatures(
            rms = frame.rms,
            snoreBandEnergy = snoreEnergy,
            snoreBandRatio = spectralRatio,
            primaryBandRatio = primaryRatio,
            lowFrequencyRatio = lowFrequencyRatio,
            spectralCentroid = spectralCentroid,
            spectralFlatness = spectralFlatness,
            zeroCrossingRate = zeroCrossingRate
        )
    }

    /**
     * Computes the magnitude spectrum using a simple radix-2 Cooley-Tukey FFT.
     * Returns magnitudes for bins 0..fftSize/2.
     */
    private fun computeMagnitudeSpectrum(samples: FloatArray, fftSize: Int): FloatArray {
        if (samples.isEmpty()) return FloatArray((fftSize / 2) + 1)

        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        val windowDenominator = (samples.size - 1).coerceAtLeast(1)
        // Apply Hann window and zero-pad
        for (i in samples.indices) {
            val window = 0.5f * (1f - cos(2.0 * PI * i / windowDenominator)).toFloat()
            real[i] = samples[i] * window
        }

        fft(real, imag, fftSize)

        val halfSize = fftSize / 2
        return FloatArray(halfSize + 1) { bin ->
            sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
        }
    }

    /** In-place FFT (Cooley-Tukey, radix-2, DIT). Modifies real and imag arrays. */
    private fun fft(real: FloatArray, imag: FloatArray, n: Int) {
        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tempReal = real[i]
                real[i] = real[j]
                real[j] = tempReal

                val tempImag = imag[i]
                imag[i] = imag[j]
                imag[j] = tempImag
            }
        }
        // Cooley-Tukey iterative FFT
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var uRe = 1f; var uIm = 0f
                for (k in 0 until len / 2) {
                    val tRe = uRe * real[i + k + len / 2] - uIm * imag[i + k + len / 2]
                    val tIm = uRe * imag[i + k + len / 2] + uIm * real[i + k + len / 2]
                    real[i + k + len / 2] = real[i + k] - tRe
                    imag[i + k + len / 2] = imag[i + k] - tIm
                    real[i + k] += tRe
                    imag[i + k] += tIm
                    val tmpRe = uRe * wRe - uIm * wIm
                    uIm = uRe * wIm + uIm * wRe
                    uRe = tmpRe
                }
                i += len
            }
            len *= 2
        }
    }

    private fun bandEnergy(magnitude: FloatArray, freqRes: Float, lowHz: Float, highHz: Float): Float {
        val lowBin = (lowHz / freqRes).toInt().coerceIn(0, magnitude.size - 1)
        val highBin = (highHz / freqRes).toInt().coerceIn(0, magnitude.size - 1)
        var energy = 0f
        for (bin in lowBin..highBin) {
            energy += magnitude[bin] * magnitude[bin]
        }
        return energy
    }

    private fun computeSpectralCentroid(magnitude: FloatArray, freqRes: Float): Float {
        var weightedSum = 0.0
        var totalMag = 0.0
        for (bin in magnitude.indices) {
            weightedSum += bin * freqRes * magnitude[bin]
            totalMag += magnitude[bin]
        }
        return if (totalMag > 1e-8) (weightedSum / totalMag).toFloat() else 0f
    }

    private fun computeSpectralFlatness(magnitude: FloatArray, freqRes: Float, lowHz: Float, highHz: Float): Float {
        val lowBin = (lowHz / freqRes).toInt().coerceIn(0, magnitude.size - 1)
        val highBin = (highHz / freqRes).toInt().coerceIn(0, magnitude.size - 1)
        if (highBin <= lowBin) return 0f

        var logSum = 0.0
        var linSum = 0.0
        var count = 0
        for (bin in lowBin..highBin) {
            val m = magnitude[bin].toDouble()
            if (m > 1e-10) {
                logSum += ln(m)
                linSum += m
                count++
            }
        }
        if (count == 0 || linSum < 1e-10) return 0f
        val geometricMean = exp(logSum / count)
        val arithmeticMean = linSum / count
        return (geometricMean / arithmeticMean).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Zero crossing rate — number of sign changes per sample.
     *
     * Snoring typically has ZCR ≈ 0.01–0.08 at 16 kHz (corresponds to 80–640 Hz).
     * High ZCR (> 0.12) suggests broadband noise or speech, not snoring.
     */
    private fun computeZeroCrossingRate(samples: FloatArray): Float {
        if (samples.size < 2) return 0f
        var crossings = 0
        for (i in 1 until samples.size) {
            if ((samples[i] >= 0f) != (samples[i - 1] >= 0f)) crossings++
        }
        return crossings.toFloat() / samples.size
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }
}

/**
 * Feature vector for a single audio frame.
 * Designed to be extensible — a TFLite model can consume these directly.
 */
data class SnoreFeatures(
    /** Root-mean-square energy of the frame */
    val rms: Float,
    /** Total energy in the 80–500 Hz snore band */
    val snoreBandEnergy: Float,
    /** Fraction of total energy in the 80–500 Hz snore band */
    val snoreBandRatio: Float,
    /** Fraction of total energy in the primary 80–300 Hz snore sub-band */
    val primaryBandRatio: Float,
    /** Fraction of total energy in the 40–180 Hz low-frequency range */
    val lowFrequencyRatio: Float,
    /** Spectral centroid (Hz) */
    val spectralCentroid: Float,
    /** Spectral flatness in the snore band (0=tonal, 1=noise-like) */
    val spectralFlatness: Float,
    /** Zero crossing rate — sign changes per sample (proxy for dominant frequency) */
    val zeroCrossingRate: Float
)
