package com.kylecorry.trail_sense

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.preference.*
import com.kylecorry.trail_sense.astronomy.infrastructure.receivers.SunsetAlarmReceiver
import com.kylecorry.trail_sense.calibration.ui.CalibrateAltimeterFragment
import com.kylecorry.trail_sense.calibration.ui.CalibrateBarometerFragment
import com.kylecorry.trail_sense.calibration.ui.CalibrateCompassFragment
import com.kylecorry.trail_sense.calibration.ui.CalibrateGPSFragment
import com.kylecorry.trail_sense.licenses.LicenseFragment
import com.kylecorry.trail_sense.navigation.domain.LocationMath
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trailsensecore.infrastructure.sensors.SensorChecker
import com.kylecorry.trail_sense.shared.switchToFragment
import com.kylecorry.trail_sense.weather.infrastructure.WeatherUpdateScheduler
import com.kylecorry.trail_sense.weather.infrastructure.WeatherNotificationService
import com.kylecorry.trail_sense.weather.infrastructure.receivers.WeatherUpdateReceiver
import com.kylecorry.trailsensecore.domain.units.DistanceUnits
import com.kylecorry.trailsensecore.domain.units.PressureUnits
import com.kylecorry.trailsensecore.domain.units.UnitService
import com.kylecorry.trailsensecore.infrastructure.system.IntentUtils
import com.kylecorry.trailsensecore.infrastructure.system.NotificationUtils
import com.kylecorry.trailsensecore.infrastructure.system.PackageUtils
import com.kylecorry.trailsensecore.infrastructure.time.Intervalometer


class SettingsFragment : PreferenceFragmentCompat() {


    private lateinit var prefMonitorWeather: SwitchPreferenceCompat
    private lateinit var prefWeatherUpdateFrequency: ListPreference
    private lateinit var prefUpdateWeatherForeground: SwitchPreferenceCompat
    private lateinit var prefForceWeatherUpdates: SwitchPreferenceCompat
    private lateinit var prefShowWeatherNotification: SwitchPreferenceCompat
    private lateinit var prefShowPressureInNotification: SwitchPreferenceCompat
    private lateinit var prefPressureHistory: ListPreference
    private lateinit var prefStormAlerts: SwitchPreferenceCompat
    private lateinit var prefMaxBeaconDistanceKm: EditTextPreference
    private lateinit var prefMaxBeaconDistanceMi: EditTextPreference
    private val unitService = UnitService()
    private val formatService by lazy { FormatService(requireContext()) }
    private val intervalometer = Intervalometer {
        updatePreferenceStates()
    }

    private lateinit var prefs: UserPreferences

    private fun bindPreferences() {
        prefMonitorWeather = switch(R.string.pref_monitor_weather)
        prefWeatherUpdateFrequency = list(R.string.pref_weather_update_frequency)
        prefUpdateWeatherForeground = switch(R.string.pref_weather_foreground_service)
        prefForceWeatherUpdates = switch(R.string.pref_force_weather_updates)
        prefShowWeatherNotification = switch(R.string.pref_show_weather_notification)
        prefShowPressureInNotification = switch(R.string.pref_show_pressure_in_notification)
        prefPressureHistory = list(R.string.pref_pressure_history)
        prefStormAlerts = switch(R.string.pref_send_storm_alert)
        prefMaxBeaconDistanceKm = editText(R.string.pref_max_beacon_distance)
        prefMaxBeaconDistanceMi = editText(R.string.pref_max_beacon_distance_miles)
    }


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        val sensorChecker = SensorChecker(requireContext())
        val userPrefs = UserPreferences(requireContext())
        prefs = userPrefs
        bindPreferences()
        updatePreferenceStates()
        if (!sensorChecker.hasBarometer()) {
            preferenceScreen.removePreferenceRecursively(getString(R.string.pref_weather_category))
            preferenceScreen.removePreferenceRecursively(getString(R.string.pref_barometer_calibration))
        }

        prefMonitorWeather.setOnPreferenceClickListener {
            if (prefs.weather.shouldMonitorWeather) {
                WeatherUpdateScheduler.start(requireContext())
            } else {
                WeatherUpdateScheduler.stop(requireContext())
            }
            updatePreferenceStates()
            true
        }
        prefUpdateWeatherForeground.setOnPreferenceClickListener {
            restartWeatherMonitor()
            updatePreferenceStates()
            true
        }
        prefShowWeatherNotification.setOnPreferenceClickListener {
            val notification = prefs.weather.shouldShowWeatherNotification
            if (notification) {
                WeatherUpdateScheduler.start(requireContext())
            } else {
                NotificationUtils.cancel(
                    requireContext(),
                    WeatherNotificationService.WEATHER_NOTIFICATION_ID
                )
            }
            updatePreferenceStates()
            true
        }
        prefWeatherUpdateFrequency.setOnPreferenceClickListener {
            restartWeatherMonitor()
            true
        }

