package com.kylecorry.trail_sense.shared.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.sensors.declination.OverrideDeclination
import com.kylecorry.trail_sense.shared.sensors.hygrometer.NullHygrometer
import com.kylecorry.trail_sense.shared.sensors.overrides.CachedAltimeter
import com.kylecorry.trail_sense.shared.sensors.overrides.CachedGPS
import com.kylecorry.trail_sense.shared.sensors.overrides.OverrideAltimeter
import com.kylecorry.trail_sense.shared.sensors.overrides.OverrideGPS
import com.kylecorry.trailsensecore.infrastructure.sensors.SensorChecker
import com.kylecorry.trailsensecore.infrastructure.sensors.altimeter.FusedAltimeter
import com.kylecorry.trailsensecore.infrastructure.sensors.altimeter.IAltimeter
import com.kylecorry.trailsensecore.infrastructure.sensors.barometer.Barometer
import com.kylecorry.trailsensecore.infrastructure.sensors.barometer.IBarometer
import com.kylecorry.trailsensecore.infrastructure.sensors.compass.ICompass
import com.kylecorry.trailsensecore.infrastructure.sensors.compass.LegacyCompass
import com.kylecorry.trailsensecore.infrastructure.sensors.compass.VectorCompass
import com.kylecorry.trailsensecore.infrastructure.sensors.declination.DeclinationProvider
import com.kylecorry.trailsensecore.infrastructure.sensors.declination.IDeclinationProvider
import com.kylecorry.trailsensecore.infrastructure.sensors.gps.IGPS
import com.kylecorry.trailsensecore.infrastructure.sensors.hygrometer.Hygrometer
import com.kylecorry.trailsensecore.infrastructure.sensors.hygrometer.IHygrometer
import com.kylecorry.trailsensecore.infrastructure.sensors.inclinometer.IInclinometer
import com.kylecorry.trailsensecore.infrastructure.sensors.inclinometer.Inclinometer
import com.kylecorry.trailsensecore.infrastructure.sensors.temperature.BatteryTemperatureSensor
import com.kylecorry.trailsensecore.infrastructure.sensors.temperature.IThermometer
import com.kylecorry.trailsensecore.infrastructure.sensors.temperature.Thermometer

class SensorService(private val context: Context) {

    private val userPrefs = UserPreferences(context.applicationContext)
    private val sensorChecker = SensorChecker(context)
    private val sensorManager = context.getSystemService<SensorManager>()

    fun getGPS(): IGPS {
        if (!userPrefs.useAutoLocation) {
            return OverrideGPS(context)
        }

        if (userPrefs.useLocationFeatures) {
            return GPS(context)
        }

        return CachedGPS(context)
    }

    fun getAltimeter(): IAltimeter {

        val mode = userPrefs.altimeterMode

        if (mode == UserPreferences.AltimeterMode.Override){
            return OverrideAltimeter(context)
        } else if (mode == UserPreferences.AltimeterMode.Barometer && sensorChecker.hasBarometer()){
            return BarometricAltimeter(getBarometer()) { userPrefs.seaLevelPressureOverride }
        } else {
            if (!userPrefs.useLocationFeatures) {
                return CachedAltimeter(context)
            }

            val gps = getGPS()

            return if (mode == UserPreferences.AltimeterMode.GPSBarometer && sensorChecker.hasBarometer()) {
                FusedAltimeter(gps, Barometer(context))
            } else {
                gps
            }
        }
    }

    fun getDeclinationProvider(): IDeclinationProvider {
        if (!userPrefs.useAutoDeclination) {
            return OverrideDeclination(context)
        }

        val gps = getGPS()
        val altimeter = getAltimeter()

        return DeclinationProvider(gps, altimeter)
    }

    fun getCompass(): ICompass {
        val smoothing = userPrefs.navigation.compassSmoothing
        val useTrueNorth = userPrefs.navigation.useTrueNorth
        return if (userPrefs.navigation.useLegacyCompass) LegacyCompass(
            context,
            smoothing,
            useTrueNorth
        ) else VectorCompass(
            context, smoothing, useTrueNorth
        )
    }

    fun getDeviceOrientation(): DeviceOrientation {
        return DeviceOrientation(context)
    }

    fun getBarometer(): IBarometer {
        return if (userPrefs.weather.hasBarometer) Barometer(context) else NullBarometer()
    }

    fun getInclinometer(): IInclinometer {
        return Inclinometer(context)
    }

    @Suppress("DEPRECATION")
    fun getThermometer(): IThermometer {
        if (sensorChecker.hasSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)) {
            return Thermometer(context, Sensor.TYPE_AMBIENT_TEMPERATURE)
        }

        if (sensorChecker.hasSensor(Sensor.TYPE_TEMPERATURE)) {
            return Thermometer(context, Sensor.TYPE_TEMPERATURE)
        }

        val builtInSensors = sensorManager?.getSensorList(Sensor.TYPE_ALL) ?: listOf()

        val first = builtInSensors.filter {
            it.name.contains("temperature", true) ||
                    it.name.contains("thermometer", true)
        }.minBy { it.resolution }

        if (first != null) {
            return Thermometer(context, first.type)
        }

        return BatteryTemperatureSensor(context)
    }

    fun getHygrometer(): IHygrometer {
        if (sensorChecker.hasHygrometer()) {
            return Hygrometer(context)
        }

        return NullHygrometer()
    }

}