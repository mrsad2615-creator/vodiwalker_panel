package com.mesad.time.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * MESAD Room Database — offline-first persistence.
 */
@Database(
    entities = [
        AlarmEntity::class,
        WorldCityEntity::class,
        TimerEntity::class,
        LapEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MesadDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun worldCityDao(): WorldCityDao
    abstract fun timerDao(): TimerDao
    abstract fun lapDao(): LapDao
}