        fragmentOnClick(preference(R.string.pref_compass_sensor)) { CalibrateCompassFragment() }
        fragmentOnClick(preference(R.string.pref_altimeter_calibration)) { CalibrateAltimeterFragment() }
        fragmentOnClick(preference(R.string.pref_gps_calibration)) { CalibrateGPSFragment() }
        fragmentOnClick(preference(R.string.pref_barometer_calibration)) { CalibrateBarometerFragment() }
        refreshOnChange(list(R.string.pref_theme))
        refreshOnChange(switch(R.string.pref_enable_experimental))

        val maxDistance = prefs.navigation.maxBeaconDistance
        prefMaxBeaconDistanceMi.summary = formatService.formatDistance(
            unitService.convert(
                maxDistance,
                DistanceUnits.Meters,
                DistanceUnits.Miles
            ), DistanceUnits.Miles
        )
        prefMaxBeaconDistanceKm.summary = formatService.formatDistance(
            unitService.convert(
                maxDistance,
                DistanceUnits.Meters,
                DistanceUnits.Kilometers
            ), DistanceUnits.Kilometers
        )

        prefMaxBeaconDistanceMi.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER.or(InputType.TYPE_NUMBER_FLAG_DECIMAL)
        }

        prefMaxBeaconDistanceKm.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER.or(InputType.TYPE_NUMBER_FLAG_DECIMAL)
        }

        prefMaxBeaconDistanceMi.setOnPreferenceChangeListener { _, newValue ->
            val miles = newValue.toString().toFloatOrNull() ?: 62f
            prefs.navigation.maxBeaconDistance =
                unitService.convert(miles, DistanceUnits.Miles, DistanceUnits.Meters)
            prefMaxBeaconDistanceMi.summary =
                formatService.formatDistance(miles, DistanceUnits.Miles)
            prefMaxBeaconDistanceKm.summary = formatService.formatDistance(
                unitService.convert(
                    miles,
                    DistanceUnits.Miles,
                    DistanceUnits.Kilometers
                ), DistanceUnits.Kilometers
            )
            true
        }

        prefMaxBeaconDistanceKm.setOnPreferenceChangeListener { _, newValue ->
            val km = newValue.toString().toFloatOrNull() ?: 100f
            preferenceManager.sharedPreferences.edit {
                putString(
                    getString(R.string.pref_max_beacon_distance_miles),
                    unitService.convert(km, DistanceUnits.Kilometers, DistanceUnits.Miles)
                        .toString()
                )
            }
            prefMaxBeaconDistanceMi.summary = formatService.formatDistance(
                unitService.convert(
                    km,
                    DistanceUnits.Kilometers,
                    DistanceUnits.Miles
                ), DistanceUnits.Miles
            )
            prefMaxBeaconDistanceKm.summary =
                formatService.formatDistance(km, DistanceUnits.Kilometers)
            true
        }

        prefShowPressureInNotification.setOnPreferenceClickListener {
            requireContext().sendBroadcast(WeatherUpdateReceiver.intent(requireContext()))
            true
        }

        preferenceScreen.findPreference<ListPreference>(getString(R.string.pref_sunset_alert_time))
            ?.setOnPreferenceClickListener { _ ->
                context?.apply {
                    sendBroadcast(SunsetAlarmReceiver.intent(this))
                }
                true
            }

        preferenceScreen.findPreference<EditTextPreference>(getString(R.string.pref_ruler_calibration))
            ?.setOnBindEditTextListener { editText ->
                editText.inputType =
                    InputType.TYPE_CLASS_NUMBER.or(InputType.TYPE_NUMBER_FLAG_DECIMAL)
            }

        preferenceScreen.findPreference<EditTextPreference>(getString(R.string.pref_num_visible_beacons))
            ?.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }

        preferenceScreen.findPreference<Preference>(getString(R.string.pref_open_source_licenses))
            ?.setOnPreferenceClickListener {
                switchToFragment(LicenseFragment(), addToBackStack = true)
                false
            }

        preferenceScreen.findPreference<Preference>(getString(R.string.pref_github))
            ?.setOnPreferenceClickListener {
                val i = IntentUtils.url(it.summary.toString())
                startActivity(i)
                true
            }

        preferenceScreen.findPreference<Preference>(getString(R.string.pref_email))
            ?.setOnPreferenceClickListener {

                val intent = IntentUtils.email(it.summary.toString(), getString(R.string.app_name))
                startActivity(Intent.createChooser(intent, it.title.toString()))
                true
            }

        val forecastSensitivity =
            preferenceScreen.findPreference<ListPreference>(getString(R.string.pref_forecast_sensitivity))
        forecastSensitivity?.setEntries(getForecastSensitivityArray(userPrefs.pressureUnits))

        val stormSensitivity =
            preferenceScreen.findPreference<ListPreference>(getString(R.string.pref_storm_alert_sensitivity))
        stormSensitivity?.setEntries(getStormSensitivityArray(userPrefs.pressureUnits))

        preferenceScreen.findPreference<ListPreference>(getString(R.string.pref_pressure_units))
            ?.setOnPreferenceChangeListener { _, newValue ->
                val mapped = when (newValue) {
                    "hpa" -> PressureUnits.Hpa
                    "mbar" -> PressureUnits.Mbar
                    "in" -> PressureUnits.Inhg
                    else -> PressureUnits.Psi
                }

                forecastSensitivity?.setEntries(getForecastSensitivityArray(mapped))
                stormSensitivity?.setEntries(getStormSensitivityArray(mapped))

                true
            }


        val version = PackageUtils.getVersionName(requireContext())
        preferenceScreen.findPreference<Preference>(getString(R.string.pref_app_version))?.summary =
            version
    }

    override fun onResume() {
        super.onResume()
        intervalometer.interval(1000)
    }

    override fun onPause() {
        intervalometer.stop()
        super.onPause()
    }

    private fun fragmentOnClick(pref: Preference, fragmentFactory: () -> Fragment) {
        pref.setOnPreferenceClickListener {
            switchToFragment(fragmentFactory.invoke(), addToBackStack = true)
            false
        }
    }

    private fun refreshOnChange(pref: Preference) {
        pref.setOnPreferenceChangeListener { _, _ ->
            activity?.recreate()
            true
        }
    }

    private fun restartWeatherMonitor() {
        WeatherUpdateScheduler.stop(requireContext())
        WeatherUpdateScheduler.start(requireContext())
    }

    private fun updatePreferenceStates() {
        val monitorWeather = prefs.weather.shouldMonitorWeather
        val foreground = prefs.weather.foregroundService
        val notification = prefs.weather.shouldShowWeatherNotification
        val distanceUnits = prefs.distanceUnits

        prefWeatherUpdateFrequency.isEnabled = monitorWeather
        prefUpdateWeatherForeground.isEnabled = monitorWeather
        prefForceWeatherUpdates.isEnabled = monitorWeather && !foreground
        prefShowWeatherNotification.isEnabled = monitorWeather && !foreground
        prefShowPressureInNotification.isEnabled = monitorWeather && (foreground || notification)
        prefPressureHistory.isEnabled = monitorWeather
        prefStormAlerts.isEnabled = monitorWeather

        if (distanceUnits == UserPreferences.DistanceUnits.Feet) {
            prefMaxBeaconDistanceKm.isVisible = false
            prefMaxBeaconDistanceMi.isVisible = true
        } else {
            prefMaxBeaconDistanceKm.isVisible = true
            prefMaxBeaconDistanceMi.isVisible = false
        }
    }

    private fun switch(@StringRes id: Int): SwitchPreferenceCompat {
        return preferenceManager.findPreference(getString(id))!!
    }

    private fun editText(@StringRes id: Int): EditTextPreference {
        return preferenceManager.findPreference(getString(id))!!
    }

    private fun list(@StringRes id: Int): ListPreference {
        return preferenceManager.findPreference(getString(id))!!
    }

    private fun preference(@StringRes id: Int): Preference {
        return preferenceManager.findPreference(getString(id))!!
    }

    @ArrayRes
    private fun getForecastSensitivityArray(units: PressureUnits): Int {
        return when (units) {
            PressureUnits.Hpa -> R.array.forecast_sensitivity_entries_hpa
            PressureUnits.Inhg -> R.array.forecast_sensitivity_entries_in
            PressureUnits.Psi -> R.array.forecast_sensitivity_entries_psi
            else -> R.array.forecast_sensitivity_entries_mbar
        }
    }

    @ArrayRes
    private fun getStormSensitivityArray(units: PressureUnits): Int {
        return when (units) {
            PressureUnits.Hpa -> R.array.storm_sensitivity_entries_hpa
            PressureUnits.Inhg -> R.array.storm_sensitivity_entries_in
            PressureUnits.Psi -> R.array.storm_sensitivity_entries_psi
            else -> R.array.storm_sensitivity_entries_mbar
        }
    }

}