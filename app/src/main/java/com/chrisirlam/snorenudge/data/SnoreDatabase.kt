package com.chrisirlam.snorenudge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SnoreEvent::class],
    version = 1,
    exportSchema = false
)
abstract class SnoreDatabase : RoomDatabase() {

    abstract fun snoreEventDao(): SnoreEventDao

    companion object {
        @Volatile
        private var INSTANCE: SnoreDatabase? = null

        fun getInstance(context: Context): SnoreDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SnoreDatabase::class.java,
                    "snore_nudge.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
