package com.kylecorry.trail_sense.shared.sensors.altimeter

import android.content.Context
import android.util.Log
import com.kylecorry.andromeda.core.coroutines.onDefault
import com.kylecorry.andromeda.core.sensors.AbstractSensor
import com.kylecorry.andromeda.core.sensors.IAltimeter
import com.kylecorry.andromeda.core.time.CoroutineTimer
import com.kylecorry.andromeda.location.IGPS
import com.kylecorry.andromeda.sense.barometer.IBarometer
import com.kylecorry.sol.math.SolMath
import com.kylecorry.sol.math.SolMath.roundPlaces
import com.kylecorry.sol.math.filters.IFilter
import com.kylecorry.sol.math.filters.LowPassFilter
import com.kylecorry.sol.science.geology.Geology
import com.kylecorry.sol.science.meteorology.Meteorology
import com.kylecorry.sol.units.Distance
import com.kylecorry.sol.units.Pressure
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.preferences.PreferencesSubsystem
import com.kylecorry.trail_sense.shared.sensors.hasFix
import java.time.Duration
import java.time.Instant

/**
 * A sensor that uses the GPS and barometer to calculate altitude.
 * @param context The context
 * @param gps The GPS sensor
 * @param barometer The barometer sensor
 * @param useContinuousCalibration True if the sensor should continuously calibrate using the GPS, otherwise it will only calibrate once an hour
 */
