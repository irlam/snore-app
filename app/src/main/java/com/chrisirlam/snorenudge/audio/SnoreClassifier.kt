package com.chrisirlam.snorenudge.audio

/**
 * Rule-based snore classifier.
 *
 * Converts a [SnoreFeatures] vector into a per-frame confidence score [0, 1].
 *
 * This is intentionally modular so the entire class can be replaced with a
 * TFLite model later:
 *
 *   class TfLiteSnoreClassifier(modelPath: String) : SnoreClassifier { ... }
 *
 * Rules (all weighted):
 * 1. RMS energy must be above a minimum threshold (below = silence/noise floor)
 * 2. Snore band ratio must exceed a target (energy concentrated in 80–500 Hz)
 * 3. Primary sub-band (80–300 Hz) is further emphasised
 * 4. Spectral centroid should be low (snore is a low-frequency sound)
 * 5. Spectral flatness penalty: pure noise would have high flatness — snore is tonal
 *
 * NOTE: Samsung Health can detect and log snoring overnight, but it is NOT
 * suitable as a real-time trigger source for instant anti-snore response because
 * it does not expose a real-time API and has significant latency. This app
 * therefore uses its own on-device pipeline.
 */
interface SnoreClassifier {
    /** Returns a confidence score in [0, 1] — higher = more likely snoring. */
    fun classify(features: SnoreFeatures, sensitivity: Float): Float
}

class RuleBasedSnoreClassifier : SnoreClassifier {

    companion object {
        private const val RMS_FLOOR = 0.003f
        private const val TARGET_BAND_RATIO = 0.30f
        private const val TARGET_PRIMARY_RATIO = 0.18f
        private const val TARGET_LOW_FREQUENCY_RATIO = 0.12f
        private const val IDEAL_CENTROID_HZ = 220f
        private const val MAX_CENTROID_HZ = 550f
        private const val FLATNESS_GOOD = 0.38f
        private const val FLATNESS_BAD = 0.82f
        private const val ZCR_IDEAL = 0.032f
        private const val ZCR_TOLERANCE = 0.05f
    }

    override fun classify(features: SnoreFeatures, sensitivity: Float): Float {
        val clampedSensitivity = sensitivity.coerceIn(0f, 1f)
        val rmsThreshold = RMS_FLOOR * (1.8f - (0.7f * clampedSensitivity))

        if (features.rms < rmsThreshold) return 0f
        if (features.spectralFlatness > 0.95f && features.spectralCentroid > 900f) return 0f

        var score = 0f
        var weight = 0f

        val bandScore = (features.snoreBandRatio / TARGET_BAND_RATIO).coerceIn(0f, 1f)
        score += bandScore * 0.24f
        weight += 0.24f

        val primaryScore = (features.primaryBandRatio / TARGET_PRIMARY_RATIO).coerceIn(0f, 1f)
        score += primaryScore * 0.20f
        weight += 0.20f

        val lowFrequencyScore = (features.lowFrequencyRatio / TARGET_LOW_FREQUENCY_RATIO).coerceIn(0f, 1f)
        score += lowFrequencyScore * 0.18f
        weight += 0.18f

        val centroidScore = when {
            features.spectralCentroid <= IDEAL_CENTROID_HZ -> 1f
            features.spectralCentroid >= MAX_CENTROID_HZ -> 0f
            else -> 1f - ((features.spectralCentroid - IDEAL_CENTROID_HZ) / (MAX_CENTROID_HZ - IDEAL_CENTROID_HZ))
        }
        score += centroidScore * 0.20f
        weight += 0.20f

        val flatnessScore = when {
            features.spectralFlatness <= FLATNESS_GOOD -> 1f
            features.spectralFlatness >= FLATNESS_BAD -> 0f
            else -> 1f - ((features.spectralFlatness - FLATNESS_GOOD) / (FLATNESS_BAD - FLATNESS_GOOD))
        }
        score += flatnessScore * 0.10f
        weight += 0.10f

        val zcrScore = (1f - (kotlin.math.abs(features.zeroCrossingRate - ZCR_IDEAL) / ZCR_TOLERANCE))
            .coerceIn(0f, 1f)
        score += zcrScore * 0.08f
        weight += 0.08f

        val rmsScore = ((features.rms - rmsThreshold) / (0.03f + rmsThreshold)).coerceIn(0f, 1f)
        score += rmsScore * 0.20f
        weight += 0.20f

        val rawScore = if (weight > 0) score / weight else 0f
        val decisionFloor = (0.34f - (0.12f * clampedSensitivity)).coerceIn(0.18f, 0.34f)
        return ((rawScore - decisionFloor) / (1f - decisionFloor)).coerceIn(0f, 1f)
    }
}
