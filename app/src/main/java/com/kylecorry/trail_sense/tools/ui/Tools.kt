package com.kylecorry.trail_sense.tools.ui

import android.content.Context
import android.hardware.Sensor
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import com.kylecorry.andromeda.sense.Sensors
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.shared.UserPreferences
import com.kylecorry.trail_sense.shared.extensions.isDebug
import com.kylecorry.trail_sense.shared.sensors.SensorService

data class ToolGroup(val name: String, val tools: List<Tool>)
data class Tool(
    val name: String,
    @DrawableRes val icon: Int,
    @IdRes val navAction: Int,
    val description: String? = null
)

object Tools {
    fun getTools(context: Context): List<ToolGroup> {
        val hasLightMeter = Sensors.hasSensor(context, Sensor.TYPE_LIGHT)
        val hasPedometer = Sensors.hasSensor(context, Sensor.TYPE_STEP_COUNTER)
        val hasCompass = SensorService(context).hasCompass()
        val hasBarometer = Sensors.hasBarometer(context)
        val prefs = UserPreferences(context)
        val signaling = ToolGroup(
            context.getString(R.string.tool_category_signaling), listOf(
                Tool(
                    context.getString(R.string.flashlight_title),
                    R.drawable.flashlight,
                    R.id.action_action_experimental_tools_to_fragmentToolFlashlight,
                ),
                Tool(
                    context.getString(R.string.tool_whistle_title),
                    R.drawable.ic_tool_whistle,
                    R.id.action_action_experimental_tools_to_toolWhistleFragment,
                )
            )
        )
        val distance = ToolGroup(
            context.getString(R.string.distance), listOfNotNull(
                Tool(
                    context.getString(R.string.tool_ruler_title),
                    R.drawable.ruler,
                    R.id.action_action_experimental_tools_to_rulerFragment
                ),
                if (hasPedometer) Tool(
                    context.getString(R.string.pedometer),
                    R.drawable.steps,
                    R.id.action_tools_to_pedometer
                ) else null,
                if (prefs.isCliffHeightEnabled) Tool(
                    context.getString(R.string.tool_cliff_height_title),
                    R.drawable.ic_tool_cliff_height,
                    R.id.action_action_experimental_tools_to_toolCliffHeightFragment,
                    context.getString(R.string.experimental) + " - " + context.getString(R.string.tool_cliff_height_description)
                ) else null
            )
        )

        val location = ToolGroup(
            context.getString(R.string.location), listOfNotNull(
                Tool(
                    context.getString(R.string.navigation),
                    R.drawable.ic_compass_icon,
                    R.id.action_navigation
                ),
                Tool(
                    context.getString(R.string.beacons),
                    R.drawable.ic_location,
                    R.id.action_tools_to_beacons
                ),
                Tool(
                    context.getString(R.string.photo_maps),
                    R.drawable.maps,
                    R.id.action_tools_to_maps_list,
                    context.getString(R.string.photo_map_summary)
                ),
                Tool(
                    context.getString(R.string.paths),
                    R.drawable.ic_tool_backtrack,
                    R.id.action_action_experimental_tools_to_fragmentBacktrack
                ),
                Tool(
                    context.getString(R.string.tool_triangulate_title),
                    R.drawable.ic_tool_triangulate,
                    R.id.action_action_experimental_tools_to_fragmentToolTriangulate
                ),
            )
        )

        val angles = ToolGroup(
            context.getString(R.string.tool_category_angles), listOfNotNull(
                Tool(
                    context.getString(R.string.clinometer_title),
                    R.drawable.clinometer,
                    R.id.action_toolsFragment_to_clinometerFragment,
                    context.getString(R.string.tool_clinometer_summary)
                ),
                Tool(
                    context.getString(R.string.tool_bubble_level_title),
                    R.drawable.level,
                    R.id.action_action_experimental_tools_to_levelFragment,
                    context.getString(R.string.tool_bubble_level_summary)
                )
            )
        )

        val time = ToolGroup(
            context.getString(R.string.time), listOfNotNull(
                Tool(
                    context.getString(R.string.tool_clock_title),
                    R.drawable.ic_tool_clock,
                    R.id.action_action_experimental_tools_to_toolClockFragment
                ),
                Tool(
                    context.getString(R.string.astronomy),
                    R.drawable.ic_astronomy,
                    R.id.action_astronomy
                ),
                Tool(
                    context.getString(R.string.water_boil_timer),
                    R.drawable.ic_tool_boil,
                    R.id.action_action_experimental_tools_to_waterPurificationFragment,
                    context.getString(R.string.tool_boil_summary)
                ),
                Tool(
                    context.getString(R.string.tides),
                    R.drawable.ic_tide_table,
                    R.id.action_toolsFragment_to_tidesFragment
                )
            )
        )

        val power = ToolGroup(
            context.getString(R.string.power), listOfNotNull(
                Tool(
                    context.getString(R.string.tool_battery_title),
                    R.drawable.ic_tool_battery,
                    R.id.action_action_experimental_tools_to_fragmentToolBattery
                ),
                if (hasCompass) Tool(
                    context.getString(R.string.tool_solar_panel_title),
                    R.drawable.ic_tool_solar_panel,
                    R.id.action_action_experimental_tools_to_fragmentToolSolarPanel,
                    context.getString(R.string.tool_solar_panel_summary)
                ) else null,
                if (hasLightMeter) Tool(
                    context.getString(R.string.tool_light_meter_title),
                    R.drawable.flashlight,
                    R.id.action_toolsFragment_to_toolLightFragment,
                    context.getString(R.string.guide_light_meter_description)
                ) else null
            )
        )

        val weather = ToolGroup(
            context.getString(R.string.weather), listOfNotNull(
                if (hasBarometer) Tool(
                    context.getString(R.string.weather),
                    R.drawable.cloud,
                    R.id.action_weather
                ) else null,
                Tool(
                    context.getString(R.string.tool_climate),
                    R.drawable.ic_temperature_range,
                    R.id.action_toolsFragment_to_toolClimate,
                    context.getString(R.string.tool_climate_summary)
                ),
                Tool(
                    context.getString(R.string.tool_temperature_estimation_title),
                    R.drawable.thermometer,
                    R.id.action_tools_to_temperature_estimation,
                    context.getString(R.string.tool_temperature_estimation_description)
                ),
                Tool(
                    context.getString(R.string.clouds),
                    R.drawable.ic_tool_clouds,
                    R.id.action_action_experimental_tools_to_cloudFragment
                ),
                Tool(
                    context.getString(R.string.tool_lightning_title),
                    R.drawable.ic_torch_on,
                    R.id.action_action_experimental_tools_to_fragmentToolLightning,
                    context.getString(R.string.tool_lightning_description)
                )
            )
        )

        val other = ToolGroup(
            context.getString(R.string.other), listOfNotNull(
                if (prefs.isAugmentedRealityEnabled && hasCompass) Tool(
                    context.getString(R.string.augmented_reality),
                    R.drawable.ic_camera,
                    R.id.action_tools_to_augmented_reality,
                    context.getString(R.string.augmented_reality_description)
                ) else null,
                Tool(
                    context.getString(R.string.convert),
                    R.drawable.ic_tool_distance_convert,
                    R.id.action_toolsFragment_to_toolConvertFragment
                ),
                Tool(
                    context.getString(R.string.packing_lists),
                    R.drawable.ic_tool_pack,
                    R.id.action_action_experimental_tools_to_action_inventory
                ),
                if (hasCompass) Tool(
                    context.getString(R.string.tool_metal_detector_title),
                    R.drawable.ic_tool_metal_detector,
                    R.id.action_action_experimental_tools_to_fragmentToolMetalDetector
                ) else null,
                Tool(
                    context.getString(R.string.tool_white_noise_title),
                    R.drawable.ic_tool_white_noise,
                    R.id.action_action_experimental_tools_to_fragmentToolWhiteNoise,
                    context.getString(R.string.tool_white_noise_summary)
                ),
                Tool(
                    context.getString(R.string.tool_notes_title),
                    R.drawable.ic_tool_notes,
                    R.id.action_action_experimental_tools_to_fragmentToolNotes
                ),
                Tool(
                    context.getString(R.string.qr_code_scanner),
                    R.drawable.ic_qr_code,
                    R.id.action_tools_to_qr
                ),
                Tool(
                    context.getString(R.string.sensors),
                    R.drawable.ic_sensors,
                    R.id.sensorDetailsFragment
                ),
                Tool(
                    context.getString(R.string.diagnostics),
                    R.drawable.ic_alert,
                    R.id.action_tools_to_diagnostics
                ),
                Tool(
                    context.getString(R.string.settings),
                    R.drawable.ic_settings,
                    R.id.action_settings
                ),
                Tool(
                    context.getString(R.string.tool_user_guide_title),
                    R.drawable.ic_user_guide,
                    R.id.action_action_experimental_tools_to_guideListFragment,
                    context.getString(R.string.tool_user_guide_summary)
                )
            )
        )

        return listOf(
            signaling,
            distance,
            location,
            angles,
            time,
            power,
            weather,
            other
        )

    }
}
