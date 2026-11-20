package com.mrsad.ambient.display

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.mrsad.ambient.data.local.AmbientDatabase
import com.mrsad.ambient.data.local.PreferencesDataStore
import com.mrsad.ambient.data.local.SessionUsageEntity
import com.mrsad.ambient.designsystem.MrsadAmbientTheme
import com.mrsad.ambient.designsystem.MrsadColors
import com.mrsad.ambient.domain.model.AmbientProfile
import com.mrsad.ambient.presentation.ambient.AmbientDisplayScreen
import com.mrsad.ambient.system.BatteryMonitor
import com.mrsad.ambient.system.SensorManagerHelper
import kotlinx.coroutines.delay

class AmbientDisplayActivity : ComponentActivity() {

    private lateinit var batteryMonitor: BatteryMonitor
    private lateinit var sensorHelper: SensorManagerHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        batteryMonitor = BatteryMonitor(this)
        sensorHelper = SensorManagerHelper(this)

        val prefsStore = PreferencesDataStore(this)
        val db = AmbientDatabase.getInstance(this)

        setContent {
            val prefs by prefsStore.preferencesFlow.collectAsState(initial = com.mrsad.ambient.data.local.UserPreferences())
            val battery by batteryMonitor.state.collectAsState()
            val sensorStatus by sensorHelper.status.collectAsState()
            val profile = AmbientProfile.defaults().find { it.type == prefs.activeProfile }
                ?: AmbientProfile.defaults().first()

            // Apply controlled IPS LCD brightness
            LaunchedEffect(profile.brightnessFraction) {
                val lp = window.attributes
                lp.screenBrightness = profile.brightnessFraction.coerceIn(0.05f, 0.85f)
                window.attributes = lp
            }

            // Automatic screen-off timeout (IPS LCD power-saving requirement)
            LaunchedEffect(profile.sessionTimeoutSeconds) {
                delay(profile.sessionTimeoutSeconds * 1000L)
                db.ambientDao().insertSession(
                    SessionUsageEntity(
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = profile.sessionTimeoutSeconds,
                        profileName = profile.type.name,
                        triggerSource = intent.getStringExtra("trigger") ?: "MANUAL"
                    )
                )
                finish()
            }

            // Pocket detection: close immediately if proximity sensor is covered
            LaunchedEffect(sensorStatus.isPocketCovered, prefs.pocketDetection) {
                if (prefs.pocketDetection && sensorStatus.isPocketCovered) {
                    finish()
                }
            }

            MrsadAmbientTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MrsadColors.PureBlack)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { finish() }
                            )
                        }
                ) {
                    AmbientDisplayScreen(
                        clockConfig = prefs.clockConfig,
                        profile = profile,
                        battery = battery,
                        usePersian = prefs.language == "fa"
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        batteryMonitor.start()
        sensorHelper.startListening(enableLight = true, enableProximity = true)
    }

    override fun onStop() {
        super.onStop()
        batteryMonitor.stop()
        sensorHelper.stopListening()
    }
}
