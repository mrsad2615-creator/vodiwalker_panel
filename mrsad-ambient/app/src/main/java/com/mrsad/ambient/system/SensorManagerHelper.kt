package com.mrsad.ambient.system

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.mrsad.ambient.domain.model.SensorStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SensorManagerHelper(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val stepCounter: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private val _status = MutableStateFlow(
        SensorStatus(
            hasProximity = proximitySensor != null,
            hasAccelerometer = accelerometer != null,
            hasLightSensor = lightSensor != null,
            hasStepCounter = stepCounter != null
        )
    )
    val status: StateFlow<SensorStatus> = _status.asStateFlow()

    private val _stepsSinceBoot = MutableStateFlow(0)
    val stepsSinceBoot: StateFlow<Int> = _stepsSinceBoot.asStateFlow()

    fun startListening(enableLight: Boolean = true, enableProximity: Boolean = true, enableSteps: Boolean = false) {
        if (enableProximity && proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        if (enableLight && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI)
        }
        if (enableSteps && stepCounter != null) {
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val maxRange = proximitySensor?.maximumRange ?: 5f
                val covered = event.values[0] < maxRange
                _status.value = _status.value.copy(isPocketCovered = covered)
            }
            Sensor.TYPE_LIGHT -> {
                _status.value = _status.value.copy(ambientLux = event.values[0])
            }
            Sensor.TYPE_STEP_COUNTER -> {
                _stepsSinceBoot.value = event.values[0].toInt()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
