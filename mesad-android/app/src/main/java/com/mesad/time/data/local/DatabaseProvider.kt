package com.mesad.time.data.local

import android.content.Context
import androidx.room.Room

/** Single process-wide Room database; migrations are additive and preserve local user data. */
object DatabaseProvider {
    @Volatile private var instance: MesadDatabase? = null
    fun get(context: Context): MesadDatabase = instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(context.applicationContext, MesadDatabase::class.java, "mesad.db")
            .build().also { instance = it }
    }
}