class FusedAltimeter(
    context: Context,
    private val gps: IGPS,
    private val barometer: IBarometer,
    private val useContinuousCalibration: Boolean
) : AbstractSensor(), IAltimeter {

    private val shouldLog = true

    private val prefs = UserPreferences(context)
    private val cache = PreferencesSubsystem.getInstance(context).preferences
    private val gpsAltimeter = GaussianAltimeterWrapper(gps, prefs.altimeterSamples)

    private val updateTimer = CoroutineTimer {
        val success = onDefault {
            update()
        }
        if (success) {
            notifyListeners()
        }
    }

    private var filteredAltitude: Float? = null

    override val altitude: Float
        get() = filteredAltitude ?: getGPSAltitude()

    override val hasValidReading: Boolean
        get() = filteredAltitude != null

    private var pressureFilter: IFilter? = null
    private var filteredPressure = barometer.pressure

    override fun startImpl() {
        pressureFilter = null
        filteredAltitude = null
        filteredPressure = 0f
        gpsAltimeter.start(this::onGPSUpdate)
        barometer.start(this::onBarometerUpdate)
        updateTimer.interval(UPDATE_FREQUENCY)
    }

    override fun stopImpl() {
        gpsAltimeter.stop(this::onGPSUpdate)
        barometer.stop(this::onBarometerUpdate)
        updateTimer.stop()
    }

    private fun onBarometerUpdate(): Boolean {
        if (barometer.pressure != 0f) {
            updatePressure(barometer.pressure)
        }
        return true
    }

    private fun onGPSUpdate(): Boolean {
        // Do nothing
        return true
    }

    private fun updatePressure(pressure: Float) {
        val filter = pressureFilter ?: LowPassFilter(1 - BAROMETER_SMOOTHING, barometer.pressure)
        pressureFilter = filter
        filteredPressure = filter.filter(pressure)
    }


    private fun update(): Boolean {
        if (filteredPressure == 0f) {
            // No barometer reading yet
            return false
        }

        val seaLevel = getLastSeaLevelPressure()

        // Sea level pressure is unknown, it needs to be calibrated
        if (seaLevel == null) {
            recalibrate()
            return false
        }

        // Calculate the barometric altitude
        val barometricAltitude =
            Geology.getAltitude(Pressure.hpa(filteredPressure), seaLevel).distance

        // At this point, the barometric altitude is available but the GPS altitude may not be
        // If the GPS has a reading, use the GPS altimeter (even if the gaussian filter hasn't converged)
        // Otherwise, use the barometric altitude
        val gpsAltitude = if (hasGPSFix()) {
            getGPSAltitude()
        } else {
            barometricAltitude
        }

        // Dynamically calculate the influence of the GPS using the altitude accuracy
        // A more accurate GPS reading will have a higher weight
        // If always on calibration is disabled, the GPS will not be used
        val gpsError = if (hasGPSFix()) {
            gpsAltimeter.altitudeAccuracy ?: MAX_GPS_ERROR
        } else {
            MAX_GPS_ERROR
        }
        val gpsWeight = if (useContinuousCalibration) {
            1 - SolMath.map(gpsError, 0f, MAX_GPS_ERROR, MIN_ALPHA, MAX_ALPHA)
                .coerceIn(MIN_ALPHA, MAX_ALPHA)
        } else {
            0f
        }

        // Create the filter if it doesn't exist, the value will be overwritten so it doesn't matter
        val altitude = gpsWeight * gpsAltitude + (1 - gpsWeight) * barometricAltitude
        filteredAltitude = altitude

        // Update the current estimate of the sea level pressure
        setLastSeaLevelPressure(
            Meteorology.getSeaLevelPressure(
                Pressure.hpa(filteredPressure),
                Distance.meters(altitude)
            ),
            false
        )

        if (shouldLog) {
            Log.d(
                "FusedAltimeter",
                "ALT: ${altitude.roundPlaces(2)}, " +
                        "GPS: ${getGPSAltitude().roundPlaces(2)}, " +
                        "BAR: ${barometricAltitude.roundPlaces(2)}, " +
                        "SEA: ${seaLevel.pressure.roundPlaces(2)}, " +
                        "ALPHA: ${gpsWeight.roundPlaces(3)}"
            )
        }
        return true
    }

    private fun getGPSAltitude(): Float {
        val altitude = gpsAltimeter.altitude
        return if (altitude == 0f && !hasGaussianGPSFix()) {
            gps.altitude
        } else {
            altitude
        }
    }

    private fun recalibrate() {
        if (filteredPressure > 0f && hasGaussianGPSFix()) {
            setLastSeaLevelPressure(
                Meteorology.getSeaLevelPressure(
                    Pressure.hpa(filteredPressure),
                    Distance.meters(getGPSAltitude())
                ),
                true
            )
        }
    }

    private fun hasGPSFix(): Boolean {
        return gps.hasFix()
    }

    private fun hasGaussianGPSFix(): Boolean {
        return hasGPSFix() && gpsAltimeter.hasValidReading
    }

    private fun getLastSeaLevelPressure(): Pressure? {
        val time = cache.getInstant(LAST_SEA_LEVEL_PRESSURE_TIME_KEY) ?: return null

        val timeSinceReading = Duration.between(time, Instant.now())
        if (timeSinceReading > SEA_LEVEL_EXPIRATION || timeSinceReading.isNegative) {
            // Sea level pressure has expired
            return null
        }

        return cache.getFloat(LAST_SEA_LEVEL_PRESSURE_KEY)?.let { Pressure.hpa(it) }
    }


    private fun setLastSeaLevelPressure(pressure: Pressure, isBaseline: Boolean) {
        cache.putFloat(LAST_SEA_LEVEL_PRESSURE_KEY, pressure.pressure)

        // Only update the time if the gaussian GPS was used - this prevents only using the barometer over the long term
        if (isBaseline) {
            cache.putInstant(LAST_SEA_LEVEL_PRESSURE_TIME_KEY, Instant.now())
        }
    }

    companion object {
        private const val LAST_SEA_LEVEL_PRESSURE_KEY =
            "cache_fused_altimeter_last_sea_level_pressure"
        private const val LAST_SEA_LEVEL_PRESSURE_TIME_KEY =
            "cache_fused_altimeter_last_sea_level_pressure_time"

        // The amount of time before the sea level pressure expires if not updated using the GPS
        private val SEA_LEVEL_EXPIRATION = Duration.ofHours(1)

        private const val MIN_ALPHA = 0.96f
        private const val MAX_ALPHA = 0.999f
        private const val MAX_GPS_ERROR = 5f
        private const val BAROMETER_SMOOTHING = 0.9f
        private val UPDATE_FREQUENCY = Duration.ofMillis(200)

        fun clearCachedCalibration(context: Context) {
            val prefs = PreferencesSubsystem.getInstance(context).preferences
            prefs.remove(LAST_SEA_LEVEL_PRESSURE_KEY)
            prefs.remove(LAST_SEA_LEVEL_PRESSURE_TIME_KEY)
        }
    }
}
