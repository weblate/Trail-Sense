package com.kylecorry.trail_sense.weather.domain

import com.kylecorry.trail_sense.weather.domain.forcasting.DailyForecaster
import com.kylecorry.trail_sense.weather.domain.sealevel.AltimeterSeaLevelPressureConverter
import com.kylecorry.trail_sense.weather.domain.sealevel.BarometerGPSAltitudeCalculator
import com.kylecorry.trail_sense.weather.domain.sealevel.GPSAltitudeCalculator
import com.kylecorry.trailsensecore.domain.weather.*
import com.kylecorry.trailsensecore.domain.weather.WeatherService
import java.time.Duration
import java.time.Instant

class WeatherService(
    private val stormThreshold: Float,
    dailyForecastChangeThreshold: Float,
    private val hourlyForecastChangeThreshold: Float,
    adjustSeaLevelWithBarometer: Boolean = true,
    adjustSeaLevelWithTemp: Boolean = false
) {
    private val longTermForecaster = DailyForecaster(dailyForecastChangeThreshold)
    private val newWeatherService: IWeatherService = WeatherService()
    private val seaLevelConverter = AltimeterSeaLevelPressureConverter(
        if (adjustSeaLevelWithBarometer) BarometerGPSAltitudeCalculator() else GPSAltitudeCalculator(),
        adjustSeaLevelWithTemp
    )

    fun getHourlyWeather(readings: List<PressureReading>, lastReading: PressureReading? = null): Weather {
        val tendency = getTendency(readings, lastReading)
        val current = readings.lastOrNull() ?: return Weather.NoChange
        return newWeatherService.forecast(tendency, current, stormThreshold)
    }

    fun getDailyWeather(readings: List<PressureReading>): Weather {
        return longTermForecaster.forecast(readings)
    }

    fun getTendency(readings: List<PressureReading>, lastReading: PressureReading? = null): PressureTendency {
        val last = readings.minByOrNull { Duration.between(it.time, Instant.now().minusSeconds(3 * 60 * 60)).abs() } ?: lastReading
        val current = readings.lastOrNull()

        if (last == null || current == null){
            return PressureTendency(PressureCharacteristic.Steady, 0f)
        }

        return newWeatherService.getTendency(last, current, hourlyForecastChangeThreshold)
    }

    fun convertToSeaLevel(readings: List<PressureAltitudeReading>): List<PressureReading> {
        return seaLevelConverter.convert(readings)
    }

    fun getHeatIndex(tempCelsius: Float, relativeHumidity: Float): Float {
        return newWeatherService.getHeatIndex(tempCelsius, relativeHumidity)
    }

    fun getHeatAlert(heatIndex: Float): HeatAlert {
        return newWeatherService.getHeatAlert(heatIndex)
    }

    fun getDewPoint(tempCelsius: Float, relativeHumidity: Float): Float {
       return newWeatherService.getDewPoint(tempCelsius, relativeHumidity)
    }
}