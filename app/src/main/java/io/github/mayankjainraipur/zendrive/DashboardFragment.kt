package io.github.mayankjainraipur.zendrive

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The screen the app opens on. Answers the three questions a vehicle owner actually has —
 * what needs attention, what is this costing, and how is each vehicle doing — rather than
 * presenting a list and waiting to be asked.
 */
class DashboardFragment : Fragment() {

    private lateinit var dueContainer: LinearLayout
    private lateinit var vehicleContainer: LinearLayout
    private lateinit var tvNothingDue: TextView
    private lateinit var tvNoVehicles: TextView
    private lateinit var tvThisMonth: TextView
    private lateinit var tvMonthChange: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dueContainer = view.findViewById(R.id.dueContainer)
        vehicleContainer = view.findViewById(R.id.vehicleContainer)
        tvNothingDue = view.findViewById(R.id.tvNothingDue)
        tvNoVehicles = view.findViewById(R.id.tvNoVehicles)
        tvThisMonth = view.findViewById(R.id.tvThisMonth)
        tvMonthChange = view.findViewById(R.id.tvMonthChange)

        view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_search) {
                    startActivity(Intent(requireContext(), SearchActivity::class.java))
                    true
                } else {
                    false
                }
            }
    }

    // Reloads on every return so figures are never stale after adding an event elsewhere.
    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val db = AppDatabase.getInstance(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val vehicles = db.vehicleDao().getAllVehicles().filterNot { it.isArchived }
            val vehiclesById = vehicles.associateBy { it.id }

            bindDue(db.reminderDao().getUpcoming(DUE_LIMIT), vehiclesById)
            bindSpending(db)
            bindVehicles(db, vehicles)
        }
    }

    private fun bindDue(reminders: List<Reminder>, vehiclesById: Map<Int, Vehicle>) {
        dueContainer.removeAllViews()
        tvNothingDue.visibility = if (reminders.isEmpty()) View.VISIBLE else View.GONE

        for (reminder in reminders) {
            val row = rowLayout()

            val labels = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            labels.addView(bodyText(reminder.title, R.color.text_primary, 15f))
            vehiclesById[reminder.vehicleId]?.let {
                labels.addView(bodyText(it.name, R.color.text_hint, 12f))
            }
            row.addView(labels)

            val days = daysUntil(reminder.dueAt)
            val (text, colour) = when {
                days < 0 -> getString(R.string.dashboard_overdue) to R.color.error
                days == 0 -> getString(R.string.dashboard_due_today) to R.color.warning
                else -> getString(R.string.dashboard_due_in, days) to R.color.text_secondary
            }
            row.addView(bodyText(text, colour, 13f, bold = days <= 0))

            dueContainer.addView(row)
        }
    }

    private suspend fun bindSpending(db: AppDatabase) {
        val thisMonth = monthRange(0)
        val lastMonth = monthRange(-1)

        val current = db.vehicleEventDao()
            .getTotalExpensesInRange(thisMonth.first, thisMonth.second) ?: 0.0
        val previous = db.vehicleEventDao()
            .getTotalExpensesInRange(lastMonth.first, lastMonth.second) ?: 0.0

        tvThisMonth.text = FormatUtil.formatCurrency(current)

        when {
            // No baseline to compare against, so say that rather than implying a 100% rise.
            previous <= 0.0 -> {
                tvMonthChange.text = getString(R.string.dashboard_no_last_month)
                tvMonthChange.setTextColor(requireContext().getColor(R.color.text_hint))
            }
            else -> {
                val changePercent = ((current - previous) / previous * 100).roundToInt()
                val (label, colour) = when {
                    changePercent > 0 ->
                        getString(R.string.dashboard_change_up, changePercent) to R.color.warning
                    changePercent < 0 ->
                        getString(R.string.dashboard_change_down, abs(changePercent)) to R.color.success
                    else -> getString(R.string.dashboard_change_same) to R.color.text_hint
                }
                tvMonthChange.text = label
                tvMonthChange.setTextColor(requireContext().getColor(colour))
            }
        }
    }

    private suspend fun bindVehicles(db: AppDatabase, vehicles: List<Vehicle>) {
        vehicleContainer.removeAllViews()
        tvNoVehicles.visibility = if (vehicles.isEmpty()) View.VISIBLE else View.GONE
        if (vehicles.isEmpty()) return

        val thisMonth = monthRange(0)
        val spendByVehicle = db.vehicleEventDao()
            .getExpensesByVehicle(thisMonth.first, thisMonth.second)
            .associate { it.vehicleId to it.totalCost }

        for (vehicle in vehicles) {
            val row = rowLayout()

            val labels = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            labels.addView(bodyText(vehicle.name, R.color.text_primary, 15f))
            labels.addView(
                bodyText(FormatUtil.formatDistance(vehicle.odometerReading), R.color.text_hint, 12f)
            )
            row.addView(labels)

            val spend = spendByVehicle[vehicle.id] ?: 0.0
            row.addView(
                bodyText(FormatUtil.formatCurrency(spend), R.color.text_secondary, 13f)
            )

            row.setOnClickListener {
                startActivity(
                    Intent(requireContext(), VehicleDetailActivity::class.java)
                        .putExtra("vehicleId", vehicle.id)
                )
            }
            vehicleContainer.addView(row)
        }
    }

    private fun rowLayout() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun bodyText(text: String, colourRes: Int, sizeSp: Float, bold: Boolean = false) =
        TextView(requireContext()).apply {
            this.text = text
            setTextColor(requireContext().getColor(colourRes))
            textSize = sizeSp
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    /** Whole calendar days, so something due late today reads as today rather than tomorrow. */
    private fun daysUntil(dueAt: Long): Int {
        val today = startOfDay(System.currentTimeMillis())
        val due = startOfDay(dueAt)
        return TimeUnit.MILLISECONDS.toDays(due - today).toInt()
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Start and end of the calendar month [offset] months from now. */
    private fun monthRange(offset: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            add(Calendar.MONTH, offset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
            add(Calendar.MILLISECOND, -1)
        }
        return start.timeInMillis to end.timeInMillis
    }

    companion object {
        private const val DUE_LIMIT = 5
    }
}
