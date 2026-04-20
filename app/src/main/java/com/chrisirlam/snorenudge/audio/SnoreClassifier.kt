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
        // Energy floor — frames below this RMS are treated as silence
        private const val RMS_FLOOR = 0.003f
        // Ideal snore band energy ratio
        private const val TARGET_BAND_RATIO = 0.35f
        // Ideal primary sub-band ratio
        private const val TARGET_PRIMARY_RATIO = 0.20f
        // Centroid above this (Hz) penalises the score (snore is < 400 Hz ideally)
        private const val CENTROID_MAX_HZ = 400f
        // Spectral flatness above this reduces confidence (pure noise)
        private const val FLATNESS_THRESHOLD = 0.6f
        // ZCR range for snoring at 16 kHz: ~0.01–0.08
        private const val ZCR_LOW = 0.005f
        private const val ZCR_HIGH = 0.10f
    }

    override fun classify(features: SnoreFeatures, sensitivity: Float): Float {
        // sensitivity in [0, 1] → adjust the RMS threshold inversely
        val rmsThreshold = RMS_FLOOR * (2f - sensitivity)   // high sens = lower threshold

        // Hard gate: very quiet frames cannot be snoring
        if (features.rms < rmsThreshold) return 0f

        var score = 0f
        var weight = 0f

        // Component 1: snore band energy ratio (weight 0.30)
        val bandScore = (features.snoreBandRatio / TARGET_BAND_RATIO).coerceIn(0f, 1f)
        score += bandScore * 0.30f
        weight += 0.30f

        // Component 2: primary sub-band ratio (weight 0.20)
        val primaryScore = (features.primaryBandRatio / TARGET_PRIMARY_RATIO).coerceIn(0f, 1f)
        score += primaryScore * 0.20f
        weight += 0.20f

        // Component 3: spectral centroid penalty (weight 0.20)
        val centroidScore = if (features.spectralCentroid <= CENTROID_MAX_HZ) 1f
        else (1f - ((features.spectralCentroid - CENTROID_MAX_HZ) / CENTROID_MAX_HZ)).coerceIn(0f, 1f)
        score += centroidScore * 0.20f
        weight += 0.20f

        // Component 4: spectral flatness (less flat = more tonal = more snore-like) (weight 0.15)
        val flatnessPenalty = if (features.spectralFlatness > FLATNESS_THRESHOLD)
            1f - ((features.spectralFlatness - FLATNESS_THRESHOLD) / (1f - FLATNESS_THRESHOLD))
        else 1f
        score += flatnessPenalty.coerceIn(0f, 1f) * 0.15f
        weight += 0.15f

        // Component 5: zero crossing rate — snore is low-freq so ZCR is in moderate-low range (weight 0.15)
        val zcrScore = when {
            features.zeroCrossingRate < ZCR_LOW ->
                // Too few crossings — near-silence or sub-20 Hz rumble
                (features.zeroCrossingRate / ZCR_LOW).coerceIn(0f, 1f)
            features.zeroCrossingRate <= ZCR_HIGH -> 1f
            else ->
                // Too many crossings — broadband noise or speech
                (1f - ((features.zeroCrossingRate - ZCR_HIGH) / ZCR_HIGH)).coerceIn(0f, 1f)
        }
        score += zcrScore * 0.15f
        weight += 0.15f

        val rawScore = if (weight > 0) score / weight else 0f

        // Apply sensitivity as a final scale — higher sensitivity amplifies marginal detections
        return (rawScore * (0.5f + sensitivity * 0.5f)).coerceIn(0f, 1f)
    }
}
