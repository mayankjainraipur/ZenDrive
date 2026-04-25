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
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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

        adapter = ExpenseAdapter(currencyCode)
        recyclerExpenses.layoutManager = LinearLayoutManager(activity)
        recyclerExpenses.adapter = adapter

        setupTimeRangeSelector()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userProfileFlow.collectLatest { profile ->
                currencyCode = profile?.preferredCurrencyCode?.takeIf { it.isNotBlank() } ?: "INR"
                adapter = ExpenseAdapter(currencyCode)
                recyclerExpenses.adapter = adapter
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

        viewModel.fetchVehicles()
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

            tvTotalExpenses.text = getString(R.string.expense_total, "$currencyCode ${String.format("%,.2f", total)}")

            if (expenses.isEmpty()) {
                emptyNoExpenses.visibility = View.VISIBLE
                recyclerExpenses.visibility = View.GONE
            } else {
                emptyNoExpenses.visibility = View.GONE
                recyclerExpenses.visibility = View.VISIBLE
                adapter.submitList(expenses)
            }
        }
    }

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

    override fun onResume() {
        super.onResume()
        viewModel.fetchVehicles()
    }
}
