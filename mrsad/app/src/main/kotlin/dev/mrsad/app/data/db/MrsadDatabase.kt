package dev.mrsad.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TypingSessionEntity::class, ActivityEventEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MrsadDatabase : RoomDatabase() {
    abstract fun typingSessionDao(): TypingSessionDao
    abstract fun activityDao(): ActivityDao

    companion object {
        const val NAME = "mrsad.db"

        fun build(context: Context): MrsadDatabase =
            Room.databaseBuilder(context, MrsadDatabase::class.java, NAME)
                // Never destroy user progress on ship builds; migrations are
                // added together with every schema version bump.
                .build()
    }
}
