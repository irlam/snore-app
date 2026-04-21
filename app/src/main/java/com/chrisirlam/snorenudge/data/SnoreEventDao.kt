package com.chrisirlam.snorenudge.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SnoreEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: SnoreEvent): Long

    /** Returns all events ordered newest-first as a live Flow. */
    @Query("SELECT * FROM snore_events ORDER BY timestampMs DESC")
    fun getAllEventsFlow(): Flow<List<SnoreEvent>>

    /** Returns events from the last N milliseconds, newest-first. */
    @Query("SELECT * FROM snore_events WHERE timestampMs >= :sinceMs ORDER BY timestampMs DESC")
    suspend fun getEventsSince(sinceMs: Long): List<SnoreEvent>

    /** Deletes all events older than the given timestamp. */
    @Query("DELETE FROM snore_events WHERE timestampMs < :beforeMs")
    suspend fun deleteEventsBefore(beforeMs: Long): Int

    @Query("DELETE FROM snore_events")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM snore_events")
    suspend fun count(): Int
}
