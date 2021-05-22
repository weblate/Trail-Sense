package com.kylecorry.trail_sense.shared.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import com.kylecorry.trailsensecore.domain.math.Euler
import com.kylecorry.trailsensecore.domain.math.Quaternion
import com.kylecorry.trailsensecore.domain.math.QuaternionMath
import com.kylecorry.trailsensecore.infrastructure.sensors.BaseSensor
import com.kylecorry.trailsensecore.infrastructure.sensors.orientation.IRotationSensor

class GameRotationSensor(context: Context) :
    BaseSensor(context, Sensor.TYPE_GAME_ROTATION_VECTOR, SensorManager.SENSOR_DELAY_FASTEST),
    IRotationSensor {

    private val lock = Object()

    override val rawEuler: FloatArray
        get() {
            val raw = rawQuaternion
            val euler = FloatArray(3)
            QuaternionMath.toEuler(raw, euler)
            return euler
        }

    override val euler: Euler
        get() = Euler.from(rawEuler)

    override val hasValidReading: Boolean
        get() = _hasReading

    override val quaternion: Quaternion
        get() = Quaternion.from(rawQuaternion)

    override val rawQuaternion: FloatArray
        get() {
            return synchronized(lock) {
                _quaternion
            }
        }

    private val _quaternion = Quaternion.zero.toFloatArray()

    private var _hasReading = false

    override fun handleSensorEvent(event: SensorEvent) {
        synchronized(lock) {
            event.values.copyInto(_quaternion)
            QuaternionMath.inverse(_quaternion, _quaternion)
        }
        _hasReading = true
    }
}