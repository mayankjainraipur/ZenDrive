package com.example.zendrive

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

class ExpensesFragment : Fragment() {

    private lateinit var viewModel: LogViewModel
    private lateinit var adapter: ExpenseAdapter

    private var vehicles: List<Vehicle> = emptyList()
    private var selectedVehicle: Vehicle? = null
    private var selectedTimeRange: TimeRange = TimeRange.ALL_TIME
    private var currencyCode: String = "INR"

    private lateinit var vehicleSelector: AutoCompleteTextView
    private lateinit var timeRangeSelector: AutoCompleteTextView
    private lateinit var vehicleSelectorLayout: TextInputLayout
    private lateinit var timeRangeLayout: TextInputLayout
    private lateinit var recyclerExpenses: RecyclerView
    private lateinit var tvTotalExpenses: TextView
    private lateinit var emptyNoVehicles: LinearLayout
    private lateinit var emptyNoExpenses: LinearLayout

    private lateinit var cardFuelStats: MaterialCardView
    private lateinit var tvCostPerKm: TextView
    private lateinit var tvTotalFuelCost: TextView
    private lateinit var tvTotalDistance: TextView
    private lateinit var rowTotalDistance: LinearLayout
    private lateinit var tvFuelFills: TextView
    private lateinit var tvNoFuelData: TextView
    private lateinit var cardCategoryBreakdown: MaterialCardView
    private lateinit var categoryContainer: LinearLayout

    enum class TimeRange(val labelResId: Int) {
        LAST_1_MONTH(R.string.time_last_1_month),
        LAST_3_MONTHS(R.string.time_last_3_months),
        LAST_6_MONTHS(R.string.time_last_6_months),
        LAST_1_YEAR(R.string.time_last_1_year),
        ALL_TIME(R.string.time_all_time)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_expenses, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity()
        val db = AppDatabase.getInstance(activity)
        val factory = ViewModelFactory(
            db.vehicleDao(),
            db.vehicleEventDao(),
            db.eventMetaDao(),
            db.userProfileDao()
        )
        viewModel = ViewModelProvider(activity, factory)[LogViewModel::class.java]

        vehicleSelector = view.findViewById(R.id.spinnerVehicle)
        timeRangeSelector = view.findViewById(R.id.spinnerTimeRange)
        vehicleSelectorLayout = view.findViewById(R.id.vehicleSelectorLayout)
        timeRangeLayout = view.findViewById(R.id.timeRangeLayout)
        recyclerExpenses = view.findViewById(R.id.recyclerExpenses)
        tvTotalExpenses = view.findViewById(R.id.tvTotalExpenses)
        emptyNoVehicles = view.findViewById(R.id.emptyNoVehicles)
        emptyNoExpenses = view.findViewById(R.id.emptyNoExpenses)

        cardFuelStats = view.findViewById(R.id.cardFuelStats)
        tvCostPerKm = view.findViewById(R.id.tvCostPerKm)
        tvTotalFuelCost = view.findViewById(R.id.tvTotalFuelCost)
        tvTotalDistance = view.findViewById(R.id.tvTotalDistance)
        rowTotalDistance = view.findViewById(R.id.rowTotalDistance)
        tvFuelFills = view.findViewById(R.id.tvFuelFills)
        tvNoFuelData = view.findViewById(R.id.tvNoFuelData)
        cardCategoryBreakdown = view.findViewById(R.id.cardCategoryBreakdown)
        categoryContainer = view.findViewById(R.id.categoryContainer)

        adapter = ExpenseAdapter(currencyCode)
        recyclerExpenses.layoutManager = LinearLayoutManager(activity)
        recyclerExpenses.adapter = adapter

        setupTimeRangeSelector()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userProfileFlow.collectLatest { profile ->
                val newCode = profile?.preferredCurrencyCode?.takeIf { it.isNotBlank() } ?: "INR"
                if (currencyCode != newCode) {
                    currencyCode = newCode
                    adapter.updateCurrency(newCode)
                }
                loadExpenses()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.vehicles.collectLatest { list ->
                vehicles = list
                setupVehicleSelector()
                updateUiState()
            }
        }

    }

