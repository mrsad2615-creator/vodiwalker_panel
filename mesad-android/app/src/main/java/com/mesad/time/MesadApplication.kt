package com.mesad.time

import android.app.Application
import androidx.room.Room
import com.mesad.time.data.local.MesadDatabase

class MesadApplication : Application() {
    val database: MesadDatabase by lazy {
        Room.databaseBuilder(applicationContext, MesadDatabase::class.java, "mesad.db")
            .build()
    }
}
