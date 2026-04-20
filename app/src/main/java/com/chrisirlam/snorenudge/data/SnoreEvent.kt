package com.chrisirlam.snorenudge.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single snore detection trigger event stored in the local database.
 * Raw audio is never stored — only lightweight metadata.
 */
@Entity(tableName = "snore_events")
data class SnoreEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Unix epoch milliseconds when the trigger fired. */
    val timestampMs: Long,
    /** Confidence value (0.0–1.0) at the moment the trigger fired. */
    val confidence: Float,
    /** RMS level at trigger time, for debugging. */
    val rmsLevel: Float,
    /** Whether the watch vibration command was sent successfully. */
    val watchCommandSent: Boolean,
    /** Whether phone vibration was triggered. */
    val phoneVibrated: Boolean,
    /** Label: "snore", "test", or "manual". */
    val triggerSource: String = "snore"
)
