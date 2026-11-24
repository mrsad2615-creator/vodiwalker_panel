package com.neurox.app

import android.app.Application
import com.neurox.data.NeuroxRepository

class NeuroxApplication : Application() {
    val repository by lazy { NeuroxRepository(this) }
}