    private fun setupTimeRangeSelector() {
        val timeRanges = TimeRange.entries.map { getString(it.labelResId) }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, timeRanges)
        timeRangeSelector.setAdapter(adapter)
        timeRangeSelector.setText(getString(TimeRange.ALL_TIME.labelResId), false)
        timeRangeSelector.setOnItemClickListener { _, _, position, _ ->
            selectedTimeRange = TimeRange.entries[position]
            loadExpenses()
        }
    }

    private fun setupVehicleSelector() {
        if (vehicles.isEmpty()) {
            vehicleSelectorLayout.isEnabled = false
            vehicleSelector.setText("", false)
            return
        }

        vehicleSelectorLayout.isEnabled = true
        val vehicleNames = vehicles.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, vehicleNames)
        vehicleSelector.setAdapter(adapter)

        // Select first vehicle by default
        if (selectedVehicle == null && vehicles.isNotEmpty()) {
            selectedVehicle = vehicles.first()
            vehicleSelector.setText(vehicles.first().name, false)
            loadExpenses()
        }

        vehicleSelector.setOnItemClickListener { _, _, position, _ ->
            selectedVehicle = vehicles[position]
            loadExpenses()
        }
    }

    private fun updateUiState() {
        when {
            vehicles.isEmpty() -> {
                emptyNoVehicles.visibility = View.VISIBLE
                emptyNoExpenses.visibility = View.GONE
                recyclerExpenses.visibility = View.GONE
                vehicleSelectorLayout.visibility = View.GONE
                timeRangeLayout.visibility = View.GONE
                cardFuelStats.visibility = View.GONE
                cardCategoryBreakdown.visibility = View.GONE
                tvTotalExpenses.text = getString(R.string.no_expenses_recorded)
            }
            else -> {
                emptyNoVehicles.visibility = View.GONE
                vehicleSelectorLayout.visibility = View.VISIBLE
                timeRangeLayout.visibility = View.VISIBLE
                loadExpenses()
            }
        }
    }

    private fun loadExpenses() {
        val vehicle = selectedVehicle ?: return
        val (startDate, endDate) = getDateRangeForTimeRange(selectedTimeRange)

        viewLifecycleOwner.lifecycleScope.launch {
            val expenses = viewModel.getExpensesForVehicleInRange(vehicle.id, startDate, endDate)
            val total = viewModel.getTotalExpensesForVehicleInRange(vehicle.id, startDate, endDate) ?: 0.0

            tvTotalExpenses.text = getString(
                R.string.expense_total,
                FormatUtil.formatCurrency(total, currencyCode)
            )

            if (expenses.isEmpty()) {
                emptyNoExpenses.visibility = View.VISIBLE
                recyclerExpenses.visibility = View.GONE
            } else {
                emptyNoExpenses.visibility = View.GONE
                recyclerExpenses.visibility = View.VISIBLE
                adapter.submitList(expenses)
            }

            loadFuelStats(vehicle.id)
            loadCategoryBreakdown(vehicle.id, startDate, endDate)
        }
    }

    private suspend fun loadFuelStats(vehicleId: Int) {
        val stats = viewModel.calculateFuelStats(vehicleId)

        if (stats.fillCount == 0) {
            cardFuelStats.visibility = View.GONE
            return
        }

        cardFuelStats.visibility = View.VISIBLE

        if (stats.avgCostPerKm != null) {
            tvCostPerKm.text = getString(
                R.string.cost_per_km_value,
                FormatUtil.formatCurrency(stats.avgCostPerKm, currencyCode)
            )
            tvNoFuelData.visibility = View.GONE
        } else {
            tvCostPerKm.text = "—"
            tvNoFuelData.visibility = View.VISIBLE
        }

        tvTotalFuelCost.text = FormatUtil.formatCurrency(stats.totalFuelCost, currencyCode)

        if (stats.totalDistance != null) {
            rowTotalDistance.visibility = View.VISIBLE
            tvTotalDistance.text = FormatUtil.formatDistance(stats.totalDistance)
        } else {
            rowTotalDistance.visibility = View.GONE
        }

        tvFuelFills.text = getString(R.string.fuel_fills_count, stats.fillCount)
    }

    private suspend fun loadCategoryBreakdown(vehicleId: Int, startDate: Long, endDate: Long) {
        val categories = viewModel.getCategoryBreakdown(vehicleId, startDate, endDate)

        if (categories.isEmpty()) {
            cardCategoryBreakdown.visibility = View.GONE
            return
        }

        cardCategoryBreakdown.visibility = View.VISIBLE
        categoryContainer.removeAllViews()

        val totalCost = categories.sumOf { it.totalCost }

        for (category in categories) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8.dpToPx() }
            }

            val icon = TextView(requireContext()).apply {
                text = getCategoryIcon(category.eventType)
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 8.dpToPx() }
            }

            val label = TextView(requireContext()).apply {
                text = category.eventType.replaceFirstChar { it.uppercase() }
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_secondary, null))
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val amount = TextView(requireContext()).apply {
                text = FormatUtil.formatCurrency(category.totalCost, currencyCode)
                textSize = 14f
                setTextColor(resources.getColor(R.color.text_primary, null))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val pct = if (totalCost > 0) (category.totalCost / totalCost * 100) else 0.0
            val percent = TextView(requireContext()).apply {
                text = String.format(Locale.getDefault(), " (%.0f%%)", pct)
                textSize = 12f
                setTextColor(resources.getColor(R.color.text_secondary, null))
            }

            row.addView(icon)
            row.addView(label)
            row.addView(amount)
            row.addView(percent)
            categoryContainer.addView(row)
        }
    }

    private fun getCategoryIcon(eventType: String): String = when (eventType.lowercase()) {
        "fuel" -> "\u26FD"
        "service" -> "\u2699\uFE0F"
        "repair" -> "\uD83D\uDD27"
        "insurance" -> "\uD83D\uDCDC"
        "tax" -> "\uD83D\uDCB8"
        else -> "\uD83D\uDED2"
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density + 0.5f).toInt()

    private fun getDateRangeForTimeRange(timeRange: TimeRange): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val endDate = calendar.timeInMillis

        when (timeRange) {
            TimeRange.LAST_1_MONTH -> calendar.add(Calendar.MONTH, -1)
            TimeRange.LAST_3_MONTHS -> calendar.add(Calendar.MONTH, -3)
            TimeRange.LAST_6_MONTHS -> calendar.add(Calendar.MONTH, -6)
            TimeRange.LAST_1_YEAR -> calendar.add(Calendar.YEAR, -1)
            TimeRange.ALL_TIME -> {
                // Return very old date for all time
                calendar.set(1970, 0, 1)
            }
        }

        return Pair(calendar.timeInMillis, endDate)
    }
}
