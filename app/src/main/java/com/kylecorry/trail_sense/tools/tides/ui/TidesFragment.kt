package com.kylecorry.trail_sense.tools.tides.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.kylecorry.andromeda.alerts.Alerts
import com.kylecorry.andromeda.core.time.Timer
import com.kylecorry.andromeda.fragments.BoundFragment
import com.kylecorry.andromeda.list.ListView
import com.kylecorry.andromeda.pickers.Pickers
import com.kylecorry.sol.science.oceanography.OceanographyService
import com.kylecorry.sol.science.oceanography.TidalRange
import com.kylecorry.sol.science.oceanography.TideType
import com.kylecorry.sol.units.Reading
import com.kylecorry.trail_sense.R
import com.kylecorry.trail_sense.databinding.FragmentTideBinding
import com.kylecorry.trail_sense.databinding.ListItemTideBinding
import com.kylecorry.trail_sense.shared.CustomUiUtils
import com.kylecorry.trail_sense.shared.FormatService
import com.kylecorry.trail_sense.tools.tides.domain.TideEntity
import com.kylecorry.trail_sense.tools.tides.domain.TideLoaderFactory
import com.kylecorry.trail_sense.tools.tides.domain.TideService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate


class TidesFragment : BoundFragment<FragmentTideBinding>() {

    private val oceanService = OceanographyService()
    private val formatService by lazy { FormatService(requireContext()) }
    private var displayDate = LocalDate.now()
    private val tideService = TideService()
    private lateinit var tideList: ListView<Pair<String, String>>
    private var tide: TideEntity? = null
    private lateinit var chart: TideChart
    private var waterLevels = listOf<Reading<Float>>()
    private val updateCurrentTideTimer = Timer {
        updateCurrentTide()
    }

    override fun generateBinding(
        layoutInflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentTideBinding {
        return FragmentTideBinding.inflate(layoutInflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chart = TideChart(binding.chart)
        tideList = ListView(binding.tideList, R.layout.list_item_tide) { itemView, tide ->
            val tideBinding = ListItemTideBinding.bind(itemView)
            tideBinding.tideType.text = tide.first
            tideBinding.tideTime.text = tide.second
        }

        binding.tideListDateText.text = formatService.formatRelativeDate(displayDate)

        CustomUiUtils.setButtonState(binding.tideCalibration, false)
        binding.tideCalibration.setOnClickListener {
            findNavController().navigate(R.id.action_tides_to_tideList)
        }
        binding.tideListDatePicker.setOnClickListener {
            Pickers.date(requireContext(), displayDate) {
                if (it != null) {
                    displayDate = it
                    onDisplayDateChanged()
                }
            }
        }

        binding.tideListDatePicker.setOnLongClickListener {
            displayDate = LocalDate.now()
            onDisplayDateChanged()
            true
        }

        binding.loading.isVisible = true
        runInBackground {
            val loader = TideLoaderFactory().getTideLoader(requireContext())
            tide = loader.getReferenceTide()
            withContext(Dispatchers.Main) {
                if (isBound) {
                    binding.loading.isVisible = false
                    if (tide == null) {
                        Alerts.dialog(
                            requireContext(),
                            getString(R.string.no_tides),
                            getString(R.string.calibrate_new_tide)
                        ) { cancelled ->
                            if (!cancelled) {
                                findNavController().navigate(R.id.action_tides_to_tideList)
                            }
                        }
                    } else {
                        onTideLoaded()
                    }
                }
            }
        }

        binding.nextDate.setOnClickListener {
            displayDate = displayDate.plusDays(1)
            onDisplayDateChanged()
        }

        binding.prevDate.setOnClickListener {
            displayDate = displayDate.minusDays(1)
            onDisplayDateChanged()
        }


        scheduleUpdates(Duration.ofSeconds(1))
    }

    private fun onTideLoaded() {
        if (!isBound) {
            return
        }
        val tide = tide ?: return
        binding.tideLocation.text = tide.name
            ?: if (tide.coordinate != null) formatService.formatLocation(tide.coordinate!!) else getString(
                android.R.string.untitled
            )
        updateTideChart()
        updateTideTable()
        updateCurrentTide()
    }

    private fun onDisplayDateChanged() {
        if (!isBound) {
            return
        }
        updateTideChart()
        updateTideTable()
        updateCurrentTide()
        binding.tideListDateText.text = formatService.formatRelativeDate(displayDate)
    }

    private fun updateTideChart() {
        val tide = tide ?: return
        runInBackground {
            waterLevels = withContext(Dispatchers.Default) {
                tideService.getWaterLevels(tide, displayDate)
            }
            withContext(Dispatchers.Main) {
                if (!isBound) {
                    return@withContext
                }
                chart.plot(waterLevels)
            }
        }

    }

    private fun updateTideTable() {
        val tide = tide ?: return
        runInBackground {
            val tides = withContext(Dispatchers.Default) {
                tideService.getTides(tide, displayDate)
            }

            withContext(Dispatchers.Main) {
                if (!isBound) {
                    return@withContext
                }
                val tideStrings = tides.map {
                    val type = if (it.type == TideType.High) {
                        getString(R.string.high_tide)
                    } else {
                        getString(R.string.low_tide)
                    }
                    val time = formatService.formatTime(it.time.toLocalTime(), false)
                    type to time
                }
                tideList.setData(tideStrings)
            }
        }
    }

    private fun updateCurrentTide() {
        val tide = tide ?: return
        runInBackground {
            val current = withContext(Dispatchers.Default) {
                tideService.getCurrentTide(tide)
            }
            val isRising = withContext(Dispatchers.Default) {
                tideService.isRising(tide)
            }
            withContext(Dispatchers.Main) {
                if (!isBound) {
                    return@withContext
                }
                binding.tideHeight.text = getTideTypeName(current)
                // TODO: Draw position on chart
                val currentLevel = waterLevels.minByOrNull {
                    Duration.between(Instant.now(), it.time).abs()
                }
                val currentIdx = waterLevels.indexOf(currentLevel)
                val point = chart.getPoint(currentIdx)
                binding.position.isInvisible = point.x == binding.chart.x && point.y == binding.chart.y || displayDate != LocalDate.now()
                binding.position.x = point.x - binding.position.width / 2f
                binding.position.y = point.y - binding.position.height / 2f
                binding.tideTendency.isVisible = true
                binding.tideTendency.setImageResource(if (isRising) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down)
            }
        }
    }

    override fun onUpdate() {
        super.onUpdate()
        updateCurrentTide()
    }

    private fun getTidalRangeName(range: TidalRange): String {
        return when (range) {
            TidalRange.Neap -> getString(R.string.tide_neap)
            TidalRange.Spring -> getString(R.string.tide_spring)
            TidalRange.Normal -> getString(R.string.tide_normal)
        }
    }

    private fun getTideTypeName(tideType: TideType): String {
        return when (tideType) {
            TideType.High -> getString(R.string.high_tide)
            TideType.Low -> getString(R.string.low_tide)
            TideType.Half -> getString(R.string.half_tide)
        }
    }

}